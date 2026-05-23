package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderProjectSettings
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import java.nio.file.Paths

class DreamShaderBridgeActionsTest : BasePlatformTestCase() {
    fun testBridgeActionCommandSetAvailability() {
        val projectBase = project.basePath ?: error("project base path is null")
        val sourcePath = Paths.get(projectBase, "DShader", "Materials", "BridgeActionSet.dsm")
        var sourceFile: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(sourcePath.parent.toString())
            val file = parent.findOrCreateChildData(this, sourcePath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Shader Main {
                    Graph {
                        float x = 1.0;
                    }
                }
                """.trimIndent()
            )
            sourceFile = file
        }
        val activeFile = sourceFile ?: error("source file not created")
        myFixture.configureFromExistingVirtualFile(activeFile)

        val diagnosticsPath = Paths.get(projectBase, "Saved", "DreamShader", "Bridge", "diagnostics.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(diagnosticsPath.parent.toString())
            val file = parent.findOrCreateChildData(this, diagnosticsPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                [
                  {
                    "sourcePath": "${activeFile.path.replace("\\", "/")}",
                    "line": 3,
                    "column": 13,
                    "severity": "warning",
                    "message": "Action set test warning"
                  }
                ]
                """.trimIndent()
            )
        }

        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.bridgeRecompileCurrentCommand = ""
        settings.bridgeRecompileAllCommand = ""
        settings.bridgeCleanGeneratedShadersCommand = ""

        val actionIds = listOf(
            "DreamShader.BridgeTools.RefreshDiagnostics",
            "DreamShader.BridgeTools.RecompileCurrent",
            "DreamShader.BridgeTools.RecompileAll",
            "DreamShader.BridgeTools.CleanGeneratedShaders",
            "DreamShader.BridgeTools.OpenBridgeDirectory",
            "DreamShader.BridgeTools.OpenDiagnosticsFile",
            "DreamShader.BridgeTools.OpenFirstDiagnosticLocation"
        )

        val manager = ActionManager.getInstance()
        actionIds.forEach { id ->
            val action = manager.getAction(id)
            assertNotNull("Expected action to be registered: $id", action)
            assertTrue("Expected non-empty action text for: $id", !action!!.templatePresentation.text.isNullOrBlank())
            runCatching { action.actionPerformed(eventFor(action, activeFile)) }
                .getOrElse { throw AssertionError("Action threw unexpectedly: $id", it) }
        }
    }

    private fun eventFor(action: AnAction, file: VirtualFile?): AnActionEvent {
        val context = DataContext { dataId ->
            when {
                CommonDataKeys.PROJECT.`is`(dataId) -> project
                CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> file
                else -> null
            }
        }
        return AnActionEvent.createEvent(
            action,
            context,
            action.templatePresentation.clone(),
            ActionPlaces.UNKNOWN,
            ActionUiKind.NONE,
            null
        )
    }
}
