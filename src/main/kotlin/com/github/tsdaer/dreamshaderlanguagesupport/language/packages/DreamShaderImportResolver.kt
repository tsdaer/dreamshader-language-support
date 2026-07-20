package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Paths

/**
 * DreamShader 导入解析器。
 *
 * 解析顺序：
 * 1. 当前文件相对路径
 * 2. `<project>/DShader`
 * 3. `<project>/DShader/Packages`
 * 4. `<project>`（历史兼容回退）
 */
internal object DreamShaderImportResolver {
    private val IMPORT_EXTENSIONS = listOf("dsh", "dsf", "dsm")
    private const val PACKAGE_METADATA_FILE = "dreamshader.package.json"
    private val DREAMSHADER_OBJECT_REGEX = Regex(
        """"dreamshader"\s*:\s*\{(.*?)\}""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ENTRY_FIELD_REGEX = Regex(
        """"entry"\s*:\s*"((?:[^"\\]|\\.)*)"""",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun resolveImport(file: PsiFile, importPath: String): VirtualFile? {
        val containing = file.virtualFile ?: return null
        val projectBase = file.project.basePath ?: return null
        val settings = file.project.getService(DreamShaderProjectSettings::class.java)?.state
        val sourceDir = settings?.sourceDirectory?.trim().orEmpty().ifBlank { "DShader" }.trimStart('/').trimEnd('/')
        return resolveImport(
            projectBasePath = projectBase,
            containingDirectory = containing.parent,
            importPath = importPath,
            sourceDirectory = sourceDir
        )
    }

    internal fun resolveImport(
        projectBasePath: String,
        containingDirectory: VirtualFile?,
        importPath: String,
        sourceDirectory: String = "DShader"
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
        val dshaderRoot = "$projectBase/$sourceDirectory"
        val packagesRoot = "$dshaderRoot/Packages"

        candidatePaths.forEach { candidate ->
            val fromDShader = fs.findFileByPath("$dshaderRoot/$candidate")
            if (isValidFile(fromDShader)) return fromDShader
        }

        candidatePaths.forEach { candidate ->
            val fromPackages = fs.findFileByPath("$packagesRoot/$candidate")
            if (isValidFile(fromPackages)) return fromPackages
        }

        val packageRootEntry = resolvePackageRootEntryImport(fs, packagesRoot, normalized)
        if (isValidFile(packageRootEntry)) return packageRootEntry

        candidatePaths.forEach { candidate ->
            val fromProjectRoot = fs.findFileByPath("$projectBase/$candidate")
            if (isValidFile(fromProjectRoot)) return fromProjectRoot
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

    private fun resolvePackageRootEntryImport(
        fs: LocalFileSystem,
        packagesRoot: String,
        normalizedImportPath: String
    ): VirtualFile? {
        val packageSegments = parsePackageRootSegments(normalizedImportPath) ?: return null
        val packageDir = packageDir(fs, packagesRoot, packageSegments)
        if (packageDir == null || !packageDir.isValid || !packageDir.isDirectory) return null

        val entryCandidates = buildPackageEntryCandidates(packageDir, packageSegments.last())
        entryCandidates.forEach { candidate ->
            val resolved = findRelativeVirtualFile(packageDir, candidate)
            if (isValidFile(resolved)) return resolved
        }
        return null
    }

    private fun parsePackageRootSegments(normalizedImportPath: String): List<String>? {
        val segments = normalizedImportPath.replace('\\', '/')
            .split('/')
            .filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        return if (segments.first().startsWith("@")) {
            if (segments.size == 2) segments else null
        } else {
            if (segments.size == 1) segments else null
        }
    }

    private fun buildPackageEntryCandidates(packageDir: VirtualFile, packageLeafName: String): List<String> {
        val candidates = linkedSetOf<String>()
        val manifestEntry = readPackageManifestEntry(packageDir)
        val safeManifestEntry = sanitizePackageRelativePath(manifestEntry)
        if (!safeManifestEntry.isNullOrBlank()) {
            buildCandidateRelativePaths(safeManifestEntry).forEach { candidates.add(it) }
        }
        buildCandidateRelativePaths("Library/${packageLeafName}Lib").forEach { candidates.add(it) }
        buildCandidateRelativePaths("Library/Main").forEach { candidates.add(it) }
        buildCandidateRelativePaths("Library/index").forEach { candidates.add(it) }
        buildCandidateRelativePaths("index").forEach { candidates.add(it) }
        return candidates.toList()
    }

    internal fun analyzePackageRootImport(
        projectBasePath: String,
        importPath: String
    ): DreamShaderPackageRootImportAnalysis? {
        val normalizedImport = importPath.trim().replace('\\', '/')
        val packageSegments = parsePackageRootSegments(normalizedImport) ?: return null
        val fs = LocalFileSystem.getInstance()
        val packagesRoot = "${normalizePath(projectBasePath)}/DShader/Packages"
        val packageDir = packageDir(fs, packagesRoot, packageSegments)
        if (packageDir == null || !packageDir.isValid || !packageDir.isDirectory) return null

        val manifestEntryRaw = readPackageManifestEntry(packageDir)
        val manifestEntrySafe = sanitizePackageRelativePath(manifestEntryRaw)
        val suggestedEntryRelativePath = manifestEntrySafe ?: "Library/${packageSegments.last()}Lib.dsh"
        val entryCandidates = buildPackageEntryCandidates(packageDir, packageSegments.last())
        val resolved = entryCandidates.firstNotNullOfOrNull { candidate ->
            val vf = findRelativeVirtualFile(packageDir, candidate)
            if (isValidFile(vf)) candidate else null
        }
        return DreamShaderPackageRootImportAnalysis(
            packageImportPath = packageSegments.joinToString("/"),
            packageExists = true,
            manifestEntryRaw = manifestEntryRaw,
            manifestEntryValid = manifestEntrySafe != null || manifestEntryRaw == null,
            resolvedEntryRelativePath = resolved,
            suggestedEntryRelativePath = suggestedEntryRelativePath
        )
    }

    private fun readPackageManifestEntry(packageDir: VirtualFile): String? {
        val metadata = packageDir.findChild(PACKAGE_METADATA_FILE) ?: return null
        if (!metadata.isValid || metadata.isDirectory) return null
        val content = runCatching { String(metadata.contentsToByteArray(), Charsets.UTF_8) }.getOrNull() ?: return null

        val dreamshaderBlock = DREAMSHADER_OBJECT_REGEX.find(content)?.groupValues?.getOrNull(1)
        val nestedEntry = dreamshaderBlock?.let { block ->
            ENTRY_FIELD_REGEX.find(block)?.groupValues?.getOrNull(1)
        }
        val topLevelEntry = ENTRY_FIELD_REGEX.find(content)?.groupValues?.getOrNull(1)
        val entry = nestedEntry ?: topLevelEntry ?: return null
        return unescapeJsonString(entry)
            .replace('\\', '/')
            .trim()
            .trimStart('/')
    }

    private fun sanitizePackageRelativePath(path: String?): String? {
        val normalized = path?.trim()?.replace('\\', '/')?.trimStart('/') ?: return null
        if (normalized.isBlank()) return null
        if (normalized.contains("://")) return null
        if (normalized.startsWith("/") || normalized.startsWith("\\")) return null
        if (runCatching { Paths.get(normalized).isAbsolute }.getOrDefault(false)) return null
        if (normalized.contains(':')) return null

        val segments = normalized.split('/').filter { it.isNotBlank() }
        if (segments.isEmpty()) return null
        if (segments.any { it == "." || it == ".." }) return null

        val last = segments.last()
        if (last.contains('.')) {
            val ext = last.substringAfterLast('.', "").lowercase()
            if (ext !in IMPORT_EXTENSIONS) return null
            return segments.joinToString("/")
        }
        return "${segments.joinToString("/")}.dsh"
    }

    private fun packageDir(
        fs: LocalFileSystem,
        packagesRoot: String,
        packageSegments: List<String>
    ): VirtualFile? {
        val packagePath = buildString {
            append(packagesRoot)
            packageSegments.forEach { segment ->
                append('/')
                append(segment)
            }
        }
        return fs.findFileByPath(packagePath)
    }

    private fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }
}

/**
 * 包根导入分析结果。
 *
 * 用于语义诊断区分“包不存在”“入口缺失”“入口非法”等场景。
 */
internal data class DreamShaderPackageRootImportAnalysis(
    val packageImportPath: String,
    val packageExists: Boolean,
    val manifestEntryRaw: String?,
    val manifestEntryValid: Boolean,
    val resolvedEntryRelativePath: String?,
    val suggestedEntryRelativePath: String
)
