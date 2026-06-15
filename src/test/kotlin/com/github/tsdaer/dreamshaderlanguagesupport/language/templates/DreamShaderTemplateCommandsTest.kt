package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.*

class DreamShaderTemplateCommandsTest : BasePlatformTestCase() {
    override fun setUp() {
        super.setUp()
        myFixture.addFileToProject(
            "Builtin/Texture.dsh",
            """
            Namespace Texture {
                Function Sample2DRGB(in Texture2D InputTexture, in float2 UV, out float3 Color) {
                    Color = float3(1.0, 1.0, 1.0);
                }
            }
            """.trimIndent()
        )
        myFixture.addFileToProject(
            "Builtin/Noise.dsh",
            """
            Namespace Noise {
                Function FBM2D(in float2 UV, out float Value) {
                    Value = 0.5;
                }
            }
            """.trimIndent()
        )
    }

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

    fun testCreateTextureSampleTemplateProducesValidDsm() {
        val service = DreamShaderTemplateService(project)
        val result = service.createTextureSampleTemplate("Templates/M_TextureSample.dsm")

        assertTrue(result.success)
        val target = requireNotNull(result.targetPath)
        assertTrue(target.isRegularFile())

        val text = target.readText()
        assertTrue(text.contains("Builtin/Texture.dsh"))
        assertTrue(text.contains("Texture::Sample2DRGB"))
        assertTrue(text.contains("Shader("))
        assertNoErrors("M_TextureSample.dsm", text)
    }

    fun testCreateNoiseMaterialTemplateProducesValidDsm() {
        val service = DreamShaderTemplateService(project)
        val result = service.createNoiseMaterialTemplate("Templates/M_NoiseMaterial.dsm")

        assertTrue(result.success)
        val target = requireNotNull(result.targetPath)
        assertTrue(target.isRegularFile())

        val text = target.readText()
        assertTrue(text.contains("Builtin/Noise.dsh"))
        assertTrue(text.contains("Noise::FBM2D"))
        assertTrue(text.contains("Shader("))
        assertNoErrors("M_NoiseMaterial.dsm", text)
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

    fun testCreatePackageScaffoldWithFullRequestWritesMetadataAndExampleToggle() {
        val service = DreamShaderTemplateService(project)
        val packageName = "@typedreammoon/full-request-${System.nanoTime()}"
        val result = service.createPackageScaffold(
            DreamShaderPackageScaffoldRequest(
                name = packageName,
                displayName = "Full Request",
                description = "A generated package with full metadata.",
                namespaceName = "FullRequest",
                author = "Dream Author",
                repository = "https://github.com/typedreammoon/full-request",
                includeExample = false
            )
        )

        assertTrue(result.success)
        val root = requireNotNull(result.targetPath)
        assertTrue(root.resolve("dreamshader.package.json").isRegularFile())
        assertFalse(root.resolve("Examples").exists())

        val metadata = root.resolve("dreamshader.package.json").readText()
        assertTrue(metadata.contains(""""displayName": "Full Request""""))
        assertTrue(metadata.contains(""""description": "A generated package with full metadata.""""))
        assertTrue(metadata.contains(""""author": "Dream Author""""))
        assertTrue(metadata.contains(""""repository": "https://github.com/typedreammoon/full-request""""))

        val libraryFile = firstLibraryDsh(root.resolve("Library"))
        assertTrue(libraryFile.readText().contains("Namespace FullRequest"))
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
