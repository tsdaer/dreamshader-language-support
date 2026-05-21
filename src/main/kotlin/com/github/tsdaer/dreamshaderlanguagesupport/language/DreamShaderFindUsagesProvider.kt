package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement

class DreamShaderFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner {
        return DefaultWordsScanner(
            DreamShaderLexer(),
            DreamShaderTokenSets.IDENTIFIERS,
            DreamShaderTokenSets.COMMENTS,
            DreamShaderTokenSets.STRINGS
        )
    }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean = psiElement is DreamShaderDeclaration

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is DreamShaderDeclaration -> "dreamshader declaration"
        else -> "dreamshader symbol"
    }

    override fun getDescriptiveName(element: PsiElement): String {
        val named = element as? PsiNamedElement
        return named?.name ?: element.text
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String = getDescriptiveName(element)
}

