"""Render the packaged Android GLSL using equivalent translated shadow maps.

Requires moderngl and numpy. Linux uses headless EGL. This tests shader math,
not a particular Android driver or the complete game's rendering pipeline.
"""

import argparse
import json
from pathlib import Path
import re
import sys

import moderngl
import numpy as np


VERTEX = """#version 120
attribute vec2 position;
uniform vec2 mapShift;
uniform vec2 slope;
uniform vec2 stableSlope;
varying vec4 shadowSpaceCoords0;
varying vec2 shadowReceiverPlaneSlope0;
void main() {
    gl_Position = vec4(position, 0.0, 1.0);
    vec2 uv = vec2(0.5) + position * 0.025;
    shadowSpaceCoords0 = vec4(uv + mapShift, 0.5 + dot(slope, uv - vec2(0.5)), 1.0);
    shadowReceiverPlaneSlope0 = stableSlope;
}
"""


def expand(source, softness, quality, resolution, terrain, es):
    source = re.sub(
        r"@foreach shadow_texture_unit_index @shadow_texture_unit_list\s*(.*?)@endforeach",
        lambda match: match[1].replace("@shadow_texture_unit_index", "0"), source, flags=re.S,
    )
    for key, value in {"shadows_enabled": 1, "perspectiveShadowMaps": 0,
                       "limitShadowMapDistance": 0, "useShadowDebugOverlay": 0}.items():
        source = source.replace("@" + key, str(value))
    for key, value in {"SOFTNESS": softness, "PCF_LEVEL": quality, "MAP_RESOLUTION": f"{resolution}.0"}.items():
        source, count = re.subn(rf"(#define OPENMW_ANDROID_SHADOW_{key}) [\d.]+", rf"\g<1> {value}", source)
        assert count == 1, f"Missing or duplicated shader setting: {key}"
    assert "@" not in source, "Unexpanded shader template"
    header = "#version 120\n"
    if es:
        header = "#version 100\n#extension GL_OES_standard_derivatives : enable\nprecision highp float;\nprecision highp int;\n"
    if terrain:
        header += "#define OPENMW_ANDROID_TERRAIN_SHADOW_RECEIVER\n"
    return header + source + "\nvoid main() { gl_FragColor = vec4(vec3(unshadowedLightRatio(100.0)), 1.0); }\n"


class Receiver:
    def __init__(self, ctx):
        self.ctx = ctx
        self.size = 96
        self.output = ctx.texture((self.size, self.size), 4, dtype="f4")
        self.fbo = ctx.framebuffer([self.output])
        self.vbo = ctx.buffer(np.array([-1, -1, 3, -1, -1, 3], dtype="f4").tobytes())

    def render(self, source, softness=2, quality=2, resolution=4096, pattern=2,
               shift=(0, 0), axis=0, terrain=True, es=False):
        vertex = VERTEX.replace("#version 120", "#version 100\nprecision highp float;") if es else VERTEX
        fragment = expand(source, softness, quality, resolution, terrain, es)
        program = self.ctx.program(vertex_shader=vertex, fragment_shader=fragment)
        vao = self.ctx.vertex_array(program, [(self.vbo, "2f", "position")])
        coordinate = np.arange(resolution) - shift[axis]
        slopes = (0.0, 0.0)
        stable = slopes
        if pattern == "plane":
            uv = (np.arange(resolution, dtype="f4") + .5) / resolution
            depth = .5 + 4 * (uv[None, :] - .5) + 2 * (uv[:, None] - .5)
            depth -= .00035 * (.5 + .5 * np.sin((uv[None, :] - .475) * 1000))
            depth = np.clip(depth, 0, 1).astype("f4")
            slopes, stable = (4.0, 2.0), (.2, .1)
        else:
            blocked = coordinate < resolution // 2 if pattern == "broad" else coordinate % pattern == 0
            column = np.where(blocked, .25, .75).astype("f4")
            depth = np.broadcast_to(column if axis == 0 else column[:, None], (resolution, resolution))
        texture = self.ctx.texture((resolution, resolution), 1, depth.tobytes(), dtype="f4")
        texture.filter = (moderngl.NEAREST, moderngl.NEAREST)
        texture.repeat_x = texture.repeat_y = False
        texture.use(0)
        program["shadowTexture0"].value = 0
        program["mapShift"].value = tuple(value / resolution for value in shift)
        program["slope"].value = slopes
        if "stableSlope" in program:
            program["stableSlope"].value = stable
        self.fbo.use()
        self.ctx.viewport = (0, 0, self.size, self.size)
        vao.render(moderngl.TRIANGLES)
        pixels = np.frombuffer(self.fbo.read(components=1, dtype="f4"), dtype="f4").reshape(self.size, self.size).copy()
        assert np.isfinite(pixels).all(), "Non-finite shadow output"
        assert self.ctx.error == "GL_NO_ERROR"
        for resource in (vao, program, texture):
            resource.release()
        return pixels

    def release(self):
        for resource in (self.vbo, self.fbo, self.output):
            resource.release()


def translated_shadow(receiver, source, mode, axis=0, quality=2, resolution=4096):
    frames = []
    # Translate depth texels and receiver coordinates together. World geometry
    # and the sampled world shadow are identical in every frame.
    for amount in range(mode + 1):
        shift = (amount, 0) if axis == 0 else (0, amount)
        frames.append(receiver.render(source, softness=mode, quality=quality,
                                      resolution=resolution, pattern=mode, shift=shift, axis=axis))
    return {"softness": "soft" if mode == 2 else "softer", "axis": axis,
            "quality": quality, "resolution": resolution,
            "max_equivalent_shadow_change": max(float(np.abs(frame - frames[0]).max()) for frame in frames[1:]),
            "frame_means": [float(frame.mean()) for frame in frames]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--shader", type=Path, default=Path(__file__).resolve().parents[1] / "app/src/main/bundled-android-assets/libopenmw/resources/shaders/compatibility/shadows_fragment.glsl")
    parser.add_argument("--baseline", type=Path, help="Optional old shader, used only as a negative control")
    parser.add_argument("--report", type=Path)
    args = parser.parse_args()
    ctx = moderngl.create_standalone_context(require=330, **({"backend": "egl"} if sys.platform.startswith("linux") else {}))
    receiver = Receiver(ctx)
    source = args.shader.read_text(encoding="utf-8")
    translations = []
    for mode in (2, 3):
        for axis in (0, 1):
            result = translated_shadow(receiver, source, mode, axis)
            # Allows desktop/mobile floating-point variation; the old filter
            # changes by 1.0 (fully dark to fully lit) on this same fixture.
            assert result["max_equivalent_shadow_change"] < 1e-4, result
            translations.append(result)
    variants = 0
    for es in (False, True):
        for mode in (0, 2, 3):
            for quality in range(5):
                for terrain in (False, True):
                    broad = receiver.render(source, softness=mode, quality=quality, resolution=1024,
                                            pattern="broad", terrain=terrain, es=es)
                    assert float(broad[:, :20].max()) < .001, "Lost broad cast shadow"
                    assert float(broad[:, -20:].min()) > .999, "Darkened fully lit area"
                    variants += 1
    for mode in (0, 2, 3):
        for quality in range(1, 5):
            plane = receiver.render(source, softness=mode, quality=quality, resolution=1024, pattern="plane")
            assert float(plane.mean()) > .999, "Terrain receiver correction regressed"
    baseline = []
    if args.baseline:
        old = args.baseline.read_text(encoding="utf-8")
        baseline = [translated_shadow(receiver, old, mode) for mode in (2, 3)]
        assert all(result["max_equivalent_shadow_change"] > .5 for result in baseline), baseline
        # The Original setting must preserve its existing quality-specific filters.
        for quality in range(5):
            before = receiver.render(old, softness=0, quality=quality, resolution=1024, pattern="broad")
            after = receiver.render(source, softness=0, quality=quality, resolution=1024, pattern="broad")
            np.testing.assert_array_equal(before, after)
    report = {"result": "passed", "renderer": ctx.info["GL_RENDERER"],
              "scope": "Actual APK GLSL on a desktop GPU; Android device validation remains separate",
              "texture_reads_per_soft_filtered_pixel": 16, "translation_tests": translations,
              "broad_shadow_variants": variants, "terrain_plane_tests": 12, "baseline": baseline}
    rendered = json.dumps(report, indent=2) + "\n"
    print(rendered, end="")
    if args.report:
        args.report.write_text(rendered, encoding="utf-8")
    receiver.release()
    ctx.release()


if __name__ == "__main__":
    main()
