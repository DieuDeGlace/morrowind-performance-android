# Android terrain shadow correction (performance release 18)

The tracked shadows_fragment.glsl and terrain.frag overlay the corresponding
files generated from the pinned e49b43d56f6d64768429a592dda631eb9a513506 engine.
All existing Android manual-comparison, PCF quality, vertex interface, and V6
object/actor fixes are retained. Only terrain defines the new receiver path.

Terrain compares each PCF tap against its geometric triangle plane instead of
clipping that reconstruction to a fixed 0.003 depth difference. A bounded
geometric texel-coverage bias addresses small caster/receiver depth differences.
The launcher specializes Standard (0.55), Stronger (0.85), or Legacy (0.0).
Legacy retains the prior V6 result; object/actor receivers keep V6 in all modes.
The existing 0.003 global-bias budget is preserved. No shadow caster is disabled.

This shader-only correction leaves the native performance engine unchanged.
Synthetic GPU/GLSL ES checks are recorded in the release notes; physical-device
terrain, contact shadow and low-sun testing remains necessary.
