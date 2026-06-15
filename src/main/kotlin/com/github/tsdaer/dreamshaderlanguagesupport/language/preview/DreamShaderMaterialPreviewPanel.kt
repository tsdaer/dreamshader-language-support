package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageNotifier
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import java.awt.BorderLayout
import java.awt.Dimension
import java.io.File
import java.nio.file.Path
import java.util.UUID
import javax.swing.Box
import javax.swing.ImageIcon
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class DreamShaderMaterialPreviewPanel(
    private val project: Project
) : JPanel(BorderLayout(8, 8)), Disposable {
    private val requestWriter = DreamShaderPreviewRequestWriter()
    private val resultReader = DreamShaderPreviewResultReader()
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val fileLabel = JLabel(DreamShaderBundle.message("preview.panel.noFile"))
    private val statusLabel = JLabel(DreamShaderBundle.message("preview.panel.ready"))
    private val meshCombo = JComboBox(arrayOf("sphere", "plane", "cube"))
    private val swingImageLabel = JLabel(DreamShaderBundle.message("preview.panel.waiting"), SwingConstants.CENTER)
    private var browser: JBCefBrowser? = null
    private var sourceFile: VirtualFile? = null
    private var lastRequestId: String? = null

    init {
        project.getService(DreamShaderMaterialPreviewPanelService::class.java).panel = this
        buildUi()
        subscribeToEditor()
        subscribeToDocumentChanges()
        subscribeToPreviewEvents()
        setSourceFile(activeDsmFile(), request = false)
        refresh()
    }

    private fun buildUi() {
        val toolbar = JPanel()
        val refreshButton = JButton(DreamShaderBundle.message("preview.panel.refresh"))
        refreshButton.addActionListener { requestPreview(force = true) }
        meshCombo.toolTipText = DreamShaderBundle.message("preview.panel.mesh.tooltip")
        meshCombo.addActionListener { requestPreview(force = true) }
        toolbar.add(refreshButton)
        toolbar.add(JLabel(DreamShaderBundle.message("preview.panel.mesh.label")))
        toolbar.add(meshCombo)
        toolbar.add(Box.createHorizontalStrut(8))
        toolbar.add(fileLabel)
        add(toolbar, BorderLayout.NORTH)

        if (JBCefApp.isSupported()) {
            val cef = JBCefBrowser()
            browser = cef
            add(cef.component, BorderLayout.CENTER)
        } else {
            swingImageLabel.verticalTextPosition = SwingConstants.BOTTOM
            swingImageLabel.horizontalTextPosition = SwingConstants.CENTER
            add(JBScrollPane(swingImageLabel), BorderLayout.CENTER)
        }
        add(statusLabel, BorderLayout.SOUTH)
    }

    private fun subscribeToEditor() {
        val connection = project.messageBus.connect(this)
        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun selectionChanged(event: FileEditorManagerEvent) {
                setSourceFile(event.newFile?.takeIf { isDsmFile(it) } ?: activeDsmFile(), request = true)
            }
        })
    }

    private fun subscribeToDocumentChanges() {
        EditorFactory.getInstance().eventMulticaster.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val current = sourceFile ?: return
                val changed = FileDocumentManager.getInstance().getFile(event.document) ?: return
                if (changed.path != current.path) return
                schedulePreviewRequest()
            }
        }, this)
    }

    private fun subscribeToPreviewEvents() {
        project.messageBus.connect(this).subscribe(DreamShaderPreviewListener.TOPIC, object : DreamShaderPreviewListener {
            override fun previewBridgeChanged() {
                refresh()
            }
        })
    }

    fun showCurrentFileAndRequest() {
        setSourceFile(activeDsmFile(), request = true)
    }

    private fun setSourceFile(file: VirtualFile?, request: Boolean) {
        if (file != null && !isDsmFile(file)) return
        val changed = sourceFile?.path != file?.path
        sourceFile = file
        fileLabel.text = file?.name ?: DreamShaderBundle.message("preview.panel.noFile")
        if (file == null) {
            renderMessage(DreamShaderBundle.message("preview.panel.noDsm"))
            statusLabel.text = DreamShaderBundle.message("preview.panel.noDsm")
            return
        }
        if (request && changed) {
            requestPreview(force = true)
        } else {
            refresh()
        }
    }

    fun requestPreview(force: Boolean = false) {
        val file = sourceFile ?: activeDsmFile()?.also { sourceFile = it }
        if (file == null) {
            statusLabel.text = DreamShaderBundle.message("preview.panel.noDsm")
            renderMessage(DreamShaderBundle.message("preview.panel.noDsm"))
            return
        }
        if (!force) {
            schedulePreviewRequest()
            return
        }
        val requestId = UUID.randomUUID().toString()
        val target = requestWriter.writePreviewMaterialRequest(
            project = project,
            sourceFile = file.path,
            mesh = meshCombo.selectedItem?.toString().orEmpty(),
            requestId = requestId
        )
        if (target == null) {
            statusLabel.text = DreamShaderBundle.message("preview.panel.requestFailed")
            DreamShaderPackageNotifier.warn(
                project,
                DreamShaderBundle.message("preview.title"),
                DreamShaderBundle.message("preview.notification.requestFailed")
            )
            return
        }
        lastRequestId = requestId
        statusLabel.text = DreamShaderBundle.message("preview.panel.requestWritten", target.toString().replace('\\', '/'))
        renderMessage(DreamShaderBundle.message("preview.panel.pending"))
    }

    private fun schedulePreviewRequest() {
        val delay = project.getService(DreamShaderProjectSettings::class.java)
            .state
            .previewAutoRefreshDelayMs
            .coerceIn(250, 10000)
        refreshAlarm.cancelAllRequests()
        refreshAlarm.addRequest({ requestPreview(force = true) }, delay)
    }

    fun refresh() {
        val file = sourceFile ?: activeDsmFile()?.also { sourceFile = it }
        if (file == null) {
            renderMessage(DreamShaderBundle.message("preview.panel.noDsm"))
            return
        }
        val result = resultReader.readPreviewResult(project, file.path)
        if (result == null) {
            if (lastRequestId == null) {
                renderMessage(DreamShaderBundle.message("preview.panel.waiting"))
            }
            return
        }
        val status = result.status.orEmpty()
        val message = result.message.orEmpty()
        statusLabel.text = when {
            message.isNotBlank() -> DreamShaderBundle.message("preview.panel.statusWithMessage", status.ifBlank { "-" }, message)
            else -> DreamShaderBundle.message("preview.panel.status", status.ifBlank { "-" })
        }
        if (status.equals("ready", ignoreCase = true)) {
            val imagePath = result.imagePath?.takeIf { it.isNotBlank() }
            if (imagePath != null) {
                renderImage(Path.of(imagePath.replace('\\', '/')))
            }
        } else if (status.equals("error", ignoreCase = true)) {
            renderMessage(message.ifBlank { DreamShaderBundle.message("preview.panel.error") })
        }
    }

    private fun renderImage(path: Path) {
        val file = path.toFile()
        if (!file.exists() || !file.isFile) {
            renderMessage(DreamShaderBundle.message("preview.panel.imageMissing", path.toString().replace('\\', '/')))
            return
        }
        val cacheBust = System.currentTimeMillis()
        browser?.loadHTML(buildPreviewHtml(file, cacheBust)) ?: run {
            swingImageLabel.text = null
            swingImageLabel.icon = ImageIcon(file.path)
            swingImageLabel.preferredSize = Dimension(512, 512)
            swingImageLabel.revalidate()
            swingImageLabel.repaint()
        }
    }

    private fun renderMessage(message: String) {
        browser?.loadHTML(buildMessageHtml(message)) ?: run {
            swingImageLabel.icon = null
            swingImageLabel.text = message
        }
    }

    private fun activeDsmFile(): VirtualFile? {
        val selected = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selected != null && isDsmFile(selected)) return selected
        return null
    }

    override fun dispose() {
        if (project.getService(DreamShaderMaterialPreviewPanelService::class.java).panel === this) {
            project.getService(DreamShaderMaterialPreviewPanelService::class.java).panel = null
        }
        browser?.dispose()
        browser = null
    }

    private fun buildPreviewHtml(file: File, cacheBust: Long): String {
        val uri = file.toURI().toString()
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                html, body { margin: 0; height: 100%; background: #181a20; color: #d8dee9; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                body { display: flex; align-items: center; justify-content: center; }
                img { max-width: 100%; max-height: 100vh; object-fit: contain; image-rendering: auto; }
              </style>
            </head>
            <body><img src="$uri?ts=$cacheBust" /></body>
            </html>
        """.trimIndent()
    }

    private fun buildMessageHtml(message: String): String {
        val escaped = message
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
        return """
            <!doctype html>
            <html>
            <head>
              <meta charset="utf-8">
              <style>
                html, body { margin: 0; height: 100%; background: #181a20; color: #c9d1d9; font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
                body { display: flex; align-items: center; justify-content: center; padding: 24px; box-sizing: border-box; text-align: center; }
              </style>
            </head>
            <body>$escaped</body>
            </html>
        """.trimIndent()
    }

    companion object {
        private fun isDsmFile(file: VirtualFile): Boolean = file.extension?.lowercase() == "dsm"
    }
}
