package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguageRules
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenSets
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.lang.cacheBuilder.DefaultWordsScanner
import com.intellij.lang.cacheBuilder.WordsScanner
import com.intellij.lang.findUsages.FindUsagesProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * 查找DreamShader声明的使用集成。
 */
class DreamShaderFindUsagesProvider : FindUsagesProvider {
    override fun getWordsScanner(): WordsScanner {
        return DefaultWordsScanner(
            DreamShaderLexer(),
            DreamShaderTokenSets.IDENTIFIERS,
            DreamShaderTokenSets.COMMENTS,
            DreamShaderTokenSets.STRINGS
        )
    }

    override fun canFindUsagesFor(psiElement: PsiElement): Boolean = targetDeclaration(psiElement) != null

    override fun getHelpId(psiElement: PsiElement): String? = null

    override fun getType(element: PsiElement): String = when (element) {
        is DreamShaderDeclaration -> typeText(element)
        else -> targetDeclaration(element)?.let(::typeText) ?: "dreamshader symbol"
    }

    override fun getDescriptiveName(element: PsiElement): String {
        val declaration = targetDeclaration(element)
        if (declaration != null) {
            return qualifiedDeclarationName(declaration)
                ?: declaration.declarationName()
                ?: declaration.text
        }

        val named = element as? PsiNamedElement
        return named?.name ?: element.text
    }

    override fun getNodeText(element: PsiElement, useFullName: Boolean): String {
        val declaration = targetDeclaration(element) ?: return getDescriptiveName(element)
        val keyword = displayDeclarationKeyword(declaration)
        val name = if (useFullName) {
            qualifiedDeclarationName(declaration) ?: declaration.declarationName()
        } else {
            declaration.declarationName()
        }
        return listOfNotNull(keyword, name).joinToString(" ").ifBlank { getDescriptiveName(element) }
    }

    private fun typeText(declaration: DreamShaderDeclaration): String {
        val keyword = declaration.keywordText().orEmpty().trim()
        return if (keyword.isBlank()) {
            "dreamshader declaration"
        } else {
            "dreamshader ${DreamShaderLanguageRules.displayDeclarationKeyword(keyword)} declaration"
        }
    }

    private fun displayDeclarationKeyword(declaration: DreamShaderDeclaration): String? {
        val keyword = declaration.keywordText().orEmpty().trim()
        if (keyword.isBlank()) return null
        return DreamShaderLanguageRules.displayDeclarationKeyword(keyword)
    }

    private fun qualifiedDeclarationName(declaration: DreamShaderDeclaration): String? {
        val ownName = declaration.declarationName()?.takeIf { it.isNotBlank() } ?: return null
        val names = mutableListOf(ownName)
        var current = PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true)
        while (current != null) {
            if (current.keywordText() == "namespace") {
                current.declarationName()?.takeIf { it.isNotBlank() }?.let { names.add(it) }
            }
            current = PsiTreeUtil.getParentOfType(current, DreamShaderDeclaration::class.java, true)
        }
        names.reverse()
        return names.joinToString("::")
    }

    private fun targetDeclaration(element: PsiElement): DreamShaderDeclaration? {
        if (element is DreamShaderDeclaration) return element
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
            ?: return null
        return declaration.takeIf { it.nameIdentifier == element }
    }
}
