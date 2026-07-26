package com.github.tsdaer.dreamshaderlanguagesupport.language.welcome

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderSettingsConfigurable
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Font
import java.beans.PropertyChangeListener
import java.net.URI
import javax.swing.*
import javax.swing.event.HyperlinkEvent
import javax.swing.text.html.HTMLEditorKit

class DreamShaderWelcomeFileEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean = file is DreamShaderWelcomeVirtualFile

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val welcomeFile = file as? DreamShaderWelcomeVirtualFile
            ?: error("Unexpected file type for DreamShaderWelcomeFileEditorProvider")
        return DreamShaderWelcomeFileEditor(project, welcomeFile)
    }

    override fun getEditorTypeId(): String = "dreamshader-welcome-editor"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

private class DreamShaderWelcomeFileEditor(
    private val project: Project,
    private val file: DreamShaderWelcomeVirtualFile
) : UserDataHolderBase(), FileEditor {
    private val root = JPanel(BorderLayout())

    init {
        val pane = JEditorPane().apply {
            editorKit = HTMLEditorKit()
            text = file.htmlContent
            isEditable = false
            isOpaque = true
            background = UIUtil.getPanelBackground()
            border = JBUI.Borders.empty(20, 24)
            putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true)
            addHyperlinkListener { e ->
                if (e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
                    val url = e.url?.toString() ?: return@addHyperlinkListener
                    if (url.startsWith("dreamshader://open-settings")) {
                        ShowSettingsUtil.getInstance().showSettingsDialog(project, DreamShaderSettingsConfigurable::class.java)
                    } else {
                        try { Desktop.getDesktop().browse(URI(url)) } catch (_: Exception) {}
                    }
                }
            }
        }
        root.add(JBScrollPane(pane).apply { border = JBUI.Borders.empty() }, BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent = root
    override fun getPreferredFocusedComponent(): JComponent? = null
    override fun getName(): String = "DreamShader Welcome"
    override fun setState(state: FileEditorState) = Unit
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun removePropertyChangeListener(listener: PropertyChangeListener) = Unit
    override fun dispose() = Unit
    override fun getCurrentLocation(): FileEditorLocation? = null
}
