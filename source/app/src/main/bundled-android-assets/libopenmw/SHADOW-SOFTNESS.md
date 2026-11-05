# Adjustable shadow softness

Launcher: Advanced settings → Shadows → Shadow softness. Restart the game.
Original retains release 18's exact quality kernels. Soft and Softer use a
continuous cubic 16-sample filter at two- and three-texel strides. Shadow-map
resolution and the terrain correction remain independent. Low/Medium use more
reads with softness enabled; High/Very High retain the same sample count.
Wider filtering can reduce small contact shadows. Rafael's post-processing
shaders can stay enabled; this changes the engine's shadow receiver filter.
The native engine, culling, signing identity and external config/mod support
are retained from release 18.
