package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * A5.3 preventive checks (DSH2248 / DSH3203) and B3 legacy GLSL alias hints (DSH5277).
 * Each rule gets a positive and a negative case; the fix behaviour is asserted on the text
 * after applying the intention.
 */
class DreamShaderDialectPreventionPassTest : BasePlatformTestCase() {

    // ------------------------------------------------------------------
    // DSH2248: 2.0 syntax in a .dsm/.dsf file
    // ------------------------------------------------------------------

    fun testDsh2248FlagsUniformDeclarationInDsm() {
        myFixture.configureByText(
            "legacy_material.dsm",
            """
            Shader(Name="Materials/M_Legacy") {
                Properties = {
                    uniform float3 BaseColor = float3(0.8, 0.8, 0.8);
                }
            }
            """.trimIndent()
        )
        val descriptions = highlightDescriptions(HighlightSeverity.ERROR)
        assertTrue(
            "expected a DSH2248 error, got: $descriptions",
            descriptions.any { it.contains("DSH2248") && it.contains("uniform") }
        )
    }

    fun testDsh2248FlagsExportFunctionAndPragmaInDsf() {
        myFixture.configureByText(
            "legacy_function.dsf",
            """
            #pragma material(Domain = Surface)

            export float3 Tint(float3 In)
            {
                return In;
            }
            """.trimIndent()
        )
        val descriptions = highlightDescriptions(HighlightSeverity.ERROR)
        assertTrue(
            "expected DSH2248 errors for export and #pragma, got: $descriptions",
            descriptions.count { it.contains("DSH2248") } >= 2
        )
    }

    fun testDsh2248IgnoresCommentsAndLookalikeWords() {
        myFixture.configureByText(
            "legacy_clean.dsm",
            """
            // uniform float3 BaseColor should not be flagged in a comment
            Shader(Name="Materials/M_Legacy") {
                Properties = {
                    float Roughness = 0.5;
                }
                Graph = {
                    // export inside a comment stays silent too
                }
            }
            """.trimIndent()
        )
        val descriptions = highlightDescriptions(HighlightSeverity.ERROR)
        assertTrue(
            "expected no DSH2248 error, got: $descriptions",
            descriptions.none { it.contains("DSH2248") }
        )
    }

    fun testSharedFilePragmasKeepLegacyDeclarationsAndAvoidDsh2248() {
        val file = myFixture.configureByText("Legacy.dsf", """
            #pragma nogeneration
            #pragma disable_warning(DSH7224)
            #pragma disable_error(DSH8219)
            ShaderFunction(Name = "MF_Legacy", NoGeneration = true) { }
        """.trimIndent())
        val declaration = com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiUtil.topLevelDeclarations(file).single()
        assertEquals("MF_Legacy", declaration.declarationName())
        assertFalse(highlightDescriptions(HighlightSeverity.ERROR).any { "DSH2248" in it })
    }

    // ------------------------------------------------------------------
    // DSH3203: angle-bracket #include
    // ------------------------------------------------------------------

    fun testDsh3203FlagsAngleBracketIncludeInDss() {
        myFixture.configureByText(
            "angled_include.dss",
            """
            #pragma material(Domain = Surface)
            #include <Game/Shared/Common.dsh>

            export void M_Main(inout material m)
            {
            }
            """.trimIndent()
        )
        val descriptions = highlightDescriptions(HighlightSeverity.ERROR)
        assertTrue(
            "expected a DSH3203 error, got: $descriptions",
            descriptions.any { it.contains("DSH3203") }
        )

        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("<Game"))
        myFixture.launchAction(myFixture.findSingleIntention("Quote the #include path"))
        val document = myFixture.getDocument(myFixture.file)
        assertTrue(
            "expected the include path to be quoted, got: ${document.text.lines().getOrNull(1)}",
            document.text.contains("#include \"Game/Shared/Common.dsh\"")
        )
    }

    fun testDsh3203AcceptsQuotedInclude() {
        myFixture.configureByText(
            "quoted_include.dss",
            """
            #pragma material(Domain = Surface)
            #include "Game/Shared/Common.dsh"

            export void M_Main(inout material m)
            {
            }
            """.trimIndent()
        )
        val descriptions = highlightDescriptions(HighlightSeverity.ERROR)
        assertTrue(
            "expected no DSH3203 error, got: $descriptions",
            descriptions.none { it.contains("DSH3203") }
        )
    }

    // ------------------------------------------------------------------
    // DSH2249 linkage: the existing .dsh shape check cites the code
    // ------------------------------------------------------------------

    fun testDsh2249CitedByShapeCheck() {
        myFixture.configureByText(
            "asset_block.dsh",
            """
            ShaderFunction BuildNoise {
                Outputs {
                    float Out = 0.0;
                }
            }
            """.trimIndent()
        )
        val descriptions = highlightDescriptions(HighlightSeverity.ERROR)
        assertTrue(
            "expected the .dsh shape error to cite DSH2249, got: $descriptions",
            descriptions.any { it.contains("DSH2249") }
        )
    }

    // ------------------------------------------------------------------
    // DSH5277: legacy GLSL alias hints
    // ------------------------------------------------------------------

    fun testDsh5277FlagsGlslFunctionAndTypeAliasesInLegacyFiles() {
        myFixture.configureByText(
            "glsl_aliases.dsm",
            """
            Shader(Name="Materials/M_Glsl") {
                Properties = {
                    vec3 Tint = vec3(1.0, 1.0, 1.0);
                }
                Graph = {
                    Color = mix(Tint, Other, 0.5);
                }
            }
            """.trimIndent()
        )
        val descriptions = highlightDescriptions(HighlightSeverity.WEAK_WARNING)
        assertTrue(
            "expected type alias hints for vec3, got: $descriptions",
            descriptions.count { it.contains("vec3") && it.contains("float3") } == 2
        )
        assertTrue(
            "expected a function alias hint for mix, got: $descriptions",
            descriptions.any { it.contains("'mix'") && it.contains("lerp") }
        )
    }

    fun testDsh5277SilentInV2FilesCommentsAndShadowedDeclarations() {
        myFixture.configureByText(
            "v2_file.dss",
            """
            #pragma material(Domain = Surface)
            export float3 Mix(float3 A)
            {
                return mix(A, A, 0.5);
            }
            """.trimIndent()
        )
        val descriptions = highlightDescriptions(HighlightSeverity.WEAK_WARNING)
        assertTrue(
            "expected no alias hints in a .dss file, got: $descriptions",
            descriptions.none { it.contains("DSH5277") }
        )

        myFixture.configureByText(
            "shadowed_mix.dsm",
            """
            Shader(Name="Materials/M_Shadow") {
                Graph = {
                    Color = mix(A, B, 0.5);
                }
            }

            Function mix(in float a, in float b, in float t, out float r) {
                r = a;
            }
            """.trimIndent()
        )
        val shadowed = highlightDescriptions(HighlightSeverity.WEAK_WARNING)
        assertTrue(
            "expected mix hints to be skipped when the file declares a mix() function, got: $shadowed",
            shadowed.none { it.contains("'mix'") }
        )
    }

    fun testDsh5277ReplaceAliasFixRewritesToHlslName() {
        myFixture.configureByText(
            "fix_target.dsm",
            """
            Shader(Name="Materials/M_Fix") {
                Graph = {
                    Color = fract(UV);
                }
            }
            """.trimIndent()
        )
        myFixture.editor.caretModel.moveToOffset(myFixture.file.text.indexOf("fract"))
        val intention = myFixture.findSingleIntention("Replace with HLSL spelling")
        myFixture.launchAction(intention)
        val text = myFixture.getDocument(myFixture.file).text
        assertTrue(
            "expected fract to be replaced by frac, got: $text",
            text.contains("frac(UV)") && !text.contains("fract(UV)")
        )
    }

    private fun highlightDescriptions(severity: HighlightSeverity): List<String> =
        myFixture.doHighlighting(severity).mapNotNull(HighlightInfo::getDescription)
}
