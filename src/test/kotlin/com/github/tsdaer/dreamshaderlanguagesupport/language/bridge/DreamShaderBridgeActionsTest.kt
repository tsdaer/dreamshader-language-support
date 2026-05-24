package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderBridgeActionsTest : BasePlatformTestCase() {
    data class CapturedNotification(val type: NotificationType, val title: String, val content: String)

    private val capturedNotifications = mutableListOf<CapturedNotification>()

    override fun setUp() {
        super.setUp()
        DreamShaderBridgeActionTestHooks.notificationSink = { _, type, title, content ->
            capturedNotifications.add(CapturedNotification(type, title, content))
        }
    }

    override fun tearDown() {
        DreamShaderBridgeActionTestHooks.reset()
        capturedNotifications.clear()
        super.tearDown()
    }

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

    fun testBridgeActionCommandSetReturnsExpectedSuccessAndErrorMessages() {
        val projectBase = project.basePath ?: error("project base path is null")
        val sourcePath = Paths.get(projectBase, "DShader", "Materials", "BridgeActionMessage.dsm")
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
                    "message": "Action message warning"
                  }
                ]
                """.trimIndent()
            )
        }

        val manager = ActionManager.getInstance()
        val recompileCurrentAction = manager.getAction("DreamShader.BridgeTools.RecompileCurrent")!!
        val recompileAllAction = manager.getAction("DreamShader.BridgeTools.RecompileAll")!!
        val refreshAction = manager.getAction("DreamShader.BridgeTools.RefreshDiagnostics")!!
        val settings = project.getService(DreamShaderProjectSettings::class.java).state

        settings.bridgeRecompileCurrentCommand = ""
        capturedNotifications.clear()
        recompileCurrentAction.actionPerformed(eventFor(recompileCurrentAction, activeFile))
        assertNotification(
            NotificationType.ERROR,
            DreamShaderBundle.message("bridge.notification.commandCurrentEmpty")
        )

        settings.bridgeRecompileCurrentCommand = "echo ok"
        DreamShaderBridgeActionTestHooks.commandExecutor = { _, _, _, _ ->
            DreamShaderBridgeCommandResult(success = true, message = "bridge current success")
        }
        capturedNotifications.clear()
        recompileCurrentAction.actionPerformed(eventFor(recompileCurrentAction, activeFile))
        assertNotification(NotificationType.INFORMATION, "bridge current success")

        settings.bridgeRecompileAllCommand = "echo all"
        DreamShaderBridgeActionTestHooks.commandExecutor = { _, _, _, _ ->
            DreamShaderBridgeCommandResult(success = false, message = "bridge all failed")
        }
        capturedNotifications.clear()
        recompileAllAction.actionPerformed(eventFor(recompileAllAction, activeFile))
        assertNotification(NotificationType.ERROR, "bridge all failed")

        DreamShaderBridgeActionTestHooks.commandExecutor = null
        capturedNotifications.clear()
        refreshAction.actionPerformed(eventFor(refreshAction, activeFile))
        val refreshed = capturedNotifications.lastOrNull()
        assertNotNull("Expected refresh notification", refreshed)
        assertEquals(NotificationType.WARNING, refreshed!!.type)
        assertTrue(
            "Expected refresh message to include loaded item count",
            refreshed.content.contains("Loaded 1 item(s).")
        )
    }

    private fun assertNotification(expectedType: NotificationType, expectedContent: String) {
        val notification = capturedNotifications.lastOrNull()
        assertNotNull("Expected notification to be captured", notification)
        assertEquals(expectedType, notification!!.type)
        assertEquals(expectedContent, notification.content)
        assertFalse("Expected non-empty notification title", notification.title.isBlank())
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
