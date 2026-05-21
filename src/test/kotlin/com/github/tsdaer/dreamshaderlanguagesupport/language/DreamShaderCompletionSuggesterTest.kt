package com.github.tsdaer.dreamshaderlanguagesupport.language

import org.junit.Assert.assertTrue
import org.junit.Test

class DreamShaderCompletionSuggesterTest {
    @Test
    fun `suggests settings keys in settings section`() {
        val text = """
            Shader MySurface {
                Settings {
                    
                }
            }
        """.trimIndent()
        val marker = "Settings {\n"
        val offset = text.indexOf(marker) + marker.length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Domain"))
        assertTrue(labels.contains("ShadingModel"))
        assertTrue(labels.contains("BlendMode"))
    }

    @Test
    fun `suggests settings value mappings for domain`() {
        val text = """
            Shader MySurface {
                Settings {
                    Domain = "S
                }
            }
        """.trimIndent()
        val offset = text.indexOf("\"S") + "\"S".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Surface"))
        assertTrue(labels.contains("PostProcess"))
    }

    @Test
    fun `suggests base output members after Base dot`() {
        val text = """
            Shader MySurface {
                Outputs {
                    Base.Ba
                }
            }
        """.trimIndent()
        val offset = text.indexOf("Base.Ba") + "Base.Ba".length

        val suggestions = DreamShaderCompletionSuggester.suggest(text, offset)
        val labels = suggestions.map { it.label }.toSet()
        assertTrue(labels.contains("BaseColor"))
        val baseColor = suggestions.firstOrNull { it.label == "BaseColor" }
        assertTrue(baseColor?.insertText == "BaseColor = ;")
    }

    @Test
    fun `suggests UE builtins after UE dot in graph section`() {
        val text = """
            Shader MySurface {
                Graph {
                    UE.
                }
            }
        """.trimIndent()
        val offset = text.indexOf("UE.") + "UE.".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Expression"))
        assertTrue(labels.contains("TexCoord"))
    }

    @Test
    fun `suggests hlsl intrinsics in function body`() {
        val text = """
            Function Util {
                
            }
        """.trimIndent()
        val offset = text.indexOf('\n') + 1

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("normalize"))
        assertTrue(labels.contains("lerp"))
    }

    @Test
    fun `suggests import candidates in import string`() {
        val text = """import "Com"""
        val offset = text.length
        val importCandidates = listOf(
            "Common/Core.dsh",
            "Common/Lighting.dsf",
            "UI/Widget.dsh"
        )

        val labels = DreamShaderCompletionSuggester
            .suggest(text, offset, importCandidates = importCandidates)
            .map { it.label }
            .toSet()

        assertTrue(labels.contains("Common/Core.dsh"))
        assertTrue(labels.contains("Common/Lighting.dsf"))
        assertTrue(!labels.contains("UI/Widget.dsh"))
    }

    @Test
    fun `suggests UE expression class values from manifest candidates`() {
        val text = """
            Shader Main {
                Graph {
                    float x = UE.Expression(Class="Si");
                }
            }
        """.trimIndent()
        val offset = text.indexOf("\"Si") + "\"Si".length

        val labels = DreamShaderCompletionSuggester.suggest(
            text = text,
            offset = offset,
            expressionClassCandidates = listOf("Sine", "Cosine", "Multiply")
        ).map { it.label }.toSet()

        assertTrue(labels.contains("Sine"))
        assertTrue(!labels.contains("Cosine"))
    }
}
