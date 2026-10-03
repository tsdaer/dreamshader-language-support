package com.github.tsdaer.dreamshaderlanguagesupport.language.integration

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Plan A2 acceptance: every fenced example in the upstream language-v2 documentation (checked-in
 * copies of `Docs/language-v2/index.md`, `substrate.md`, `instances.md`, upstream 2.0.2 @b3bbf48)
 * parses without error elements.
 *
 * Routing mirrors how a user would save the snippet:
 * - a block holding 1.x asset blocks becomes a `.dsm` (legacy front end);
 * - a `#pragma instance(` block becomes a `.dsi`;
 * - statement fragments (Substrate builder forms) are wrapped in an export function body;
 * - everything else is a `.dss`.
 */
class DreamShaderUpstreamV2DocsTest : BasePlatformTestCase() {

    fun testLanguageV2DocExamplesParseWithoutErrors() {
        val docs = listOf("index.md", "substrate.md", "instances.md")
        var caseCount = 0
        val failures = mutableListOf<String>()

        for (doc in docs) {
            val markdown = javaClass.getResourceAsStream("/upstream/v2/docs/$doc")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Missing doc resource: /upstream/v2/docs/$doc")

            extractTaggedCodeBlocks(markdown).forEachIndexed { index, (tag, block) ->
                val snippet = block.trim()
                if (snippet.isEmpty()) return@forEachIndexed
                caseCount++
                val case = materializeCase(doc, index + 1, tag, snippet)
                val file = myFixture.configureByText(case.second, case.first)
                val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
                if (errors.isNotEmpty()) {
                    failures += "${doc}#${index + 1} (${case.second}) -> ${errors.map { it.errorDescription }.distinct()}"
                }
            }
        }

        assertTrue(caseCount >= 7, "Expected at least 7 documented examples, got $caseCount")
        assertTrue(failures.isEmpty(), "Documented examples must parse without errors:\n${failures.joinToString("\n")}")
    }

    fun testInstanceDocIsPinnedAsDsi() {
        val markdown = javaClass.getResourceAsStream("/upstream/v2/docs/instances.md")!!
            .bufferedReader(Charsets.UTF_8).use { it.readText() }
        val blocks = extractTaggedCodeBlocks(markdown).map { it.second.trim() }
        assertEquals(1, blocks.size, "instances.md pins exactly one example")
        val file = myFixture.configureByText("MI_HeroSkin.dsi", blocks.single())
        val uniforms = PsiTreeUtil.collectElementsOfType(file, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2UniformDeclarationImpl::class.java)
        assertEquals(5, uniforms.size, "The .dsi example pins five overrides")
    }

    /** Fenced blocks that carry a language tag; bare fences (shell commands) are skipped. */
    private fun extractTaggedCodeBlocks(markdown: String): List<Pair<String, String>> {
        val regex = Regex("```([A-Za-z0-9_+-]*)\\R([\\s\\S]*?)\\R```")
        return regex.findAll(markdown)
            .map { it.groupValues[1] to it.groupValues[2] }
            .filter { it.first.isNotEmpty() }
            .toList()
    }

    private fun materializeCase(doc: String, index: Int, tag: String, snippet: String): Pair<String, String> {
        val sanitized = doc.substringBeforeLast('.')
        if (containsLegacyAssetBlock(snippet)) {
            return snippet to "upstream_v2_${sanitized}_$index.dsm"
        }
        if (snippet.contains("#pragma instance(")) {
            return snippet to "upstream_v2_${sanitized}_$index.dsi"
        }
        if (!hasV2DeclarationMarker(snippet)) {
            val wrapped = """
                export void DocFragment_${sanitized}_$index(inout material m)
                {
                $snippet
                }
            """.trimIndent()
            return wrapped to "upstream_v2_${sanitized}_$index.dss"
        }
        return snippet to "upstream_v2_${sanitized}_$index.dss"
    }

    private fun containsLegacyAssetBlock(snippet: String): Boolean =
        Regex("(?im)^\\s*(Shader|ShaderFunction|ShaderLayer|ShaderLayerBlend)\\s*\\(").containsMatchIn(snippet)

    private fun hasV2DeclarationMarker(snippet: String): Boolean =
        Regex("(?im)^\\s*(#pragma|#include|import\\b|uniform\\b|export\\b|extern\\b|static\\b|const\\b|struct\\b|///)").containsMatchIn(snippet)
}
