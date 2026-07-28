package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderSemanticAnnotatorTest : BasePlatformTestCase() {
    fun testAnnotatesDeclarationNameAndCallableReferences() {
        val text = """
            Shader SurfaceMain {
                Graph {
                    float2 uv = UE.TexCoord(0);
                    float s = saturate(uv.x);
                }
            }
        """.trimIndent()
        val file = myFixture.configureByText("semantic_annotator.dsm", text)

        val highlights = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
            .filter { it.description == null }
            .map { info -> info.text to info.forcedTextAttributesKey?.externalName }

        val debug = highlights.joinToString(" | ") { "${it.first}:${it.second}" }

        assertFalse("Expected information highlights, got none", highlights.isEmpty())
        assertTrue(
            "Expected declaration name semantic highlight",
            highlights.any { it.first == "SurfaceMain" && it.second == "DREAMSHADER_DECLARATION_NAME" }
        )
        assertTrue(
            "Expected builtin function semantic highlight for UE.TexCoord. Actual: $debug",
            highlights.any { it.first == "TexCoord" && it.second == "DREAMSHADER_BUILTIN_FUNCTION" }
        )
        assertTrue(
            "Expected builtin function semantic highlight for saturate. Actual: $debug",
            highlights.any { it.first == "saturate" && it.second == "DREAMSHADER_BUILTIN_FUNCTION" }
        )
    }

    fun testDoesNotAnnotateSettingsValueAsCallableReference() {
        val text = """
            Shader Main {
                Settings {
                    Domain = "Surface";
                }
            }
        """.trimIndent()
        myFixture.configureByText("semantic_settings.dsm", text)

        val highlights = myFixture.doHighlighting(HighlightSeverity.INFORMATION)
            .filter { it.description == null }
            .map { it.forcedTextAttributesKey?.externalName }

        assertTrue(
            "Settings-only file should not produce callable reference semantic highlights",
            highlights.none { it == "DREAMSHADER_CALLABLE_REFERENCE" }
        )
    }
}
