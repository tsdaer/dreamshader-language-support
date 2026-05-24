package com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import java.util.Locale

class DreamShaderSectionImpl(node: ASTNode) : ASTWrapperPsiElement(node), DreamShaderSection {
    override fun sectionName(): String? {
        val sectionNode = node.findChildByType(DreamShaderTokenTypes.SECTION) ?: return null
        return sectionNode.text.lowercase(Locale.ROOT)
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
