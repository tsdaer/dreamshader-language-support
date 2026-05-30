package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.util.ArrayDeque

/**
 * Resolves recursive import closure for a DreamShader source file.
 *
 * Closure direction is import-only:
 * seed file -> imported files -> recursively imported files.
 */
internal object DreamShaderImportClosureResolver {
    fun resolveImportClosure(seedFile: PsiFile): List<PsiFile> {
        val project = seedFile.project
        val projectBasePath = project.basePath ?: return listOf(seedFile)
        val psiManager = PsiManager.getInstance(project)
        val visited = linkedSetOf<String>()
        val ordered = mutableListOf<PsiFile>()
        val queue = ArrayDeque<PsiFile>()

        queue.add(seedFile)
        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentVf = current.virtualFile ?: continue
            val currentPath = currentVf.path
            if (!visited.add(currentPath)) continue

            ordered.add(current)

            val containingDirectory = currentVf.parent
            for (importPath in collectImportPaths(current)) {
                val targetVf = DreamShaderImportResolver.resolveImport(
                    projectBasePath = projectBasePath,
                    containingDirectory = containingDirectory,
                    importPath = importPath
                ) ?: continue
                val targetPsi = psiManager.findFile(targetVf) ?: continue
                if (targetPsi.language != DreamShaderLanguage) continue

                if (!visited.contains(targetVf.path)) {
                    queue.addLast(targetPsi)
                }
            }
        }

        return ordered
    }

    private fun collectImportPaths(file: PsiFile): List<String> {
        val paths = linkedSetOf<String>()
        val stringLiterals = PsiTreeUtil.collectElements(file) { element ->
            element.node?.elementType == DreamShaderTokenTypes.STRING
        }
        for (literal in stringLiterals) {
            if (!isImportStringLiteral(literal)) continue
            val raw = literal.text.trim()
            if (raw.length < 2 || !raw.startsWith('"') || !raw.endsWith('"')) continue
            val importPath = raw.substring(1, raw.length - 1).trim()
            if (importPath.isBlank()) continue
            paths.add(importPath)
        }
        return paths.toList()
    }

    private fun isImportStringLiteral(element: com.intellij.psi.PsiElement): Boolean {
        var prev = PsiTreeUtil.prevLeaf(element, true)
        while (prev != null) {
            val tokenType = prev.node?.elementType
            if (tokenType == DreamShaderTokenTypes.WHITE_SPACE ||
                tokenType == DreamShaderTokenTypes.LINE_COMMENT ||
                tokenType == DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                prev = PsiTreeUtil.prevLeaf(prev, true)
                continue
            }
            return tokenType == DreamShaderTokenTypes.KEYWORD && prev.text.equals("import", ignoreCase = true)
        }
        return false
    }
}
