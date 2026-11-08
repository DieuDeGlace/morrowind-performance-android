# Android ragdoll test

This branch ports the desktop physics-test feature to the existing Android
OpenMW 0.51 performance engine. The preview uses the Android app ID
com.dieudeglace.openmw.ragdolltest and its own Documents/OpenMW-Physics-Test
configuration folder. It reads the Morrowind installation selected in its
launcher.

Aim at a supported corpse and tap Activate to loot. Hold the on-screen
Activate button for about a third of a second to grab the aimed body part.
Walk or look around while holding, then let go to drop the body. Hardware
Activate bindings use the same gesture. Melee attacks also push the struck
part. Corpses with unmapped skeletons use the original death animation.

The Android solver sleeps idle rigs and caps awake rigs at six. Its culling,
mobile shadows, and optimized native build follow the Android performance
baseline.

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


## Android 18: terrain shadow stripes

The user confirmed that disabling Terrain Shadows removes the ground bands.
Release 18 adds a terrain-only geometric PCF comparison and bounded coverage
correction, with a Shadows → Terrain shadow correction selector. Start with
Standard; use Stronger if some self-shadow stripes remain. Legacy restores the
previous receiver result. Keep Terrain Shadows enabled to test the fix.

Shared configuration and mod folders, the duplicate user-data fix, mobile PCF,
occlusion culling and temporal shadow reuse are retained. The native library
and engine source pin are unchanged. This is a shader/launcher update; actual
phone validation is still required. Original core shaders are refreshed by the
launcher each start; OMWFX shader changes alone do not replace this receiver.


## Release 19: selectable shadow softness

In the launcher open Advanced settings → Shadows → Shadow softness, choose
Original, Soft or Softer, and restart the game. Original preserves release 18
exactly. Soft/Softer use a continuous cubic 16-tap filter with two-/three-texel
spacing, independent of quality/map resolution. This costs more samples on
Low/Medium and preserves the High/Very High sample count. Wider filtering may
reduce small contact shadows. The terrain correction and Rafael compatibility
are retained. The native engine, culling and external mod/config support are
unchanged. Desktop GPU fixtures cover edge widths, exact original output,
ES100 compilation/rendering, and grazing terrain; Android device playtesting
remains necessary for device-specific driver behavior.

## Physics Preview 3: shared saves by default

The Android launcher now uses the selected shared configuration folder as the
default engine user-data folder. The default is
Documents/OpenMW-Physics-Test/config; saves go in its saves/<character>/ subfolders,
beside openmw.cfg and settings.cfg. Screenshots made by the engine also use this
shared user-data folder. The launcher calls this the Settings and saves folder.

On the first launch for a selected folder, existing saves in this app's old
app-specific storage and the previous configuration folder are copied there.
Original saves remain in place. Existing shared saves are not replaced:
identical saves are skipped, and differing same-name saves are retained under
an imported-<content hash>.omwsave filename with their original in-game save
description. A failed copy stops the launch without changing the selected
configuration path. Copying can safely be retried.

Explicit user-data entries in the shared openmw.cfg and explicit --user-data
command-line arguments keep their custom behavior. The launcher supplies the
shared-folder default when no custom override exists. It no longer silently
launches into private save storage when Documents is unavailable; enable Shared
storage access or choose another writable shared folder.

This update keeps application ID com.dieudeglace.openmw.ragdolltest and the same
release signing key, and increases versionCode to 5122. Install it over the current
physics preview without uninstalling. Shared saves survive normal app removal;
reselect the same shared folder if a later installation uses a custom location.
App-private mod-folder registrations and load order are not exported by this
save-storage change. The mod files themselves remain wherever you stored them.
Already-deleted saves cannot be recreated by this migration.
## Physics preview 5: animation-guided body morph physics

The native engine is pinned to f1d4f14f200a9d41503050f836a4d1d75d8d3beb, with source archive
SHA256 f3937443e59728218cb71648dc9bf8fd016f3f96dde458dc29cb6f82e277e8da.
Large deforming creature body meshes can now receive a death-only fitted skin,
including the kwama forager and applicable bonelord, netch and mixed body rigs.
The engine estimates bend regions and smooth weights from the installed model's
own animation. Original living morphs stay unchanged, the exact death pose is
preserved at handoff, and resurrection restores the original model. No modified
game assets are bundled. This is jointed rigid-body physics without elastic
impact squash/stretch. Facial/effect morphs and unsupported geometry retain
existing handling. Cached fitting and generated parts are bounded within the
existing actor limits.

VersionCode is 5124; application ID and release signing key remain the same.
Install over the existing physics preview without uninstalling. Shared config
and save defaults from prior previews remain in place.

## Physics preview 4: general actor and native item physics

The native engine is pinned to bba6c0dc9cd40e6f12dee1daabff20c26cc622e8 and shares the Windows
automatic actor-rig implementation. Known tuned skeletons remain preferred.
Other NPCs and creatures generate joints from skin bones or separate animated
mesh parts, with at most 24 generated bodies per actor. Unrigged/vertex-morphed
models (including the vanilla kwama forager) use whole-body collision, hits and
dragging. Independent limbs require actual usable joints. Body morphs are frozen
during physics and their controllers return on resurrection. Empty/invalid
models and the existing Android limits (6 awake, 32 retained rigs) can still
use ordinary death handling. Living actors keep their normal animation and movement.

This release keeps Preview 3's shared settings-and-saves behavior and the same
application ID and release signing key. VersionCode is 5123. Install over the
existing physics preview without uninstalling; the regular Performance app is
a separate application.

## Native item controls and compatibility

The native backend independently implements MaxYari/OpenMWLuaPhysics 1.4's
default Potato object rules (https://github.com/MaxYari/OpenMWLuaPhysics).
Clothing, armor, books, ingredients, miscellaneous items, potions, weapons,
apparatus, lockpicks, probes and repair tools can be pushed, grabbed and thrown.
Doors, containers, static scenery, activators and lights keep their ordinary
behavior. Items wake on interaction, sleep when settled, and collide with world
geometry and living actors. Item/item and item/corpse collisions are disabled.
Android limits native items to 16 awake and 64 retained bodies.

Tap Activate for normal pickup. Hold Activate to grab, release to drop, and use
Attack while holding to throw. Melee pushes eligible items. Item position and
rotation update normal world records and survive save/reload. Owned-item
disturbance/destruction uses the engine's crime detection.

Matching optional meshes/debris assets permit unscripted miscellaneous items to
break into bounded debris. No third-party assets are bundled; without matching
meshes, items remain intact. The full Lua API, optional Normal collision mode,
and Impact Effects package are not implemented by the native backend.

Native items yield automatically when LuaPhysicsEngine.omwscripts or its engine
scripts are configured. Disable that manifest to use native items; its installed
asset data folder can remain enabled for debris. Actor ragdolls remain available.
Preview 3's shared settings and saves default is preserved.
