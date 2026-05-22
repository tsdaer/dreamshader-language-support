package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderProjectSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Desktop
import java.io.File

private const val DREAMSHADER_NOTIFICATIONS = "DreamShader Notifications"

private object DreamShaderBridgeNotifier {
    fun info(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(DREAMSHADER_NOTIFICATIONS)
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    fun warning(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(DREAMSHADER_NOTIFICATIONS)
            .createNotification(title, content, NotificationType.WARNING)
            .notify(project)
    }

    fun error(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(DREAMSHADER_NOTIFICATIONS)
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }
}

private fun activeDreamShaderFile(project: Project): VirtualFile? {
    val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
    if (selected != null && selected.extension?.lowercase() in setOf("dsm", "dsf", "dsh")) return selected

    val editor = EditorFactory.getInstance().allEditors.firstOrNull { it.project == project } ?: return null
    val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
    val vf = psiFile.virtualFile ?: return null
    return if (vf.extension?.lowercase() in setOf("dsm", "dsf", "dsh")) vf else null
}

class DreamShaderRefreshBridgeDiagnosticsAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.BridgeTools.RefreshDiagnostics.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.BridgeTools.RefreshDiagnostics.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val activeFile = activeDreamShaderFile(project)
        val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)
        val snapshot = repository.refresh(activeFile)
        val path = snapshot.loadedFromPath ?: DreamShaderBundle.message("bridge.toolwindow.unresolvedPath")
        val title = DreamShaderBundle.message("bridge.title")
        if (snapshot.diagnostics.isEmpty()) {
            DreamShaderBridgeNotifier.info(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.refreshedEmpty", path)
            )
            return
        }
        DreamShaderBridgeNotifier.warning(
            project,
            title,
            DreamShaderBundle.message("bridge.notification.refreshedCount", path, snapshot.diagnostics.size)
        )
    }
}

class DreamShaderOpenBridgeDirectoryAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.BridgeTools.OpenBridgeDirectory.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.BridgeTools.OpenBridgeDirectory.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val title = DreamShaderBundle.message("bridge.title")
        val activeFile = activeDreamShaderFile(project)
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeFile)
        if (bridgeDir.isNullOrBlank()) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.resolveDirFailed")
            )
            return
        }
        val directory = File(bridgeDir)
        if (!directory.exists() || !directory.isDirectory) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.dirMissing", directory.path.replace('\\', '/'))
            )
            return
        }
        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory)
            }
        }.onFailure {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.openDirFailed", it.message ?: "unknown error")
            )
            return
        }
        DreamShaderBridgeNotifier.info(
            project,
            title,
            DreamShaderBundle.message("bridge.notification.openedDir", directory.path.replace('\\', '/'))
        )
    }
}

class DreamShaderOpenBridgeDiagnosticsFileAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.BridgeTools.OpenDiagnosticsFile.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.BridgeTools.OpenDiagnosticsFile.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val title = DreamShaderBundle.message("bridge.title")
        val activeFile = activeDreamShaderFile(project)
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeFile)
        if (bridgeDir.isNullOrBlank()) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.resolveDirFailed")
            )
            return
        }

        val diagnosticsPath = "${bridgeDir.trimEnd('/', '\\')}/diagnostics.json".replace('\\', '/')
        val vf = LocalFileSystem.getInstance().findFileByPath(diagnosticsPath)
        if (vf == null || !vf.isValid || vf.isDirectory) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.diagnosticsMissing", diagnosticsPath)
            )
            return
        }

        FileEditorManager.getInstance(project).openFile(vf, true)
        DreamShaderBridgeNotifier.info(
            project,
            title,
            DreamShaderBundle.message("bridge.notification.openedDiagnostics", diagnosticsPath)
        )
    }
}

class DreamShaderOpenFirstBridgeDiagnosticLocationAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.BridgeTools.OpenFirstDiagnosticLocation.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.BridgeTools.OpenFirstDiagnosticLocation.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val title = DreamShaderBundle.message("bridge.title")
        val activeFile = activeDreamShaderFile(project)
        val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)
        val snapshot = repository.refresh(activeFile)
        val first = snapshot.diagnostics.firstOrNull()
        if (first == null) {
            DreamShaderBridgeNotifier.info(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.noDiagnostics")
            )
            return
        }

        val sourcePath = first.sourcePath.replace('\\', '/')
        val sourceFile = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        if (sourceFile == null || !sourceFile.isValid || sourceFile.isDirectory) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.openSourceFailed", sourcePath)
            )
            return
        }

        OpenFileDescriptor(project, sourceFile, (first.line - 1).coerceAtLeast(0), (first.column - 1).coerceAtLeast(0))
            .navigate(true)
        DreamShaderBridgeNotifier.info(
            project,
            title,
            DreamShaderBundle.message(
                "bridge.notification.openedLocation",
                sourceFile.path.replace('\\', '/'),
                first.line,
                first.column
            )
        )
    }
}

class DreamShaderRecompileCurrentAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.BridgeTools.RecompileCurrent.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.BridgeTools.RecompileCurrent.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val title = DreamShaderBundle.message("bridge.title")
        val activeFile = activeDreamShaderFile(project)
        if (activeFile == null) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.noActiveFile")
            )
            return
        }
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        val template = settings.bridgeRecompileCurrentCommand.trim()
        if (template.isBlank()) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.commandCurrentEmpty")
            )
            return
        }
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeFile)
        val result = DreamShaderBridgeCommandExecutor.execute(
            project = project,
            commandTemplate = template,
            activeFilePath = activeFile.path.replace('\\', '/'),
            bridgeDirectoryPath = bridgeDir
        )
        if (result.success) {
            DreamShaderBridgeNotifier.info(project, title, result.message)
        } else {
            DreamShaderBridgeNotifier.error(project, title, result.message)
        }
    }
}

class DreamShaderRecompileAllAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.BridgeTools.RecompileAll.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.BridgeTools.RecompileAll.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val title = DreamShaderBundle.message("bridge.title")
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        val template = settings.bridgeRecompileAllCommand.trim()
        if (template.isBlank()) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.commandAllEmpty")
            )
            return
        }
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeDreamShaderFile(project))
        val result = DreamShaderBridgeCommandExecutor.execute(
            project = project,
            commandTemplate = template,
            activeFilePath = null,
            bridgeDirectoryPath = bridgeDir
        )
        if (result.success) {
            DreamShaderBridgeNotifier.info(project, title, result.message)
        } else {
            DreamShaderBridgeNotifier.error(project, title, result.message)
        }
    }
}

class DreamShaderCleanGeneratedShadersAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.BridgeTools.CleanGeneratedShaders.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.BridgeTools.CleanGeneratedShaders.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val title = DreamShaderBundle.message("bridge.title")
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        val template = settings.bridgeCleanGeneratedShadersCommand.trim()
        if (template.isBlank()) {
            DreamShaderBridgeNotifier.error(
                project,
                title,
                DreamShaderBundle.message("bridge.notification.commandCleanEmpty")
            )
            return
        }
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeDreamShaderFile(project))
        val result = DreamShaderBridgeCommandExecutor.execute(
            project = project,
            commandTemplate = template,
            activeFilePath = null,
            bridgeDirectoryPath = bridgeDir
        )
        if (result.success) {
            DreamShaderBridgeNotifier.info(project, title, result.message)
        } else {
            DreamShaderBridgeNotifier.error(project, title, result.message)
        }
    }
}
