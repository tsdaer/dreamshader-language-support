package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.icons.AllIcons
import javax.swing.Icon

/**
 * 「Reveal Node in Unreal Material Editor」编辑器动作（A3.4）。
 *
 * `.dss`/`.dsi` 源文件 → 光标所在行 → `reveal-node` 请求（写 `Requests/`，临时文件 + 原子
 * rename）。写入前先检查 `status.json` 存活（上游规则：无编辑器监听时请求会被丢弃），
 * 响应轮询在后台线程完成，成功后在行首装订 gutter 标记并通知资产路径与表达式 GUID。
 */
class DreamShaderRevealNodeAction : DumbAwareAction() {

    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.BridgeTools.RevealNode.text")
        templatePresentation.description =
            DreamShaderBundle.message("action.DreamShader.BridgeTools.RevealNode.description")
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val editor = e.getData(CommonDataKeys.EDITOR)
        val enabled = project != null &&
            file != null &&
            file.extension?.lowercase() in REVEAL_EXTENSIONS &&
            editor != null
        e.presentation.isEnabled = enabled
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        val editor = e.getData(CommonDataKeys.EDITOR) ?: return
        val line = editor.caretModel.logicalPosition.line + 1

        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, file)
        val liveness = DreamShaderBridgeStatus.resolveLiveness(bridgeDir)
        if (!liveness.alive) {
            notify(
                project,
                NotificationType.WARNING,
                DreamShaderBundle.message("bridge.notification.revealNode.editorOffline")
            )
            return
        }

        val requestId = "reveal-${System.currentTimeMillis()}-${RandomBridgeIds.next()}"
        val target = DreamShaderRevealNodeRequestWriter().writeRevealNodeRequest(
            project = project,
            sourceFile = file.path,
            line = line,
            requestId = requestId
        )
        if (target == null) {
            notify(
                project,
                NotificationType.ERROR,
                DreamShaderBundle.message("bridge.notification.revealNode.requestFailed")
            )
            return
        }

        ApplicationManager.getApplication().executeOnPooledThread {
            val response = DreamShaderRevealNodeResponseReader().poll(project, requestId)
            ApplicationManager.getApplication().invokeLater {
                handleResponse(project, editor, line, response)
            }
        }
    }

    private fun handleResponse(
        project: Project,
        editor: Editor,
        line: Int,
        response: DreamShaderRevealNodeResponseDto?
    ) {
        if (editor.isDisposed) return
        when {
            response == null -> notify(
                project,
                NotificationType.ERROR,
                DreamShaderBundle.message("bridge.notification.revealNode.timedOut")
            )
            !response.ok -> {
                val diagnostic = response.diagnostics
                    .firstOrNull { it.stage.equals("navigate", ignoreCase = true) }
                val notificationContent = DreamShaderBundle.message(
                    "bridge.notification.revealNode.failed",
                    response.navigateFailureCode ?: "-",
                    response.message ?: DreamShaderBundle.message("common.unknownError")
                )
                // C7: a navigate failure that carries a source location offers "Open at line".
                if (diagnostic != null && !diagnostic.file.isNullOrBlank() && diagnostic.line >= 1) {
                    notifyWithOpenAtLine(project, NotificationType.ERROR, notificationContent, diagnostic)
                } else {
                    notify(project, NotificationType.ERROR, notificationContent)
                }
            }
            else -> {
                DreamShaderRevealNodeMarker.mark(editor, line, response)
                // C7: the reverse direction lands the caret on the revealed source line.
                val document = editor.document
                if (line >= 1 && line <= document.lineCount) {
                    editor.caretModel.moveToOffset(document.getLineStartOffset(line - 1))
                    editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
                }
                notify(
                    project,
                    NotificationType.INFORMATION,
                    DreamShaderBundle.message(
                        "bridge.notification.revealNode.success",
                        response.assetPath ?: "-",
                        response.expressions.size
                    )
                )
            }
        }
    }

    /** The failure notification plus an "Open at line" jump to the diagnostic's source row. */
    private fun notifyWithOpenAtLine(
        project: Project,
        type: NotificationType,
        content: String,
        diagnostic: DreamShaderRevealNodeDiagnosticDto,
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("DreamShader Notifications")
            .createNotification(DreamShaderBundle.message("bridge.title"), content, type)
        val actionText = DreamShaderBundle.message("bridge.notification.revealNode.openAtLine")
        notification.addAction(object : com.intellij.openapi.project.DumbAwareAction(actionText) {
            override fun actionPerformed(e: com.intellij.openapi.actionSystem.AnActionEvent) {
                DreamShaderSourceSpanNavigator.openAtLine(
                    project,
                    diagnostic.file.orEmpty(),
                    diagnostic.line,
                    diagnostic.column,
                )
            }
        })
        notification.notify(project)
    }

    private fun notify(project: Project, type: NotificationType, content: String) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("DreamShader Notifications")
            .createNotification(DreamShaderBundle.message("bridge.title"), content, type)
            .notify(project)
    }

    companion object {
        /** reveal-node 只对 2.0 前端文件有意义（上游按 `.dss` 源行 → 节点 span 反查）。 */
        internal val REVEAL_EXTENSIONS = setOf("dss", "dsi")
    }
}

/** 请求 id 用的短随机数源（独立 object 便于测试替换）。 */
internal object RandomBridgeIds {
    fun next(): Int = (100000..999999).random()
}

/**
 * reveal 成功行的 gutter 标记：在行区间挂 [RangeHighlighter] 并写入 [DreamShaderRevealNodeGutterMark]。
 * 会话级（编辑器关闭随之失效），不参与诊断。
 */
internal object DreamShaderRevealNodeMarker {

    fun mark(editor: Editor, line: Int, response: DreamShaderRevealNodeResponseDto): RangeHighlighter? {
        val document = editor.document
        if (line < 1 || line > document.lineCount) return null
        val lineStart = document.getLineStartOffset(line - 1)
        val lineEnd = document.getLineEndOffset(line - 1)
        return editor.markupModel.addRangeHighlighter(
            null,
            lineStart,
            lineEnd,
            HighlighterLayer.LAST,
            HighlighterTargetArea.EXACT_RANGE
        ).apply {
            gutterIconRenderer = DreamShaderRevealNodeGutterMark(
                assetPath = response.assetPath.orEmpty(),
                instanceAssetPath = response.instanceAssetPath,
                expressionGuids = response.expressions
            )
        }
    }
}

/** 行首 gutter 图标渲染器：提示该行 reveal 到的资产与首个选中表达式。 */
internal class DreamShaderRevealNodeGutterMark(
    private val assetPath: String,
    private val instanceAssetPath: String?,
    private val expressionGuids: List<String>
) : GutterIconRenderer() {

    override fun getIcon(): Icon = AllIcons.Actions.Preview

    override fun getTooltipText(): String? = buildString {
        append("DreamShader: revealed ")
        append(assetPath.ifBlank { "-" })
        if (!instanceAssetPath.isNullOrBlank()) {
            append(" (instance: ")
            append(instanceAssetPath)
            append(")")
        }
        expressionGuids.firstOrNull()?.let { append(" | node: ").append(it) }
    }

    override fun equals(other: Any?): Boolean =
        other is DreamShaderRevealNodeGutterMark &&
            other.assetPath == assetPath &&
            other.instanceAssetPath == instanceAssetPath &&
            other.expressionGuids == expressionGuids

    override fun hashCode(): Int {
        var result = assetPath.hashCode()
        result = 31 * result + instanceAssetPath.hashCode()
        result = 31 * result + expressionGuids.hashCode()
        return result
    }
}
