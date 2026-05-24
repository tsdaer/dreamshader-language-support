package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange

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
            holder.newAnnotation(severity, diagnostic.message)
                .range(TextRange(start, end))
                .create()
        }
    }

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

