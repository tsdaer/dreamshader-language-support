package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiUtil
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope

internal object DreamShaderDeclarationSearch {
    fun allDeclarations(project: Project, includeNonProjectItems: Boolean): List<DreamShaderDeclaration> {
        val scope = if (includeNonProjectItems) {
            GlobalSearchScope.allScope(project)
        } else {
            GlobalSearchScope.projectScope(project)
        }
        return DreamShaderPsiUtil.allDeclarations(project, scope)
    }

    fun parentDeclaration(declaration: DreamShaderDeclaration): DreamShaderDeclaration? {
        return DreamShaderPsiUtil.parentDeclaration(declaration)
    }

    fun isNamespaceMember(declaration: DreamShaderDeclaration): Boolean {
        return parentDeclaration(declaration)?.keywordText() == "namespace"
    }

    fun namespacePath(declaration: DreamShaderDeclaration): List<String> {
        return DreamShaderPsiUtil.enclosingNamespacePath(declaration)
    }

    fun qualifiedName(declaration: DreamShaderDeclaration): String {
        return DreamShaderPsiUtil.qualifiedDeclarationName(declaration) ?: "<anonymous>"
    }
}
