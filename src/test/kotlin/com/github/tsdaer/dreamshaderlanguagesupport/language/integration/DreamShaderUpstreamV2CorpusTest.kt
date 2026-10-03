package com.github.tsdaer.dreamshaderlanguagesupport.language.integration

import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Plan A2 acceptance: every file of the upstream 2.0 corpus (a checked-in copy of upstream
 * `Tests/Corpus/Lang`, upstream 2.0.2 @b3bbf48) parses to a usable tree.
 *
 * Valid corpus files must parse with **zero** error elements - the plugin is not the compiler and
 * the 2.0 grammar must accept every upstream-blessed example. Files named `*.bad.*` upstream carry
 * deliberate front-end errors; the plugin stays permissive about them, so the test only requires a
 * complete, total parse (the tree spans the whole file).
 */
class DreamShaderUpstreamV2CorpusTest : BasePlatformTestCase() {

    private fun corpusFileNames(): List<String> =
        javaClass.getResourceAsStream("/upstream/v2/corpus-files.txt")
            ?.bufferedReader(Charsets.UTF_8)?.readLines()?.filter { it.isNotBlank() }
            ?: error("Missing test resource: /upstream/v2/corpus-files.txt")

    private fun corpusFileText(name: String): String =
        javaClass.getResourceAsStream("/upstream/v2/corpus/$name")
            ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
            // Documents normalize CRLF to LF; normalize here so length assertions line up.
            ?.replace("\r\n", "\n")
            ?: error("Missing corpus resource: /upstream/v2/corpus/$name")

    fun testUpstreamV2CorpusValidFilesParseWithoutErrors() {
        val valid = corpusFileNames().filter { !it.contains(".bad.") }
        assertTrue(valid.size >= 40, "Expected the upstream corpus to ship many valid files, got ${valid.size}")

        val failures = mutableListOf<String>()
        for (name in valid) {
            val text = corpusFileText(name)
            val file = myFixture.configureByText(name.substringAfterLast('/'), text)
            val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
            if (errors.isNotEmpty()) {
                failures += "$name -> ${errors.map { it.errorDescription }.distinct()}"
            }
        }
        assertTrue(failures.isEmpty(), "Upstream V2 corpus files must parse without errors:\n${failures.joinToString("\n")}")
    }

    fun testUpstreamV2CorpusBadFilesParseTotally() {
        val bad = corpusFileNames().filter { it.contains(".bad.") }
        assertTrue(bad.isNotEmpty(), "Expected upstream .bad. corpus files to be present")

        for (name in bad) {
            val text = corpusFileText(name)
            val file = myFixture.configureByText(name.substringAfterLast('/'), text)
            assertEquals(
                text.length,
                file.textLength,
                "Corpus file $name must produce a tree spanning the whole file",
            )
        }
    }

    fun testUpstreamTemplatesRenderAndParseWithoutErrors() {
        val stem = "M_NewMaterial"
        for (template in listOf("NewMaterial.dss", "NewFunction.dss", "NewInstance.dsi")) {
            val raw = javaClass.getResourceAsStream("/upstream/v2/templates/$template")
                ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: error("Missing template resource: /upstream/v2/templates/$template")
            // Render the upstream placeholders the same way the Content Browser does.
            val rendered = raw
                .replace("{FILENAME}", template)
                .replace("{ASSETPATH}", "/Game/Materials/$stem")
                .replace("{STEM}", stem)
                .replace("{PARENT}", stem)
            val file = myFixture.configureByText("rendered_$template", rendered)
            val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
            assertTrue(
                errors.isEmpty(),
                "Template $template must render into an error-free file: ${errors.map { it.errorDescription }}",
            )
        }
    }
}
