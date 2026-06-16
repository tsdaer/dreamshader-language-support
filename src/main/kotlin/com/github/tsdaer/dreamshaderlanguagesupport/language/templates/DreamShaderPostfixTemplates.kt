package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateExpressionSelector
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate
import com.intellij.lang.Language
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.util.Function

class DreamShaderPostfixTemplateProvider : PostfixTemplateProvider {
    private val expressionSelector = DreamShaderPostfixExpressionSelector()
    private val templates = setOf<PostfixTemplate>(
        DreamShaderIfPostfixTemplate(this, expressionSelector),
        DreamShaderIfElsePostfixTemplate(this, expressionSelector)
    )

    override fun getTemplates(): Set<PostfixTemplate> = templates

    override fun isTerminalSymbol(currentChar: Char): Boolean = currentChar == '.'

    override fun preExpand(file: PsiFile, editor: Editor) = Unit

    override fun afterExpand(file: PsiFile, editor: Editor) = Unit

    override fun preCheck(copyFile: PsiFile, realEditor: Editor, currentOffset: Int): PsiFile = copyFile
}

private class DreamShaderIfPostfixTemplate(
    provider: PostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector
) : StringBasedPostfixTemplate(
    "if",
    "if (expr) { ... }",
    selector,
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "if (\$expr\$\$END\$\n) {\n    \n}"
    }

    override fun shouldReformat(): Boolean = true
}

private class DreamShaderIfElsePostfixTemplate(
    provider: PostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector
) : StringBasedPostfixTemplate(
    "ifel",
    "if (expr) { ... } else { ... }",
    selector,
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "if (\$expr\$) {\n    \n} else {\n    \$END\$\n}"
    }

    override fun shouldReformat(): Boolean = true
}

private class DreamShaderPostfixExpressionSelector : PostfixTemplateExpressionSelector {
    override fun hasExpression(context: PsiElement, document: Document, offset: Int): Boolean {
        return getExpressions(context, document, offset).isNotEmpty()
    }

    override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
        val line = currentLinePrefix(document, offset).trim()
        if (line.isBlank()) return emptyList()
        if (!looksLikeExpression(line)) return emptyList()
        return listOf(context)
    }

    override fun getRenderer(): Function<PsiElement, String> {
        return Function { it.text }
    }

    private fun currentLinePrefix(document: Document, offset: Int): String {
        val safeOffset = offset.coerceIn(0, document.textLength)
        val lineNumber = document.getLineNumber(safeOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        return document.getText(com.intellij.openapi.util.TextRange(lineStart, safeOffset))
    }

    private fun looksLikeExpression(text: String): Boolean {
        if (text.endsWith("{") || text.endsWith("}") || text.endsWith(";")) return false
        if (text.startsWith("if ") || text.startsWith("if(") || text.startsWith("else")) return false
        if (text.startsWith("Properties") || text.startsWith("Inputs") || text.startsWith("Outputs") ||
            text.startsWith("Settings") || text.startsWith("Options") || text.startsWith("Graph")
        ) {
            return false
        }
        if (looksLikeTopLevelAssignment(text)) return false
        return text.any { !it.isWhitespace() }
    }

    private fun looksLikeTopLevelAssignment(text: String): Boolean {
        var parenDepth = 0
        for (index in text.indices) {
            when (text[index]) {
                '(' -> parenDepth++
                ')' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                '=' -> {
                    val previous = text.getOrNull(index - 1)
                    val next = text.getOrNull(index + 1)
                    if (parenDepth == 0 && previous != '=' && next != '=') {
                        return true
                    }
                }
            }
        }
        return false
    }
}

internal fun dreamShaderPostfixTemplateLanguage(): Language = DreamShaderLanguage
