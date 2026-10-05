package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
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
        DreamShaderIfElsePostfixTemplate(this, expressionSelector),
        DreamShaderLerpPostfixTemplate(this, expressionSelector),
        DreamShaderSaturatePostfixTemplate(this, expressionSelector),
        DreamShaderSamplePostfixTemplate(this, expressionSelector),
        DreamShaderSwizzlePostfixTemplate(this, expressionSelector),
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

private class DreamShaderLerpPostfixTemplate(
    provider: PostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector
) : StringBasedPostfixTemplate(
    "lerp",
    "lerp(expr, y, s)",
    selector,
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "lerp(\$expr\$, \$y\$, \$s\$)\$END\$"
    }

    override fun shouldReformat(): Boolean = true
}

private class DreamShaderSaturatePostfixTemplate(
    provider: PostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector
) : StringBasedPostfixTemplate(
    "saturate",
    "saturate(expr)",
    selector,
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "saturate(\$expr\$)\$END\$"
    }

    override fun shouldReformat(): Boolean = true
}

private class DreamShaderSamplePostfixTemplate(
    provider: PostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector
) : StringBasedPostfixTemplate(
    "sample",
    "expr.Sample()",
    selector,
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "\$expr\$.Sample(\$END\$)"
    }

    override fun shouldReformat(): Boolean = false
}

private class DreamShaderSwizzlePostfixTemplate(
    provider: PostfixTemplateProvider,
    selector: PostfixTemplateExpressionSelector
) : StringBasedPostfixTemplate(
    "swizzle",
    "expr.<components>",
    selector,
    provider
) {
    override fun getTemplateString(element: PsiElement): String {
        return "\$expr\$.\$swz\$\$END\$"
    }

    override fun shouldReformat(): Boolean = false
}

private class DreamShaderPostfixExpressionSelector : PostfixTemplateExpressionSelector {
    override fun hasExpression(context: PsiElement, document: Document, offset: Int): Boolean {
        return getExpressions(context, document, offset).isNotEmpty()
    }

    override fun getExpressions(context: PsiElement, document: Document, offset: Int): List<PsiElement> {
        val line = currentLinePrefix(document, offset).trim()
        // 2.0 statements are declarations/assignments, so the template candidate is the value
        // after the last top-level `=` — the line prefix itself would always look like an
        // assignment and kill every template in `.dss`/`.dsi`. 1.x keeps the whole-line rule.
        val candidate = (if (servesV2(context)) textAfterLastTopLevelAssignment(line) else line).trim()
        if (candidate.isEmpty()) return emptyList()
        if (!looksLikeExpression(candidate)) return emptyList()
        return listOf(expressionElementAt(context, offset) ?: context)
    }

    override fun getRenderer(): Function<PsiElement, String> {
        return Function { it.text }
    }

    /** The outermost expression-shaped PSI node ending exactly at [offset]; null in 1.x files. */
    private fun expressionElementAt(context: PsiElement, offset: Int): PsiElement? {
        var best: PsiElement? = null
        var current: PsiElement? = context
        while (current != null) {
            if (current.textRange.endOffset > offset) break
            val elementType = current.node?.elementType
            if (elementType != null && elementType in EXPRESSION_NODE_TYPES) best = current
            current = current.parent
        }
        return best
    }

    private fun servesV2(context: PsiElement): Boolean {
        val file = context.containingFile ?: return false
        val fileName = file.virtualFile?.name ?: file.name
        return DreamShaderDialects.extensionOf(fileName) in DreamShaderDialects.V2_EXTENSIONS
    }

    private fun currentLinePrefix(document: Document, offset: Int): String {
        val safeOffset = offset.coerceIn(0, document.textLength)
        val lineNumber = document.getLineNumber(safeOffset)
        val lineStart = document.getLineStartOffset(lineNumber)
        return document.getText(com.intellij.openapi.util.TextRange(lineStart, safeOffset))
    }

    /**
     * Text after the last top-level assignment `=` of [text] (nesting- and string-aware;
     * `==`/`<=`/`>=`/`!=` excluded, compound forms like `+=` included — the tail after them is
     * still a value being computed); [text] itself when no assignment is present.
     */
    private fun textAfterLastTopLevelAssignment(text: String): String {
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false
        var splitIndex = -1
        for (index in text.indices) {
            val ch = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '(' -> parenDepth++
                ')' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                '[' -> bracketDepth++
                ']' -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                '{' -> braceDepth++
                '}' -> braceDepth = (braceDepth - 1).coerceAtLeast(0)
                '=' -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && isAssignmentEquals(text, index)) {
                        splitIndex = index
                    }
                }
            }
        }
        return if (splitIndex < 0) text else text.substring(splitIndex + 1)
    }

    /** True for plain `=` and compound `op=`; false for `==`, `<=`, `>=`, `!=` and `>>=`-style edges. */
    private fun isAssignmentEquals(text: String, index: Int): Boolean {
        if (text.getOrNull(index + 1) == '=') return false
        return when (text.getOrNull(index - 1)) {
            null, '=', '<', '>', '!' -> false
            else -> true
        }
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
                    if (parenDepth == 0 && isAssignmentEquals(text, index)) {
                        return true
                    }
                }
            }
        }
        return false
    }
}

internal fun dreamShaderPostfixTemplateLanguage(): Language = DreamShaderLanguage

/** Expression-shaped 2.0 node types the selector may wrap into the template's `$expr$`. */
private val EXPRESSION_NODE_TYPES = setOf(
    DreamShader2ElementTypes.EXPRESSION,
    DreamShader2ElementTypes.LITERAL_EXPRESSION,
    DreamShader2ElementTypes.REFERENCE_EXPRESSION,
    DreamShader2ElementTypes.CONSTRUCTOR_EXPRESSION,
    DreamShader2ElementTypes.CAST_EXPRESSION,
    DreamShader2ElementTypes.PARENTHESIZED_EXPRESSION,
    DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION,
    DreamShader2ElementTypes.INDEX_EXPRESSION,
    DreamShader2ElementTypes.CALL_EXPRESSION,
)
