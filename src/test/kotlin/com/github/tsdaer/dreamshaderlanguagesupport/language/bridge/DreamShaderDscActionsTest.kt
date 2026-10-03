package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.notification.NotificationType
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
import java.nio.file.Paths

/**
 * A8: `dsc` CLI toolchain actions. Process execution and the migrate confirmation are
 * replaced through `DreamShaderDscActionTestHooks`, mirroring the bridge-actions hook pattern.
 */
class DreamShaderDscActionsTest : BasePlatformTestCase() {
    data class CapturedNotification(val type: NotificationType, val title: String, val content: String)

    data class CapturedCommand(val command: List<String>, val workingDir: String?)

    private val capturedNotifications = mutableListOf<CapturedNotification>()
    private val capturedCommands = mutableListOf<CapturedCommand>()

    override fun setUp() {
        super.setUp()
        DreamShaderBridgeActionTestHooks.notificationSink = { _, type, title, content ->
            capturedNotifications.add(CapturedNotification(type, title, content))
        }
    }

    override fun tearDown() {
        DreamShaderDscActionTestHooks.reset()
        DreamShaderBridgeActionTestHooks.reset()
        capturedNotifications.clear()
        capturedCommands.clear()
        super.tearDown()
    }

    private fun configureDsc(path: String) {
        project.getService(DreamShaderProjectSettings::class.java).state.dscExecutablePath = path
    }

    private fun hookRunner(result: DreamShaderDscResult) {
        DreamShaderDscActionTestHooks.commandRunner = { command, workingDir ->
            capturedCommands.add(CapturedCommand(command, workingDir))
            result
        }
    }

    private fun newSourceFile(name: String, text: String): VirtualFile {
        val projectBase = project.basePath ?: error("project base path is null")
        val sourcePath = Paths.get(projectBase, "DShader", "Materials", name)
        var file: VirtualFile? = null
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(sourcePath.parent.toString())
            val created = parent.findOrCreateChildData(this, sourcePath.fileName.toString())
            VfsUtil.saveText(created, text)
            file = created
        }
        return file ?: error("source file not created")
    }

    private fun eventFor(file: VirtualFile?): AnActionEvent {
        val context = DataContext { dataId ->
            when {
                CommonDataKeys.PROJECT.`is`(dataId) -> project
                CommonDataKeys.VIRTUAL_FILE.`is`(dataId) -> file
                else -> null
            }
        }
        val action = action("DreamShader.BridgeTools.DscCheck")
        return AnActionEvent.createEvent(
            action,
            context,
            action.templatePresentation.clone(),
            ActionPlaces.UNKNOWN,
            ActionUiKind.NONE,
            null
        )
    }

    private fun action(id: String): AnAction =
        ActionManager.getInstance().getAction(id) ?: error("action not registered: $id")

    fun testActionsAreRegisteredAndDisabledWithoutDscPath() {
        configureDsc("")
        val ids = listOf(
            "DreamShader.BridgeTools.DscCheck",
            "DreamShader.BridgeTools.DscFormat",
            "DreamShader.BridgeTools.DscMigrate",
            "DreamShader.BridgeTools.DscIndex"
        )
        ids.forEach { id ->
            val event = eventFor(null)
            action(id).update(event)
            assertFalse(
                "expected $id disabled without a configured dsc path",
                event.presentation.isEnabled
            )
            assertTrue(
                "expected a configure hint in the disabled description of $id",
                event.presentation.description.orEmpty().contains("Settings")
            )
        }
    }

    fun testCheckActionRunsDscWithFileArgumentAndNotifies() {
        configureDsc("C:/Tools/DreamShader/dsc.exe")
        hookRunner(DreamShaderDscResult(0, "no diagnostics"))
        val file = newSourceFile("DscCheck.dsm", "Shader Main {\n    Graph {\n        float x = 1.0;\n    }\n}\n")

        val event = eventFor(file)
        action("DreamShader.BridgeTools.DscCheck").update(event)
        assertTrue("expected the action enabled with a configured path", event.presentation.isEnabled)
        action("DreamShader.BridgeTools.DscCheck").actionPerformed(event)

        assertEquals(1, capturedCommands.size)
        assertEquals(
            listOf("C:/Tools/DreamShader/dsc.exe", "check", file.path.replace('\\', '/')),
            capturedCommands[0].command
        )
        assertEquals(listOf(NotificationType.INFORMATION), capturedNotifications.map { it.type })
    }

    fun testCheckActionFailureNotifiesWithError() {
        configureDsc("dsc")
        hookRunner(DreamShaderDscResult(1, "error DSH4301: Unknown Graph identifier"))
        val file = newSourceFile("DscCheckFail.dsm", "Shader Main {\n    Graph {\n    }\n}\n")

        action("DreamShader.BridgeTools.DscCheck").actionPerformed(eventFor(file))

        assertEquals(listOf(NotificationType.ERROR), capturedNotifications.map { it.type })
        assertTrue(capturedNotifications[0].content.contains("DSH4301"))
    }

    fun testPowerShellWrapperCommandShape() {
        val command = DreamShaderDscCommandRunner.commandFor(
            "C:/Proj/dsc.ps1",
            listOf("migrate", "M_Foo.dsm", "-Check")
        )
        assertEquals(
            listOf(
                "powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", "C:/Proj/dsc.ps1",
                "migrate", "M_Foo.dsm", "-Check"
            ),
            command
        )
    }

    fun testMigrateRunsCheckFirstThenWritesAfterConfirmation() {
        configureDsc("dsc")
        hookRunner(DreamShaderDscResult(0, "ok"))
        DreamShaderDscActionTestHooks.migrateConfirmer = { _, _ -> true }
        val file = newSourceFile("DscMigrate.dsm", "Shader Main {\n    Graph {\n        float x = 1.0;\n    }\n}\n")

        action("DreamShader.BridgeTools.DscMigrate").actionPerformed(eventFor(file))

        assertEquals("expected -Check preview followed by the real migration", 2, capturedCommands.size)
        assertEquals(listOf("dsc", "migrate", file.path.replace('\\', '/'), "-Check"), capturedCommands[0].command)
        assertEquals(listOf("dsc", "migrate", file.path.replace('\\', '/')), capturedCommands[1].command)
        assertTrue(capturedNotifications.any { it.type == NotificationType.INFORMATION })
    }

    fun testMigrateAbortsWhenCheckFails() {
        configureDsc("dsc")
        hookRunner(DreamShaderDscResult(1, "DSH9090: refused"))
        DreamShaderDscActionTestHooks.migrateConfirmer = { _, _ -> true }
        val file = newSourceFile("DscMigrateFail.dsm", "Shader Main {\n    Graph {\n    }\n}\n")

        action("DreamShader.BridgeTools.DscMigrate").actionPerformed(eventFor(file))

        assertEquals("the real migration must not run after a failed preview", 1, capturedCommands.size)
        assertTrue(capturedNotifications.any { it.type == NotificationType.ERROR })
    }

    fun testMigrateAbortsWhenUserDeclines() {
        configureDsc("dsc")
        hookRunner(DreamShaderDscResult(0, "ok"))
        DreamShaderDscActionTestHooks.migrateConfirmer = { _, _ -> false }
        val file = newSourceFile("DscMigrateDeclined.dsm", "Shader Main {\n    Graph {\n    }\n}\n")

        action("DreamShader.BridgeTools.DscMigrate").actionPerformed(eventFor(file))

        assertEquals(1, capturedCommands.size)
        assertTrue(
            "expected a cancellation notification, got: $capturedNotifications",
            capturedNotifications.any { it.type == NotificationType.INFORMATION && it.content.contains("cancelled") }
        )
    }

    fun testMigrateRefusesV2Files() {
        configureDsc("dsc")
        hookRunner(DreamShaderDscResult(0, "ok"))
        val file = newSourceFile("DscMigrateV2.dss", "#pragma material(Domain = Surface)\n")

        action("DreamShader.BridgeTools.DscMigrate").actionPerformed(eventFor(file))

        assertEquals("a .dss file is already 2.0; migrate must refuse it", 0, capturedCommands.size)
        assertTrue(capturedNotifications.any { it.type == NotificationType.WARNING })
    }

    fun testFormatAndIndexActionArgumentShapes() {
        configureDsc("dsc")
        hookRunner(DreamShaderDscResult(0, ""))
        val file = newSourceFile("DscFormat.dsm", "Shader Main {\n    Graph {\n    }\n}\n")

        action("DreamShader.BridgeTools.DscFormat").actionPerformed(eventFor(file))
        action("DreamShader.BridgeTools.DscIndex").actionPerformed(eventFor(file))

        assertEquals(2, capturedCommands.size)
        assertEquals(listOf("dsc", "fmt", file.path.replace('\\', '/')), capturedCommands[0].command)
        assertEquals(listOf("dsc", "index"), capturedCommands[1].command)
    }
}
