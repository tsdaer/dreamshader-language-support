package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.ide.actions.QualifiedNameProvider
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale

class DreamShaderQualifiedNameProvider : QualifiedNameProvider {
    override fun adjustElementToCopy(element: PsiElement): PsiElement? {
        return when {
            element.language != DreamShaderLanguage -> null
            element is DreamShaderDeclaration || element is DreamShaderSection -> element
            else -> PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
                ?: PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false)
        }
    }

    override fun getQualifiedName(element: PsiElement): String? {
        return when (element) {
            is DreamShaderDeclaration -> qualifiedDeclarationName(element)
            is DreamShaderSection -> {
                val section = displaySectionName(element.sectionName()) ?: return null
                val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
                val declarationName = declaration?.let(::qualifiedDeclarationName) ?: return section
                "$declarationName#$section"
            }
            else -> null
        }
    }

    override fun qualifiedNameToElement(fqn: String, project: Project): PsiElement? {
        val parsed = ParsedQualifiedName.parse(fqn) ?: return null
        val scope = GlobalSearchScope.projectScope(project)
        val psiManager = PsiManager.getInstance(project)
        return FileTypeIndex.getFiles(com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderFileType.INSTANCE, scope)
            .asSequence()
            .mapNotNull { psiManager.findFile(it) }
            .flatMap { PsiTreeUtil.findChildrenOfType(it, DreamShaderDeclaration::class.java).asSequence() }
            .firstNotNullOfOrNull { declaration ->
                if (!qualifiedDeclarationName(declaration).equals(parsed.declarationFqn, ignoreCase = true)) {
                    return@firstNotNullOfOrNull null
                }
                if (parsed.sectionName != null) {
                    PsiTreeUtil.findChildrenOfType(declaration, DreamShaderSection::class.java)
                        .firstOrNull { displaySectionName(it.sectionName()).equals(parsed.sectionName, ignoreCase = true) }
                } else {
                    declaration
                }
            }
    }

    private fun qualifiedDeclarationName(declaration: DreamShaderDeclaration): String? {
        val ownName = declaration.declarationName()?.takeIf { it.isNotBlank() } ?: return null
        val names = mutableListOf(ownName)
        var current = PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true)
        while (current != null) {
            if (current.keywordText().equals("namespace", ignoreCase = true)) {
                current.declarationName()?.takeIf { it.isNotBlank() }?.let { names.add(it) }
            }
            current = PsiTreeUtil.getParentOfType(current, DreamShaderDeclaration::class.java, true)
        }
        names.reverse()
        return names.joinToString("::")
    }

    private fun displaySectionName(section: String?): String? {
        return when (section?.lowercase(Locale.ROOT)) {
            "settings" -> "Settings"
            "options" -> "Options"
            "inputs" -> "Inputs"
            "outputs" -> "Outputs"
            "results" -> "Results"
            "graph" -> "Graph"
            null, "" -> null
            else -> section.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }
        }
    }

    private data class ParsedQualifiedName(
        val declarationFqn: String,
        val sectionName: String?
    ) {
        companion object {
            fun parse(raw: String): ParsedQualifiedName? {
                val trimmed = raw.trim()
                if (trimmed.isBlank()) return null
                val parts = trimmed.split("#", limit = 2)
                val declaration = parts[0].trim().takeIf { it.isNotBlank() } ?: return null
                val section = parts.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
                return ParsedQualifiedName(declaration, section)
            }
        }
    }
}
