package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.ui.DreamShaderUi
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.PsiDocumentManager
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

private const val DREAMSHADER_BRIDGE_NOTIFICATIONS = "DreamShader Notifications"

/**
 * Bridge 诊断工具窗口工厂。
 */
internal class DreamShaderBridgeToolWindowFactory : ToolWindowFactory, DumbAware {
    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = DreamShaderBridgeDiagnosticsPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override suspend fun isApplicableAsync(project: Project): Boolean = true

    override fun shouldBeAvailable(project: Project): Boolean = true

    override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) = Unit
}

/**
 * Bridge 诊断面板。
 *
 * 提供刷新、打开选中项、打开首条诊断等基础交互。
 */
private class DreamShaderBridgeDiagnosticsPanel(
    private val project: Project
) : JPanel(BorderLayout(JBUI.scale(12), JBUI.scale(12))) {
    private val model = DefaultListModel<DreamShaderBridgeDiagnostic>()
    private val list = JBList(model)
    private val status = JLabel(DreamShaderBundle.message("bridge.toolwindow.ready"))
    private val summaryLabel = JLabel(DreamShaderBundle.message("bridge.toolwindow.ready"))
    private val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)

    init {
        DreamShaderUi.installSurface(this)

        val refreshButton = JButton(DreamShaderBundle.message("bridge.toolwindow.button.refresh")).apply {
            addActionListener { refresh() }
        }
        val openSelectedButton = JButton(DreamShaderBundle.message("bridge.toolwindow.button.openSelected")).apply {
            addActionListener { openSelected() }
        }
        val openFirstButton = JButton(DreamShaderBundle.message("bridge.toolwindow.button.openFirst")).apply {
            addActionListener { openFirst() }
        }

        val header = DreamShaderUi.card(BorderLayout(JBUI.scale(10), 0)).apply {
            border = JBUI.Borders.empty(12)
            val title = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(DreamShaderUi.titleLabel(DreamShaderBundle.message("bridge.title")), BorderLayout.NORTH)
                summaryLabel.foreground = DreamShaderUi.mutedForeground
                add(summaryLabel, BorderLayout.SOUTH)
            }
            add(title, BorderLayout.CENTER)
            add(
                DreamShaderUi.horizontal(8, java.awt.FlowLayout.RIGHT, refreshButton, openFirstButton, openSelectedButton),
                BorderLayout.EAST
            )
        }

        list.selectionMode = ListSelectionModel.SINGLE_SELECTION
        list.fixedCellHeight = JBUI.scale(88)
        list.border = JBUI.Borders.empty()
        list.background = DreamShaderUi.panelBackground
        list.emptyText.text = DreamShaderBundle.message("bridge.toolwindow.noDiagnostics")
        list.cellRenderer = DiagnosticRenderer()
        list.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.button == MouseEvent.BUTTON1 && e.clickCount == 2) {
                    openSelected()
                }
            }
        })

        add(header, BorderLayout.NORTH)
        add(DreamShaderUi.section(
            title = DreamShaderBundle.message("bridge.toolwindow.button.refresh"),
            description = DreamShaderBundle.message("bridge.widget.tooltip"),
            content = JBScrollPane(list).apply {
                border = JBUI.Borders.empty()
                viewport.background = DreamShaderUi.panelBackground
            }
        ), BorderLayout.CENTER)
        status.foreground = UIUtil.getContextHelpForeground()
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
        val errors = snapshot.diagnostics.count { it.severity.equals("error", ignoreCase = true) }
        val warnings = snapshot.diagnostics.count { it.severity.equals("warning", ignoreCase = true) }
        summaryLabel.text = DreamShaderBundle.message("bridge.widget.summary", errors, warnings)
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

    private class DiagnosticRenderer : ListCellRenderer<DreamShaderBridgeDiagnostic> {
        override fun getListCellRendererComponent(
            list: JList<out DreamShaderBridgeDiagnostic>,
            value: DreamShaderBridgeDiagnostic,
            index: Int,
            isSelected: Boolean,
            cellHasFocus: Boolean
        ): Component {
            val tone = when (value.severity.lowercase()) {
                "error" -> DreamShaderUi.Tone.DANGER
                "warning" -> DreamShaderUi.Tone.WARNING
                else -> DreamShaderUi.Tone.ACCENT
            }
            val panel = JPanel(BorderLayout(JBUI.scale(10), JBUI.scale(4))).apply {
                isOpaque = true
                background = if (isSelected) {
                    JBColor(Color(0xE8, 0xF2, 0xFF), Color(0x22, 0x35, 0x4D))
                } else {
                    DreamShaderUi.elevatedBackground
                }
                border = javax.swing.BorderFactory.createCompoundBorder(
                    JBUI.Borders.empty(4, 2),
                    DreamShaderUi.RoundedBorder(
                        if (isSelected) DreamShaderUi.accent else DreamShaderUi.borderColor,
                        JBUI.scale(12),
                        JBUI.insets(10, 12)
                    )
                )
            }
            val title = JLabel(value.message).apply {
                font = font.deriveFont(Font.BOLD)
                foreground = UIUtil.getLabelForeground()
            }
            val location = JLabel("${value.sourcePath}:${value.line}:${value.column}").apply {
                foreground = DreamShaderUi.mutedForeground
            }
            panel.add(title, BorderLayout.NORTH)
            panel.add(location, BorderLayout.CENTER)
            panel.add(DreamShaderUi.pill(value.severity.uppercase(), tone), BorderLayout.EAST)
            return panel
        }
    }
}
