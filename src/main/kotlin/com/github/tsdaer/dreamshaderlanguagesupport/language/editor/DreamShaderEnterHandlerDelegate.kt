package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegate
import com.intellij.codeInsight.editorActions.enter.EnterHandlerDelegateAdapter
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiFile

class DreamShaderEnterHandlerDelegate : EnterHandlerDelegateAdapter() {
    override fun postProcessEnter(file: PsiFile, editor: Editor, dataContext: DataContext): EnterHandlerDelegate.Result {
        if (file.language != DreamShaderLanguage) return EnterHandlerDelegate.Result.Continue

        val document = editor.document
        val caretOffset = editor.caretModel.offset.coerceIn(0, document.textLength)
        val lineNumber = document.getLineNumber(caretOffset)
        if (lineNumber <= 0) return EnterHandlerDelegate.Result.Continue

        val previousLine = findPreviousMeaningfulLine(document, lineNumber - 1)
            ?: return EnterHandlerDelegate.Result.Continue
        val previousText = lineText(document, previousLine)
        val previousTrimmed = previousText.trimEnd()
        if (previousTrimmed.isEmpty()) return EnterHandlerDelegate.Result.Continue

        val targetIndent = if (previousTrimmed.endsWith("{")) {
            leadingIndent(previousText) + indentUnit(file)
        } else {
            leadingIndent(previousText)
        }

        val currentLineStart = document.getLineStartOffset(lineNumber)
        val currentLineEnd = document.getLineEndOffset(lineNumber)
        val currentText = document.getText(com.intellij.openapi.util.TextRange(currentLineStart, currentLineEnd))
        val currentIndentLength = currentText.takeWhile { it == ' ' || it == '\t' }.length
        val currentIndentEnd = currentLineStart + currentIndentLength
        val currentIndent = currentText.take(currentIndentLength)
        if (currentIndent == targetIndent) return EnterHandlerDelegate.Result.Continue

        document.replaceString(currentLineStart, currentIndentEnd, targetIndent)
        editor.caretModel.moveToOffset(currentLineStart + targetIndent.length)
        return EnterHandlerDelegate.Result.Continue
    }

    private fun findPreviousMeaningfulLine(document: com.intellij.openapi.editor.Document, fromLine: Int): Int? {
        var line = fromLine
        while (line >= 0) {
            val text = lineText(document, line)
            if (text.isNotBlank()) return line
            line--
        }
        return null
    }

    private fun lineText(document: com.intellij.openapi.editor.Document, line: Int): String {
        val start = document.getLineStartOffset(line)
        val end = document.getLineEndOffset(line)
        return document.getText(com.intellij.openapi.util.TextRange(start, end))
    }

    private fun leadingIndent(text: String): String = text.takeWhile { it == ' ' || it == '\t' }

    private fun indentUnit(file: PsiFile): String {
        val options = CodeStyle.getIndentOptions(file)
        return if (options.USE_TAB_CHARACTER) {
            "\t"
        } else {
            " ".repeat(options.INDENT_SIZE.coerceAtLeast(1))
        }
    }
}
