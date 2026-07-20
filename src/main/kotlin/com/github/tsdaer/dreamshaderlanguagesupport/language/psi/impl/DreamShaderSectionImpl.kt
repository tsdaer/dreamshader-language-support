package com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import java.util.*

/**
 * Implementation of DreamShaderSectionImpl.
 */
class DreamShaderSectionImpl(node: ASTNode) : ASTWrapperPsiElement(node), DreamShaderSection {
    override fun sectionName(): String? {
        val sectionNode = node.findChildByType(DreamShaderTokenTypes.SECTION) ?: return null
        return sectionNode.text.lowercase(Locale.ROOT)
    }

    override fun groupName(): String? {
        var child = node.firstChildNode
        var seenGroupKeyword = false
        while (child != null) {
            if (child.elementType == DreamShaderTokenTypes.KEYWORD &&
                child.text.lowercase(Locale.ROOT) in setOf("group", "propgroup")) {
                seenGroupKeyword = true
            } else if (seenGroupKeyword && child.elementType == DreamShaderTokenTypes.STRING) {
                val raw = child.text
                if (raw.length >= 2 && raw.startsWith("\"") && raw.endsWith("\"")) {
                    return raw.substring(1, raw.length - 1)
                }
                return raw
            }
            if (seenGroupKeyword && child.elementType == DreamShaderTokenTypes.LBRACE) {
                break
            }
            child = child.treeNext
        }
        return null
    }

    override fun bodyTextRange(): TextRange? {
        val leftBrace = node.findChildByType(DreamShaderTokenTypes.LBRACE) ?: return null
        val rightBrace = findMatchingRightBrace(leftBrace) ?: return null
        return TextRange(leftBrace.startOffset, rightBrace.startOffset + rightBrace.textLength)
    }

    private fun findMatchingRightBrace(leftBrace: ASTNode): ASTNode? {
        var depth = 0
        var current: ASTNode? = leftBrace
        while (current != null) {
            when (current.elementType) {
                DreamShaderTokenTypes.LBRACE -> depth++
                DreamShaderTokenTypes.RBRACE -> {
                    depth--
                    if (depth == 0) {
                        return current
                    }
                }
            }
            current = current.treeNext
        }
        return null
    }
}
