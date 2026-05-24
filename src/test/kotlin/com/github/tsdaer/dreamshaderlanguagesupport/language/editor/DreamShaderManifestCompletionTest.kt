package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

class DreamShaderManifestCompletionTest : BasePlatformTestCase() {
    fun testUsesConfiguredMaterialExpressionManifestPath() {
        val manifestPath = createTempManifest(
            """
            { "classes": [ { "className": "ConfiguredNode" } ] }
            """.trimIndent()
        )
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.materialExpressionManifestPath = manifestPath.toString()

        val text = """
            Shader Main {
                Graph {
                    float x = UE.Expression(Class="Con");
                }
            }
        """.trimIndent()
        val offset = text.indexOf("\"Con") + "\"Con".length

        val labels = DreamShaderCompletionSuggester.suggest(
            text = text,
            offset = offset,
            expressionClassCandidates = DreamShaderMaterialExpressionManifest.expressionClassNames(
                project,
                settings.state.materialExpressionManifestPath
            )
        ).map { it.label }.toSet()

        assertTrue(labels.contains("ConfiguredNode"))
    }

    fun testUeExpressionClassCompletionWithManifestAndFallback() {
        val projectBase = project.basePath ?: error("project base path is null")
        val bridgeManifestPath = Paths.get(projectBase, "Saved", "DreamShader", "Bridge", "material-expression-manifest.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(bridgeManifestPath.parent.toString())
            val file = parent.findOrCreateChildData(this, bridgeManifestPath.fileName.toString())
            VfsUtil.saveText(file, """{ "classes": [ { "className": "BridgeNode" } ] }""")
        }

        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.materialExpressionManifestPath = ""

        val classes = DreamShaderMaterialExpressionManifest.expressionClassNames(project, settings.state.materialExpressionManifestPath)
        assertTrue(classes.contains("BridgeNode"))
        assertTrue(classes.contains("Sine"))
    }

    fun testInvalidManifestPathFallsBackGracefully() {
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.materialExpressionManifestPath = "Z:/invalid/path/not_exists_manifest.json"

        val classes = DreamShaderMaterialExpressionManifest.expressionClassNames(project, settings.state.materialExpressionManifestPath)
        assertTrue(classes.isNotEmpty())
        assertTrue(classes.contains("Sine"))
    }

    private fun createTempManifest(content: String): Path {
        val tempFile = Files.createTempFile("dreamshader-manifest-", ".json")
        Files.writeString(tempFile, content)
        tempFile.toFile().deleteOnExit()
        return tempFile
    }
}
