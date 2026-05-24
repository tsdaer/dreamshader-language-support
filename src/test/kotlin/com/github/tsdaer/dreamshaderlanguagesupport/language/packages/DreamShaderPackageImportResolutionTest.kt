package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderGotoDeclarationHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderPackageImportResolutionTest : BasePlatformTestCase() {
    fun testResolvesScopedImportWithExtension() {
        val projectBase = project.basePath ?: error("project base path is null")
        val targetPath = Paths.get(
            projectBase,
            "DShader",
            "Packages",
            "@typedreammoon",
            "dream-noise",
            "Library",
            "Noise.dsh"
        )
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(targetPath.parent.toString())
            val file = parent.findOrCreateChildData(this, targetPath.fileName.toString())
            VfsUtil.saveText(file, "Function Noise { }")
        }

        val source = myFixture.configureByText(
            "main.dsm",
            """import "@typedreammoon/dream-noise/Library/Noise.dsh";"""
        )
        val offset = source.text.indexOf("@typedreammoon") + 1
        val sourceElement = source.findElementAt(offset)
        assertNotNull(sourceElement)

        val targets = DreamShaderGotoDeclarationHandler()
            .getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)
        assertNotNull(targets)
        assertEquals("Noise.dsh", targets!!.first().containingFile.name)
    }

    fun testResolvesScopedImportWithoutExtension() {
        val projectBase = project.basePath ?: error("project base path is null")
        val targetPath = Paths.get(
            projectBase,
            "DShader",
            "Packages",
            "@typedreammoon",
            "dream-noise",
            "Library",
            "Noise.dsh"
        )
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(targetPath.parent.toString())
            val file = parent.findOrCreateChildData(this, targetPath.fileName.toString())
            VfsUtil.saveText(file, "Function Noise { }")
        }

        val source = myFixture.configureByText(
            "main2.dsm",
            """import "@typedreammoon/dream-noise/Library/Noise";"""
        )
        val offset = source.text.indexOf("@typedreammoon") + 1
        val sourceElement = source.findElementAt(offset)
        assertNotNull(sourceElement)

        val targets = DreamShaderGotoDeclarationHandler()
            .getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)
        assertNotNull(targets)
        assertEquals("Noise.dsh", targets!!.first().containingFile.name)
    }

    fun testImportResolutionPrecedenceForPackagePaths() {
        val projectBase = project.basePath ?: error("project base path is null")
        val relativeLocal = Paths.get(projectBase, "Materials", "LocalNoise.dsh")
        val dshaderLevel = Paths.get(projectBase, "DShader", "Lib", "Shared.dsh")
        val packageLevel = Paths.get(projectBase, "DShader", "Packages", "@scope", "pkg", "Lib", "Shared.dsh")

        WriteCommandAction.runWriteCommandAction(project) {
            val p1 = VfsUtil.createDirectories(relativeLocal.parent.toString())
            VfsUtil.saveText(p1.findOrCreateChildData(this, relativeLocal.fileName.toString()), "Function Local { }")

            val p2 = VfsUtil.createDirectories(dshaderLevel.parent.toString())
            VfsUtil.saveText(p2.findOrCreateChildData(this, dshaderLevel.fileName.toString()), "Function DShaderLevel { }")

            val p3 = VfsUtil.createDirectories(packageLevel.parent.toString())
            VfsUtil.saveText(p3.findOrCreateChildData(this, packageLevel.fileName.toString()), "Function PackageLevel { }")
        }

        var callingVf = VfsUtil.findFile(relativeLocal.parent.resolve("Main.dsm"), false)
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(relativeLocal.parent.toString())
            val file = parent.findOrCreateChildData(this, "Main.dsm")
            VfsUtil.saveText(
                file,
                """
                import "LocalNoise.dsh";
                import "Lib/Shared.dsh";
                """.trimIndent()
            )
            callingVf = file
        }
        val callingFile = myFixture.configureFromExistingVirtualFile(callingVf ?: error("calling file not created"))

        val handler = DreamShaderGotoDeclarationHandler()

        val localOffset = myFixture.file.text.indexOf("LocalNoise.dsh") + 1
        val localTargets = handler.getGotoDeclarationTargets(myFixture.file.findElementAt(localOffset), localOffset, myFixture.editor)
        assertNotNull(localTargets)
        assertTrue(localTargets!!.first().containingFile.virtualFile.path.replace('\\', '/').endsWith("/Materials/LocalNoise.dsh"))

        val sharedOffset = myFixture.file.text.lastIndexOf("Lib/Shared.dsh") + 1
        val sharedTargets = handler.getGotoDeclarationTargets(myFixture.file.findElementAt(sharedOffset), sharedOffset, myFixture.editor)
        assertNotNull(sharedTargets)
        assertTrue(sharedTargets!!.first().containingFile.virtualFile.path.replace('\\', '/').endsWith("/DShader/Lib/Shared.dsh"))
    }

    fun testResolvesScopedPackageRootImportToManifestEntry() {
        val projectBase = project.basePath ?: error("project base path is null")
        val packageRoot = Paths.get(
            projectBase,
            "DShader",
            "Packages",
            "@typedreammoon",
            "dream-noise"
        )
        val entryPath = packageRoot.resolve("Library").resolve("NoiseMain.dsh")
        val metadataPath = packageRoot.resolve("dreamshader.package.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val entryParent = VfsUtil.createDirectories(entryPath.parent.toString())
            VfsUtil.saveText(entryParent.findOrCreateChildData(this, entryPath.fileName.toString()), "Function Entry { }")
            val metadataParent = VfsUtil.createDirectories(metadataPath.parent.toString())
            VfsUtil.saveText(metadataParent.findOrCreateChildData(this, metadataPath.fileName.toString()),
                """
                {
                  "name": "@typedreammoon/dream-noise",
                  "dreamshader": {
                    "entry": "Library/NoiseMain"
                  }
                }
                """.trimIndent()
            )
        }

        val source = myFixture.configureByText(
            "main3.dsm",
            """import "@typedreammoon/dream-noise";"""
        )
        val offset = source.text.indexOf("@typedreammoon") + 1
        val sourceElement = source.findElementAt(offset)
        assertNotNull(sourceElement)

        val targets = DreamShaderGotoDeclarationHandler()
            .getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)
        assertNotNull(targets)
        assertEquals("NoiseMain.dsh", targets!!.first().containingFile.name)
    }

    fun testResolvesUnscopedPackageRootImportToDefaultLibraryEntry() {
        val projectBase = project.basePath ?: error("project base path is null")
        val packageRoot = Paths.get(projectBase, "DShader", "Packages", "dream-common")
        val entryPath = packageRoot.resolve("Library").resolve("dream-commonLib.dsh")
        val metadataPath = packageRoot.resolve("dreamshader.package.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val entryParent = VfsUtil.createDirectories(entryPath.parent.toString())
            VfsUtil.saveText(entryParent.findOrCreateChildData(this, entryPath.fileName.toString()), "Function Entry { }")
            val metadataParent = VfsUtil.createDirectories(metadataPath.parent.toString())
            VfsUtil.saveText(metadataParent.findOrCreateChildData(this, metadataPath.fileName.toString()),
                """
                {
                  "name": "dream-common",
                  "version": "1.0.0"
                }
                """.trimIndent()
            )
        }

        val source = myFixture.configureByText(
            "main4.dsm",
            """import "dream-common";"""
        )
        val offset = source.text.indexOf("dream-common") + 1
        val sourceElement = source.findElementAt(offset)
        assertNotNull(sourceElement)

        val targets = DreamShaderGotoDeclarationHandler()
            .getGotoDeclarationTargets(sourceElement, offset, myFixture.editor)
        assertNotNull(targets)
        assertEquals("dream-commonLib.dsh", targets!!.first().containingFile.name)
    }
}
