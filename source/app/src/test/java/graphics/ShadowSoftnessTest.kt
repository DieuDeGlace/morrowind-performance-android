package graphics

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ShadowSoftnessTest {
    private val shader = """// OPENMW_ANDROID_SHADOW_SOFTNESS_CUBIC_V1
#define OPENMW_ANDROID_SHADOW_SOFTNESS 0
#define OPENMW_ANDROID_SHADOW_PCF_LEVEL 2
#define OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE 0.55
"""
    @Test fun defaultKeepsOriginal() { assertEquals(0, ShadowSoftness.stride(null)) }
    @Test fun unknownPreferenceKeepsOriginal() { assertEquals(0, ShadowSoftness.stride("bad")) }
    @Test fun softUsesTwoTexelStride() { assertEquals(2, ShadowSoftness.stride("soft")) }
    @Test fun softerUsesThreeTexelStride() { assertEquals(3, ShadowSoftness.stride("softer")) }
    @Test fun changesOnlyFilterFootprint() {
        assertEquals(shader.replace("SOFTNESS 0", "SOFTNESS 3"), ShadowSoftness.specialize(shader,"softer"))
    }
    @Test fun originalRestoresAfterSofter() {
        assertEquals(shader, ShadowSoftness.specialize(ShadowSoftness.specialize(shader,"softer"),"original"))
    }
    @Test(expected = IOException::class) fun rejectsMissingMarker() { ShadowSoftness.specialize("",null) }
    @Test(expected = IOException::class) fun rejectsDuplicateConstants() {
        ShadowSoftness.specialize(shader+"\n#define OPENMW_ANDROID_SHADOW_SOFTNESS 2",null)
    }
}
