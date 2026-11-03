# OpenMW Performance for Android

This update starts from the current Andiweli/OpenMW-Android port and its supported OpenMW 0.51.0 Final engine. It adds Intel Masked Occlusion Culling for terrain, static objects and object paging, plus temporal shadow reuse. The desktop companion follows OpenMW 0.53 development; the Android build retains the port's current 0.51-specific Surface, GL4ES, shadows, input, OMWFX and graphics fixes.

The existing Android PCF shadow quality system, CPU sun-occlusion rays and active-grid paging are retained. Desktop hardware shadow-sampler filtering is replaced by the mobile port's existing PCF implementation. The ARM64 culler uses the preserved sse2neon implementation.

## Controls

Engine settings now include Software occlusion culling (enabled by default for this performance build). Restart the game after changing it. Shadow settings include Shadow update interval, defaulting to 2; choose Every frame to disable temporal reuse. The original shadow-enable and quality controls are unchanged. These are experimental features: check frame times and visual correctness on your own device and game setup.

## Build and provenance

The Android Performance Release workflow builds the native ARM64 engine from the exact archive and SHA-256 recorded in `source/buildscripts/performance-engine.json`, uses ThinLTO with two linker backends to limit memory use, builds a non-debuggable Release APK, verifies its ABI and newly compiled engine bytes, and signs it. `source/tools/build-performance-release.sh` performs the same steps on Linux with JDK 17, Android SDK 35, NDK r26b, and the native build dependencies listed in the workflow.

The engine source is published on the `codex/android-0.51-performance` branch of DieuDeGlace/morrowind-performance. All existing Android compatibility patches are included directly in that source. Five bundled OMWFX shaders and three compatibility shader files are retained from the upstream 0.51.0-14 APK. The compatibility files preserve projective water sampling and LocalMap object-fog handling that are absent from its source patch chain. Their provenance, hashes and original credits are included in `bundled-android-assets`.

This app uses `com.dieudeglace.openmw.performance`, so it can be installed alongside the original port. Your own Morrowind data is required. Release signing credentials are kept outside Git in the local `.signing` folder and as GitHub Actions secrets; the APK contains the public signing certificate. Retain the local signing files for future updates.

Build verification does not substitute for testing gameplay and graphics on a physical Android device. No device-specific FPS claim is made.


## Separate mod folders and shared configuration (0.51.0-17)

Open Mods and use **Add mod folder** (+). Select a folder containing the mod's meshes, textures, scripts or plugins. A package with a `Data Files` child is accepted too. Folders may be outside the Morrowind directory or on accessible SD storage; no files are copied into the vanilla game. The Directories tab enables/disables folders and sets their order by dragging. Folders lower in the list override earlier folders. Enable ESP/ESM plugins in the Plugins tab. Long-press a directory to unregister it without deleting its files.

Previously detected sibling mod folders and their enable/load-order state are imported on upgrade. Folder identities use full paths, so separate mods with the same folder name stay separate. If an enabled folder is disconnected, launch is blocked with its path rather than silently dropping mods. Disabled/offline entries keep their stored state.

The default editable configuration is `Documents/OpenMW-Performance/config` in shared storage. With shared storage permission it is prepared on the next game start. **Settings and config folder** can choose that default or another accessible folder. Existing `settings.cfg`, user `openmw.cfg`, bindings, shader settings and Lua settings are copied without removing the originals; existing files in the destination win. Saves and the engine resource cache keep their current location. Configuration reset affects only known OpenMW config files and never recursively deletes a shared folder or unrelated documents.

On Android 11+, **Shared storage access** opens Android's All files access page when needed. Grant access and return to the launcher; root is not required. This uses Android's [documented direct file access](https://developer.android.com/training/data-storage/manage-all-files). Use ordinary shared folders such as Documents or an SD-card folder rather than another app's Android/data directory.

Close the game before editing the shared files. The selected user `openmw.cfg` is loaded after the generated launcher config. The old app-private user-config location is excluded to prevent loading the copied options twice. Launcher-managed controls such as shadows, occlusion culling and resolution still apply at launch; other manual settings, including `[Camera] viewing distance`, remain in the shared `settings.cfg`.
