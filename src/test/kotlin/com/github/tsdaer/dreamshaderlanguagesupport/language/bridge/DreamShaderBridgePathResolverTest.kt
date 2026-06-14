package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths

class DreamShaderBridgePathResolverTest : BasePlatformTestCase() {
    fun testAutoDetectsProjectRootFromActiveFile() {
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.projectRoot = ""

        val projectBase = project.basePath ?: error("project base path is null")
        val filePath = Paths.get(projectBase, "DShader", "Materials", "Main.dsm")
        var activeFile = VfsUtil.findFile(filePath, false)
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(filePath.parent.toString())
            val file = parent.findOrCreateChildData(this, filePath.fileName.toString())
            VfsUtil.saveText(file, "Shader Main { }")
            activeFile = file
        }
        val createdFile = activeFile ?: error("active file not created")

        val root = DreamShaderBridgePathResolver.resolveProjectRoot(project, createdFile)
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, createdFile)
        assertEquals(projectBase.replace('\\', '/').trimEnd('/'), root)
        assertEquals(
            "${projectBase.replace('\\', '/').trimEnd('/')}/Saved/DreamShader/Bridge",
            bridgeDir
        )
    }

    fun testProjectRootSettingOverridesAutoDetect() {
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.projectRoot = "J:/CustomProjectRoot"

        val root = DreamShaderBridgePathResolver.resolveProjectRoot(project, null)
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, null)
        assertEquals("J:/CustomProjectRoot", root)
        assertEquals("J:/CustomProjectRoot/Saved/DreamShader/Bridge", bridgeDir)
    }

    fun testAutoDetectsUprojectRootFromNestedFile() {
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.projectRoot = ""

        // 构造临时 UE 项目树：<ueRoot>/Foo.uproject 与 <ueRoot>/DShader/Materials/Main.dsm
        val ueRoot = Files.createTempDirectory("ds-uproject-test").toFile()
        try {
            File(ueRoot, "Foo.uproject").writeText("{ \"EngineAssociation\": \"5.6\" }")
            val materialsDir = File(ueRoot, "DShader/Materials").apply { mkdirs() }
            val dsmFile = File(materialsDir, "Main.dsm").apply { writeText("Shader Main { }") }
            val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(dsmFile)
                ?: error("virtual file not found for ${dsmFile.path}")

            val expectedRoot = ueRoot.path.replace('\\', '/').trimEnd('/')
            val root = DreamShaderBridgePathResolver.resolveProjectRoot(project, vf)
            val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, vf)

            // .uproject 锚点应优先于 /DShader/ 路径标记，解析到 .uproject 所在目录。
            assertEquals(expectedRoot, root)
            assertEquals("$expectedRoot/Saved/DreamShader/Bridge", bridgeDir)
        } finally {
            ueRoot.deleteRecursively()
        }
    }
}
