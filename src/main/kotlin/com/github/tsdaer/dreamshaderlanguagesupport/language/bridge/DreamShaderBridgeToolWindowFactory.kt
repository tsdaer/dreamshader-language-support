package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.intellij.icons.AllIcons
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
import com.intellij.ui.*
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.FlowLayout
import javax.swing.*
import javax.swing.table.AbstractTableModel

private const val DREAMSHADER_BRIDGE_NOTIFICATIONS = "DreamShader Notifications"

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

private class DreamShaderBridgeDiagnosticsPanel(
    private val project: Project
) : JPanel(BorderLayout()) {
    private val model = BridgeDiagnosticsTableModel()
    private val table = JBTable(model)
    private val summary = JLabel(" ").apply { foreground = UIUtil.getContextHelpForeground() }
    private val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)

    init {
        val toolbar = JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 6)
            add(summary, BorderLayout.CENTER)
            val buttons = JPanel(FlowLayout(FlowLayout.RIGHT, 4, 0)).apply {
                isOpaque = false
                add(makeButton(DreamShaderBundle.message("bridge.toolwindow.button.refresh"), AllIcons.Actions.Refresh) { refresh() })
                add(makeButton(DreamShaderBundle.message("bridge.toolwindow.button.openFirst"), AllIcons.Actions.Forward) { openFirst() })
            }
            add(buttons, BorderLayout.EAST)
        }

        table.apply {
            setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
            rowHeight = JBUI.scale(22)
            columnModel.getColumn(0).apply { maxWidth = JBUI.scale(26); minWidth = JBUI.scale(26); preferredWidth = JBUI.scale(26) }
            columnModel.getColumn(1).apply { preferredWidth = JBUI.scale(400) }
            columnModel.getColumn(2).apply { preferredWidth = JBUI.scale(260) }
            columnModel.getColumn(3).apply { maxWidth = JBUI.scale(100); minWidth = JBUI.scale(100); preferredWidth = JBUI.scale(100) }
            setDefaultRenderer(Any::class.java, DiagnosticTableCellRenderer())
            emptyText.text = DreamShaderBundle.message("bridge.toolwindow.noDiagnostics")
            addMouseListener(object : java.awt.event.MouseAdapter() {
                override fun mouseClicked(e: java.awt.event.MouseEvent) {
                    if (e.clickCount == 2) openSelected()
                }
            })
        }

        val scrollPane = JBScrollPane(table).apply {
            border = JBUI.Borders.empty()
        }

        add(toolbar, BorderLayout.NORTH)
        add(scrollPane, BorderLayout.CENTER)
        refresh()
    }

    private fun makeButton(tooltip: String, icon: Icon, action: () -> Unit): JButton {
        return JButton(icon).apply {
            this.toolTipText = tooltip
            putClientProperty("ActionToolbar.isActionToolbarButton", true)
            border = JBUI.Borders.empty(4)
            addActionListener { action() }
        }
    }

    private fun refresh() {
        val snapshot = repository.refresh(activeDreamShaderFile(project))
        model.setDiagnostics(snapshot.diagnostics)
        val errors = snapshot.diagnostics.count { it.severity.equals("error", ignoreCase = true) }
        val warnings = snapshot.diagnostics.count { it.severity.equals("warning", ignoreCase = true) }
        val other = snapshot.diagnostics.size - errors - warnings
        val dbTag = when {
            snapshot.loadedFromPath == null -> "no source"
            snapshot.loadedFromPath.endsWith("bridge.db") -> "bridge.db"
            else -> "diagnostics.json"
        }
        summary.text = "${snapshot.diagnostics.size} diagnostics — $errors errors, $warnings warnings" +
            if (other > 0) ", $other info" else "" +
            "  |  $dbTag"
    }

    private fun openFirst() {
        if (model.rowCount == 0) {
            notifyInfo(DreamShaderBundle.message("bridge.toolwindow.noDiagnostics"))
            return
        }
        table.setRowSelectionInterval(0, 0)
        openSelected()
    }

    private fun openSelected() {
        val row = table.selectedRow
        if (row < 0) {
            notifyInfo(DreamShaderBundle.message("bridge.toolwindow.selectDiagnostic"))
            return
        }
        val diag = model.get(row)
        val sourcePath = diag.sourcePath.replace('\\', '/')
        val sourceFile = LocalFileSystem.getInstance().findFileByPath(sourcePath)
        if (sourceFile == null || !sourceFile.isValid || sourceFile.isDirectory) {
            notifyError(DreamShaderBundle.message("bridge.toolwindow.openSourceFailed", sourcePath))
            return
        }
        OpenFileDescriptor(project, sourceFile, (diag.line - 1).coerceAtLeast(0), (diag.column - 1).coerceAtLeast(0)).navigate(true)
    }

    private fun activeDreamShaderFile(project: Project): com.intellij.openapi.vfs.VirtualFile? {
        val selected = com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selected != null && selected.extension?.lowercase() in setOf("dsm", "dsf", "dsh")) return selected
        val editor = com.intellij.openapi.editor.EditorFactory.getInstance().allEditors.firstOrNull { it.project == project } ?: return null
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(editor.document) ?: return null
        val vf = psiFile.virtualFile ?: return null
        return if (vf.extension?.lowercase() in setOf("dsm", "dsf", "dsh")) vf else null
    }

    private fun notifyInfo(msg: String) {
        NotificationGroupManager.getInstance().getNotificationGroup(DREAMSHADER_BRIDGE_NOTIFICATIONS)
            .createNotification(DreamShaderBundle.message("bridge.title"), msg, NotificationType.INFORMATION).notify(project)
    }

    private fun notifyError(msg: String) {
        NotificationGroupManager.getInstance().getNotificationGroup(DREAMSHADER_BRIDGE_NOTIFICATIONS)
            .createNotification(DreamShaderBundle.message("bridge.title"), msg, NotificationType.ERROR).notify(project)
    }
}

private class BridgeDiagnosticsTableModel : AbstractTableModel() {
    private val diagnostics = mutableListOf<DreamShaderBridgeDiagnostic>()
    private val columns = arrayOf("", "Message", "File", "Location")

    fun setDiagnostics(list: List<DreamShaderBridgeDiagnostic>) {
        diagnostics.clear()
        diagnostics.addAll(list)
        fireTableDataChanged()
    }

    fun get(row: Int) = diagnostics[row]

    override fun getRowCount() = diagnostics.size
    override fun getColumnCount() = 4
    override fun getColumnName(column: Int) = columns[column]
    override fun getValueAt(row: Int, column: Int): Any = diagnostics[row]
}

private class DiagnosticTableCellRenderer : ColoredTableCellRenderer() {
    override fun customizeCellRenderer(
        table: JTable, value: Any?, selected: Boolean, hasFocus: Boolean, row: Int, column: Int
    ) {
        val diag = value as? DreamShaderBridgeDiagnostic ?: return
        when (column) {
            0 -> {
                icon = when (diag.severity.lowercase()) {
                    "error" -> AllIcons.General.Error
                    "warning" -> AllIcons.General.Warning
                    else -> AllIcons.General.Information
                }
            }
            1 -> {
                append(diag.message, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                if (!diag.severity.equals("error", ignoreCase = true)) {
                    append("  ${diag.severity.lowercase()}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            }
            2 -> {
                val name = diag.sourcePath.substringAfterLast('/')
                append(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
                val dir = diag.sourcePath.substringBeforeLast('/', diag.sourcePath)
                if (dir.isNotEmpty()) {
                    append("  $dir", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
                }
            }
            3 -> append("${diag.line}:${diag.column}", SimpleTextAttributes.REGULAR_ATTRIBUTES)
        }
        if (!isEnabled) {
            background = UIUtil.getTextFieldBackground()
        }
    }
}
