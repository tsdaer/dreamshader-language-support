package com.github.tsdaer.dreamshaderlanguagesupport.language.packages
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import java.awt.Desktop

private const val NOTIFICATION_GROUP_ID = "DreamShader Notifications"

internal object DreamShaderPackageNotifier {
    fun info(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    fun error(project: Project, title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(NOTIFICATION_GROUP_ID)
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }
}

class DreamShaderInstallPackageFromGitHubAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.PackageTools.InstallFromGitHub.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.PackageTools.InstallFromGitHub.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("packages.dialog.install.input"),
            DreamShaderBundle.message("packages.dialog.install.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = DreamShaderPackageManager(project).installFromGitHub(input)
        if (result.success) {
            DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.title"), result.message)
        } else {
            DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.title"), result.message)
        }
    }
}

class DreamShaderUpdateInstalledPackageAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.PackageTools.UpdateInstalled.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.PackageTools.UpdateInstalled.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("packages.dialog.update.input"),
            DreamShaderBundle.message("packages.dialog.update.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = DreamShaderPackageManager(project).updateInstalledPackage(input)
        if (result.success) {
            DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.title"), result.message)
        } else {
            DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.title"), result.message)
        }
    }
}

class DreamShaderRemoveInstalledPackageAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.PackageTools.RemoveInstalled.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.PackageTools.RemoveInstalled.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("packages.dialog.remove.input"),
            DreamShaderBundle.message("packages.dialog.remove.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = DreamShaderPackageManager(project).removeInstalledPackage(input)
        if (result.success) {
            DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.title"), result.message)
        } else {
            DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.title"), result.message)
        }
    }
}

class DreamShaderOpenPackagesFolderAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.PackageTools.OpenPackagesFolder.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.PackageTools.OpenPackagesFolder.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val folder = DreamShaderPackageManager(project).openPackagesFolder()

        runCatching {
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(folder.toFile())
            }
        }

        DreamShaderPackageNotifier.info(
            project,
            DreamShaderBundle.message("packages.title"),
            DreamShaderBundle.message("packages.notification.folder", folder.toAbsolutePath().normalize())
        )
    }
}

class DreamShaderAddPackageIndexSourceAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.PackageTools.AddIndexSource.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.PackageTools.AddIndexSource.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("packages.dialog.addSource.input"),
            DreamShaderBundle.message("packages.dialog.addSource.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = project.getService(DreamShaderPackageStoreService::class.java).addIndexSource(input)
        if (result.changed) {
            DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.sources.title"), result.message)
        } else {
            DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.sources.title"), result.message)
        }
    }
}

class DreamShaderRemovePackageIndexSourceAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.PackageTools.RemoveIndexSource.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.PackageTools.RemoveIndexSource.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("packages.dialog.removeSource.input"),
            DreamShaderBundle.message("packages.dialog.removeSource.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = project.getService(DreamShaderPackageStoreService::class.java).removeIndexSource(input)
        if (result.changed) {
            DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("packages.sources.title"), result.message)
        } else {
            DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("packages.sources.title"), result.message)
        }
    }
}

class DreamShaderBrowsePackageStoreAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.PackageTools.BrowseStore.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.PackageTools.BrowseStore.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        DreamShaderPackageStoreDialog(project).show()
    }
}
