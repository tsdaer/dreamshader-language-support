package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderFileType
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

internal object DreamShaderDeclarationSearch {
    fun allDeclarations(project: Project, includeNonProjectItems: Boolean): List<DreamShaderDeclaration> {
        val scope = if (includeNonProjectItems) {
            GlobalSearchScope.allScope(project)
        } else {
            GlobalSearchScope.projectScope(project)
        }
        val psiManager = PsiManager.getInstance(project)

        return FileTypeIndex.getFiles(DreamShaderFileType.INSTANCE, scope)
            .asSequence()
            .mapNotNull(psiManager::findFile)
            .filter { it.language == DreamShaderLanguage }
            .flatMap { file ->
                PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java).asSequence()
            }
            .toList()
    }

    fun parentDeclaration(declaration: DreamShaderDeclaration): DreamShaderDeclaration? {
        return PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true)
    }

    fun isNamespaceMember(declaration: DreamShaderDeclaration): Boolean {
        return parentDeclaration(declaration)?.keywordText() == "namespace"
    }

    fun namespacePath(declaration: DreamShaderDeclaration): List<String> {
        val path = mutableListOf<String>()
        var current = parentDeclaration(declaration)
        while (current != null) {
            if (current.keywordText() == "namespace") {
                current.declarationName()?.takeIf { it.isNotBlank() }?.let(path::add)
            }
            current = parentDeclaration(current)
        }
        path.reverse()
        return path
    }

    fun qualifiedName(declaration: DreamShaderDeclaration): String {
        val name = declaration.declarationName().orEmpty().ifBlank { "<anonymous>" }
        val namespacePath = namespacePath(declaration)
        if (namespacePath.isEmpty()) return name
        return namespacePath.joinToString("::", postfix = "::") + name
    }
}
