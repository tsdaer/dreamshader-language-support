package com.github.tsdaer.dreamshaderlanguagesupport.language.psi

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementType
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2StubElementType
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiElement
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2PsiElementFactory
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShaderDeclarationImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShaderSectionImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

/**
 * Singleton for DreamShaderPsiElementFactory.
 */
object DreamShaderPsiElementFactory {
    fun createElement(node: ASTNode): PsiElement {
        if (node.elementType is DreamShader2ElementType || node.elementType is DreamShader2StubElementType<*, *>) {
            return DreamShader2PsiElementFactory.createElement(node)
                ?: DreamShaderPsiElement(node)
        }
        return when (node.elementType) {
            DreamShaderElementTypes.DECLARATION -> DreamShaderDeclarationImpl(node)
            DreamShaderElementTypes.SECTION -> DreamShaderSectionImpl(node)
            else -> DreamShaderPsiElement(node)
        }
    }
}
