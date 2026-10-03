package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Detection matrix for [DreamShaderDialects]: extension x content markers -> dialect.
 *
 * Markers are scanned with the frozen V1 lexer, so comments and string literals must not trigger
 * misdetection, and the 1.x `#Region` layout directive must stay a V1 file.
 */
class DreamShaderDialectTest {

    // ------------------------------------------------------------------
    // extension baseline
    // ------------------------------------------------------------------

    @Test
    fun extensionBaselines() {
        assertEquals(DreamShaderDialect.V1, DreamShaderDialects.defaultByExtension("dsm"))
        assertEquals(DreamShaderDialect.V1, DreamShaderDialects.defaultByExtension("dsf"))
        assertEquals(DreamShaderDialect.V2, DreamShaderDialects.defaultByExtension("dss"))
        assertEquals(DreamShaderDialect.V2, DreamShaderDialects.defaultByExtension("dsi"))
        assertEquals(DreamShaderDialect.V1, DreamShaderDialects.defaultByExtension("dsh"))
        assertEquals(null, DreamShaderDialects.defaultByExtension("txt"))
    }

    @Test
    fun extensionDetectionIsCaseInsensitive() {
        assertEquals(DreamShaderDialect.V2, DreamShaderDialects.defaultByExtension("DSS"))
        assertTrue(DreamShaderDialects.isDreamShaderExtension("DSM"))
    }

    // ------------------------------------------------------------------
    // content markers decide
    // ------------------------------------------------------------------

    @Test
    fun dssWithNoMarkersStaysV2() {
        val text = """
            float Helper(float x)
            {
                return x * 2.0;
            }
        """.trimIndent()
        assertEquals(DreamShaderDialect.V2, DreamShaderDialects.detect("M_Test.dss", text))
    }

    @Test
    fun dsmWithV2MarkersRoutesToV2() {
        val text = """
            uniform float3 Tint = float3(1, 1, 1);
            export void M_Test(inout material m) { m.BaseColor = Tint; }
        """.trimIndent()
        assertEquals(DreamShaderDialect.V2, DreamShaderDialects.detect("M_Test.dsm", text))
        assertTrue(DreamShaderDialects.routesToV2(text))
    }

    @Test
    fun dsiPragmaInstanceIsV2() {
        val text = """
            #pragma instance(Parent = "M_Skin")
            uniform float Roughness = 0.35;
        """.trimIndent()
        assertEquals(DreamShaderDialect.V2, DreamShaderDialects.detect("MI_HeroSkin.dsi", text))
    }

    @Test
    fun mixedHeaderIsHeaderMixed() {
        val text = """
            uniform float SharedGain = 1.0;

            Function LegacyHelper(in float x, out float y) {
                y = x;
            }
        """.trimIndent()
        assertEquals(DreamShaderDialect.HEADER_MIXED, DreamShaderDialects.detect("Shared.dsh", text))
        // Any V2 marker routes the parse to the 2.0 front end, which captures legacy blocks.
        assertTrue(DreamShaderDialects.routesToV2(text))
    }

    @Test
    fun plainLegacyFileStaysV1() {
        val text = """
            Shader(Name="Materials/M_Water")
            {
                Outputs {
                    vec3 Color;
                }
            }
        """.trimIndent()
        assertEquals(DreamShaderDialect.V1, DreamShaderDialects.detect("M_Water.dsm", text))
        assertFalse(DreamShaderDialects.routesToV2(text))
    }

    @Test
    fun headerWithOnlyLegacyBlocksStaysV1() {
        val text = """
            Namespace(Name="Common")
            {
                Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                    result = color * tint;
                }
            }
        """.trimIndent()
        assertEquals(DreamShaderDialect.V1, DreamShaderDialects.detect("Common.dsh", text))
    }

    // ------------------------------------------------------------------
    // comment / string immunity and 1.x directives
    // ------------------------------------------------------------------

    @Test
    fun commentsAndStringsDoNotTriggerV2() {
        val text = """
            // uniform float X = 1.0; export void Fake(); extern float2 Proto();
            /* export extern uniform */
            Shader(Name="Materials/M_Water")
            {
                Graph {
                    float3 Color = "the word uniform inside a string";
                }
            }
        """.trimIndent()
        assertEquals(DreamShaderDialect.V1, DreamShaderDialects.detect("M_Water.dsm", text))
        assertFalse(DreamShaderDialects.routesToV2(text))
    }

    @Test
    fun oneXRegionDirectiveIsNotAV2Marker() {
        val text = """
            Shader(Name="Materials/M_Water")
            {
                Graph {
                    #Region "Sampling"
                    float2 uv = vec2(0.0, 0.0);
                    #EndRegion
                }
            }
        """.trimIndent()
        assertEquals(DreamShaderDialect.V1, DreamShaderDialects.detect("M_Water.dsm", text))
        assertFalse(DreamShaderDialects.routesToV2(text))
    }

    @Test
    fun hashNotAtLineStartIsNotAMarker() {
        val text = """
            Shader(Name="M") {
                Graph {
                    float x = a # b;
                }
            }
        """.trimIndent()
        assertEquals(DreamShaderDialect.V1, DreamShaderDialects.detect("M.dsm", text))
    }

    // ------------------------------------------------------------------
    // routing predicate agreement
    // ------------------------------------------------------------------

    @Test
    fun routingAgreesWithDialectForV2() {
        val v2 = """
            #pragma material(ShadingModel = DefaultLit)
            export void M_Test(inout material m) { }
        """.trimIndent()
        assertTrue(DreamShaderDialects.routesToV2(v2))
        assertEquals(DreamShaderDialect.V2, DreamShaderDialects.detect("M_Test.dss", v2))
    }

    @Test
    fun includeDirectiveIsAV2Marker() {
        val text = "#include \"/Game/Shared/Common.dsh\"\n\nfloat Luma(float3 c) { return c.x; }"
        assertTrue(DreamShaderDialects.routesToV2(text))
        assertEquals(DreamShaderDialect.V2, DreamShaderDialects.detect("Common.dsh", text))
    }
}
