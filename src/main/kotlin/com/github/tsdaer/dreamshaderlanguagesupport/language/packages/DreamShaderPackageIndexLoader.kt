package com.github.tsdaer.dreamshaderlanguagesupport.language.packages
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * Loads package store index data from configured local/remote sources.
 *
 * Key behaviors:
 * - source resolution: multi-source setting -> legacy single-source -> default upstream
 * - payload compatibility: supports array root and object root with `"packages"`
 * - failure isolation: malformed or unreadable source does not block other sources
 * - install source resolution: prefer local `path` when resolvable, otherwise fallback to `repository`
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
        val text = rawJson.trim()
        if (text.isBlank()) return emptyList()

        val packageObjects = when {
            text.startsWith("[") -> extractTopLevelObjects(text)
            text.startsWith("{") -> {
                val packagesArray = extractArrayField(text, "packages")
                    ?: throw IllegalArgumentException("missing field 'packages'")
                extractTopLevelObjects(packagesArray)
            }
            else -> throw IllegalArgumentException("index root must be array or object")
        }

        return packageObjects.mapNotNull { parsePackageObject(it, source) }
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

    private fun parsePackageObject(objText: String, source: String): DreamShaderPackageIndexEntry? {
        val name = findStringField(objText, listOf("name")) ?: return null
        val repository = findStringField(objText, listOf("repository")) ?: return null
        val displayName = findStringField(objText, listOf("displayName"))
        val description = findStringField(objText, listOf("description"))
        val version = findStringField(objText, listOf("version"))
        val path = findStringField(objText, listOf("path"))
        val tags = findStringArrayField(objText, listOf("tags"))

        return DreamShaderPackageIndexEntry(
            name = name,
            displayName = displayName,
            description = description,
            version = version,
            repository = repository,
            source = source,
            path = path,
            tags = tags
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

    private fun extractArrayField(text: String, field: String): String? {
        val index = text.indexOf("\"$field\"")
        if (index < 0) return null
        val openBracket = text.indexOf('[', index)
        if (openBracket < 0) return null

        var depth = 0
        var inString = false
        var escaped = false
        for (i in openBracket until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(openBracket, i + 1)
                }
            }
        }
        return null
    }

    private fun extractTopLevelObjects(arrayText: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var objectStart = -1
        var inString = false
        var escaped = false

        for (i in arrayText.indices) {
            val ch = arrayText[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }

            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        result.add(arrayText.substring(objectStart, i + 1))
                        objectStart = -1
                    }
                }
            }
        }
        return result
    }

    private fun findStringField(text: String, names: List<String>): String? {
        names.forEach { name ->
            val regex = Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""", setOf(RegexOption.IGNORE_CASE))
            val match = regex.find(text) ?: return@forEach
            return unescapeJsonString(match.groupValues[1])
        }
        return null
    }

    private fun findStringArrayField(text: String, names: List<String>): List<String> {
        names.forEach { name ->
            val arrayText = extractArrayField(text, name) ?: return@forEach
            val result = mutableListOf<String>()
            Regex(""""((?:[^"\\]|\\.)*)"""").findAll(arrayText).forEach { match ->
                result.add(unescapeJsonString(match.groupValues[1]))
            }
            return result
        }
        return emptyList()
    }

    private fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
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
