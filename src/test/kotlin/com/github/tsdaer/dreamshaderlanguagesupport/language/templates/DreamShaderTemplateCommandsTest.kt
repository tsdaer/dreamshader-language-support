package com.github.tsdaer.dreamshaderlanguagesupport.language.templates
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import kotlin.io.path.readText

class DreamShaderTemplateCommandsTest : BasePlatformTestCase() {
    fun testCreateMaterialTemplateProducesValidDsm() {
        val service = DreamShaderTemplateService(project)
        val result = service.createMaterialTemplate("Templates/NewMaterial.dsm")

        assertTrue(result.success)
        val target = requireNotNull(result.targetPath)
        assertTrue(target.isRegularFile())

        val text = target.readText()
        assertTrue(text.contains("Shader("))
        assertTrue(text.contains("Properties"))
        assertTrue(text.contains("Outputs"))
        assertTrue(text.contains("Settings"))
        assertTrue(text.contains("Graph"))
        assertNoErrors("NewMaterial.dsm", text)
    }

    fun testCreateFunctionFileTemplateProducesValidDsf() {
        val service = DreamShaderTemplateService(project)
        val result = service.createFunctionTemplate("Templates/NewFunction.dsf")

        assertTrue(result.success)
        val target = requireNotNull(result.targetPath)
        assertTrue(target.isRegularFile())

        val text = target.readText()
        assertTrue(text.contains("ShaderFunction("))
        assertFalse(text.contains("Shader(Name="))
        assertNoErrors("NewFunction.dsf", text)
    }

    fun testCreateHeaderTemplateProducesValidDsh() {
        val service = DreamShaderTemplateService(project)
        val result = service.createHeaderTemplate("Templates/NewHeader.dsh")

        assertTrue(result.success)
        val target = requireNotNull(result.targetPath)
        assertTrue(target.isRegularFile())

        val text = target.readText()
        assertTrue(text.contains("Namespace"))
        assertTrue(text.contains("Function"))
        assertFalse(text.contains("ShaderFunction("))
        assertFalse(text.contains("Shader("))
        assertNoErrors("NewHeader.dsh", text)
    }

    fun testCreatePackageScaffoldLayout() {
        val service = DreamShaderTemplateService(project)
        val packageName = "@typedreammoon/dream-noise-template-${System.nanoTime()}"
        val result = service.createPackageScaffold(packageName)

        assertTrue(result.success)
        val root = requireNotNull(result.targetPath)
        assertTrue(root.exists())
        assertTrue(root.isDirectory())
        assertTrue(root.resolve("dreamshader.package.json").isRegularFile())
        assertTrue(root.resolve("README.md").isRegularFile())
        assertTrue(root.resolve("LICENSE").isRegularFile())
        assertTrue(root.resolve("Library").isDirectory())
        assertTrue(root.resolve("Examples").isDirectory())
        assertTrue(root.resolve("Examples").resolve("Sample.dsm").isRegularFile())

        val metadata = root.resolve("dreamshader.package.json").readText()
        assertTrue(metadata.contains(""""name": "$packageName""""))
        assertTrue(metadata.contains(""""dreamshader""""))
        assertTrue(metadata.contains("\"entry\": \"Library/"))

        val libraryFile = firstLibraryDsh(root.resolve("Library"))
        assertTrue(libraryFile.isRegularFile())

        assertNoErrors("Sample.dsm", root.resolve("Examples").resolve("Sample.dsm").readText())
        assertNoErrors(libraryFile.name, libraryFile.readText())
    }

    private fun firstLibraryDsh(libraryDir: Path): Path {
        Files.list(libraryDir).use { stream ->
            return stream
                .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".dsh") }
                .findFirst()
                .orElseThrow { AssertionError("expected at least one .dsh in Library/") }
        }
    }

    private fun assertNoErrors(fileName: String, text: String) {
        myFixture.configureByText(fileName, text)
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        assertTrue(
            "Expected no errors, actual: ${errors.map { it.description }}",
            errors.isEmpty()
        )
    }
}
