package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale

class DreamShaderDocumentationProvider : AbstractDocumentationProvider() {
    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?): String? {
        return generateDoc(element, originalElement)
    }

    override fun generateDoc(element: PsiElement?, originalElement: PsiElement?): String? {
        val target = element ?: originalElement ?: return null
        if (target.language != DreamShaderLanguage) return null

        val declarationDoc = declarationDocumentation(target)
        if (declarationDoc != null) return declarationDoc

        val token = normalizeTokenText(target.text)
        if (token.isBlank()) return null

        val settingsKeyDoc = settingsKeyDocumentation(target, token)
        if (settingsKeyDoc != null) return settingsKeyDoc

        val settingsValueDoc = settingsValueDocumentation(target, token)
        if (settingsValueDoc != null) return settingsValueDoc

        val ueBuiltinDoc = ueBuiltinDocumentation(target, token)
        if (ueBuiltinDoc != null) return ueBuiltinDoc

        return null
    }

    private fun declarationDocumentation(element: PsiElement): String? {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return null
        if (declaration.nameIdentifier != element && declaration != element) return null

        val keyword = declaration.keywordText() ?: return null
        val name = declaration.declarationName().orEmpty().ifBlank { "<anonymous>" }
        val kind = DreamShaderDocumentationData.declarationKeywordDescriptions[keyword]
            ?: "DreamShader declaration symbol."

        return buildString {
            append("<b>")
            append(keyword.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() })
            append(' ')
            append(name)
            append("</b><br/>")
            append(kind)
        }
    }

    private fun settingsKeyDocumentation(element: PsiElement, token: String): String? {
        if (!isInSettingsOrOptionsSection(element)) return null
        val info = DreamShaderDocumentationData.settings[token.lowercase(Locale.ROOT)] ?: return null

        return buildString {
            append("<b>Settings Key: ")
            append(info.key)
            append("</b><br/>")
            append(info.description)
            if (info.commonValues.isNotEmpty()) {
                append("<br/>Common values: ")
                append(info.commonValues.joinToString(", "))
            }
        }
    }

    private fun settingsValueDocumentation(element: PsiElement, token: String): String? {
        if (!isInSettingsOrOptionsSection(element)) return null
        val value = token.trim('"')
        if (value.isBlank()) return null

        val owners = DreamShaderDocumentationData.valueOwners(value)
        if (owners.isEmpty()) return null

        return buildString {
            append("<b>Setting Value: ")
            append(value)
            append("</b><br/>")
            append("Used by: ")
            append(owners.joinToString(", "))
        }
    }

    private fun ueBuiltinDocumentation(element: PsiElement, token: String): String? {
        if (!isGraphLikeContext(element)) return null
        val builtin = DreamShaderDocumentationData.ueBuiltins[token.lowercase(Locale.ROOT)] ?: return null

        return buildString {
            append("<b>")
            append(builtin.signature)
            append("</b><br/>")
            append(builtin.description)
        }
    }

    private fun isGraphLikeContext(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        if (declaration?.isFunctionLike() == true) return true

        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false) ?: return false
        val name = section.sectionName() ?: return false
        return name == "graph" || name == "outputs" || name == "inputs" || name == "results"
    }

    private fun isInSettingsOrOptionsSection(element: PsiElement): Boolean {
        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false) ?: return false
        val sectionName = section.sectionName() ?: return false
        return sectionName == "settings" || sectionName == "options"
    }

    private fun normalizeTokenText(text: String): String {
        return text.trim().trim('"')
    }
}
