package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.codeInsight.highlighting.CodeBlockSupportHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.psi.util.PsiUtilCore

/**
 * Code block support (D5): makes the brace-delimited constructs - 1.x declarations and
 * sections, 2.0 functions/structs/statements - participate in the platform code block
 * navigation and selection (block start/end jumping). The declaration/section braces are
 * already flagged structural in [DreamShaderBraceMatcher] (`BracePair(..., true)`), which
 * gives them the higher matching priority; this handler adds the block ranges.
 */
class DreamShaderCodeBlockSupportHandler : CodeBlockSupportHandler {
    override fun getCodeBlockMarkerRanges(element: PsiElement): List<TextRange> {
        val block = containingBlock(element) ?: return emptyList()
        return braceMarkerRanges(block)
    }

    override fun getCodeBlockRange(element: PsiElement): TextRange {
        return containingBlock(element)?.textRange ?: TextRange.EMPTY_RANGE
    }

    /** The nearest brace-delimited block containing [element], or the element itself. */
    private fun containingBlock(element: PsiElement): PsiElement? {
        var current: PsiElement? = element
        while (current != null && current !is com.intellij.psi.PsiFile) {
            if (BLOCK_ELEMENT_TYPES.contains(PsiUtilCore.getElementType(current))) return current
            current = current.parent
        }
        return null
    }

    /** Marker ranges sit on the opening and closing brace leaves when present. */
    private fun braceMarkerRanges(block: PsiElement): List<TextRange> {
        val ranges = mutableListOf<TextRange>()
        var current: PsiElement? = block.firstChild
        var closing: PsiElement? = null
        while (current != null) {
            if (PsiUtilCore.getElementType(current) == DreamShaderTokenTypes.LBRACE) {
                ranges.add(current.textRange)
            }
            if (PsiUtilCore.getElementType(current) == DreamShaderTokenTypes.RBRACE) {
                closing = current
            }
            current = current.nextSibling
        }
        closing?.let { ranges.add(it.textRange) }
        return ranges
    }

    private companion object {
        val BLOCK_ELEMENT_TYPES: TokenSet = TokenSet.create(
            DreamShaderElementTypes.DECLARATION,
            DreamShaderElementTypes.SECTION,
            DreamShader2ElementTypes.FUNCTION_DECLARATION,
            DreamShader2ElementTypes.STRUCT_DECLARATION,
            DreamShader2ElementTypes.UNIFORM_DECLARATION,
            DreamShader2ElementTypes.BLOCK,
            DreamShader2ElementTypes.IF_STATEMENT,
            DreamShader2ElementTypes.FOR_STATEMENT,
            DreamShader2ElementTypes.WHILE_STATEMENT,
            DreamShader2ElementTypes.DO_WHILE_STATEMENT,
        )
    }
}
