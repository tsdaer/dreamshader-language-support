package com.github.tsdaer.dreamshaderlanguagesupport.language.packages
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderGotoDeclarationHandler
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
}
