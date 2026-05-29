package com.github.tsdaer.dreamshaderlanguagesupport.language.welcome

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import java.awt.BorderLayout
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

class DreamShaderWelcomeFileEditorProvider : FileEditorProvider {
    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file is DreamShaderWelcomeVirtualFile
    }

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
    private var browser: JBCefBrowser? = null

    init {
        if (JBCefApp.isSupported()) {
            val cef = JBCefBrowser()
            cef.loadHTML(file.htmlContent)
            browser = cef
            root.add(cef.component, BorderLayout.CENTER)
        } else {
            root.add(JPanel(BorderLayout()).apply {
                add(javax.swing.JLabel(com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle.message("welcome.fallback.jcefUnavailable")), BorderLayout.NORTH)
            }, BorderLayout.CENTER)
        }
    }

    override fun getComponent(): JComponent = root

    override fun getPreferredFocusedComponent(): JComponent = root

    override fun getName(): String = "DreamShader Welcome"

    override fun setState(state: FileEditorState) {
        // No mutable editor state.
    }

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = file.isValid && !project.isDisposed

    override fun selectNotify() {
    }

    override fun deselectNotify() {
    }

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
    }

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun dispose() {
        browser?.dispose()
        browser = null
    }
}
