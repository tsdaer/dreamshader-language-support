package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.icons.AllIcons
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.ClickListener
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.event.MouseEvent
import javax.swing.*

internal class DreamShaderBridgeStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID
    override fun getDisplayName(): String = DreamShaderBundle.message("statusbar.widget.dreamshader.bridge.displayName")

    override fun isAvailable(project: Project): Boolean {
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state ?: return true
        return settings.showStatusBar
    }

    override fun createWidget(project: Project): StatusBarWidget = DreamShaderBridgeStatusBarWidget(project)
    override fun disposeWidget(widget: StatusBarWidget) { widget.dispose() }
    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

private class DreamShaderBridgeStatusBarWidget(
    private val project: Project
) : CustomStatusBarWidget {
    private val label = JLabel()
    private var statusBar: StatusBar? = null
    private val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)

    init {
        label.border = JBUI.Borders.empty(0, 4)
        object : ClickListener() {
            override fun onClick(e: MouseEvent, clickCount: Int): Boolean {
                if (clickCount == 1) refreshAndRender()
                return true
            }
        }.installOn(label)
        refreshAndRender()
    }

    override fun ID(): String = WIDGET_ID
    override fun install(statusBar: StatusBar) { this.statusBar = statusBar; refreshAndRender() }
    override fun dispose() { statusBar = null }
    override fun getComponent(): JComponent = label

    private fun refreshAndRender() {
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        if (settings != null && !settings.showStatusBar) {
            label.icon = AllIcons.Actions.Cancel
            label.text = DreamShaderBundle.message("bridge.widget.hidden")
            label.toolTipText = null
            return
        }
        val activeFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val snapshot = repository.refresh(activeFile)
        val warnings = snapshot.diagnostics.count { it.severity == "warning" || it.severity == "warn" }
        val errors = snapshot.diagnostics.size - warnings
        val isDb = snapshot.loadedFromPath?.endsWith("bridge.db") == true

        label.icon = when {
            errors > 0 -> AllIcons.General.Error
            warnings > 0 -> AllIcons.General.Warning
            else -> AllIcons.General.Information
        }
        label.text = if (errors + warnings > 0) " $errors/$warnings" else ""
        label.toolTipText = buildString {
            append("DreamShader Bridge")
            if (errors + warnings > 0) append(" | $errors errors, $warnings warnings")
            append(" | ")
            append(if (isDb) "SQLite" else snapshot.loadedFromPath?.let { "JSON" } ?: "no data")
            append(" | click to refresh")
        }
        statusBar?.updateWidget(ID())
    }
}

internal const val WIDGET_ID = "DreamShader.Bridge.StatusWidget"
