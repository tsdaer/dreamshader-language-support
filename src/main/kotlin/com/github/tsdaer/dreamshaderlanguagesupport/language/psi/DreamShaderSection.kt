package com.github.tsdaer.dreamshaderlanguagesupport.language.psi

import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

interface DreamShaderSection : PsiElement {
    fun sectionName(): String?

    fun bodyTextRange(): TextRange?
}
