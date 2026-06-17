package com.github.tsdaer.dreamshaderlanguagesupport.language.psi

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderFileType
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

object DreamShaderPsiUtil {
    fun dreamShaderFiles(project: Project, scope: GlobalSearchScope): Sequence<PsiFile> {
        val psiManager = PsiManager.getInstance(project)
        return FileTypeIndex.getFiles(DreamShaderFileType.INSTANCE, scope)
            .asSequence()
            .mapNotNull(psiManager::findFile)
            .filter { it.language == DreamShaderLanguage }
    }

    fun allDeclarations(project: Project, scope: GlobalSearchScope): List<DreamShaderDeclaration> {
        return dreamShaderFiles(project, scope)
            .flatMap { file -> declarationsIn(file).asSequence() }
            .toList()
    }

    fun declarationsIn(file: PsiElement): List<DreamShaderDeclaration> {
        return PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java).toList()
    }

    fun topLevelDeclarations(file: PsiElement): List<DreamShaderDeclaration> {
        return declarationsIn(file)
            .filter { declaration -> parentDeclaration(declaration) == null }
            .toList()
    }

    fun parentDeclaration(declaration: DreamShaderDeclaration): DreamShaderDeclaration? {
        return PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true)
    }

    fun containingDeclaration(element: PsiElement): DreamShaderDeclaration? {
        return PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
    }

    fun directChildDeclarations(parent: DreamShaderDeclaration): List<DreamShaderDeclaration> {
        return PsiTreeUtil.findChildrenOfType(parent, DreamShaderDeclaration::class.java)
            .filter { declaration ->
                declaration != parent && parentDeclaration(declaration) == parent
            }
            .toList()
    }

    fun directSectionsOf(declaration: DreamShaderDeclaration): List<DreamShaderSection> {
        return PsiTreeUtil.findChildrenOfType(declaration, DreamShaderSection::class.java)
            .filter { section ->
                PsiTreeUtil.getParentOfType(section, DreamShaderSection::class.java, true) == null &&
                    PsiTreeUtil.getParentOfType(section, DreamShaderDeclaration::class.java, true) == declaration
            }
            .toList()
    }

    fun enclosingNamespacePath(declaration: DreamShaderDeclaration): List<String> {
        val path = mutableListOf<String>()
        var current = parentDeclaration(declaration)
        while (current != null) {
            appendNamespaceName(current, path)
            current = parentDeclaration(current)
        }
        path.reverse()
        return path
    }

    fun enclosingNamespacePathAt(element: PsiElement): List<String> {
        val path = mutableListOf<String>()
        var current = containingDeclaration(element)
        if (current == element) {
            current = parentDeclaration(current)
        }
        while (current != null) {
            appendNamespaceName(current, path)
            current = parentDeclaration(current)
        }
        path.reverse()
        return path
    }

    fun qualifiedDeclarationName(declaration: DreamShaderDeclaration): String? {
        val ownName = declaration.declarationName()?.takeIf { it.isNotBlank() } ?: return null
        val namespacePath = enclosingNamespacePath(declaration)
        if (namespacePath.isEmpty()) return ownName
        return namespacePath.joinToString("::", postfix = "::") + ownName
    }

    fun precedingDocumentationComment(element: PsiElement): String? {
        val lines = mutableListOf<String>()
        var sawComment = false
        var leaf = PsiTreeUtil.prevLeaf(element, true)
        while (leaf != null) {
            when (leaf.node?.elementType) {
                DreamShaderTokenTypes.WHITE_SPACE -> {
                    if (sawComment && leaf.text.count { it == '\n' } > 1) break
                }
                DreamShaderTokenTypes.LINE_COMMENT,
                DreamShaderTokenTypes.BLOCK_COMMENT -> {
                    normalizeCommentText(leaf.text)?.let(lines::add)
                    sawComment = true
                }
                else -> break
            }
            leaf = PsiTreeUtil.prevLeaf(leaf, true)
        }

        return lines.asReversed()
            .joinToString("\n")
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun appendNamespaceName(declaration: DreamShaderDeclaration, path: MutableList<String>) {
        if (!declaration.keywordText().equals("namespace", ignoreCase = true)) return
        declaration.declarationName()?.takeIf { it.isNotBlank() }?.let(path::add)
    }

    private fun normalizeCommentText(text: String): String? {
        val normalized = when {
            text.trimStart().startsWith("//") -> text.trim().replace(Regex("^//+\\s?"), "")
            text.trimStart().startsWith("/*") -> text.trim()
                .replace(Regex("^/\\*+\\s?"), "")
                .replace(Regex("\\s?\\*+/$"), "")
                .lines()
                .joinToString("\n") { line -> line.trim().replace(Regex("^\\*\\s?"), "") }
            else -> text.trim()
        }.trim()
        return normalized.takeIf { it.isNotBlank() }
    }
}
