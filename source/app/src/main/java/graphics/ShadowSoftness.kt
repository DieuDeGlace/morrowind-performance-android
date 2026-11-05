package graphics

import java.io.IOException

/** Independent filter footprint; the original quality kernels remain available. */
object ShadowSoftness {
    private val constant = Regex("(?m)^#define\\s+OPENMW_ANDROID_SHADOW_SOFTNESS\\s+\\d+\\s*$")

    fun stride(preference: String?): Int = when (preference) {
        "soft" -> 2
        "softer" -> 3
        else -> 0
    }

    fun specialize(source: String, preference: String?): String {
        if (!source.contains("OPENMW_ANDROID_SHADOW_SOFTNESS_CUBIC_V1") || constant.findAll(source).count() != 1) {
            throw IOException("Shadow softness shader is missing or mismatched")
        }
        return source.replace(constant, "#define OPENMW_ANDROID_SHADOW_SOFTNESS ${stride(preference)}")
    }
}
