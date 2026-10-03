package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDshCodeCatalog
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDshCodeEntry
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil

/**
 * Bridge 诊断注入 pass。
 *
 * 从项目级 Bridge 诊断仓库读取当前文件诊断，并以编辑器注解形式渲染。
 * 携带 DSH 码的诊断在 tooltip 中附带码义（A5），并挂「打开上游文档」意图。
 */
internal object DreamShaderBridgeDiagnosticsPass {
    fun annotate(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        val repository = file.project.getService(DreamShaderBridgeDiagnosticsRepository::class.java) ?: return
        repository.refresh(file.virtualFile)
        val diagnostics = file.virtualFile?.let { repository.diagnosticsForFile(it) }.orEmpty()
        diagnostics.forEach { diagnostic ->
            val lineIndex = (diagnostic.line - 1).coerceAtLeast(0)
            val columnIndex = (diagnostic.column - 1).coerceAtLeast(0)
            val lineStartOffset = lineStartOffset(file.text, lineIndex)
            val start = (lineStartOffset + columnIndex).coerceIn(0, file.textLength)
            val end = (start + 1).coerceAtMost(file.textLength)
            if (start >= end) return@forEach

            val severity = if (diagnostic.severity == "warning" || diagnostic.severity == "warn") {
                HighlightSeverity.WARNING
            } else {
                HighlightSeverity.ERROR
            }
            val code = diagnostic.code?.takeIf { it.isNotBlank() }
                ?: DreamShaderDshCodeCatalog.firstCodeIn(diagnostic.message)
            val entry = DreamShaderDshCodeCatalog.lookup(code)
            val displayMessage = code?.let { "[$it] ${diagnostic.message}" }
                ?: diagnostic.message
            val builder = holder.newAnnotation(severity, displayMessage)
                .range(TextRange(start, end))
                .tooltip(renderTooltip(diagnostic, code, entry))
            if (entry != null) {
                builder.withFix(DreamShaderOpenDshDocumentationIntention(entry))
            }
            builder.create()
        }
    }

    /** Tooltip 布局：原始消息、stage/detail/asset 上下文，最后是 DSH 码义（码表命中时）。 */
    internal fun renderTooltip(
        diagnostic: DreamShaderBridgeDiagnostic,
        code: String?,
        entry: DreamShaderDshCodeEntry?
    ): String = buildString {
        append(StringUtil.escapeXmlEntities(displayMessageOf(diagnostic, code)))
        diagnostic.stage?.takeIf { it.isNotBlank() }?.let {
            append("<br>Stage: ").append(StringUtil.escapeXmlEntities(it))
        }
        diagnostic.detail?.takeIf { it.isNotBlank() }?.let {
            append("<br>").append(StringUtil.escapeXmlEntities(it))
        }
        diagnostic.assetPath?.takeIf { it.isNotBlank() }?.let {
            append("<br>Asset: ").append(StringUtil.escapeXmlEntities(it))
        }
        if (entry != null) {
            append("<hr><b>").append(StringUtil.escapeXmlEntities(entry.code)).append("</b> (")
                .append(StringUtil.escapeXmlEntities(entry.severity)).append(")")
            entry.summaryZh?.let {
                append("<br>").append(StringUtil.escapeXmlEntities(it))
            }
            if (entry.message != diagnostic.message) {
                append("<br><small>Upstream: ").append(StringUtil.escapeXmlEntities(entry.message))
                    .append("</small>")
            }
        }
    }

    private fun displayMessageOf(diagnostic: DreamShaderBridgeDiagnostic, code: String?): String =
        code?.let { "[$it] ${diagnostic.message}" } ?: diagnostic.message

    private fun lineStartOffset(text: String, lineIndex: Int): Int {
        if (lineIndex <= 0) return 0
        var currentLine = 0
        var offset = 0
        while (offset < text.length && currentLine < lineIndex) {
            if (text[offset] == '\n') currentLine++
            offset++
        }
        return offset.coerceIn(0, text.length)
    }
}
