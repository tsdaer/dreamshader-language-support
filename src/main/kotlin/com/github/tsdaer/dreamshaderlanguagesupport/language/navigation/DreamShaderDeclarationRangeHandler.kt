package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.codeInsight.hint.DeclarationRangeHandler
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

class DreamShaderDeclarationRangeHandler : DeclarationRangeHandler<PsiElement> {
    override fun getDeclarationRange(container: PsiElement): TextRange? {
        return when (container) {
            is DreamShaderDeclaration -> headerRange(container, container.bodyTextRange()?.startOffset)
            is DreamShaderSection -> headerRange(container, container.bodyTextRange()?.startOffset)
            else -> null
        }
    }

    private fun headerRange(element: PsiElement, bodyStartOffset: Int?): TextRange? {
        val start = element.textRange?.startOffset ?: return null
        val end = bodyStartOffset ?: element.textRange?.endOffset ?: return null
        if (end <= start) return element.textRange
        return TextRange(start, end)
    }
}
