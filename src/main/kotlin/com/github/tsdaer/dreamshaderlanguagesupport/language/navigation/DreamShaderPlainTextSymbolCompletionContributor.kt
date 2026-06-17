package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiUtil
import com.intellij.codeInsight.completion.PlainTextSymbolCompletionContributor
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiFile

class DreamShaderPlainTextSymbolCompletionContributor : PlainTextSymbolCompletionContributor {
    override fun getLookupElements(file: PsiFile, invocationCount: Int, prefix: String): Collection<LookupElement> {
        if (file.language != DreamShaderLanguage) return emptyList()
        val provider = DreamShaderQualifiedNameProvider()
        val result = linkedSetOf<String>()
        DreamShaderPsiUtil.declarationsIn(file)
            .forEach { declaration ->
                provider.getQualifiedName(declaration)?.let(result::add)
                DreamShaderPsiUtil.directSectionsOf(declaration)
                    .forEach { section -> provider.getQualifiedName(section)?.let(result::add) }
            }
        return result
            .filter { prefix.isBlank() || it.startsWith(prefix, ignoreCase = true) }
            .map { LookupElementBuilder.create(it).withTypeText("DreamShader", true) }
    }
}
