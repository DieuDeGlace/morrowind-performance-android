# Retained Android compatibility shaders

Source: Andiweli/OpenMW-Android upstream release APK `OpenMW.0.51-14.apk`, SHA-256 `db78a8b6076acb0cd3db59705539490292dc5c7a098cd76f7c9af1f773145540`.

These three text shaders preserve its resolution-independent water reflection/refraction coordinates and LocalMap object-fog bypass, which were present in the released assets but omitted from the source patch chain. All other engine shaders were compared with the upstream APK: differences were only comments or blank lines. Native engine code is built from the pinned performance source; no upstream native library is reused.

SHA-256 of the retained shader bytes:

- `libopenmw/resources/shaders/compatibility/water.vert`: `7bb912c71050ea3fa5d9dcb04ca039fbf35abe1f681e736653b9586991085852`
- `libopenmw/resources/shaders/compatibility/water.frag`: `b9a6eaf8204b36ad8434e329cfd8c97daa2bee5a32169504d038f9c0048af00f` (upstream `2baf7349608db8f0226087a42816793f1f8f00ad13ea52e060a8f9118406b029`, then changed for the 0.53 engine's packed ripple slopes, marked `OPENMW_ANDROID_053_PACKED_RIPPLES`, and to treat a scene depth in front of the surface as unusable, marked `OPENMW_ANDROID_053_WATER_DEPTH_GUARD`, to take the surface depth from view space instead of the medium-precision gl_FragCoord, marked `OPENMW_ANDROID_053_VIEW_SPACE_SURFACE_DEPTH`, and to output what it read on diagnostic frames, marked `OPENMW_ANDROID_053_WATER_DEBUG_READBACK`)
- `libopenmw/resources/shaders/compatibility/objects.frag`: `66ebedd140696620fa0114546f50108e5ed090483463ecf8c8a5d6b4c236a178`
