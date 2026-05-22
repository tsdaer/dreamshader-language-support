package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Paths

/**
 * Unified import resolver for DreamShader files.
 *
 * Resolution order:
 * 1. current file relative path
 * 2. `<project>/DShader`
 * 3. `<project>/DShader/Packages`
 * 4. `<project>` (legacy compatibility fallback)
 * 5. built-in library roots under project (best-effort fallback)
 */
internal object DreamShaderImportResolver {
    private val IMPORT_EXTENSIONS = listOf("dsh", "dsf", "dsm")
    private const val BUILTIN_LIBRARY_RELATIVE_PATH = "Plugins/DreamShader/Library"

    fun resolveImport(file: PsiFile, importPath: String): VirtualFile? {
        val containing = file.virtualFile ?: return null
        val projectBase = file.project.basePath ?: return null
        return resolveImport(
            projectBasePath = projectBase,
            containingDirectory = containing.parent,
            importPath = importPath
        )
    }

    internal fun resolveImport(
        projectBasePath: String,
        containingDirectory: VirtualFile?,
        importPath: String
    ): VirtualFile? {
        val normalized = importPath.trim().replace('\\', '/')
        if (normalized.isBlank()) return null
        val candidatePaths = buildCandidateRelativePaths(normalized)
        val fs = LocalFileSystem.getInstance()

        candidatePaths.forEach { candidate ->
            if (isAbsolutePath(candidate)) {
                val direct = fs.findFileByPath(candidate)
                if (isValidFile(direct)) return direct
            }
        }

        if (containingDirectory != null) {
            candidatePaths.forEach { candidate ->
                val resolved = findRelativeVirtualFile(containingDirectory, candidate)
                if (isValidFile(resolved)) return resolved
            }
        }

        val projectBase = normalizePath(projectBasePath)
        val dshaderRoot = "$projectBase/DShader"
        val packagesRoot = "$dshaderRoot/Packages"

        candidatePaths.forEach { candidate ->
            val fromDShader = fs.findFileByPath("$dshaderRoot/$candidate")
            if (isValidFile(fromDShader)) return fromDShader
        }

        candidatePaths.forEach { candidate ->
            val fromPackages = fs.findFileByPath("$packagesRoot/$candidate")
            if (isValidFile(fromPackages)) return fromPackages
        }

        candidatePaths.forEach { candidate ->
            val fromProjectRoot = fs.findFileByPath("$projectBase/$candidate")
            if (isValidFile(fromProjectRoot)) return fromProjectRoot
        }

        candidatePaths.forEach { candidate ->
            val fromBuiltin = fs.findFileByPath("$projectBase/$BUILTIN_LIBRARY_RELATIVE_PATH/$candidate")
            if (isValidFile(fromBuiltin)) return fromBuiltin
        }

        return null
    }

    private fun buildCandidateRelativePaths(normalizedPath: String): List<String> {
        val candidates = linkedSetOf<String>()
        candidates.add(normalizedPath)
        if (!normalizedPath.substringAfterLast('/', "").contains('.')) {
            IMPORT_EXTENSIONS.forEach { ext -> candidates.add("$normalizedPath.$ext") }
        }
        return candidates.toList()
    }

    private fun isAbsolutePath(path: String): Boolean {
        return runCatching { Paths.get(path).isAbsolute }.getOrDefault(false)
    }

    private fun isValidFile(file: VirtualFile?): Boolean {
        if (file == null || !file.isValid || file.isDirectory) return false
        val ext = file.extension?.lowercase() ?: return false
        return ext in IMPORT_EXTENSIONS
    }

    private fun findRelativeVirtualFile(baseDir: VirtualFile, relativePath: String): VirtualFile? {
        var current: VirtualFile? = baseDir
        val parts = relativePath.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() && it != "." }
        for (part in parts) {
            current = when (part) {
                ".." -> current?.parent
                else -> current?.findChild(part)
            }
            if (current == null) return null
        }
        return current
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }
}
