package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Milestone M12 C6: Code Vision for the 2.0 dialect - usage counts on export functions
 * and the "Parent: X" hint above a `.dsi` instance pragma.
 */
class DreamShader2CodeVisionTest : BasePlatformTestCase() {
    fun testExportFunctionCarriesUsageCount() {
        val file = myFixture.configureByText(
            "vision.dss",
            """
            #include "Lib/Math.dss"
            export void Work() {
            }
            """.trimIndent()
        )
        val entries = DreamShader2CodeVision.compute(file, "dreamshader.codeVision.workflows")
        assertTrue("Expected an export lens, got ${entries.map { it.second.toString() }}", entries.isNotEmpty())
        val text = entries.single().second.toString()
        assertTrue("Usage count expected in: $text", "usages" in text)
    }

    fun testHelperFunctionCarriesNoLens() {
        val file = myFixture.configureByText(
            "vision_helper.dss",
            "void Helper() {\n}\n",
        )
        val entries = DreamShader2CodeVision.compute(file, "dreamshader.codeVision.workflows")
        assertTrue(entries.isEmpty())
    }

    fun testInstancePragmaCarriesParentHint() {
        val file = myFixture.configureByText(
            "vision.dsi",
            "#pragma instance(Parent = \"M_Skin\")\nuniform float Gain;\n",
        )
        val entries = DreamShader2CodeVision.compute(file, "dreamshader.codeVision.workflows")
        val labels = entries.map { it.second.toString() }
        assertTrue("Parent hint expected, got $labels", labels.any { it.contains("Parent:") && it.contains("M_Skin") })
    }

    fun testLegacyFilesCarryNoV2Lens() {
        val file = myFixture.configureByText("vision.dsf", "Shader Main {\n}\n")
        val entries = DreamShader2CodeVision.compute(file as DreamShaderPsiFile, "dreamshader.codeVision.workflows")
        assertTrue(entries.isEmpty())
    }
}
