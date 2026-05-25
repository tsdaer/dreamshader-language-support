package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import java.util.*

/**
 * Provider implementation for DreamShaderDocumentationProvider.
 */
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
        val overrideKey = "declaration.$keyword.description"
        val kind = overrideDoc(element, overrideKey)
            ?: DreamShaderDocumentationData.declarationDescription(keyword)
            ?: DreamShaderBundle.message("docs.declaration.default")

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
        val info = DreamShaderDocumentationData.settingInfo(token) ?: return null
        val overrideKey = "settings.${info.key.lowercase(Locale.ROOT)}.description"
        val description = overrideDoc(element, overrideKey) ?: info.description

        return buildString {
            append("<b>")
            append(DreamShaderBundle.message("docs.label.settingsKey"))
            append(": ")
            append(info.key)
            append("</b><br/>")
            append(description)
            if (info.commonValues.isNotEmpty()) {
                append("<br/>")
                append(DreamShaderBundle.message("docs.label.commonValues"))
                append(": ")
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
            append("<b>")
            append(DreamShaderBundle.message("docs.label.settingValue"))
            append(": ")
            append(value)
            append("</b><br/>")
            append(DreamShaderBundle.message("docs.label.usedBy"))
            append(": ")
            append(owners.joinToString(", "))
        }
    }

    private fun ueBuiltinDocumentation(element: PsiElement, token: String): String? {
        if (!isGraphLikeContext(element)) return null
        val builtin = DreamShaderDocumentationData.ueBuiltinInfo(token) ?: return null
        val overrideKey = "ueBuiltins.${builtin.name.lowercase(Locale.ROOT)}.description"
        val description = overrideDoc(element, overrideKey) ?: builtin.description

        return buildString {
            append("<b>")
            append(builtin.signature)
            append("</b><br/>")
            append(description)
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

    private fun overrideDoc(element: PsiElement, key: String): String? {
        return DreamShaderHoverOverrideService.resolve(element.project, key)
    }
}
