package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

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
    fun `suggests scoped package root imports after bare at marker`() {
        val text = """import "@"""
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
    fun `import completion replaces existing import string prefix`() {
        val text = """import "@"""
        val offset = text.length
        val suggestion = DreamShaderCompletionSuggester
            .suggest(
                text,
                offset,
                importCandidates = listOf("@typedreammoon/dream-noise")
            )
            .single { it.label == "@typedreammoon/dream-noise" }

        assertEquals(text.indexOf('@'), suggestion.replacementStartOffset)
        assertEquals("@typedreammoon/dream-noise", suggestion.insertText)
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

    @Test
    fun `suggests UE members from catalog entries`() {
        val text = """
            Shader MySurface {
                Graph {
                    UE.Dr
                }
            }
        """.trimIndent()
        val offset = text.indexOf("UE.Dr") + "UE.Dr".length
        val catalogEntries = listOf(
            DreamShaderMaterialExpressionInfo(
                namespace = "UE",
                className = "UMaterialExpressionDreamOnly",
                ueName = "DreamOnly",
                signature = "UE.DreamOnly(Input=Value)",
                source = DreamShaderMaterialExpressionSource.CONFIGURED_MANIFEST
            )
        )

        val suggestions = DreamShaderCompletionSuggester.suggest(
            text = text,
            offset = offset,
            materialExpressionCatalogEntries = catalogEntries
        )
        val dreamOnly = suggestions.firstOrNull { it.label == "DreamOnly" }

        assertTrue(dreamOnly != null)
        assertTrue(dreamOnly?.insertText == "DreamOnly(Input=Value)")
        assertTrue(dreamOnly?.detail == "UE.DreamOnly")
    }

    @Test
    fun `suggests namespaced catalog members`() {
        val text = """
            Shader MySurface {
                Graph {
                    Substrate.Sl
                }
            }
        """.trimIndent()
        val offset = text.indexOf("Substrate.Sl") + "Substrate.Sl".length
        val catalogEntries = listOf(
            DreamShaderMaterialExpressionInfo(
                namespace = "Substrate",
                className = "UMaterialExpressionSubstrateSlabBSDF",
                ueName = "Slab",
                signature = "Substrate.Slab(BaseColor=Color)",
                outputType = "Substrate",
                source = DreamShaderMaterialExpressionSource.CONFIGURED_MANIFEST
            )
        )

        val suggestions = DreamShaderCompletionSuggester.suggest(
            text = text,
            offset = offset,
            materialExpressionCatalogEntries = catalogEntries
        )
        val slab = suggestions.firstOrNull { it.label == "Slab" }

        assertTrue(slab != null)
        assertTrue(slab?.insertText == "Slab(BaseColor=Color)")
        // detail 现在附带输出类型（需求 D）。
        assertTrue(slab?.detail == "Substrate.Slab → Substrate")
    }

    @Test
    fun `catalog snippet strips namespace prefix to avoid duplication`() {
        val text = """
            Shader MySurface {
                Graph {
                    UE.Mater
                }
            }
        """.trimIndent()
        val offset = text.indexOf("UE.Mater") + "UE.Mater".length
        // 模拟 richEntry 合成的 snippet：含完整 `UE.` 前缀与 OutputType 制表位。
        val catalogEntries = listOf(
            DreamShaderMaterialExpressionInfo(
                namespace = "UE",
                className = "UMaterialExpressionMaterialXScreen",
                ueName = "MaterialXScreen",
                signature = "UE.MaterialXScreen(OutputType=\"float1\", A=Value, B=Value, Alpha=Value)",
                outputType = "float1",
                snippet = "UE.MaterialXScreen(OutputType=\"\${1:float1}\", A=\${2:Value}, B=\${3:Value}, Alpha=\${4:Value})\$0",
                source = DreamShaderMaterialExpressionSource.CONFIGURED_MANIFEST
            )
        )

        val suggestions = DreamShaderCompletionSuggester.suggest(
            text = text,
            offset = offset,
            materialExpressionCatalogEntries = catalogEntries
        )
        val item = suggestions.firstOrNull { it.label == "MaterialXScreen" }

        assertTrue(item != null)
        // snippet 已去掉 `UE.` 前缀，插入后不会变成 `UE.UE.MaterialXScreen`。
        assertEquals(
            "MaterialXScreen(OutputType=\"\${1:float1}\", A=\${2:Value}, B=\${3:Value}, Alpha=\${4:Value})\$0",
            item?.snippet
        )
    }

    @Test
    fun `suggests callable candidates in graph section with function presentation`() {
        val text = """
            Shader MySurface {
                Graph {
                    Ap
                }
            }
        """.trimIndent()
        val offset = text.indexOf("Ap") + "Ap".length

        val suggestions = DreamShaderCompletionSuggester.suggest(
            text = text,
            offset = offset,
            callableCandidates = listOf(
                DreamShaderCompletionItem(
                    label = "ApplyTint",
                    insertText = "ApplyTint()",
                    detail = "ApplyTint(color, tint, result)",
                    tailText = "(color, tint, result)",
                    typeText = "callable",
                    priority = 65.0
                )
            )
        )
        val callable = suggestions.firstOrNull { it.label == "ApplyTint" }

        assertTrue(callable != null)
        assertEquals("ApplyTint()", callable?.insertText)
        assertEquals("(color, tint, result)", callable?.tailText)
        assertEquals("callable", callable?.typeText)
    }

    @Test
    fun `suggests local symbols in graph section`() {
        val text = """
            Shader MySurface {
                Graph {
                    float roughness = 0.5;
                    rou
                }
            }
        """.trimIndent()
        val offset = text.indexOf("rou\n") + "rou".length

        val suggestions = DreamShaderCompletionSuggester.suggest(text, offset)
        val local = suggestions.firstOrNull { it.label == "roughness" }

        assertTrue(local != null)
        assertEquals("float", local?.detail)
        assertEquals("local", local?.typeText)
    }

    @Test
    fun `filters section completions by declaration kind`() {
        val shaderText = """
            Shader MySurface {
                <caret>
            }
        """.trimIndent().replace("<caret>", "")
        val shaderOffset = shaderText.indexOf("    \n")
        val shaderLabels = DreamShaderCompletionSuggester.suggest(shaderText, shaderOffset).map { it.label }.toSet()
        assertTrue(!shaderLabels.contains("Inputs"))
        assertTrue(shaderLabels.contains("Graph"))

        val virtualFunctionText = """
            VirtualFunction BufferWriter {
                <caret>
            }
        """.trimIndent().replace("<caret>", "")
        val virtualFunctionOffset = virtualFunctionText.indexOf("    \n")
        val virtualFunctionLabels = DreamShaderCompletionSuggester.suggest(virtualFunctionText, virtualFunctionOffset).map { it.label }.toSet()
        assertTrue(!virtualFunctionLabels.contains("Graph"))
        assertTrue(virtualFunctionLabels.contains("Options"))

        val functionText = """
            Function Util {
                <caret>
            }
        """.trimIndent().replace("<caret>", "")
        val functionOffset = functionText.indexOf("    \n")
        val functionLabels = DreamShaderCompletionSuggester.suggest(functionText, functionOffset).map { it.label }.toSet()
        assertTrue(!functionLabels.contains("Graph"))
        assertTrue(!functionLabels.contains("Inputs"))
    }

    @Test
    fun `suggests declaration head arguments and root values`() {
        val shaderText = """Shader("""
        val shaderLabels = DreamShaderCompletionSuggester.suggest(shaderText, shaderText.length).map { it.label }.toSet()
        assertTrue(shaderLabels.contains("Name"))
        assertTrue(shaderLabels.contains("Root"))

        val rootText = """Shader(Root="P"""
        val rootLabels = DreamShaderCompletionSuggester.suggest(rootText, rootText.length).map { it.label }.toSet()
        assertTrue(rootLabels.contains("Plugin.MyPlugin"))
        assertTrue(rootLabels.contains("Plugins.MyPlugin"))

        val virtualFunctionText = """VirtualFunction("""
        val virtualFunctionLabels = DreamShaderCompletionSuggester.suggest(
            virtualFunctionText,
            virtualFunctionText.length
        ).map { it.label }.toSet()
        assertTrue(virtualFunctionLabels.contains("Name"))
        assertTrue(!virtualFunctionLabels.contains("Root"))
    }

    @Test
    fun `suggests namespace qualified callable members`() {
        val text = """
            Shader MySurface {
                Graph {
                    Tools::Ap
                }
            }
        """.trimIndent()
        val offset = text.indexOf("Tools::Ap") + "Tools::Ap".length

        val suggestions = DreamShaderCompletionSuggester.suggest(
            text = text,
            offset = offset,
            namespaceCallableCandidates = listOf(
                DreamShaderNamespaceCallableCandidate(
                    namespacePath = listOf("Tools"),
                    item = DreamShaderCompletionItem(
                        label = "ApplyTint",
                        detail = "Tools::ApplyTint(color, tint)",
                        typeText = "callable",
                        priority = 65.0
                    )
                ),
                DreamShaderNamespaceCallableCandidate(
                    namespacePath = listOf("Other"),
                    item = DreamShaderCompletionItem(label = "ApplyOther")
                )
            )
        )
        val callable = suggestions.firstOrNull { it.label == "ApplyTint" }

        assertTrue(callable != null)
        assertEquals("ApplyTint()", callable?.insertText)
        assertEquals("callable", callable?.typeText)
        assertTrue(suggestions.none { it.label == "ApplyOther" })
    }

    @Test
    fun `scopes local symbol completion to current graph body before caret`() {
        val text = """
            Shader MySurface {
                Graph {
                    float localBefore = 0.5;
                    loc
                    float localAfter = 1.0;
                }
            }

            Shader OtherSurface {
                Graph {
                    float outside = 0.0;
                }
            }
        """.trimIndent()
        val offset = text.indexOf("loc\n") + "loc".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("localBefore"))
        assertTrue(!labels.contains("localAfter"))
        assertTrue(!labels.contains("outside"))
    }

    @Test
    fun `suggests qualifiers at input declaration starts`() {
        val text = """
            ShaderFunction MyFunction {
                Inputs {
                    o
                }
                Graph {
                }
            }
        """.trimIndent()
        val offset = text.indexOf("o\n") + "o".length

        val labels = DreamShaderCompletionSuggester.suggest(text, offset).map { it.label }.toSet()
        assertTrue(labels.contains("out"))
        assertTrue(labels.contains("opt"))
    }

    @Test
    fun `auto popup triggers only in high value DreamShader contexts`() {
        val graphText = """
            Shader MySurface {
                Graph {
                    UE.
                }
            }
        """.trimIndent()
        val graphOffset = graphText.indexOf("UE.") + "UE.".length
        val importText = """import "Lib"""

        assertTrue(DreamShaderCompletionAutoPopup.shouldAutoPopup(graphText, graphOffset, '.'))
        assertTrue(DreamShaderCompletionAutoPopup.shouldAutoPopup(importText, importText.length, '"'))
        assertTrue(DreamShaderCompletionAutoPopup.shouldAutoPopup("""import "@""", """import "@""".length, '@'))
        assertTrue(!DreamShaderCompletionAutoPopup.shouldAutoPopup("// UE.", "// UE.".length, '.'))
    }
}
