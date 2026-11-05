package graphics

import java.io.IOException

/** Compile-time terrain bias only; object/actor receivers keep the existing path. */
object TerrainShadowCorrection {
    private val constant = Regex("(?m)^#define\\s+OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE\\s+\\d+(?:\\.\\d+)?\\s*$")

    fun coverage(preference: String?): String = when (preference) {
        "legacy" -> "0.0"
        "strong" -> "0.85"
        else -> "0.55"
    }

    fun specialize(source: String, preference: String?): String {
        if (!source.contains("OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE_V1") || constant.findAll(source).count() != 1) {
            throw IOException("Terrain shadow correction shader is missing or mismatched")
        }
        return source.replace(constant, "#define OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE ${coverage(preference)}")
    }
}
