package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
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
    fun `suggests virtual function options keys in options section`() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    
                }
            }
        """.trimIndent()
        val marker = "Options {\n"
        val offset = text.indexOf(marker) + marker.length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Asset"))
        assertTrue(labels.contains("Description"))
        assertTrue(!labels.contains("Domain"))
        assertTrue(!labels.contains("BlendMode"))
    }

    @Test
    fun `suggests virtual function options alias keys in settings section`() {
        val text = """
            VirtualFunction BufferWriter {
                Settings {
                    
                }
            }
        """.trimIndent()
        val marker = "Settings {\n"
        val offset = text.indexOf(marker) + marker.length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Asset"))
        assertTrue(labels.contains("Description"))
        assertTrue(!labels.contains("Domain"))
        assertTrue(!labels.contains("BlendMode"))
    }

    @Test
    fun `suggests virtual function asset path templates in options`() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = P
                }
            }
        """.trimIndent()
        val offset = text.indexOf("Asset = P") + "Asset = P".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Path(Game, Materials/M_VFAsset)"))
        assertTrue(labels.contains("Path(Engine, Materials/M_VFAsset)"))
    }

    @Test
    fun `suggests quoted object path templates for virtual function asset`() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = "G
                }
            }
        """.trimIndent()
        val offset = text.indexOf("\"G") + "\"G".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Game/MaterialFunctions/MF_VFAsset"))
        assertTrue(!labels.contains("Engine/Functions/Engine_MF"))
        assertTrue(!labels.contains("Path(Game, Materials/M_VFAsset)"))
    }

    @Test
    fun `suggests virtual function asset path templates in settings alias`() {
        val text = """
            VirtualFunction BufferWriter {
                Settings {
                    Asset = P
                }
            }
        """.trimIndent()
        val offset = text.indexOf("Asset = P") + "Asset = P".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Path(Game, Materials/M_VFAsset)"))
        assertTrue(labels.contains("Path(Engine, Materials/M_VFAsset)"))
    }

    @Test
    fun `suggests virtual function description templates in settings alias`() {
        val text = """
            VirtualFunction BufferWriter {
                Settings {
                    Description = "B
                }
            }
        """.trimIndent()
        val offset = text.indexOf("\"B") + "\"B".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("Bridge-compatible virtual function"))
        assertTrue(!labels.contains("Existing material function asset"))
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
        assertTrue(!labels.contains("PostProcess"))
        assertTrue(!labels.contains("DeferredDecal"))
    }

    @Test
    fun `suggests boolean settings values without quotes`() {
        val text = """
            Shader MySurface {
                Settings {
                    TwoSided = t
                }
            }
        """.trimIndent()
        val offset = text.indexOf("TwoSided = t") + "TwoSided = t".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("true"))
        assertTrue(!labels.contains("false"))
    }

    @Test
    fun `suggests num customized uvs values without quotes`() {
        val text = """
            Shader MySurface {
                Settings {
                    NumCustomizedUVs = 1
                }
            }
        """.trimIndent()
        val offset = text.indexOf("NumCustomizedUVs = 1") + "NumCustomizedUVs = 1".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("1"))
        assertTrue(!labels.contains("0"))
        assertTrue(!labels.contains("8"))
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
            "Common/Material.dsm",
            "UI/Widget.dsh"
        )

        val labels = DreamShaderCompletionSuggester
            .suggest(text, offset, importCandidates = importCandidates)
            .map { it.label }
            .toSet()

        assertTrue(labels.contains("Common/Core.dsh"))
        assertTrue(labels.contains("Common/Lighting.dsf"))
        assertTrue(labels.contains("Common/Material.dsm"))
        assertTrue(!labels.contains("UI/Widget.dsh"))
    }

    @Test
    fun `suggests package root imports in import string`() {
        val text = """import "@typed"""
        val offset = text.length
        val importCandidates = listOf(
            "@typedreammoon/dream-noise",
            "@typedreammoon/dream-sdf",
            "Common/Core.dsh"
        )

        val labels = DreamShaderCompletionSuggester
            .suggest(text, offset, importCandidates = importCandidates)
            .map { it.label }
            .toSet()

        assertTrue(labels.contains("@typedreammoon/dream-noise"))
        assertTrue(labels.contains("@typedreammoon/dream-sdf"))
        assertTrue(!labels.contains("Common/Core.dsh"))
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
