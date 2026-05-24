package com.github.tsdaer.dreamshaderlanguagesupport.language.psi
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiElement
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShaderDeclarationImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShaderSectionImpl
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement

object DreamShaderPsiElementFactory {
    fun createElement(node: ASTNode): PsiElement {
        return when (node.elementType) {
            DreamShaderElementTypes.DECLARATION -> DreamShaderDeclarationImpl(node)
            DreamShaderElementTypes.SECTION -> DreamShaderSectionImpl(node)
            else -> DreamShaderPsiElement(node)
        }
    }
}
