package com.github.tsdaer.dreamshaderlanguagesupport.language.integration

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderUpstreamExamplesTest : BasePlatformTestCase() {
    fun testUpstreamExamplesMarkdownCodeBlocksAreParsable() {
        val markdown = loadUpstreamExamplesMarkdown()
        val codeBlocks = extractCodeBlocks(markdown)
        assertTrue(
            "Expected at least 14 fenced code blocks from upstream Examples.md, actual: ${codeBlocks.size}",
            codeBlocks.size >= 14
        )

        codeBlocks.forEachIndexed { idx, block ->
            val case = materializeExampleCase(idx + 1, block.trim())
            val file = myFixture.configureByText(case.fileName, case.fileText)
            val psiErrors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
            assertTrue(
                buildString {
                    appendLine("Upstream example #${idx + 1} contains parser errors")
                    appendLine("File: ${case.fileName}")
                    appendLine("Snippet first line: ${block.lineSequence().firstOrNull()?.trim().orEmpty()}")
                    appendLine("Parser errors: ${psiErrors.map { it.errorDescription }}")
                },
                psiErrors.isEmpty()
            )
        }
    }

    private fun loadUpstreamExamplesMarkdown(): String {
        val stream = javaClass.getResourceAsStream("/upstream/Examples.md")
            ?: error("Missing test resource: /upstream/Examples.md")
        return stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun extractCodeBlocks(markdown: String): List<String> {
        val regex = Regex("```(?:[A-Za-z0-9_+-]+)?\\R([\\s\\S]*?)\\R```")
        return regex.findAll(markdown).map { it.groupValues[1] }.toList()
    }

    private fun materializeExampleCase(index: Int, snippet: String): ExampleCase {
        val trimmed = snippet.trim()
        return when {
            startsWithTopLevelDeclaration(trimmed) -> {
                ExampleCase(
                    fileName = "upstream_example_${index}${chooseTopLevelExtension(trimmed)}",
                    fileText = trimmed
                )
            }
            trimmed.startsWith("Graph = {") -> {
                ExampleCase(
                    fileName = "upstream_example_${index}_graph.dsm",
                    fileText = wrapAsMaterialWithGraph(index, trimmed)
                )
            }
            trimmed.startsWith("Properties = {") -> {
                ExampleCase(
                    fileName = "upstream_example_${index}_properties.dsm",
                    fileText = wrapAsMaterialWithProperties(index, trimmed)
                )
            }
            else -> {
                ExampleCase(
                    fileName = "upstream_example_${index}.dsh",
                    fileText = trimmed
                )
            }
        }
    }

    private fun startsWithTopLevelDeclaration(snippet: String): Boolean {
        val prefixPattern = Regex(
            "^(?:\\s*import\\s+\"[^\"]+\"\\s*;\\s*)*(Shader|ShaderFunction|ShaderLayer|ShaderLayerBlend|VirtualFunction|Function|GraphFunction|Namespace)\\b",
            setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL)
        )
        return prefixPattern.containsMatchIn(snippet)
    }

    private fun chooseTopLevelExtension(snippet: String): String {
        val head = snippet.lineSequence().firstOrNull()?.trim().orEmpty()
        return when {
            head.startsWith("ShaderFunction(") ||
                head.startsWith("ShaderLayer(") ||
                head.startsWith("ShaderLayerBlend(") -> ".dsf"
            head.startsWith("Shader(") || head.startsWith("import ") -> ".dsm"
            else -> ".dsh"
        }
    }

    private fun wrapAsMaterialWithGraph(index: Int, graphSection: String): String {
        return """
            Shader(Name="DreamMaterials/M_UpstreamGraph_$index")
            {
                Outputs = {
                    vec3 Color;
                    Base.EmissiveColor = Color;
                }

                $graphSection
            }
        """.trimIndent()
    }

    private fun wrapAsMaterialWithProperties(index: Int, propertiesSection: String): String {
        return """
            Shader(Name="DreamMaterials/M_UpstreamProperties_$index")
            {
                $propertiesSection

                Outputs = {
                    vec3 Color;
                    Base.EmissiveColor = Color;
                }

                Graph = {
                    Color = vec3(1.0, 1.0, 1.0);
                }
            }
        """.trimIndent()
    }

    private data class ExampleCase(
        val fileName: String,
        val fileText: String
    )
}
