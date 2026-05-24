package com.github.tsdaer.dreamshaderlanguagesupport.language.packages
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeDiagnosticsRepository
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import java.nio.file.Paths

class DreamShaderPackageBridgeInteropTest : BasePlatformTestCase() {
    fun testBridgeDiagnosticsMapToInstalledPackageSource() {
        val projectBase = project.basePath ?: error("project base path is null")
        val packageFilePath = Paths.get(
            projectBase,
            "DShader",
            "Packages",
            "@scope",
            "dream-noise",
            "Library",
            "Noise.dsh"
        )

        var packageFile: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(packageFilePath.parent.toString())
            val file = parent.findOrCreateChildData(this, packageFilePath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Function Noise {
                }
                """.trimIndent()
            )
            packageFile = file
        }
        val installedPackageFile = packageFile
        assertNotNull("Expected installed package source file", installedPackageFile)

        val diagnosticsPath = Paths.get(projectBase, "Saved", "DreamShader", "Bridge", "diagnostics.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(diagnosticsPath.parent.toString())
            val file = parent.findOrCreateChildData(this, diagnosticsPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                [
                  {
                    "sourcePath": "${installedPackageFile!!.path.replace("\\", "/")}",
                    "line": 1,
                    "column": 10,
                    "severity": "warning",
                    "message": "Package bridge diagnostic"
                  }
                ]
                """.trimIndent()
            )
        }

        val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)
        repository.refresh(installedPackageFile)
        val mapped = repository.diagnosticsForFile(installedPackageFile!!)
        assertEquals(1, mapped.size)
        assertEquals("Package bridge diagnostic", mapped.first().message)
        assertEquals(1, mapped.first().line)
        assertEquals(10, mapped.first().column)
    }
}
