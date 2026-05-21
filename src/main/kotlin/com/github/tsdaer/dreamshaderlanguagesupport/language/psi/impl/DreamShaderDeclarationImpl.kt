package com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl

import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderLanguageKeywords
import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.util.IncorrectOperationException
import java.util.Locale

class DreamShaderDeclarationImpl(node: ASTNode) : ASTWrapperPsiElement(node), DreamShaderDeclaration {
    override fun keywordText(): String? {
        val keywordNode = node.findChildByType(DreamShaderTokenTypes.KEYWORD) ?: return null
        return keywordNode.text.lowercase(Locale.ROOT)
    }

    override fun declarationName(): String? {
        return nameIdentifier?.text
    }

    override fun bodyTextRange(): TextRange? {
        val leftBrace = node.findChildByType(DreamShaderTokenTypes.LBRACE) ?: return null
        val rightBrace = findMatchingRightBrace(leftBrace) ?: return null
        return TextRange(leftBrace.startOffset, rightBrace.startOffset + rightBrace.textLength)
    }

    override fun isFunctionLike(): Boolean {
        val keyword = keywordText() ?: return false
        return keyword in DreamShaderLanguageKeywords.FUNCTION_LIKE_DECLARATION_KEYWORDS
    }

    override fun getName(): String? = declarationName()

    override fun getNameIdentifier(): PsiElement? {
        var child = node.firstChildNode
        var seenKeyword = false
        while (child != null) {
            if (child.elementType == DreamShaderTokenTypes.KEYWORD) {
                seenKeyword = true
            } else if (seenKeyword && child.elementType == DreamShaderTokenTypes.IDENTIFIER) {
                return child.psi
            }
            child = child.treeNext
        }
        return null
    }

    override fun setName(newName: String): PsiElement {
        if (newName.isBlank()) throw IncorrectOperationException("DreamShader declaration name cannot be blank")
        val identifier = nameIdentifier ?: throw IncorrectOperationException("Cannot rename declaration without identifier")
        val replacementIdentifier = createIdentifierFromText(project, newName)
        identifier.replace(replacementIdentifier)
        return this
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

    private fun createIdentifierFromText(project: Project, name: String): PsiElement {
        val file = PsiFileFactory.getInstance(project).createFileFromText(
            "dummy.dsf",
            DreamShaderLanguage,
            "Shader $name { }"
        )
        val declaration = com.intellij.psi.util.PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)
            ?: throw IncorrectOperationException("Failed to create declaration identifier")
        return declaration.nameIdentifier
            ?: throw IncorrectOperationException("Failed to create declaration identifier")
    }
}
