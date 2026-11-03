#!/usr/bin/env bash
set -euo pipefail

if [[ $# -ne 3 ]]; then
    echo 'Usage: compose-android-config.sh ENGINE_CONFIG ANDROID_CONFIG OUTPUT' >&2
    exit 2
fi

# data= is composing and must retain resources/vfs-mw and other engine data.
# user-data is scalar and belongs to Android's app-specific storage mapping.
awk '!/^[[:space:]]*(data-local|user-data)[[:space:]]*=/' "$1" > "$3"
printf '\n' >> "$3"
cat "$2" >> "$3"

user_data_count=$(grep -Ec '^[[:space:]]*user-data[[:space:]]*=' "$3" || true)
if [[ "$user_data_count" -ne 1 ]]; then
    echo "Android base config must contain exactly one user-data entry; found $user_data_count." >&2
    exit 3
fi
