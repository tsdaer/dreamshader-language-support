package com.github.tsdaer.dreamshaderlanguagesupport.language.psi

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiElement
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShaderDeclarationImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShaderSectionImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * $name 单例对象。
 */
object DreamShaderPsiElementFactory {
    fun createElement(node: ASTNode): PsiElement {
        return when (node.elementType) {
            DreamShaderElementTypes.DECLARATION -> DreamShaderDeclarationImpl(node)
            DreamShaderElementTypes.SECTION -> DreamShaderSectionImpl(node)
            else -> DreamShaderPsiElement(node)
        }
    }
}
