package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageNotifier
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.github.tsdaer.dreamshaderlanguagesupport.language.ui.DreamShaderUi
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.jcef.JBCefApp
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.util.Alarm
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.io.File
import java.nio.file.Path
import java.util.*
import javax.swing.*

internal class DreamShaderMaterialPreviewPanel(
    private val project: Project
) : JPanel(BorderLayout(8, 8)), Disposable {
    private val requestWriter = DreamShaderPreviewRequestWriter()
    private val resultReader = DreamShaderPreviewResultReader()
    private val refreshAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)
    private val fileLabel = JLabel(DreamShaderBundle.message("preview.panel.noFile"))
    private val statusLabel = JLabel(DreamShaderBundle.message("preview.panel.ready"))
    private val meshCombo = com.intellij.openapi.ui.ComboBox(arrayOf("sphere", "plane", "cube"))
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
        DreamShaderUi.installSurface(this)

        val refreshButton = JButton(DreamShaderBundle.message("preview.panel.refresh")).apply {
            addActionListener { requestPreview(force = true) }
        }
        meshCombo.toolTipText = DreamShaderBundle.message("preview.panel.mesh.tooltip")
        meshCombo.addActionListener { requestPreview(force = true) }

        fileLabel.foreground = UIUtil.getContextHelpForeground()
        statusLabel.foreground = UIUtil.getContextHelpForeground()

        val toolbar = DreamShaderUi.card(BorderLayout(JBUI.scale(10), 0)).apply {
            border = JBUI.Borders.empty(10, 12)
            val identity = JPanel(BorderLayout()).apply {
                isOpaque = false
                add(DreamShaderUi.titleLabel(DreamShaderBundle.message("preview.title")), BorderLayout.NORTH)
                add(fileLabel, BorderLayout.SOUTH)
            }
            val controls = JPanel(FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0)).apply {
                isOpaque = false
                add(JLabel(DreamShaderBundle.message("preview.panel.mesh.label")))
                add(meshCombo)
                add(refreshButton)
            }
            add(identity, BorderLayout.CENTER)
            add(controls, BorderLayout.EAST)
        }
        add(toolbar, BorderLayout.NORTH)

        val stage = DreamShaderUi.card(BorderLayout()).apply {
            background = DreamShaderUi.stageBackground
            border = BorderFactory.createCompoundBorder(
                DreamShaderUi.RoundedBorder(DreamShaderUi.borderColor, JBUI.scale(14), JBUI.insets(1)),
                JBUI.Borders.empty(10)
            )
        }
        if (JBCefApp.isSupported()) {
            val cef = JBCefBrowser()
            browser = cef
            stage.add(cef.component, BorderLayout.CENTER)
        } else {
            swingImageLabel.verticalTextPosition = SwingConstants.BOTTOM
            swingImageLabel.horizontalTextPosition = SwingConstants.CENTER
            swingImageLabel.foreground = UIUtil.getContextHelpForeground()
            stage.add(JBScrollPane(swingImageLabel).apply {
                border = JBUI.Borders.empty()
            }, BorderLayout.CENTER)
        }
        add(stage, BorderLayout.CENTER)

        val statusBar = JPanel(BorderLayout()).apply {
            isOpaque = false
            border = JBUI.Borders.emptyTop(2)
            add(DreamShaderUi.pill(DreamShaderBundle.message("preview.panel.ready"), DreamShaderUi.Tone.ACCENT), BorderLayout.WEST)
            add(statusLabel, BorderLayout.CENTER)
        }
        add(statusBar, BorderLayout.SOUTH)
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
                :root {
                  color-scheme: light dark;
                  --bg0: #edf2f7;
                  --bg1: #f9fbfd;
                  --panel: rgba(255, 255, 255, .82);
                  --border: rgba(99, 115, 129, .22);
                  --shadow: rgba(25, 35, 45, .16);
                }
                @media (prefers-color-scheme: dark) {
                  :root {
                    --bg0: #141820;
                    --bg1: #222832;
                    --panel: rgba(32, 37, 47, .88);
                    --border: rgba(170, 184, 205, .16);
                    --shadow: rgba(0, 0, 0, .42);
                  }
                }
                html, body {
                  margin: 0;
                  height: 100%;
                  color: #d8dee9;
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background:
                    radial-gradient(circle at 20% 18%, rgba(86, 154, 255, .16), transparent 32%),
                    radial-gradient(circle at 82% 78%, rgba(89, 199, 163, .10), transparent 34%),
                    linear-gradient(135deg, var(--bg0), var(--bg1));
                }
                body {
                  box-sizing: border-box;
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  padding: 22px;
                }
                .frame {
                  box-sizing: border-box;
                  width: min(100%, 980px);
                  height: min(100%, 980px);
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  padding: 18px;
                  border: 1px solid var(--border);
                  border-radius: 20px;
                  background: var(--panel);
                  box-shadow: 0 18px 50px var(--shadow);
                  backdrop-filter: blur(8px);
                }
                img {
                  max-width: 100%;
                  max-height: 100%;
                  object-fit: contain;
                  image-rendering: auto;
                  border-radius: 14px;
                }
              </style>
            </head>
            <body><div class="frame"><img src="$uri?ts=$cacheBust" /></div></body>
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
                :root {
                  color-scheme: light dark;
                  --bg0: #eef3f8;
                  --bg1: #f9fbfd;
                  --fg: #354052;
                  --muted: #6b7788;
                  --panel: rgba(255, 255, 255, .82);
                  --border: rgba(99, 115, 129, .22);
                }
                @media (prefers-color-scheme: dark) {
                  :root {
                    --bg0: #141820;
                    --bg1: #222832;
                    --fg: #d8dee9;
                    --muted: #aab4c2;
                    --panel: rgba(32, 37, 47, .88);
                    --border: rgba(170, 184, 205, .16);
                  }
                }
                html, body {
                  margin: 0;
                  height: 100%;
                  color: var(--fg);
                  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
                  background:
                    radial-gradient(circle at 24% 20%, rgba(86, 154, 255, .14), transparent 34%),
                    linear-gradient(135deg, var(--bg0), var(--bg1));
                }
                body {
                  display: flex;
                  align-items: center;
                  justify-content: center;
                  padding: 24px;
                  box-sizing: border-box;
                  text-align: center;
                }
                .empty {
                  max-width: 520px;
                  padding: 28px 30px;
                  border: 1px solid var(--border);
                  border-radius: 18px;
                  background: var(--panel);
                }
                .eyebrow {
                  margin-bottom: 10px;
                  color: var(--muted);
                  font-size: 12px;
                  font-weight: 700;
                  letter-spacing: .08em;
                  text-transform: uppercase;
                }
                .message {
                  font-size: 15px;
                  line-height: 1.55;
                }
              </style>
            </head>
            <body><div class="empty"><div class="eyebrow">DreamShader Preview</div><div class="message">$escaped</div></div></body>
            </html>
        """.trimIndent()
    }

    companion object {
        private fun isDsmFile(file: VirtualFile): Boolean = file.extension?.lowercase() == "dsm"
    }
}
