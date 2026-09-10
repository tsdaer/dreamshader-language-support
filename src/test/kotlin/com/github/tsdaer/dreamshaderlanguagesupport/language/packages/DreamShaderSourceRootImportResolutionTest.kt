package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderSourceRootImportResolutionTest : BasePlatformTestCase() {
    fun testPluginRootImportStaysInsideOwningRoot() {
        val base = project.basePath ?: error("project base path is null")
        val projectShared = Paths.get(base, "DShader", "Shared", "Common.dsh")
        val pluginShared = Paths.get(base, "Plugins", "MoonToon", "DShader", "Shared", "Common.dsh")
        val pluginMain = Paths.get(base, "Plugins", "MoonToon", "DShader", "Materials", "Main.dsm")
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.createDirectories(projectShared.parent.toString()).let {
                VfsUtil.saveText(it.findOrCreateChildData(this, projectShared.fileName.toString()), "Function ProjectCommon { }")
            }
            VfsUtil.createDirectories(pluginShared.parent.toString()).let {
                VfsUtil.saveText(it.findOrCreateChildData(this, pluginShared.fileName.toString()), "Function PluginCommon { }")
            }
            VfsUtil.createDirectories(pluginMain.parent.toString()).let {
                VfsUtil.saveText(it.findOrCreateChildData(this, pluginMain.fileName.toString()), "import \"Shared/Common.dsh\";")
            }
        }
        myFixture.configureFromExistingVirtualFile(VfsUtil.findFile(pluginMain, true)!!)
        val source = myFixture.file
        val resolved = DreamShaderImportResolver.resolveImport(source, "Shared/Common.dsh")
        assertNotNull(resolved)
        assertTrue(resolved!!.path.replace('\\', '/').contains("/Plugins/MoonToon/DShader/"))
    }

    fun testRootQualifiedImportsCrossRootsExplicitly() {
        val base = project.basePath ?: error("project base path is null")
        val projectShared = Paths.get(base, "DShader", "Shared", "Common.dsh")
        val pluginShared = Paths.get(base, "Plugins", "MoonToon", "DShader", "Shared", "Toon.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.createDirectories(projectShared.parent.toString()).let {
                VfsUtil.saveText(it.findOrCreateChildData(this, projectShared.fileName.toString()), "Function Common { }")
            }
            VfsUtil.createDirectories(pluginShared.parent.toString()).let {
                VfsUtil.saveText(it.findOrCreateChildData(this, pluginShared.fileName.toString()), "Function Toon { }")
            }
        }
        val source = myFixture.configureByText("loose.dsm", "Shader Main { Graph { } }")
        assertEquals("Common.dsh", DreamShaderImportResolver.resolveImport(source, "Project:Shared/Common.dsh")?.name)
        assertEquals("Toon.dsh", DreamShaderImportResolver.resolveImport(source, "Plugin.MoonToon:Shared/Toon.dsh")?.name)
        assertEquals("Toon.dsh", DreamShaderImportResolver.resolveImport(source, "Plugins/MoonToon:Shared/Toon.dsh")?.name)
    }

    fun testOwnedRootCannotEscapeWithDotDot() {
        val base = project.basePath ?: error("project base path is null")
        val outside = Paths.get(base, "Secret.dsh")
        val sourcePath = Paths.get(base, "DShader", "Main.dsm")
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.createDirectories(outside.parent.toString()).let {
                VfsUtil.saveText(it.findOrCreateChildData(this, outside.fileName.toString()), "Function Secret { }")
            }
            VfsUtil.createDirectories(sourcePath.parent.toString()).let {
                VfsUtil.saveText(it.findOrCreateChildData(this, sourcePath.fileName.toString()), "import \"../Secret.dsh\";")
            }
        }
        myFixture.configureFromExistingVirtualFile(VfsUtil.findFile(sourcePath, true)!!)
        val source = myFixture.file
        assertNull(DreamShaderImportResolver.resolveImport(source, "../Secret.dsh"))
    }
}
