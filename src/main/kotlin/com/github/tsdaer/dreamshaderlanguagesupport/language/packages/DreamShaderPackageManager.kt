package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.intellij.openapi.project.Project
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.absolutePathString
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.streams.asSequence

/** `dreamshader.package.json` 的最小反序列化 DTO。 */
@Serializable
private data class PackageMetadataDto(
    val name: String? = null,
    val version: String? = null,
    val repository: String? = null
)

/** `dreamshader.lock.json` 根：`{ "packages": [ ... ] }`。 */
@Serializable
private data class PackageLockFileDto(
    val packages: List<PackageLockEntryDto> = emptyList()
)

/** lock 文件中的单个包记录 DTO。 */
@Serializable
private data class PackageLockEntryDto(
    val name: String? = null,
    val version: String? = null,
    val repository: String? = null,
    val commit: String? = null,
    val installPath: String? = null
)

/**
 * 包生命周期管理器。
 *
 * 负责安装、更新、移除、打开包目录，以及 lock 文件读写与维护。
 */
internal class DreamShaderPackageManager(
    private val project: Project,
    private val gitClient: DreamShaderGitClient = DreamShaderProcessGitClient()
) {
    fun isGitAvailable(): Boolean = gitClient.isAvailable()

    fun installFromGitHub(input: String): DreamShaderPackageOperationResult {
        val repository = normalizeGitHubRepositoryInput(input)
            ?: return DreamShaderPackageOperationResult(
                false,
                DreamShaderBundle.message("package.manager.invalidGithubRepo", input)
            )
        return installFromRepository(repository)
    }

    fun installFromRepository(repository: String): DreamShaderPackageOperationResult {
        if (!gitClient.isAvailable()) {
            return DreamShaderPackageOperationResult(false, DreamShaderBundle.message("package.manager.gitRequired"))
        }

        val tempRoot = Files.createTempDirectory("dreamshader-package-install-")
        val checkoutDir = tempRoot.resolve("repo")
        try {
            val cloneResult = gitClient.clone(repository, checkoutDir)
            if (!cloneResult.success) {
                return DreamShaderPackageOperationResult(
                    false,
                    cloneResult.message ?: DreamShaderBundle.message("package.manager.cloneFailed")
                )
            }

            val metadataFile = checkoutDir.resolve(PACKAGE_METADATA_FILE)
            if (!metadataFile.exists() || !metadataFile.isRegularFile()) {
                return DreamShaderPackageOperationResult(false, DreamShaderBundle.message("package.manager.metadataMissing"))
            }
            val metadata = parsePackageMetadata(metadataFile)
                ?: return DreamShaderPackageOperationResult(false, DreamShaderBundle.message("package.manager.metadataNameMissing"))

            val packageRoot = packageDirectoryForName(metadata.name)
            val packagesRoot = ensurePackagesRoot()
            ensurePathUnder(packagesRoot, packageRoot)

            if (packageRoot.exists()) {
                deleteDirectoryRecursively(packageRoot, packagesRoot)
            } else {
                Files.createDirectories(packageRoot.parent)
            }

            val commit = gitClient.currentCommit(checkoutDir) ?: "unknown"
            moveOrCopyDirectory(checkoutDir, packageRoot)
            upsertLockEntry(
                DreamShaderPackageLockEntry(
                    name = metadata.name,
                    version = metadata.version ?: "0.0.0",
                    repository = metadata.repository ?: repository,
                    commit = commit,
                    installPath = toRelativeProjectPath(packageRoot)
                )
            )

            return DreamShaderPackageOperationResult(
                success = true,
                message = DreamShaderBundle.message("package.manager.installed", metadata.name),
                packageName = metadata.name,
                installPath = packageRoot.absolutePathString().replace('\\', '/')
            )
        } finally {
            deleteQuietly(tempRoot)
        }
    }

    fun updateInstalledPackage(name: String): DreamShaderPackageOperationResult {
        if (!gitClient.isAvailable()) {
            return DreamShaderPackageOperationResult(false, DreamShaderBundle.message("package.manager.gitRequired"))
        }

        val entries = readLockEntries().toMutableList()
        val lockEntry = entries.firstOrNull { it.name == name }
        val packageDir = lockEntry?.let { resolveProjectPath(it.installPath) } ?: packageDirectoryForName(name)
        if (!packageDir.exists() || !packageDir.isDirectory()) {
            return DreamShaderPackageOperationResult(false, DreamShaderBundle.message("package.manager.notInstalled", name))
        }

        val pullResult = gitClient.pull(packageDir)
        if (!pullResult.success) {
            return DreamShaderPackageOperationResult(
                false,
                pullResult.message ?: DreamShaderBundle.message("package.manager.updateFailed", name)
            )
        }

        val metadataFile = packageDir.resolve(PACKAGE_METADATA_FILE)
        if (!metadataFile.exists() || !metadataFile.isRegularFile()) {
            return DreamShaderPackageOperationResult(false, DreamShaderBundle.message("package.manager.metadataMissing"))
        }
        val metadata = parsePackageMetadata(metadataFile)
            ?: return DreamShaderPackageOperationResult(false, DreamShaderBundle.message("package.manager.metadataNameMissing"))

        val commit = gitClient.currentCommit(packageDir) ?: lockEntry?.commit ?: "unknown"
        val repository = metadata.repository ?: lockEntry?.repository ?: ""
        val updatedEntry = DreamShaderPackageLockEntry(
            name = metadata.name,
            version = metadata.version ?: "0.0.0",
            repository = repository,
            commit = commit,
            installPath = toRelativeProjectPath(packageDir)
        )
        upsertLockEntry(updatedEntry)

        return DreamShaderPackageOperationResult(
            success = true,
            message = DreamShaderBundle.message("package.manager.updated", metadata.name),
            packageName = metadata.name,
            installPath = packageDir.absolutePathString().replace('\\', '/')
        )
    }

    fun removeInstalledPackage(name: String): DreamShaderPackageOperationResult {
        val entries = readLockEntries().toMutableList()
        val lockEntry = entries.firstOrNull { it.name == name }
        val packageDir = lockEntry?.let { resolveProjectPath(it.installPath) } ?: packageDirectoryForName(name)
        if (!packageDir.exists()) {
            return DreamShaderPackageOperationResult(false, DreamShaderBundle.message("package.manager.notInstalled", name))
        }

        val packagesRoot = ensurePackagesRoot()
        ensurePathUnder(packagesRoot, packageDir)
        deleteDirectoryRecursively(packageDir, packagesRoot)

        val remaining = entries.filterNot { it.name == name }
        writeLockEntries(remaining)
        return DreamShaderPackageOperationResult(
            true,
            DreamShaderBundle.message("package.manager.removed", name),
            packageName = name
        )
    }

    fun openPackagesFolder(): Path {
        val root = ensurePackagesRoot()
        Files.createDirectories(root)
        return root
    }

    internal fun listLockEntries(): List<DreamShaderPackageLockEntry> = readLockEntries()

    private fun normalizeGitHubRepositoryInput(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isBlank()) return null

        val shorthand = Regex("""^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$""")
        if (shorthand.matches(trimmed)) {
            return "https://github.com/$trimmed"
        }

        if (
            trimmed.startsWith("https://github.com/", ignoreCase = true) ||
            trimmed.startsWith("http://github.com/", ignoreCase = true)
        ) {
            return trimmed.removeSuffix("/").removeSuffix(".git")
        }
        return null
    }

    private fun packageDirectoryForName(name: String): Path {
        val clean = name.trim().trim('/').trim('\\')
        val parts = clean.split('/').filter { it.isNotBlank() }
        val root = ensurePackagesRoot()
        var current = root
        parts.forEach { part -> current = current.resolve(part) }
        return current
    }

    private fun ensurePackagesRoot(): Path {
        val basePath = project.basePath ?: error("project base path is null")
        return Path.of(basePath).resolve("DShader").resolve("Packages")
    }

    private fun resolveProjectPath(relativeOrAbsolute: String): Path {
        val normalized = relativeOrAbsolute.replace('\\', '/')
        val asPath = runCatching { Path.of(normalized) }.getOrNull()
        if (asPath != null && asPath.isAbsolute) return asPath
        val basePath = project.basePath ?: error("project base path is null")
        return Path.of(basePath).resolve(normalized)
    }

    private fun toRelativeProjectPath(path: Path): String {
        val basePath = project.basePath ?: error("project base path is null")
        return Path.of(basePath).toAbsolutePath().normalize()
            .relativize(path.toAbsolutePath().normalize())
            .toString()
            .replace('\\', '/')
    }

    private fun parsePackageMetadata(file: Path): DreamShaderPackageMetadata? {
        val raw = runCatching { Files.readString(file, StandardCharsets.UTF_8) }.getOrNull() ?: return null
        val dto = DreamShaderJson.decodeOrNull<PackageMetadataDto>(raw) ?: return null
        val name = dto.name?.trim().orEmpty()
        if (name.isBlank()) return null
        return DreamShaderPackageMetadata(
            name = name,
            version = dto.version?.trim(),
            repository = dto.repository?.trim()
        )
    }

    private fun readLockEntries(): List<DreamShaderPackageLockEntry> {
        val lockFile = lockFilePath()
        if (!lockFile.exists() || !lockFile.isRegularFile()) return emptyList()
        val raw = runCatching { Files.readString(lockFile, StandardCharsets.UTF_8) }.getOrNull() ?: return emptyList()
        val parsed = DreamShaderJson.decodeOrNull<PackageLockFileDto>(raw) ?: return emptyList()
        return parsed.packages.mapNotNull { dto ->
            DreamShaderPackageLockEntry(
                name = dto.name ?: return@mapNotNull null,
                version = dto.version ?: return@mapNotNull null,
                repository = dto.repository ?: return@mapNotNull null,
                commit = dto.commit ?: return@mapNotNull null,
                installPath = dto.installPath ?: return@mapNotNull null
            )
        }
    }

    private fun upsertLockEntry(entry: DreamShaderPackageLockEntry) {
        val current = readLockEntries().toMutableList()
        val existingIndex = current.indexOfFirst { it.name == entry.name }
        if (existingIndex >= 0) {
            current[existingIndex] = entry
        } else {
            current.add(entry)
        }
        writeLockEntries(current)
    }

    private fun writeLockEntries(entries: List<DreamShaderPackageLockEntry>) {
        val file = lockFilePath()
        Files.createDirectories(file.parent)
        val dto = PackageLockFileDto(
            packages = entries.map {
                PackageLockEntryDto(
                    name = it.name,
                    version = it.version,
                    repository = it.repository,
                    commit = it.commit,
                    installPath = it.installPath
                )
            }
        )
        Files.writeString(file, DreamShaderJson.encodePretty(dto) + "\n", StandardCharsets.UTF_8)
    }

    private fun lockFilePath(): Path {
        val basePath = project.basePath ?: error("project base path is null")
        return Path.of(basePath).resolve("DShader").resolve("dreamshader.lock.json")
    }

    private fun moveOrCopyDirectory(from: Path, to: Path) {
        runCatching {
            Files.move(from, to, StandardCopyOption.REPLACE_EXISTING)
        }.onSuccess { return }

        Files.walk(from).use { stream ->
            stream.asSequence().forEach { source ->
                val relative = from.relativize(source)
                val target = to.resolve(relative.toString())
                if (Files.isDirectory(source)) {
                    Files.createDirectories(target)
                } else {
                    Files.createDirectories(target.parent)
                    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        }
    }

    private fun deleteDirectoryRecursively(path: Path, packagesRoot: Path) {
        ensurePathUnder(packagesRoot, path)
        Files.walk(path).use { stream ->
            stream.asSequence()
                .sortedByDescending { it.toString().length }
                .forEach { Files.deleteIfExists(it) }
        }
    }

    private fun ensurePathUnder(root: Path, target: Path) {
        val canonicalRoot = root.toFile().canonicalFile.toPath().normalize()
        val canonicalTarget = target.toFile().canonicalFile.toPath().normalize()
        require(canonicalTarget.startsWith(canonicalRoot)) {
            "Refusing to operate outside packages root: $canonicalTarget"
        }
        require(canonicalTarget != canonicalRoot) {
            "Refusing to operate on packages root directly: $canonicalTarget"
        }
    }

    private fun deleteQuietly(path: Path) {
        if (!path.exists()) return
        runCatching {
            Files.walk(path).use { stream ->
                stream.asSequence()
                    .sortedByDescending { it.toString().length }
                    .forEach { Files.deleteIfExists(it) }
            }
        }
    }

    private companion object {
        private const val PACKAGE_METADATA_FILE = "dreamshader.package.json"
    }
}

/**
 * 包生命周期操作结果。
 */
internal data class DreamShaderPackageOperationResult(
    val success: Boolean,
    val message: String,
    val packageName: String? = null,
    val installPath: String? = null
)

/**
 * lock 文件中的单个包记录。
 */
internal data class DreamShaderPackageLockEntry(
    val name: String,
    val version: String,
    val repository: String,
    val commit: String,
    val installPath: String
)

/**
 * 包元数据最小模型。
 */
internal data class DreamShaderPackageMetadata(
    val name: String,
    val version: String?,
    val repository: String?
)

/**
 * Git 客户端抽象，便于测试替身注入。
 */
internal interface DreamShaderGitClient {
    fun isAvailable(): Boolean
    fun clone(source: String, targetDir: Path): DreamShaderGitCommandResult
    fun pull(repoDir: Path): DreamShaderGitCommandResult
    fun currentCommit(repoDir: Path): String?
}

/**
 * Git 命令执行结果。
 */
internal data class DreamShaderGitCommandResult(
    val success: Boolean,
    val message: String? = null
)

/**
 * 基于系统进程的 Git 客户端实现。
 */
internal class DreamShaderProcessGitClient : DreamShaderGitClient {
    override fun isAvailable(): Boolean {
        val result = runGitCommand(null, listOf("git", "--version"))
        return result.success
    }

    override fun clone(source: String, targetDir: Path): DreamShaderGitCommandResult {
        Files.createDirectories(targetDir.parent)
        return runGitCommand(null, listOf("git", "clone", source, targetDir.toString()))
    }

    override fun pull(repoDir: Path): DreamShaderGitCommandResult {
        return runGitCommand(repoDir, listOf("git", "pull"))
    }

    override fun currentCommit(repoDir: Path): String? {
        val result = runGitCommand(repoDir, listOf("git", "rev-parse", "HEAD"))
        if (!result.success) return null
        return result.message?.lineSequence()?.firstOrNull()?.trim()?.takeIf { it.isNotBlank() }
    }

    private fun runGitCommand(workingDir: Path?, command: List<String>): DreamShaderGitCommandResult {
        return runCatching {
            val process = ProcessBuilder(command)
                .directory(workingDir?.toFile())
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader(StandardCharsets.UTF_8).use { it.readText() }
            val exitCode = process.waitFor()
            if (exitCode == 0) {
                DreamShaderGitCommandResult(true, output)
            } else {
                DreamShaderGitCommandResult(
                    false,
                    output.ifBlank { DreamShaderBundle.message("package.manager.gitCommandFailed", command.joinToString(" ")) }
                )
            }
        }.getOrElse { error ->
            DreamShaderGitCommandResult(
                false,
                error.message ?: DreamShaderBundle.message("package.manager.gitCommandFailedSimple")
            )
        }
    }
}
