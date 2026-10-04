package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate
import com.intellij.codeInsight.editorActions.moveUpDown.LineRange
import com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover
import com.intellij.codeInsight.editorActions.moveLeftRight.MoveElementLeftRightHandler
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.openapi.editor.actions.lists.DefaultListSplitJoinContext
import com.intellij.openapi.editor.actions.lists.ListWithElements

/**
 * Editor action handlers (D4): Join Lines for stacked comment lines, Alt+Shift+Left/Right
 * element moving inside 2.0 argument/parameter lists, Ctrl+Alt+-/+ statement moving for
 * 1.x declarations/sections and 2.0 statements, and the Alt+Enter split/join intention
 * context for 2.0 comma lists.
 */

/** Joins two adjacent comment lines (`// a` + `// b` -> `// a b`); everything else falls through. */
class DreamShaderJoinLinesHandler : JoinLinesHandlerDelegate {
    override fun tryJoinLines(document: Document, file: PsiFile, start: Int, end: Int): Int {
        val anchor = minOf(start, end).coerceIn(0, file.textLength - 1)
        val first = commentElementBefore(file, anchor) ?: return JoinLinesHandlerDelegate.CANNOT_JOIN
        val second = commentElementAtLineStart(file, maxOf(start, end)) ?: return JoinLinesHandlerDelegate.CANNOT_JOIN
        if (second.textRange.startOffset <= first.textRange.endOffset) return JoinLinesHandlerDelegate.CANNOT_JOIN

        val merged = buildString {
            append(first.text.trimEnd())
            append(' ')
            append(commentContent(second))
        }.trimEnd()
        val target = TextRangeOf(first, second)
        document.replaceString(target.first, target.second, merged)
        return target.first + merged.length
    }

    /** The comment token ending at or just before [offset] (the first line's tail comment). */
    private fun commentElementBefore(file: PsiFile, offset: Int): PsiElement? {
        var leaf = file.findElementAt(offset)
        if (leaf == null || !isComment(leaf)) {
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf ?: return null)
        }
        return leaf?.takeIf { isComment(it) }
    }

    /** The comment token that opens the line containing [offset] (indentation before it only). */
    private fun commentElementAtLineStart(file: PsiFile, offset: Int): PsiElement? {
        val lineStart = documentLineStart(file, offset) ?: return null
        val leaf = firstSignificantLeafOnLine(file, lineStart, offset) ?: return null
        if (!isComment(leaf)) return null
        return leaf
    }

    private fun documentLineStart(file: PsiFile, offset: Int): Int? {
        val document = file.viewProvider.document ?: return null
        val line = document.getLineNumber(offset.coerceAtMost(document.textLength))
        return document.getLineStartOffset(line)
    }

    private fun firstSignificantLeafOnLine(file: PsiFile, lineStart: Int, limit: Int): PsiElement? {
        var current = file.findElementAt(lineStart)
        while (current != null && current.textRange.startOffset <= limit) {
            if (current.textLength > 0 && current.text.isNotBlank()) return current
            current = PsiTreeUtil.nextLeaf(current)
        }
        return current
    }

    private fun commentContent(element: PsiElement): String {
        val text = element.text
        val withoutMarker = when {
            text.startsWith("///") -> text.removePrefix("///")
            text.startsWith("//") -> text.removePrefix("//")
            else -> text
        }
        return withoutMarker.trim()
    }

    private fun isComment(element: PsiElement): Boolean =
        element.node?.elementType in COMMENT_TOKENS

    private fun TextRangeOf(first: PsiElement, second: PsiElement): kotlin.Pair<Int, Int> =
        first.textRange.startOffset to second.textRange.endOffset

    private val COMMENT_TOKENS: TokenSet = TokenSet.create(
        DreamShaderTokenTypes.LINE_COMMENT,
        DreamShaderTokenTypes.DOC_COMMENT,
    )
}

/** Alt+Shift+Left/Right inside 2.0 argument/parameter lists and initializer lists. */
class DreamShaderMoveElementLeftRightHandler : MoveElementLeftRightHandler() {
    override fun getMovableSubElements(element: PsiElement): Array<PsiElement> {
        val type = PsiUtilCore.getElementType(element) ?: return PsiElement.EMPTY_ARRAY
        if (type !in LIST_ELEMENT_TYPES) return PsiElement.EMPTY_ARRAY
        return element.children
            .filter { PsiUtilCore.getElementType(it) in LIST_ITEM_TYPES }
            .toTypedArray()
    }

    private companion object {
        val LIST_ELEMENT_TYPES: TokenSet = TokenSet.create(
            DreamShader2ElementTypes.ARGUMENT_LIST,
            DreamShader2ElementTypes.PARAMETER_LIST,
            DreamShader2ElementTypes.INITIALIZER_LIST,
        )
        val LIST_ITEM_TYPES: TokenSet = TokenSet.create(
            DreamShader2ElementTypes.ARGUMENT,
            DreamShader2ElementTypes.NAMED_ARGUMENT,
            DreamShader2ElementTypes.PARAMETER,
        )
    }
}

/** Ctrl+Alt+(Shift+)Plus/Minus moves 1.x declarations/sections and 2.0 statements past their siblings. */
class DreamShaderStatementUpDownMover : StatementUpDownMover() {
    override fun checkAvailable(editor: Editor, file: PsiFile, info: MoveInfo, down: Boolean): Boolean {
        val seed = file.findElementAt(editor.caretModel.offset) ?: return false
        val element = DreamShaderStatementLevelElements.enclosing(seed) ?: return false
        val target = DreamShaderStatementLevelElements.adjacentSibling(element, down) ?: return false

        info.toMove = LineRange(element)
        info.toMove2 = LineRange(target)
        info.indentTarget = false
        return true
    }
}

/**
 * Split/join of 2.0 comma lists (argument lists, parameter lists, initializer lists) via
 * the platform's Alt+Enter "Split into lines / Join lines" intentions.
 */
class DreamShaderListSplitJoinContext : DefaultListSplitJoinContext() {
    override fun isSeparator(element: PsiElement): Boolean =
        PsiUtilCore.getElementType(element) == DreamShaderTokenTypes.COMMA

    override fun extractData(element: PsiElement): ListWithElements? {
        val type: IElementType = PsiUtilCore.getElementType(element) ?: return null
        if (type !in LIST_ELEMENT_TYPES) return null
        val items = element.children
            .filter { PsiUtilCore.getElementType(it) in LIST_ITEM_TYPES }
            .toList()
        if (items.size < 2) return null
        return ListWithElements(element, items)
    }

    private companion object {
        val LIST_ELEMENT_TYPES: TokenSet = TokenSet.create(
            DreamShader2ElementTypes.ARGUMENT_LIST,
            DreamShader2ElementTypes.PARAMETER_LIST,
            DreamShader2ElementTypes.INITIALIZER_LIST,
        )
        val LIST_ITEM_TYPES: TokenSet = TokenSet.create(
            DreamShader2ElementTypes.ARGUMENT,
            DreamShader2ElementTypes.NAMED_ARGUMENT,
            DreamShader2ElementTypes.PARAMETER,
        )
    }
}
