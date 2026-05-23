package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertTrue

class DreamShaderSemanticTokensTest : BasePlatformTestCase() {
    fun testSemanticTokensForDeclarationAndSectionScopes() {
        val text = """
            Shader SurfaceMain {
                Inputs {
                    float3 InColor;
                }
                Outputs {
                    float3 OutColor;
                    Base.BaseColor = InColor;
                }
                Graph {
                    float3 Tint = InColor;
                    OutColor = Tint;
                }
            }
        """.trimIndent()
        myFixture.configureByText("semantic_tokens_declaration_sections.dsm", text)

        val highlights = semanticHighlights()

        assertHasSemanticToken(highlights, "Shader", "DREAMSHADER_KEYWORD")
        assertHasSemanticToken(highlights, "SurfaceMain", "DREAMSHADER_DECLARATION_NAME")
        assertHasSemanticToken(highlights, "Inputs", "DREAMSHADER_SECTION")
        assertHasSemanticToken(highlights, "Outputs", "DREAMSHADER_SECTION")
        assertHasSemanticToken(highlights, "Graph", "DREAMSHADER_SECTION")
        assertHasSemanticToken(highlights, "InColor", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasSemanticToken(highlights, "BaseColor", "DREAMSHADER_MATERIAL_OUTPUT_MEMBER")
    }

    fun testSemanticTokensInGraphAndFunctionBodies() {
        val text = """
            Function Util(in float3 InColor, out float3 OutColor) {
                float2 uv = UE.TexCoord(Index=0);
                float s = saturate(uv.x);
                OutColor = InColor * s;
            }
        """.trimIndent()
        myFixture.configureByText("semantic_tokens_graph_body.dsf", text)

        val highlights = semanticHighlights()

        assertHasSemanticToken(highlights, "Function", "DREAMSHADER_KEYWORD")
        assertHasSemanticToken(highlights, "Util", "DREAMSHADER_DECLARATION_NAME")
        assertHasSemanticToken(highlights, "UE", "DREAMSHADER_BUILTIN_NAMESPACE")
        assertHasSemanticToken(highlights, "TexCoord", "DREAMSHADER_CALLABLE_REFERENCE")
        assertHasSemanticToken(highlights, "saturate", "DREAMSHADER_CALLABLE_REFERENCE")
        assertHasSemanticToken(highlights, "uv", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasSemanticToken(highlights, "OutColor", "DREAMSHADER_LOCAL_SYMBOL")
    }

    private fun semanticHighlights(): List<Pair<String, String?>> {
        return myFixture.doHighlighting(HighlightSeverity.INFORMATION)
            .filter { it.description == null }
            .map { it.text to it.forcedTextAttributesKey?.externalName }
    }

    private fun assertHasSemanticToken(
        highlights: List<Pair<String, String?>>,
        text: String,
        key: String
    ) {
        val debug = highlights.joinToString(" | ") { "${it.first}:${it.second}" }
        assertTrue(
            "Expected semantic token [$text:$key], actual: $debug",
            highlights.any { it.first == text && it.second == key }
        )
    }
}
