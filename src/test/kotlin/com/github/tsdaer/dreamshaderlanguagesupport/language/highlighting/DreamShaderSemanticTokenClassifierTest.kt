package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderSemanticTokenClassifierTest : BasePlatformTestCase() {
    fun testClassifierCoversNamespaceCallableAndLocalSplit() {
        val text = """
            Function Util(in float3 InColor, out float3 OutColor) {
                float2 uv = UE.TexCoord(Index=0);
                float3 world = A::B::Blend(uv.xxx, InColor);
                OutColor = world;
            }
        """.trimIndent()
        val file = myFixture.configureByText("classifier_namespace_local.dsf", text)

        val elements = collectIdentifierAndKeywordElements(file)
        val classified = elements.mapNotNull { element ->
            DreamShaderSemanticTokenClassifier.classify(element)?.externalName?.let { key ->
                element.text to key
            }
        }

        assertHas(classified, "Function", "DREAMSHADER_KEYWORD")
        assertHas(classified, "Util", "DREAMSHADER_DECLARATION_NAME")
        assertHas(classified, "UE", "DREAMSHADER_BUILTIN_NAMESPACE")
        assertHas(classified, "A", "DREAMSHADER_NAMESPACE_QUALIFIER")
        assertHas(classified, "B", "DREAMSHADER_NAMESPACE_QUALIFIER")
        assertHas(classified, "Blend", "DREAMSHADER_CALLABLE_REFERENCE")
        assertHas(classified, "uv", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHas(classified, "world", "DREAMSHADER_LOCAL_SYMBOL_DECLARATION")
        assertHas(classified, "world", "DREAMSHADER_LOCAL_SYMBOL")
    }

    fun testClassifierSkipsNamedArgsAndSwizzles() {
        val text = """
            Function Util(in float3 InColor, out float3 OutColor) {
                float2 uv = UE.TexCoord(Index=0);
                float s = saturate(uv.x);
                OutColor = float3(s, s, s);
            }
        """.trimIndent()
        val file = myFixture.configureByText("classifier_named_arg_swizzle.dsf", text)
        val elements = collectIdentifierAndKeywordElements(file)

        val classified = elements.mapNotNull { element ->
            DreamShaderSemanticTokenClassifier.classify(element)?.externalName?.let { key ->
                element.text to key
            }
        }

        assertTrue(classified.none { it.first == "Index" && it.second.startsWith("DREAMSHADER_LOCAL_SYMBOL") })
        assertTrue(classified.none { it.first == "x" && it.second.startsWith("DREAMSHADER_LOCAL_SYMBOL") })
    }

    fun testClassifierMatchesAnnotatorHighlightsOnKeySamples() {
        val text = """
            Shader Main {
                Outputs {
                    float3 Color;
                    Base.BaseColor = Color;
                }
                Graph {
                    float2 uv = UE.TexCoord(Index=0);
                    float n = saturate(uv.x);
                    Color = float3(n, n, n);
                }
            }
        """.trimIndent()
        val file = myFixture.configureByText("classifier_vs_annotator.dsm", text)
        val elements = collectIdentifierAndKeywordElements(file)

        val classifierMap = elements.associateWith { DreamShaderSemanticTokenClassifier.classify(it)?.externalName }
        val infoHighlights = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
            .filter { it.description == null }
            .associate { info -> info.text to info.forcedTextAttributesKey?.externalName }

        val verifyTokens = listOf(
            "Shader" to "DREAMSHADER_KEYWORD",
            "Main" to "DREAMSHADER_DECLARATION_NAME",
            "BaseColor" to "DREAMSHADER_MATERIAL_OUTPUT_MEMBER",
            "UE" to "DREAMSHADER_BUILTIN_NAMESPACE",
            "TexCoord" to "DREAMSHADER_CALLABLE_REFERENCE"
        )

        verifyTokens.forEach { (token, expectedKey) ->
            val classifierKeys = classifierMap
                .filterKeys { it.text == token }
                .values
                .filterNotNull()
            assertTrue("Classifier missing $token:$expectedKey", classifierKeys.contains(expectedKey))
            assertEquals("Annotator mismatch for $token", expectedKey, infoHighlights[token])
        }
    }

    fun testClassifierMarksPathCallInPropertiesAsCallableReference() {
        val text = """
            Shader Main {
                Properties = {
                    Texture2D MainTex = Path(Engine, "/EngineResources/DefaultTexture");
                }
            }
        """.trimIndent()
        val file = myFixture.configureByText("classifier_path_properties.dsm", text)

        val elements = collectIdentifierAndKeywordElements(file)
        val classified = elements.mapNotNull { element ->
            DreamShaderSemanticTokenClassifier.classify(element)?.externalName?.let { key ->
                element.text to key
            }
        }

        assertHas(classified, "Path", "DREAMSHADER_CALLABLE_REFERENCE")
    }

    private fun collectIdentifierAndKeywordElements(file: com.intellij.psi.PsiFile): List<com.intellij.psi.PsiElement> {
        return com.intellij.psi.util.PsiTreeUtil.collectElements(file) { element ->
            val type = element.node?.elementType
            type == DreamShaderTokenTypes.IDENTIFIER ||
                type == DreamShaderTokenTypes.KEYWORD ||
                type == DreamShaderTokenTypes.SECTION
        }.toList()
    }

    private fun assertHas(classified: List<Pair<String, String>>, text: String, key: String) {
        val debug = classified.joinToString(" | ") { "${it.first}:${it.second}" }
        assertTrue("Expected [$text:$key], got: $debug", classified.any { it.first == text && it.second == key })
    }
}
