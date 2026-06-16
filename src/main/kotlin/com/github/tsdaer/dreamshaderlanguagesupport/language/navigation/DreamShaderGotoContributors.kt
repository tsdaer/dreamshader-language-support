package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguageRules
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.navigation.ChooseByNameContributor
import com.intellij.navigation.ItemPresentation
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.pom.Navigatable
import javax.swing.Icon

class DreamShaderGotoClassContributor : ChooseByNameContributor {
    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> {
        return classDeclarations(project, includeNonProjectItems)
            .mapNotNull(::declarationName)
            .distinct()
            .sorted()
            .toList()
            .toTypedArray()
    }

    override fun getItemsByName(
        name: String,
        pattern: String,
        project: Project,
        includeNonProjectItems: Boolean
    ): Array<NavigationItem> {
        return classDeclarations(project, includeNonProjectItems)
            .filter { declarationName(it) == name }
            .sortedWith(compareBy({ DreamShaderDeclarationSearch.qualifiedName(it) }, { it.containingFile.name }))
            .map { declaration ->
                DreamShaderDeclarationNavigationItem(
                    declaration = declaration,
                    presentableText = DreamShaderDeclarationSearch.qualifiedName(declaration)
                )
            }
            .toList()
            .toTypedArray()
    }

    private fun classDeclarations(project: Project, includeNonProjectItems: Boolean): Sequence<DreamShaderDeclaration> {
        return DreamShaderDeclarationSearch.allDeclarations(project, includeNonProjectItems)
            .asSequence()
            .filter { declaration ->
                declaration.keywordText() in DreamShaderLanguageRules.assetDeclarationKeywords
            }
    }
}

class DreamShaderGotoSymbolContributor : ChooseByNameContributor {
    override fun getNames(project: Project, includeNonProjectItems: Boolean): Array<String> {
        return symbolDeclarations(project, includeNonProjectItems)
            .mapNotNull(::declarationName)
            .distinct()
            .sorted()
            .toList()
            .toTypedArray()
    }

    override fun getItemsByName(
        name: String,
        pattern: String,
        project: Project,
        includeNonProjectItems: Boolean
    ): Array<NavigationItem> {
        return symbolDeclarations(project, includeNonProjectItems)
            .filter { declarationName(it) == name }
            .sortedWith(compareBy({ DreamShaderDeclarationSearch.qualifiedName(it) }, { it.containingFile.name }))
            .map { declaration ->
                DreamShaderDeclarationNavigationItem(
                    declaration = declaration,
                    presentableText = DreamShaderDeclarationSearch.qualifiedName(declaration)
                )
            }
            .toList()
            .toTypedArray()
    }

    private fun symbolDeclarations(project: Project, includeNonProjectItems: Boolean): Sequence<DreamShaderDeclaration> {
        return DreamShaderDeclarationSearch.allDeclarations(project, includeNonProjectItems)
            .asSequence()
            .filter { declaration ->
                declaration.keywordText() == "namespace" ||
                    declaration.isFunctionLike() ||
                    DreamShaderDeclarationSearch.isNamespaceMember(declaration)
            }
    }
}

internal class DreamShaderDeclarationNavigationItem(
    internal val declaration: DreamShaderDeclaration,
    private val presentableText: String
) : NavigationItem, ItemPresentation {
    override fun getName(): String? = declaration.declarationName()

    override fun getPresentation(): ItemPresentation = this

    override fun getPresentableText(): String = presentableText

    override fun getLocationString(): String {
        val keyword = declaration.keywordText().orEmpty()
        val keywordDisplay = DreamShaderLanguageRules.displayDeclarationKeyword(keyword)
        return "$keywordDisplay in ${declaration.containingFile.name}"
    }

    override fun getIcon(unused: Boolean): Icon = when {
        declaration.keywordText() == "shader" -> DreamShaderIcons.SHADER
        declaration.isFunctionLike() -> DreamShaderIcons.FUNCTION
        else -> DreamShaderIcons.DECLARATION
    }

    override fun navigate(requestFocus: Boolean) {
        (declaration as? Navigatable)?.navigate(requestFocus)
    }

    override fun canNavigate(): Boolean = (declaration as? Navigatable)?.canNavigate() == true

    override fun canNavigateToSource(): Boolean = (declaration as? Navigatable)?.canNavigateToSource() == true
}

private fun declarationName(declaration: DreamShaderDeclaration): String? {
    return declaration.declarationName()?.takeIf { it.isNotBlank() }
}
