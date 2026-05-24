package com.github.tsdaer.dreamshaderlanguagesupport.language.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNameIdentifierOwner

/**
 * Contract for DreamShaderDeclaration.
 */
interface DreamShaderDeclaration : PsiElement, PsiNameIdentifierOwner {
    fun keywordText(): String?

    fun declarationName(): String?

    fun bodyTextRange(): TextRange?

    fun isFunctionLike(): Boolean
}
