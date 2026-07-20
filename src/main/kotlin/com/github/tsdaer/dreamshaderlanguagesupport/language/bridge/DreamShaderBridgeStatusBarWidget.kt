package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.ui.components.JBLabel
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent

/**
 * Bridge 状态栏组件工厂。
 *
 * 根据项目设置决定是否显示组件，并创建对应 widget 实例。
 */
internal class DreamShaderBridgeStatusBarWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = DreamShaderBundle.message("statusbar.widget.dreamshader.bridge.displayName")

    override fun isAvailable(project: Project): Boolean {
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state ?: return true
        return settings.showStatusBar
    }

    override fun createWidget(project: Project): StatusBarWidget {
        return DreamShaderBridgeStatusBarWidget(project)
    }

    override fun disposeWidget(widget: StatusBarWidget) {
        widget.dispose()
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

/**
 * Bridge 状态栏组件。
 *
 * 展示当前诊断错误/警告计数，点击后触发一次刷新。
 */
private class DreamShaderBridgeStatusBarWidget(
    private val project: Project
) : CustomStatusBarWidget {
    private val label = JBLabel()
    private var statusBar: StatusBar? = null
    private val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)

    init {
        label.toolTipText = DreamShaderBundle.message("bridge.widget.tooltip")
        label.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                refreshAndRender()
            }
        })
        refreshAndRender()
    }

    override fun ID(): String = WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        refreshAndRender()
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getComponent(): JComponent = label

    private fun refreshAndRender() {
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        if (settings != null && !settings.showStatusBar) {
            label.text = DreamShaderBundle.message("bridge.widget.hidden")
            return
        }
        val activeFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        val snapshot = repository.refresh(activeFile)
        val total = snapshot.diagnostics.size
        val warnings = snapshot.diagnostics.count { it.severity == "warning" || it.severity == "warn" }
        val errors = total - warnings
        val transportSuffix = if (snapshot.loadedFromPath?.endsWith("bridge.db") == true) {
            DreamShaderBundle.message("bridge.widget.transport.db")
        } else if (snapshot.loadedFromPath != null) {
            DreamShaderBundle.message("bridge.widget.transport.file")
        } else {
            ""
        }
        label.text = DreamShaderBundle.message("bridge.widget.summary", errors, warnings) + transportSuffix
        statusBar?.updateWidget(ID())
    }
}

internal const val WIDGET_ID = "DreamShader.Bridge.StatusWidget"
