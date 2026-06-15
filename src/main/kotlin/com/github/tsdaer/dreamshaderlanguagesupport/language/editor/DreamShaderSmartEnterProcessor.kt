package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLanguageKeywords
import com.intellij.application.options.CodeStyle
import com.intellij.codeInsight.editorActions.smartEnter.SmartEnterProcessor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import java.util.Locale
import kotlin.collections.ArrayDeque
import kotlin.collections.any
import kotlin.collections.asReversed
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString
import kotlin.collections.setOf

class DreamShaderSmartEnterProcessor : SmartEnterProcessor() {
    override fun process(project: Project, editor: Editor, psiFile: PsiFile): Boolean {
        if (psiFile.language != DreamShaderLanguage) return false
        val document = editor.document
        val offset = editor.caretModel.offset.coerceIn(0, document.textLength)
        val text = document.charsSequence
        if (isInsideStringOrComment(text, offset)) return false

        val line = currentLine(document, offset)
        if (text.subSequence(offset, line.end).isNotBlank()) return false

        val beforeCaret = text.subSequence(line.start, offset).toString()
        val significantBeforeCaret = stripLineComment(beforeCaret).trimEnd()
        if (significantBeforeCaret.isBlank()) return false

        val result = completionFor(significantBeforeCaret, lineIndent(beforeCaret), indentUnit(psiFile)) ?: return false
        document.insertString(offset, result.insertText)
        editor.caretModel.moveToOffset(offset + result.caretShift)
        SmartEnterProcessor.commitDocument(editor)
        PsiDocumentManager.getInstance(project).commitDocument(document)
        return true
    }

    private fun completionFor(textBeforeCaret: String, lineIndent: String, indentUnit: String): Completion? {
        if (textBeforeCaret.trimEnd().endsWith("{")) return null
        val closers = missingClosers(textBeforeCaret)
        val completedText = textBeforeCaret + closers
        val normalized = completedText.trim().lowercase(Locale.ROOT)

        if (startsWithDeclaration(normalized)) {
            return blockCompletion(closers, completedText, lineIndent, indentUnit)
        }

        if (startsWithSection(normalized)) {
            return blockCompletion(closers, completedText, lineIndent, indentUnit)
        }

        if (isIfHeader(normalized)) {
            return blockCompletion(closers, completedText, lineIndent, indentUnit)
        }

        if (isElseHeader(normalized)) {
            return blockCompletion(closers, completedText, lineIndent, indentUnit)
        }

        if (normalized.startsWith("import ") && !completedText.endsWith(";")) {
            return Completion("$closers;", closers.length + 1)
        }

        if (shouldTerminateStatement(completedText)) {
            val suffix = if (completedText.endsWith(";")) closers else "$closers;"
            return Completion(suffix, suffix.length)
        }

        if (closers.isNotEmpty()) {
            return Completion(closers, closers.length)
        }

        return null
    }

    private fun blockCompletion(closers: String, completedText: String, lineIndent: String, indentUnit: String): Completion? {
        if (completedText.trimEnd().endsWith("{")) return null
        val beforeBlock = if (completedText.trimEnd().endsWith("=")) " {" else " {"
        val childIndent = lineIndent + indentUnit
        val insertion = "$closers$beforeBlock\n$childIndent\n$lineIndent}"
        return Completion(insertion, closers.length + beforeBlock.length + 1 + childIndent.length)
    }

    private fun startsWithDeclaration(normalized: String): Boolean {
        return firstWord(normalized) in DreamShaderLanguageKeywords.DECLARATION_KEYWORDS
    }

    private fun startsWithSection(normalized: String): Boolean {
        val first = firstWord(normalized)
        return DreamShaderLanguageKeywords.SECTION_KEYWORDS.any { it.equals(first, ignoreCase = true) }
    }

    private fun isIfHeader(normalized: String): Boolean {
        return normalized.startsWith("if ") ||
            normalized.startsWith("if(") ||
            normalized.startsWith("else if ") ||
            normalized.startsWith("else if(") ||
            normalized.contains(" else if ") ||
            normalized.endsWith(" else if") ||
            normalized.contains("} else if ")
    }

    private fun isElseHeader(normalized: String): Boolean {
        return normalized == "else" || normalized.endsWith(" else") || normalized.endsWith("} else")
    }

    private fun shouldTerminateStatement(completedText: String): Boolean {
        val trimmed = completedText.trimEnd()
        if (trimmed.isBlank()) return false
        if (trimmed.endsWith(";") || trimmed.endsWith("{")) return false
        if (trimmed.endsWith("}") && firstWord(trimmed).isEmpty()) return false
        val normalized = trimmed.lowercase(Locale.ROOT)
        if (startsWithDeclaration(normalized) || startsWithSection(normalized)) return false
        val first = firstWord(normalized)
        if (first in setOf("if", "else", "for", "while", "switch", "do")) return false
        return true
    }

    private fun firstWord(text: String): String {
        return text.trimStart().takeWhile { it == '_' || it.isLetterOrDigit() }.lowercase(Locale.ROOT)
    }

    private fun missingClosers(text: String): String {
        val stack = ArrayDeque<Char>()
        var inString = false
        var inBlockComment = false
        var inLineComment = false
        var escaped = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val next = text.getOrNull(i + 1)
            when {
                inLineComment -> Unit
                inBlockComment -> {
                    if (ch == '*' && next == '/') {
                        inBlockComment = false
                        i++
                    }
                }
                inString -> {
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '"' -> inString = false
                    }
                }
                ch == '/' && next == '/' -> {
                    inLineComment = true
                    i++
                }
                ch == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                ch == '"' -> inString = true
                ch == '(' || ch == '[' || ch == '{' -> stack.addLast(ch)
                ch == ')' || ch == ']' || ch == '}' -> {
                    if (stack.isNotEmpty() && matchingCloser(stack.last()) == ch) {
                        stack.removeLast()
                    }
                }
            }
            i++
        }
        return stack.asReversed().joinToString("") { matchingCloser(it).toString() }
    }

    private fun matchingCloser(opener: Char): Char {
        return when (opener) {
            '(' -> ')'
            '[' -> ']'
            '{' -> '}'
            else -> opener
        }
    }

    private fun currentLine(document: Document, offset: Int): LineRange {
        val lineNumber = document.getLineNumber(offset.coerceIn(0, document.textLength))
        return LineRange(
            start = document.getLineStartOffset(lineNumber),
            end = document.getLineEndOffset(lineNumber)
        )
    }

    private fun lineIndent(lineBeforeCaret: String): String {
        return lineBeforeCaret.takeWhile { it == ' ' || it == '\t' }
    }

    private fun indentUnit(psiFile: PsiFile): String {
        val options = CodeStyle.getIndentOptions(psiFile)
        return if (options.USE_TAB_CHARACTER) {
            "\t"
        } else {
            " ".repeat(options.INDENT_SIZE.coerceAtLeast(1))
        }
    }

    private fun stripLineComment(text: String): String {
        var inString = false
        var escaped = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            val next = text.getOrNull(i + 1)
            when {
                inString -> {
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '"' -> inString = false
                    }
                }
                ch == '"' -> inString = true
                ch == '/' && next == '/' -> return text.substring(0, i)
            }
            i++
        }
        return text
    }

    private fun isInsideStringOrComment(text: CharSequence, offset: Int): Boolean {
        var inString = false
        var inBlockComment = false
        var inLineComment = false
        var escaped = false
        var i = 0
        val end = offset.coerceIn(0, text.length)
        while (i < end) {
            val ch = text[i]
            val next = text.getOrNull(i + 1)
            when {
                inLineComment -> {
                    if (ch == '\n' || ch == '\r') inLineComment = false
                }
                inBlockComment -> {
                    if (ch == '*' && next == '/') {
                        inBlockComment = false
                        i++
                    }
                }
                inString -> {
                    when {
                        escaped -> escaped = false
                        ch == '\\' -> escaped = true
                        ch == '"' -> inString = false
                    }
                }
                ch == '/' && next == '/' -> {
                    inLineComment = true
                    i++
                }
                ch == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                ch == '"' -> inString = true
            }
            i++
        }
        return inString || inLineComment || inBlockComment
    }

    private data class Completion(val insertText: String, val caretShift: Int)

    private data class LineRange(val start: Int, val end: Int)
}
