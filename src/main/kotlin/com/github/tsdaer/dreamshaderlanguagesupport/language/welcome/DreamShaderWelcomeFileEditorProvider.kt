package com.github.tsdaer.dreamshaderlanguagesupport.language.welcome

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
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

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
        val jcefAvailable = try {
            Class.forName("com.intellij.ui.jcef.JBCefApp")
            true
        } catch (_: Exception) { false }

        if (jcefAvailable) {
            createJcefContent()
        } else {
            root.add(JLabel(com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle.message("welcome.fallback.jcefUnavailable")), BorderLayout.NORTH)
        }
    }

    private fun createJcefContent() {
        try {
            val cef = JBCefBrowser()
            val jsQuery = JBCefJSQuery.create(cef as JBCefBrowserBase)
            jsQuery.addHandler {
                ShowSettingsUtil.getInstance().showSettingsDialog(project, DreamShaderSettingsConfigurable::class.java)
                null
            }
            val injectedHtml = file.htmlContent.replace(
                "</body>",
                """<script>
                    window.openSettings = function() {
                        ${jsQuery.inject("open-settings")};
                    };
                    </script></body>"""
            )
            cef.loadHTML(injectedHtml)
            jsQuery.addHandler { null }
            root.add(cef.component, BorderLayout.CENTER)
        } catch (_: Exception) {
            root.add(JLabel(
                com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle.message("welcome.fallback.jcefUnavailable")
            ), BorderLayout.NORTH)
        }
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
