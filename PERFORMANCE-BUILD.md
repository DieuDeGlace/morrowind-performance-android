# OpenMW Performance for Android

This update starts from the current Andiweli/OpenMW-Android port and its supported OpenMW 0.51.0 Final engine. It adds Intel Masked Occlusion Culling for terrain, static objects and object paging, plus temporal shadow reuse. The desktop companion follows OpenMW 0.53 development; the Android build retains the port's current 0.51-specific Surface, GL4ES, shadows, input, OMWFX and graphics fixes.

The existing Android PCF shadow quality system, CPU sun-occlusion rays and active-grid paging are retained. Desktop hardware shadow-sampler filtering is replaced by the mobile port's existing PCF implementation. The ARM64 culler uses the preserved sse2neon implementation.

## Controls

Engine settings now include Software occlusion culling (enabled by default for this performance build). Restart the game after changing it. Shadow settings include Shadow update interval, defaulting to 2; choose Every frame to disable temporal reuse. The original shadow-enable and quality controls are unchanged. These are experimental features: check frame times and visual correctness on your own device and game setup.

## Build and provenance

The Android Performance Release workflow builds the native ARM64 engine from the exact archive and SHA-256 recorded in `source/buildscripts/performance-engine.json`, uses ThinLTO with two linker backends to limit memory use, builds a non-debuggable Release APK, verifies its ABI and newly compiled engine bytes, and signs it. `source/tools/build-performance-release.sh` performs the same steps on Linux with JDK 17, Android SDK 35, NDK r26b, and the native build dependencies listed in the workflow.

The engine source is published on the `codex/android-0.51-performance` branch of DieuDeGlace/morrowind-performance. All existing Android compatibility patches are included directly in that source. The five bundled OMWFX shader files are retained from the upstream 0.51.0-14 APK; their provenance and original credits are included in `bundled-android-assets`.

This app uses `com.dieudeglace.openmw.performance`, so it can be installed alongside the original port. Your own Morrowind data is required. Release signing credentials are kept outside Git in the local `.signing` folder and as GitHub Actions secrets; the APK contains the public signing certificate. Retain the local signing files for future updates.

Build verification does not substitute for testing gameplay and graphics on a physical Android device. No device-specific FPS claim is made.
