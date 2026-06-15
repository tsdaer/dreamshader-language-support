package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.codeInsight.completion.PlainTextSymbolCompletionContributor
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil

class DreamShaderPlainTextSymbolCompletionContributor : PlainTextSymbolCompletionContributor {
    override fun getLookupElements(file: PsiFile, invocationCount: Int, prefix: String): Collection<LookupElement> {
        if (file.language != DreamShaderLanguage) return emptyList()
        val provider = DreamShaderQualifiedNameProvider()
        val result = linkedSetOf<String>()
        PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java)
            .forEach { declaration ->
                provider.getQualifiedName(declaration)?.let(result::add)
                PsiTreeUtil.findChildrenOfType(declaration, DreamShaderSection::class.java)
                    .forEach { section -> provider.getQualifiedName(section)?.let(result::add) }
            }
        return result
            .filter { prefix.isBlank() || it.startsWith(prefix, ignoreCase = true) }
            .map { LookupElementBuilder.create(it).withTypeText("DreamShader", true) }
    }
}
