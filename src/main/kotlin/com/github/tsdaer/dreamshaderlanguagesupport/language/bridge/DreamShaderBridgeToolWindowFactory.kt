package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.ListSelectionModel

private const val DREAMSHADER_BRIDGE_NOTIFICATIONS = "DreamShader Notifications"

internal class DreamShaderBridgeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DreamShaderBridgeDiagnosticsPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }
}

private class DreamShaderBridgeDiagnosticsPanel(
    private val project: Project
) : JPanel(BorderLayout(8, 8)) {
    private val model = DefaultListModel<DreamShaderBridgeDiagnostic>()
    private val list = JBList(model)
    private val status = JLabel(DreamShaderBundle.message("bridge.toolwindow.ready"))
    private val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)

    init {
        val toolbar = JPanel()
        val refreshButton = JButton(DreamShaderBundle.message("bridge.toolwindow.button.refresh"))
        val openSelectedButton = JButton(DreamShaderBundle.message("bridge.toolwindow.button.openSelected"))
        val openFirstButton = JButton(DreamShaderBundle.message("bridge.toolwindow.button.openFirst"))
        refreshButton.addActionListener { refresh() }
        openSelectedButton.addActionListener { openSelected() }
        openFirstButton.addActionListener { openFirst() }
        toolbar.add(refreshButton)
        toolbar.add(openSelectedButton)
        toolbar.add(openFirstButton)

        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.cellRenderer = javax.swing.ListCellRenderer { _, value, _, isSelected, _ ->
            val label = JLabel(
                "[${value.severity.uppercase()}] ${value.sourcePath}:${value.line}:${value.column} - ${value.message}"
            )
            if (isSelected) {
                label.background = list.selectionBackground
                label.foreground = list.selectionForeground
                label.isOpaque = true
            }
            label
        }
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && e.clickCount == 2) {
                    openSelected()
                }
            }
        })

        add(toolbar, BorderLayout.NORTH)
        add(JBScrollPane(list), BorderLayout.CENTER)
        add(status, BorderLayout.SOUTH)

        refresh()
    }

    private fun refresh() {
        val snapshot = repository.refresh(activeDreamShaderFile(project))
        model.clear()
        snapshot.diagnostics.forEach { model.addElement(it) }
        if (model.size > 0) {
            list.selectedIndex = 0
        }
        status.text = DreamShaderBundle.message(
            "bridge.toolwindow.loaded",
            snapshot.diagnostics.size,
            snapshot.loadedFromPath ?: DreamShaderBundle.message("bridge.toolwindow.unresolvedPath")
        )
    }

    private fun openFirst() {
        if (model.isEmpty) {
            notifyInfo(
                DreamShaderBundle.message("bridge.title"),
                DreamShaderBundle.message("bridge.toolwindow.noDiagnostics")
            )
            return
        }
        list.selectedIndex = 0
        openSelected()
    }

    private fun openSelected() {
        val selected = list.selectedValue
        if (selected == null) {
            notifyInfo(
                DreamShaderBundle.message("bridge.title"),
                DreamShaderBundle.message("bridge.toolwindow.selectDiagnostic")
            )
            return
        }
        val sourcePath = selected.sourcePath.replace('\\', '/')
        val sourceFile = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        if (sourceFile == null || !sourceFile.isValid || sourceFile.isDirectory) {
            notifyError(
                DreamShaderBundle.message("bridge.title"),
                DreamShaderBundle.message("bridge.toolwindow.openSourceFailed", sourcePath)
            )
            return
        }

        OpenFileDescriptor(
            project,
            sourceFile,
            (selected.line - 1).coerceAtLeast(0),
            (selected.column - 1).coerceAtLeast(0)
        ).navigate(true)
    }

    private fun activeDreamShaderFile(project: Project): com.intellij.openapi.vfs.VirtualFile? {
        val selected = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selected != null && selected.extension?.lowercase() in setOf("dsm", "dsf", "dsh")) return selected

        val editor = com.intellij.openapi.editor.EditorFactory.getInstance().allEditors.firstOrNull { it.project == project }
            ?: return null
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        val vf = psiFile.virtualFile ?: return null
        return if (vf.extension?.lowercase() in setOf("dsm", "dsf", "dsh")) vf else null
    }

    private fun notifyInfo(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(DREAMSHADER_BRIDGE_NOTIFICATIONS)
            .createNotification(title, content, NotificationType.INFORMATION)
            .notify(project)
    }

    private fun notifyError(title: String, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup(DREAMSHADER_BRIDGE_NOTIFICATIONS)
            .createNotification(title, content, NotificationType.ERROR)
            .notify(project)
    }
}
