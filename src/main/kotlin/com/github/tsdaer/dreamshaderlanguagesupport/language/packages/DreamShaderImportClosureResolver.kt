package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
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
        invalidateConfiguration(seedFile)
        return CachedValuesManager.getManager(seedFile.project).getCachedValue(
            seedFile,
            IMPORT_CLOSURE_KEY,
            {
                CachedValueProvider.Result.create(
                    computeImportClosure(seedFile),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
                )
            },
            false
        )
    }

    fun resolveDirectImports(sourceFile: PsiFile): List<PsiFile> {
        invalidateConfiguration(sourceFile)
        return CachedValuesManager.getManager(sourceFile.project).getCachedValue(
            sourceFile,
            DIRECT_IMPORTS_KEY,
            {
                CachedValueProvider.Result.create(
                    computeDirectImports(sourceFile),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
                )
            },
            false
        )
    }

    private fun computeImportClosure(seedFile: PsiFile): List<PsiFile> {
        val visited = linkedSetOf<String>()
        val ordered = mutableListOf<PsiFile>()
        val queue = ArrayDeque<PsiFile>()

        queue.add(seedFile)
        while (queue.isNotEmpty()) {
            com.intellij.openapi.progress.ProgressManager.checkCanceled()
            val current = queue.removeFirst()
            val currentVf = current.virtualFile ?: continue
            val currentPath = currentVf.path
            if (!visited.add(currentPath)) continue

            ordered.add(current)

            for (targetPsi in resolveDirectImports(current)) {
                val targetPath = targetPsi.virtualFile?.path ?: continue
                if (!visited.contains(targetPath)) {
                    queue.addLast(targetPsi)
                }
            }
        }

        return ordered
    }

    private fun computeDirectImports(sourceFile: PsiFile): List<PsiFile> {
        val project = sourceFile.project
        val psiManager = PsiManager.getInstance(project)
        if (sourceFile.virtualFile == null) return emptyList()
        val resolved = linkedMapOf<String, PsiFile>()
        for (importPath in collectImportPaths(sourceFile)) {
            val targetVf = DreamShaderImportResolver.resolveImport(sourceFile, importPath) ?: continue
            val targetPsi = psiManager.findFile(targetVf) ?: continue
            if (targetPsi.language != DreamShaderLanguage) continue

            resolved.putIfAbsent(targetVf.path, targetPsi)
        }
        return resolved.values.toList()
    }

    private fun collectImportPaths(file: PsiFile): List<String> {
        val paths = linkedSetOf<String>()
        val stringLiterals = PsiTreeUtil.collectElements(file) { element ->
            element.node?.elementType == DreamShaderTokenTypes.STRING
        }
        for (literal in stringLiterals) {
            if (!isImportStringLiteralToken(literal)) continue
            if ((file as? com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile)?.let {
                    com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderPreprocessorModel.of(it).stateAt(literal.textOffset) ==
                        com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderPreprocessorModel.State.INACTIVE
                } == true) continue
            val raw = literal.text.trim()
            if (raw.length < 2 || !raw.startsWith('"') || !raw.endsWith('"')) continue
            val importPath = raw.substring(1, raw.length - 1).trim()
            if (importPath.isBlank()) continue
            paths.add(importPath)
        }
        return paths.toList()
    }

    fun isImportStringLiteralToken(element: PsiElement): Boolean {
        if (PsiTreeUtil.getParentOfType(element, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective::class.java) != null) return true
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

    private val CONFIG_KEY = Key.create<String>("dreamshader.import.config")
    private fun invalidateConfiguration(file: PsiFile) {
        val configuration = file.project.getService(com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java).state.sourceDirectory
        if (file.getUserData(CONFIG_KEY) != configuration) {
            file.putUserData(DIRECT_IMPORTS_KEY, null)
            file.putUserData(IMPORT_CLOSURE_KEY, null)
            file.putUserData(CONFIG_KEY, configuration)
        }
    }

    private val DIRECT_IMPORTS_KEY: Key<CachedValue<List<PsiFile>>> =
        Key.create("dreamshader.import.direct.files")

    private val IMPORT_CLOSURE_KEY: Key<CachedValue<List<PsiFile>>> =
        Key.create("dreamshader.import.closure.files")
}
