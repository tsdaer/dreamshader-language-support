package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets

/** 索引源中单个包条目的反序列化 DTO。 */
@Serializable
private data class PackageIndexEntryDto(
    val name: String? = null,
    val displayName: String? = null,
    val description: String? = null,
    val version: String? = null,
    val repository: String? = null,
    val path: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * 包索引加载器。
 *
 * 负责从本地/远程索引源读取包列表，并做统一解析与错误隔离。
 *
 * 关键行为：
 * - 源解析顺序：多源配置 -> 旧单源配置 -> 默认上游索引
 * - 载荷兼容：支持数组根与 `{ "packages": [...] }` 对象根
 * - 失败隔离：单源损坏不会阻断其他源
 * - 安装源解析：优先可解析的本地 `path`，否则回退 `repository`
 */
internal object DreamShaderPackageIndexLoader {
    private const val DEFAULT_INDEX_URL =
        "https://raw.githubusercontent.com/TypeDreamMoon/dreamshader-package-index/main/packages.json"

    fun resolveIndexSources(project: Project): List<String> {
        val state = project.getService(DreamShaderProjectSettings::class.java)?.state
        val fromList = state?.packageStoreIndexUrls.orEmpty()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (fromList.isNotEmpty()) return deduplicateSources(fromList)

        val legacy = state?.packageStoreIndexUrl?.trim().orEmpty()
        if (legacy.isNotBlank()) return listOf(normalizeSource(legacy))
        return listOf(DEFAULT_INDEX_URL)
    }

    fun loadFromSources(sources: List<String>): DreamShaderPackageIndexLoadResult {
        val entries = mutableListOf<DreamShaderPackageIndexEntry>()
        val errors = mutableListOf<DreamShaderPackageIndexLoadError>()

        for (source in deduplicateSources(sources)) {
            val payload = readSourceText(source)
            if (payload == null) {
                errors.add(
                    DreamShaderPackageIndexLoadError(
                        source,
                        errorMessage("package.loader.failedToReadIndex", source)
                    )
                )
                continue
            }

            val parsedResult = runCatching { parseEntries(payload, source) }
            if (parsedResult.isFailure) {
                val throwable = parsedResult.exceptionOrNull()
                errors.add(
                    DreamShaderPackageIndexLoadError(
                        source,
                        errorMessage(
                            "package.loader.failedToParseIndex",
                            source,
                            throwable?.message ?: "unknown error"
                        )
                    )
                )
                continue
            }

            entries.addAll(parsedResult.getOrDefault(emptyList()))
        }

        return DreamShaderPackageIndexLoadResult(entries = entries, errors = errors)
    }

    internal fun parseEntries(rawJson: String, source: String): List<DreamShaderPackageIndexEntry> {
        if (rawJson.isBlank()) return emptyList()
        val root = DreamShaderJson.decodeOrNull<kotlinx.serialization.json.JsonElement>(rawJson)
            ?: throw IllegalArgumentException("index root is not valid JSON")

        val packageArray: JsonArray = when {
            root is JsonArray -> root
            root is JsonObject -> {
                val packages = root["packages"]
                    ?: throw IllegalArgumentException("missing field 'packages'")
                packages as? JsonArray
                    ?: throw IllegalArgumentException("field 'packages' is not an array")
            }
            else -> throw IllegalArgumentException("index root must be array or object")
        }

        return packageArray.mapNotNull { element ->
            val dto = runCatching {
                DreamShaderJson.lenient.decodeFromJsonElement<PackageIndexEntryDto>(element)
            }.getOrNull() ?: return@mapNotNull null
            toEntry(dto, source)
        }
    }

    internal fun resolveInstallSource(entry: DreamShaderPackageIndexEntry): DreamShaderPackageInstallSource {
        val rawPath = entry.path?.trim().orEmpty()
        if (rawPath.isNotBlank()) {
            val resolved = resolvePathAgainstSource(entry.source, rawPath)
            if (resolved != null && File(resolved).exists()) {
                return DreamShaderPackageInstallSource(
                    sourcePathOrUrl = normalizePath(resolved),
                    resolvedFromLocalPath = true
                )
            }
        }
        return DreamShaderPackageInstallSource(
            sourcePathOrUrl = entry.repository,
            resolvedFromLocalPath = false
        )
    }

    private fun toEntry(dto: PackageIndexEntryDto, source: String): DreamShaderPackageIndexEntry? {
        // 与旧 findStringField 行为对齐：字段缺失（null）才丢弃，空串保留。
        val name = dto.name ?: return null
        val repository = dto.repository ?: return null
        return DreamShaderPackageIndexEntry(
            name = name,
            displayName = dto.displayName,
            description = dto.description,
            version = dto.version,
            repository = repository,
            source = source,
            path = dto.path,
            tags = dto.tags
        )
    }

    private fun readSourceText(source: String): String? {
        val trimmed = source.trim()
        if (trimmed.isBlank()) return null

        resolveLocalFile(trimmed)?.let { file ->
            return runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
        }

        return runCatching {
            val url = URI(trimmed).toURL()
            url.openStream().use { String(it.readBytes(), StandardCharsets.UTF_8) }
        }.getOrNull()
    }

    private fun resolveLocalFile(source: String): File? {
        if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
            return null
        }
        if (source.startsWith("file://", ignoreCase = true)) {
            return runCatching { File(URI(source)) }.getOrNull()
        }

        val byVfs = LocalFileSystem.getInstance().findFileByPath(source)?.path
        val candidate = byVfs?.let(::File) ?: File(source)
        return if (candidate.exists() && candidate.isFile) candidate else null
    }

    private fun resolvePathAgainstSource(indexSource: String, rawPath: String): String? {
        if (rawPath.isBlank()) return null

        val directCandidate = if (rawPath.startsWith("file://", ignoreCase = true)) {
            runCatching { File(URI(rawPath)) }.getOrNull()
        } else {
            File(rawPath)
        }
        if (directCandidate != null && directCandidate.isAbsolute) {
            return runCatching { directCandidate.canonicalPath }.getOrDefault(directCandidate.path)
        }

        val indexFile = resolveLocalFile(indexSource) ?: return null
        val parent = indexFile.parentFile ?: return null
        val combined = File(parent, rawPath)
        return runCatching { combined.canonicalPath }.getOrDefault(combined.path)
    }

    private fun deduplicateSources(sources: List<String>): List<String> {
        val normalized = linkedSetOf<String>()
        sources.forEach { source ->
            val trimmed = source.trim()
            if (trimmed.isBlank()) return@forEach
            normalized.add(normalizeSource(trimmed))
        }
        return normalized.toList()
    }

    private fun normalizeSource(source: String): String {
        if (source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true)) {
            return source.trim()
        }
        if (source.startsWith("file://", ignoreCase = true)) {
            val file = runCatching { File(URI(source)) }.getOrNull()
            if (file != null) return normalizePath(file.path)
        }
        return normalizePath(source)
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }

    private fun errorMessage(key: String, vararg args: Any): String {
        return runCatching { com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle.message(key, *args) }
            .getOrElse {
                when (key) {
                    "package.loader.failedToReadIndex" -> "Failed to read package index: ${args[0]}"
                    "package.loader.failedToParseIndex" -> "Failed to parse package index: ${args[0]} (${args[1]})"
                    else -> "Package index error"
                }
            }
    }
}
