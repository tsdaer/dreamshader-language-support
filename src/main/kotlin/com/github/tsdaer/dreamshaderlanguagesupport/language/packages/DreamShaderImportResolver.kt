package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import java.nio.file.Path
import java.nio.file.Paths

/** DreamShader 2.1.1 source-root aware header resolution, shared by both dialects. */
internal object DreamShaderImportResolver {
    private val IMPORT_EXTENSIONS = listOf("dsh", "dss")

    /** Both front ends include headers; extensionless paths always mean .dsh. */
    internal val V2_INCLUDE_EXTENSIONS = listOf("dsh", "dss")
    private const val PACKAGE_METADATA_FILE = "dreamshader.package.json"
    private val DREAMSHADER_OBJECT_REGEX = Regex(
        """"dreamshader"\s*:\s*\{(.*?)\}""",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    )
    private val ENTRY_FIELD_REGEX = Regex(
        """"entry"\s*:\s*"((?:[^"\\]|\\.)*)"""",
        setOf(RegexOption.IGNORE_CASE)
    )

    fun resolveImport(file: PsiFile, importPath: String): VirtualFile? =
        resolveImport(file, importPath, IMPORT_EXTENSIONS)

    fun resolveImport(file: PsiFile, importPath: String, extensions: List<String>): VirtualFile? {
        val containing = file.virtualFile ?: return null
        val projectBase = projectBase(file) ?: return null
        val settings = file.project.getService(DreamShaderProjectSettings::class.java)?.state
        val sourceDir = settings?.sourceDirectory?.trim().orEmpty().ifBlank { "DShader" }.trimStart('/').trimEnd('/')
        return resolveImport(
            projectBasePath = projectBase,
            containingDirectory = containing.parent,
            importPath = importPath,
            sourceDirectory = sourceDir,
            extensions = extensions,
            fileSystem = rootFileSystem(file)
        )
    }

    internal fun resolveImport(
        projectBasePath: String,
        containingDirectory: VirtualFile?,
        importPath: String,
        sourceDirectory: String = "DShader",
        extensions: List<String> = IMPORT_EXTENSIONS,
        fileSystem: com.intellij.openapi.vfs.VirtualFileSystem = LocalFileSystem.getInstance()
    ): VirtualFile? {
        var normalized = importPath.trim().replace('\\', '/')
        while (normalized.startsWith("./")) normalized = normalized.removePrefix("./")
        if (normalized.isBlank() || parseRootQualifiedImport(normalized) != null) return null
        if (!normalized.substringAfterLast('/').contains('.')) normalized += ".dsh"
        val fs = fileSystem
        val projectBase = normalizePath(projectBasePath)
        val roots = discoverSourceRoots(fs, projectBase, sourceDirectory)
        val owner = containingDirectory?.let { directory ->
            roots.filter { containsPath(it.sourcePath, directory.path) }.maxByOrNull { it.sourcePath.length }
        }
        if (isAbsolutePath(normalized)) {
            fs.findFileByPath(normalized)?.takeIf { isValidFile(it, extensions) }?.let { return it }
        }
        if (normalized.startsWith("/")) {
            val relative = if (normalized.startsWith("/Game/", ignoreCase = true)) normalized.substring(6)
                else normalized.trimStart('/')
            return (listOfNotNull(owner) + roots).distinct().firstNotNullOfOrNull { root ->
                resolveInsideRoot(fs, root, relative, false, null, extensions)
            }
        }
        val targetRoot = owner ?: roots.first()
        if (containingDirectory != null) {
            val containment = roots.flatMap { listOf(it.sourcePath, it.packagesPath) }
                .filter { containsPath(it, containingDirectory.path) }.maxByOrNull { it.length }
                ?: containingDirectory.path
            findContainedRelativeVirtualFile(containingDirectory, containment, normalized, extensions)?.let { return it }
        }
        return resolveInsideRoot(fs, targetRoot, normalized, false, null, extensions)
    }

    internal fun candidatePaths(file: PsiFile, target: VirtualFile): List<String> {
        val base = projectBase(file) ?: return emptyList()
        val roots = discoverSourceRoots(rootFileSystem(file), normalizePath(base),
            file.project.getService(DreamShaderProjectSettings::class.java)?.state?.sourceDirectory?.ifBlank { "DShader" } ?: "DShader")
        val candidates = linkedSetOf<String>()
        file.virtualFile?.parent?.let { directory ->
            runCatching { Path.of(directory.path).relativize(Path.of(target.path)).toString().replace('\\', '/') }.getOrNull()?.let { candidates += it }
        }
        for (root in roots) {
            for (directory in listOf(root.sourcePath, root.packagesPath)) {
                if (containsPath(directory, target.path)) {
                    val relative = target.path.substring(directory.length).trimStart('/')
                    candidates += relative
                    candidates += "/$relative"
                }
            }
        }
        return candidates.filter { resolveImport(file, it) == target }
    }

    internal fun sourceRoot(file: PsiFile): String? {
        val settings = file.project.getService(DreamShaderProjectSettings::class.java)?.state
        val base = projectBase(file) ?: return null
        val roots = discoverSourceRoots(rootFileSystem(file), normalizePath(base), settings?.sourceDirectory?.ifBlank { "DShader" } ?: "DShader")
        return roots.filter { containsPath(it.sourcePath, file.virtualFile?.path.orEmpty()) }.maxByOrNull { it.sourcePath.length }?.sourcePath
            ?: roots.first().sourcePath
    }

    private fun virtualProjectRoot(file: PsiFile): VirtualFile? {
        val vf = file.virtualFile ?: return null
        if (vf.parent == null || vf.fileSystem.protocol == "file") return null
        val root = com.intellij.openapi.roots.ProjectFileIndex.getInstance(file.project).getContentRootForFile(vf) ?: return null
        val source = file.project.getService(DreamShaderProjectSettings::class.java)?.state?.sourceDirectory?.ifBlank { "DShader" } ?: "DShader"
        return root.takeIf { containsPath("${root.path}/$source", vf.path) || containsPath("${root.path}/Plugins", vf.path) }
    }
    private fun projectBase(file: PsiFile): String? = virtualProjectRoot(file)?.path ?: file.project.basePath
    private fun rootFileSystem(file: PsiFile): com.intellij.openapi.vfs.VirtualFileSystem =
        virtualProjectRoot(file)?.fileSystem ?: LocalFileSystem.getInstance()

    private data class SourceRoot(val sourcePath: String, val pluginName: String?) {
        val packagesPath: String get() = "$sourcePath/Packages"
    }

    private data class QualifiedImport(val qualifier: String, val pluginName: String?, val path: String)

    private fun discoverSourceRoots(
        fs: com.intellij.openapi.vfs.VirtualFileSystem,
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
        fs: com.intellij.openapi.vfs.VirtualFileSystem,
        root: SourceRoot,
        importPath: String,
        includeRelative: Boolean,
        containingDirectory: VirtualFile?,
        extensions: List<String> = IMPORT_EXTENSIONS
    ): VirtualFile? {
        val candidates = buildCandidateRelativePaths(importPath, extensions)
        if (includeRelative && containingDirectory != null) {
            val containmentRoot = if (containsPath(root.packagesPath, containingDirectory.path)) {
                root.packagesPath
            } else {
                root.sourcePath
            }
            candidates.firstNotNullOfOrNull { candidate ->
                findContainedRelativeVirtualFile(containingDirectory, containmentRoot, candidate, extensions)
            }?.let { return it }
        }
        candidates.firstNotNullOfOrNull { candidate ->
            findContainedFile(fs, root.sourcePath, root.sourcePath, candidate, extensions)
        }?.let { return it }
        return candidates.firstNotNullOfOrNull { candidate ->
            findContainedFile(fs, root.packagesPath, root.packagesPath, candidate, extensions)
        }
    }

    private fun findContainedFile(
        fs: com.intellij.openapi.vfs.VirtualFileSystem,
        containmentRoot: String,
        basePath: String,
        relativePath: String,
        extensions: List<String> = IMPORT_EXTENSIONS
    ): VirtualFile? {
        if (isAbsolutePath(relativePath)) return null
        val root = runCatching { Path.of(containmentRoot).normalize() }.getOrNull() ?: return null
        val target = runCatching { Path.of(basePath).resolve(relativePath).normalize() }.getOrNull() ?: return null
        if (!containsPath(root.toString(), target.toString())) return null
        return fs.findFileByPath(normalizePath(target.toString())).takeIf { isValidFile(it, extensions) }
    }

    private fun findContainedRelativeVirtualFile(
        baseDirectory: VirtualFile,
        containmentRoot: String,
        relativePath: String,
        extensions: List<String> = IMPORT_EXTENSIONS
    ): VirtualFile? {
        if (isAbsolutePath(relativePath)) return null
        val resolved = findRelativeVirtualFile(baseDirectory, relativePath) ?: return null
        if (!containsPath(containmentRoot, resolved.path)) return null
        return resolved.takeIf { isValidFile(it, extensions) }
    }

    private fun containsPath(rootPath: String, candidatePath: String): Boolean {
        val root = normalizePath(rootPath).trimEnd('/')
        val candidate = normalizePath(candidatePath)
        return candidate.equals(root, ignoreCase = true) ||
            candidate.startsWith("$root/", ignoreCase = true)
    }

    private fun buildCandidateRelativePaths(
        normalizedPath: String,
        extensions: List<String> = IMPORT_EXTENSIONS
    ): List<String> {
        return listOf(if (normalizedPath.substringAfterLast('/').contains('.')) normalizedPath else "$normalizedPath.dsh")
    }

    private fun isAbsolutePath(path: String): Boolean {
        return runCatching { Paths.get(path).isAbsolute }.getOrDefault(false)
    }

    private fun isValidFile(file: VirtualFile?, extensions: List<String> = IMPORT_EXTENSIONS): Boolean {
        if (file == null || !file.isValid || file.isDirectory) return false
        val ext = file.extension?.lowercase() ?: return false
        return ext in extensions
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
        fs: com.intellij.openapi.vfs.VirtualFileSystem,
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
        fs: com.intellij.openapi.vfs.VirtualFileSystem,
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
