package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.lang.Language
import com.intellij.ui.breadcrumbs.BreadcrumbsProvider
import com.intellij.psi.PsiElement
import java.util.Locale
import javax.swing.Icon

class DreamShaderBreadcrumbsInfoProvider : BreadcrumbsProvider {
    override fun getLanguages(): Array<Language> = arrayOf(DreamShaderLanguage)

    override fun acceptElement(element: PsiElement): Boolean {
        return element is DreamShaderDeclaration || element is DreamShaderSection
    }

    override fun getElementInfo(element: PsiElement): String {
        return when (element) {
            is DreamShaderDeclaration -> listOfNotNull(displayDeclarationKeyword(element.keywordText()), element.declarationName())
                .joinToString(" ")
                .ifBlank { "Declaration" }
            is DreamShaderSection -> displaySectionName(element.sectionName())
            else -> ""
        }
    }

    override fun getElementIcon(element: PsiElement): Icon? {
        return when (element) {
            is DreamShaderSection -> when (element.sectionName()?.lowercase()) {
                "settings", "options" -> DreamShaderIcons.SECTION_SETTINGS
                "inputs" -> DreamShaderIcons.SECTION_INPUTS
                "outputs", "results" -> DreamShaderIcons.SECTION_OUTPUTS
                "graph" -> DreamShaderIcons.SECTION_GRAPH
                else -> DreamShaderIcons.SECTION
            }
            is DreamShaderDeclaration -> when (element.keywordText()?.lowercase()) {
                "shader" -> DreamShaderIcons.SHADER
                "function", "graphfunction", "shaderfunction", "virtualfunction" -> DreamShaderIcons.FUNCTION
                else -> DreamShaderIcons.DECLARATION
            }
            else -> null
        }
    }

    private fun displayDeclarationKeyword(keyword: String?): String? {
        return when (keyword?.lowercase(Locale.ROOT)) {
            "shader" -> "Shader"
            "namespace" -> "Namespace"
            "function" -> "Function"
            "graphfunction" -> "GraphFunction"
            "shaderfunction" -> "ShaderFunction"
            "shaderlayer" -> "ShaderLayer"
            "shaderlayerblend" -> "ShaderLayerBlend"
            "virtualfunction" -> "VirtualFunction"
            null -> null
            else -> keyword.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private fun displaySectionName(section: String?): String {
        return when (section?.lowercase(Locale.ROOT)) {
            "settings" -> "Settings"
            "options" -> "Options"
            "inputs" -> "Inputs"
            "outputs" -> "Outputs"
            "results" -> "Results"
            "graph" -> "Graph"
            null, "" -> "Section"
            else -> section.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }
}
