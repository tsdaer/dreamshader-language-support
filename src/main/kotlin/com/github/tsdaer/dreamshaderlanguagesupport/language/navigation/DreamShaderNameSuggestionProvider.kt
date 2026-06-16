package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.psi.PsiElement
import com.intellij.psi.codeStyle.SuggestedNameInfo
import com.intellij.refactoring.rename.NameSuggestionProvider
import java.util.Locale

class DreamShaderNameSuggestionProvider : NameSuggestionProvider {
    override fun getSuggestedNames(
        element: PsiElement,
        nameSuggestionContext: PsiElement?,
        result: MutableSet<String>
    ): SuggestedNameInfo? {
        val declaration = element as? DreamShaderDeclaration ?: return null
        val baseName = sanitizeIdentifier(DreamShaderDeclarationPresentation.displayName(declaration).orEmpty())
            .ifBlank { defaultBaseName(declaration.keywordText()) }
        if (baseName.isBlank()) return null

        result.add(baseName)
        prefixForKeyword(declaration.keywordText())?.let { prefix ->
            result.add(ensurePrefix(baseName, prefix))
        }
        result.add(defaultBaseName(declaration.keywordText()))
        val names = result.filter { it.isNotBlank() }.distinct().toTypedArray()
        if (names.isEmpty()) return null
        return object : SuggestedNameInfo(names) {}
    }

    internal fun testSuggestedNames(keyword: String?, currentName: String?): List<String> {
        val result = linkedSetOf<String>()
        val baseName = sanitizeIdentifier(currentName.orEmpty()).ifBlank { defaultBaseName(keyword) }
        if (baseName.isNotBlank()) {
            result.add(baseName)
            prefixForKeyword(keyword)?.let { result.add(ensurePrefix(baseName, it)) }
            result.add(defaultBaseName(keyword))
        }
        return result.filter { it.isNotBlank() }
    }

    private fun sanitizeIdentifier(raw: String): String {
        val replaced = raw.trim()
            .substringAfterLast('/')
            .substringAfterLast('\\')
            .replace(Regex("[^A-Za-z0-9_]"), "_")
            .replace(Regex("_+"), "_")
            .trim('_')
        if (replaced.isBlank()) return ""
        return if (replaced.first().isDigit()) "_$replaced" else replaced
    }

    private fun prefixForKeyword(keyword: String?): String? {
        return when (keyword?.lowercase(Locale.ROOT)) {
            "shader" -> "M_"
            "shaderfunction", "function", "graphfunction" -> "F_"
            "shaderlayer" -> "SL_"
            "shaderlayerblend" -> "SLB_"
            "virtualfunction" -> "VF_"
            "namespace" -> "NS_"
            else -> null
        }
    }

    private fun defaultBaseName(keyword: String?): String {
        return when (keyword?.lowercase(Locale.ROOT)) {
            "shader" -> "M_Material"
            "shaderfunction" -> "F_ShaderFunction"
            "function" -> "F_Function"
            "graphfunction" -> "F_GraphFunction"
            "shaderlayer" -> "SL_Layer"
            "shaderlayerblend" -> "SLB_LayerBlend"
            "virtualfunction" -> "VF_Function"
            "namespace" -> "NS_Tools"
            else -> "DreamShaderSymbol"
        }
    }

    private fun ensurePrefix(name: String, prefix: String): String {
        return if (name.startsWith(prefix, ignoreCase = true)) name else prefix + name
    }
}
