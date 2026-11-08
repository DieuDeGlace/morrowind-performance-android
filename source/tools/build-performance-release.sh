#!/usr/bin/env bash
set -euo pipefail

project_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$project_root"
jobs="${1:-4}"
[[ "$jobs" =~ ^[1-9][0-9]*$ ]] || { echo 'Invalid job count' >&2; exit 2; }

phase="${2:-all}"
case "$phase" in all|build|sign) ;; *) echo 'Invalid build phase' >&2; exit 2 ;; esac
native_library="app/src/main/jniLibs/arm64-v8a/libopenmw.so"
sdk_root="${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}"
test -n "$sdk_root"
build_tools="$sdk_root/build-tools/35.0.0"

if [[ "$phase" != "sign" ]]; then
    python3 tools/test-android-base-config.py
    chmod +x gradlew buildscripts/build.sh buildscripts/include/*.sh
    (
        cd buildscripts
        ./build.sh --arch arm64 --release --lto --jobs "$jobs"
    ) 2>&1 | tee buildscripts/performance-native-build.log
    
    test -s "$native_library"
    sha256sum "$native_library" > buildscripts/openmw-051-patch39-libopenmw.sha256
    cp -R app/src/main/bundled-android-assets/android_omwfx app/src/main/assets/
    cp -R app/src/main/bundled-android-assets/libopenmw/. app/src/main/assets/libopenmw/
    cp buildscripts/performance-engine.json app/src/main/assets/libopenmw/openmw/performance-engine.json
    
    # Include the preserved culling implementation's distribution notices.
    engine_source="buildscripts/build/arm64/openmw-prefix/src/openmw"
    {
        printf '\n\nIntel Masked Occlusion Culling\n'
        cat "$engine_source/extern/maskedoc/license.txt"
        printf '\n\nsse2neon (license text in the source header)\n'
        sed -n '1,75p' "$engine_source/extern/maskedoc/sse2neon.h"
    } >> app/src/main/assets/libopenmw/3rdparty-licenses.txt
    
    readelf="buildscripts/toolchain/ndk/toolchains/llvm/prebuilt/linux-x86_64/bin/llvm-readelf"
    "$readelf" -h "$native_library" | grep -q 'AArch64'
    if "$readelf" -S "$native_library" | grep -Eq '\.debug_(info|line|str|abbrev)'; then
        echo 'Packaged native library still contains debug sections' >&2
        exit 3
    fi
    
    ./gradlew --no-daemon testMainlineReleaseUnitTest assembleMainlineRelease
fi
if [[ "$phase" == "build" ]]; then
    echo 'Unsigned ARM64 Release APK built and verified.'
    exit 0
fi

: "${ANDROID_RELEASE_KEYSTORE:?Release signing key path is required}"
: "${ANDROID_KEYSTORE_PASSWORD:?Release signing password is required}"
: "${ANDROID_KEY_ALIAS:=openmw-performance}"
release_root="$project_root/../release"
mkdir -p "$release_root"
apk_name='OpenMW-Android-0.51.0-ragdoll-preview-arm64.apk'
unsigned='app/build/outputs/apk/mainline/release/app-mainline-release-unsigned.apk'
test -s "$unsigned"
"$build_tools/zipalign" -p -f 4 "$unsigned" "$release_root/aligned.apk"
"$build_tools/apksigner" sign --ks "$ANDROID_RELEASE_KEYSTORE" \
    --ks-key-alias "$ANDROID_KEY_ALIAS" --ks-pass env:ANDROID_KEYSTORE_PASSWORD \
    --out "$release_root/$apk_name" "$release_root/aligned.apk"
"$build_tools/apksigner" verify --verbose --print-certs "$release_root/$apk_name" \
    | tee "$release_root/SIGNATURE.txt"
rm "$release_root/aligned.apk"
"$build_tools/aapt" dump badging "$release_root/$apk_name" | sed -n '1p' \
    > "$release_root/APK-INFO.txt"
grep -Fq "package: name='com.dieudeglace.openmw.ragdolltest'" "$release_root/APK-INFO.txt"
grep -Fq "versionCode='5125'" "$release_root/APK-INFO.txt"
python3 - "$native_library" "$release_root/$apk_name" <<'PY'
from pathlib import Path
from zipfile import ZipFile
import hashlib
import sys

native, apk = map(Path, sys.argv[1:])
with ZipFile(apk) as archive:
    abis = {n.split('/')[1] for n in archive.namelist() if n.startswith('lib/')}
    assert abis == {'arm64-v8a'}, abis
    packaged = archive.read('lib/arm64-v8a/libopenmw.so')
    assert hashlib.sha256(packaged).digest() == hashlib.sha256(native.read_bytes()).digest()
    assert archive.read('assets/libopenmw/openmw/performance-engine.json') == Path('buildscripts/performance-engine.json').read_bytes()
print('Verified APK ABI, newly built engine bytes, and performance source pin.')
PY
(
    cd "$release_root"
    sha256sum "$apk_name" > "$apk_name.sha256"
)
cp buildscripts/performance-engine.json "$release_root/ENGINE-PROVENANCE.json"
printf 'App commit %s\nNative build Release, ARM64, ThinLTO (2 backends)\n' "$(git rev-parse HEAD)" > "$release_root/BUILD-INFO.txt"
echo "Release ready: $release_root/$apk_name"
