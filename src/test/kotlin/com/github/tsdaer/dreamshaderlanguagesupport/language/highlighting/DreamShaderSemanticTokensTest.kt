package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
                    float3 LocalColor = Tint;
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
        assertHasSemanticToken(highlights, "LocalColor", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasSemanticToken(highlights, "InColor", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasSemanticToken(highlights, "BaseColor", "DREAMSHADER_MATERIAL_OUTPUT_MEMBER")
    }

    fun testSemanticTokensInGraphAndFunctionBodies() {
        val text = """
            Function Util(in float3 InColor, out float3 OutColor) {
                float2 uv = UE.TexCoord(Index=0);
                float s = saturate(uv.x);
                float3 world = Common::ApplyTint(InColor);
                OutColor = InColor * s;
            }
        """.trimIndent()
        myFixture.configureByText("semantic_tokens_graph_body.dsf", text)

        val highlights = semanticHighlights()

        assertHasSemanticToken(highlights, "Function", "DREAMSHADER_KEYWORD")
        assertHasSemanticToken(highlights, "Util", "DREAMSHADER_DECLARATION_NAME")
        assertHasSemanticToken(highlights, "UE", "DREAMSHADER_BUILTIN_NAMESPACE")
        assertHasSemanticToken(highlights, "Common", "DREAMSHADER_NAMESPACE_QUALIFIER")
        assertHasSemanticToken(highlights, "TexCoord", "DREAMSHADER_CALLABLE_REFERENCE")
        assertHasSemanticToken(highlights, "saturate", "DREAMSHADER_CALLABLE_REFERENCE")
        assertHasSemanticToken(highlights, "world", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasSemanticToken(highlights, "uv", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasSemanticToken(highlights, "s", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasSemanticToken(highlights, "s", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasSemanticToken(highlights, "OutColor", "DREAMSHADER_LOCAL_SYMBOL")
    }

    fun testSemanticTokensForNestedCallsAndMemberLikeSyntax() {
        val text = """
            Function Nested(in float3 InColor, out float3 OutColor) {
                float2 uv = UE.TexCoord(Index=0);
                float4 sample = UE.Expression(Class="Multiply", Input=saturate(uv.x));
                OutColor = sample.xyz + InColor;
            }
        """.trimIndent()
        myFixture.configureByText("semantic_tokens_nested_calls.dsf", text)

        val highlights = semanticHighlights()

        assertHasSemanticToken(highlights, "UE", "DREAMSHADER_BUILTIN_NAMESPACE")
        assertHasSemanticToken(highlights, "Expression", "DREAMSHADER_CALLABLE_REFERENCE")
        assertHasSemanticToken(highlights, "saturate", "DREAMSHADER_CALLABLE_REFERENCE")
        assertHasSemanticToken(highlights, "uv", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasSemanticToken(highlights, "sample", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasNoSemanticToken(highlights, "Index", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasNoSemanticToken(highlights, "Index", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasNoSemanticToken(highlights, "x", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasNoSemanticToken(highlights, "x", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasNoSemanticToken(highlights, "xyz", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasNoSemanticToken(highlights, "xyz", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
    }

    fun testSemanticTokensForArrayIndexAndMultiLevelNamespaceQualifier() {
        val text = """
            Function Complex(in float3 InColor, out float3 OutColor) {
                float3 Values;
                int idx = 1;
                float3 Picked = Values[idx];
                float3 Mixed = A::B::Blend(Picked, InColor);
                OutColor = Mixed;
            }
        """.trimIndent()
        myFixture.configureByText("semantic_tokens_array_namespace.dsf", text)

        val highlights = semanticHighlights()

        assertHasSemanticToken(highlights, "A", "DREAMSHADER_NAMESPACE_QUALIFIER")
        assertHasSemanticToken(highlights, "B", "DREAMSHADER_NAMESPACE_QUALIFIER")
        assertHasSemanticToken(highlights, "Blend", "DREAMSHADER_CALLABLE_REFERENCE")
        assertHasSemanticToken(highlights, "Values", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasSemanticToken(highlights, "Values", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasSemanticToken(highlights, "idx", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasSemanticToken(highlights, "idx", "DREAMSHADER_LOCAL_SYMBOL")
        assertHasSemanticToken(highlights, "Picked", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHasSemanticToken(highlights, "Picked", "DREAMSHADER_LOCAL_SYMBOL")
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

    private fun assertHasNoSemanticToken(
        highlights: List<Pair<String, String?>>,
        text: String,
        key: String
    ) {
        val debug = highlights.joinToString(" | ") { "${it.first}:${it.second}" }
        assertTrue(
            "Expected no semantic token [$text:$key], actual: $debug",
            highlights.none { it.first == text && it.second == key }
        )
    }
}
