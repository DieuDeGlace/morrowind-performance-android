package graphics

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class TerrainShadowCorrectionTest {
    private val shader = """// OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE_V1
#define OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE 0.55
#define OPENMW_ANDROID_SHADOW_PCF_LEVEL 2
varying vec2 shadowReceiverPlaneSlope0;
"""
    @Test fun defaultIsStandard() { assertEquals("0.55", TerrainShadowCorrection.coverage(null)) }
    @Test fun unknownPreferenceFallsBackSafely() { assertEquals("0.55", TerrainShadowCorrection.coverage("old-value")) }
    @Test fun legacyDisablesOnlyExtraCoverage() { assertEquals("0.0", TerrainShadowCorrection.coverage("legacy")) }
    @Test fun strongUsesBoundedCoverage() { assertEquals("0.85", TerrainShadowCorrection.coverage("strong")) }
    @Test fun changesOnlyTerrainConstant() {
        val result = TerrainShadowCorrection.specialize(shader, "strong")
        assertEquals(shader.replace("COVERAGE 0.55", "COVERAGE 0.85"), result)
    }
    @Test fun canSwitchBackFromStrongToLegacy() {
        val result = TerrainShadowCorrection.specialize(TerrainShadowCorrection.specialize(shader,"strong"),"legacy")
        assertTrue(result.contains("#define OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE 0.0"))
        assertTrue(result.contains("#define OPENMW_ANDROID_SHADOW_PCF_LEVEL 2"))
    }
    @Test(expected = IOException::class) fun rejectsMissingMarker() { TerrainShadowCorrection.specialize("",null) }
    @Test(expected = IOException::class) fun rejectsDuplicateConstants() {
        TerrainShadowCorrection.specialize(shader+"\n#define OPENMW_ANDROID_TERRAIN_SHADOW_COVERAGE 0.85",null)
    }
}
