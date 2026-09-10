package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Path
import java.nio.file.Paths

/**
 * DreamShader 导入解析器。
 *
 * DreamShader 1.8 source-root aware import resolver.
 *
 * Unqualified imports stay inside the source root which owns the importing file. A project and
 * every project plugin containing a `DShader` directory are independent roots. Cross-root imports
 * must use `Project:` or `Plugin.<Name>:`/`Plugins.<Name>:`. Every candidate is containment checked,
 * matching the compiler and preventing `..`/absolute-path escapes.
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
        val fs = LocalFileSystem.getInstance()
        val projectBase = normalizePath(projectBasePath)
        val roots = discoverSourceRoots(fs, projectBase, sourceDirectory)
        val qualified = parseRootQualifiedImport(normalized)
        val targetRoot = when (qualified?.qualifier?.lowercase()) {
            null -> null
            "project" -> roots.firstOrNull { it.pluginName == null }
            else -> roots.firstOrNull { root ->
                root.pluginName?.equals(qualified.pluginName, ignoreCase = true) == true
            }
        }
        if (qualified != null) {
            if (targetRoot == null) return null
            return resolveInsideRoot(fs, targetRoot, qualified.path, includeRelative = false, containingDirectory = null)
        }

        val owner = containingDirectory?.let { directory ->
            roots.filter { containsPath(it.sourcePath, directory.path) }
                .maxByOrNull { it.sourcePath.length }
        }
        if (owner != null) {
            resolveInsideRoot(fs, owner, normalized, includeRelative = true, containingDirectory = containingDirectory)
                ?.let { return it }

            val packageRootEntry = resolvePackageRootEntryImport(fs, owner.packagesPath, normalized)
            if (isValidFile(packageRootEntry)) return packageRootEntry
        } else {
            // External/test sources retain a same-directory candidate, then use the project root for
            // source/packages candidates, exactly as the compiler does for a file owned by no root.
            if (containingDirectory != null) {
                buildCandidateRelativePaths(normalized).firstNotNullOfOrNull { candidate ->
                    findContainedRelativeVirtualFile(containingDirectory, containingDirectory.path, candidate)
                }?.let { return it }
            }
            val projectRoot = roots.firstOrNull { it.pluginName == null }
            if (projectRoot != null) {
                resolveInsideRoot(fs, projectRoot, normalized, includeRelative = false, containingDirectory = null)
                    ?.let { return it }
                val packageRootEntry = resolvePackageRootEntryImport(fs, projectRoot.packagesPath, normalized)
                if (isValidFile(packageRootEntry)) return packageRootEntry
            }
            // IDE light fixtures and loose source files historically resolve project-relative
            // imports. Keep that compatibility only for files owned by no DreamShader root and
            // still containment-check it against the project directory.
            buildCandidateRelativePaths(normalized).firstNotNullOfOrNull { candidate ->
                findContainedFile(fs, projectBase, projectBase, candidate)
            }?.let { return it }
        }

        return null
    }

    private data class SourceRoot(val sourcePath: String, val pluginName: String?) {
        val packagesPath: String get() = "$sourcePath/Packages"
    }

    private data class QualifiedImport(val qualifier: String, val pluginName: String?, val path: String)

    private fun discoverSourceRoots(
        fs: LocalFileSystem,
        projectBase: String,
        sourceDirectory: String
    ): List<SourceRoot> {
        val roots = mutableListOf(SourceRoot("$projectBase/$sourceDirectory", null))
        val plugins = fs.findFileByPath("$projectBase/Plugins")
        plugins?.children.orEmpty()
            .asSequence()
            .filter { it.isDirectory && it.findChild("DShader")?.isDirectory == true }
            .forEach { plugin -> roots += SourceRoot("${normalizePath(plugin.path)}/DShader", plugin.name) }
        return roots
    }

    private fun parseRootQualifiedImport(path: String): QualifiedImport? {
        val match = ROOT_QUALIFIER_REGEX.matchEntire(path) ?: return null
        val qualifier = match.groupValues[1]
        val pluginName = match.groupValues[2].ifBlank { null }
        val relative = match.groupValues[3]
        return QualifiedImport(qualifier, pluginName, relative)
    }

    private fun resolveInsideRoot(
        fs: LocalFileSystem,
        root: SourceRoot,
        importPath: String,
        includeRelative: Boolean,
        containingDirectory: VirtualFile?
    ): VirtualFile? {
        val candidates = buildCandidateRelativePaths(importPath)
        if (includeRelative && containingDirectory != null) {
            val containmentRoot = if (containsPath(root.packagesPath, containingDirectory.path)) {
                root.packagesPath
            } else {
                root.sourcePath
            }
            candidates.firstNotNullOfOrNull { candidate ->
                findContainedRelativeVirtualFile(containingDirectory, containmentRoot, candidate)
            }?.let { return it }
        }
        candidates.firstNotNullOfOrNull { candidate ->
            findContainedFile(fs, root.sourcePath, root.sourcePath, candidate)
        }?.let { return it }
        return candidates.firstNotNullOfOrNull { candidate ->
            findContainedFile(fs, root.packagesPath, root.packagesPath, candidate)
        }
    }

    private fun findContainedFile(
        fs: LocalFileSystem,
        containmentRoot: String,
        basePath: String,
        relativePath: String
    ): VirtualFile? {
        if (isAbsolutePath(relativePath)) return null
        val root = runCatching { Path.of(containmentRoot).normalize() }.getOrNull() ?: return null
        val target = runCatching { Path.of(basePath).resolve(relativePath).normalize() }.getOrNull() ?: return null
        if (!target.startsWith(root)) return null
        return fs.findFileByPath(normalizePath(target.toString())).takeIf(::isValidFile)
    }

    private fun findContainedRelativeVirtualFile(
        baseDirectory: VirtualFile,
        containmentRoot: String,
        relativePath: String
    ): VirtualFile? {
        if (isAbsolutePath(relativePath)) return null
        val resolved = findRelativeVirtualFile(baseDirectory, relativePath) ?: return null
        if (!containsPath(containmentRoot, resolved.path)) return null
        return resolved.takeIf(::isValidFile)
    }

    private fun containsPath(rootPath: String, candidatePath: String): Boolean {
        val root = normalizePath(rootPath).trimEnd('/')
        val candidate = normalizePath(candidatePath)
        return candidate.equals(root, ignoreCase = true) ||
            candidate.startsWith("$root/", ignoreCase = true)
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

    private val ROOT_QUALIFIER_REGEX = Regex(
        "^(Project|Plugins?[./]([^:./\\\\]+)):(.+)$",
        RegexOption.IGNORE_CASE
    )
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
