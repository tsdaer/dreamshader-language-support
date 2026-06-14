package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class DreamShaderMaterialExpressionParameter(
    val name: String,
    val type: String? = null,
    val required: Boolean = false
)

internal enum class DreamShaderMaterialExpressionSource {
    CONFIGURED_MANIFEST,
    BRIDGE_MANIFEST,
    SCANNED_CACHE,
    BUNDLED_FALLBACK,
    HARDCODED_FALLBACK
}

internal data class DreamShaderMaterialExpressionInfo(
    val namespace: String,
    val className: String,
    val ueName: String,
    val signature: String?,
    val parameters: List<DreamShaderMaterialExpressionParameter> = emptyList(),
    val outputType: String? = null,
    val description: String? = null,
    val source: DreamShaderMaterialExpressionSource
) {
    val qualifiedName: String
        get() = "$namespace.$ueName"
}

/**
 * Singleton for DreamShaderMaterialExpressionManifest.
 */
internal object DreamShaderMaterialExpressionManifest {
    private const val BUNDLED_RESOURCE_PATH = "/messages/material-expression-manifest.json"
    private const val DEFAULT_BRIDGE_RELATIVE_PATH = "Saved/DreamShader/Bridge/material-expression-manifest.json"

    private val classNameRegex = Regex(
        """"className"\s*:\s*"([A-Za-z_][A-Za-z0-9_]*)"""",
        RegexOption.IGNORE_CASE
    )
    private val nameRegex = Regex(
        """"name"\s*:\s*"([A-Za-z_][A-Za-z0-9_]*)"""",
        RegexOption.IGNORE_CASE
    )
    private val simpleArrayEntryRegex = Regex(
        """"(?:className|name)"\s*:\s*"([A-Za-z_][A-Za-z0-9_]*)"|"([A-Za-z_][A-Za-z0-9_]*)"""",
        RegexOption.IGNORE_CASE
    )

    fun catalogEntries(project: Project?, explicitManifestPath: String?): List<DreamShaderMaterialExpressionInfo> {
        val merged = linkedMapOf<String, DreamShaderMaterialExpressionInfo>()
        fun add(entries: Iterable<DreamShaderMaterialExpressionInfo>) {
            entries.forEach { entry ->
                val ueKey = "ue:${entry.namespace.lowercase(Locale.ROOT)}.${entry.ueName.lowercase(Locale.ROOT)}"
                val classKey = "class:${entry.className.lowercase(Locale.ROOT)}"
                if (merged.containsKey(ueKey) || merged.containsKey(classKey)) return@forEach
                merged[ueKey] = entry
                merged[classKey] = entry
            }
        }

        add(readCatalogEntriesFromConfiguredPath(explicitManifestPath))
        add(readCatalogEntriesFromBridgeManifest(project))
        add(readCatalogEntriesFromScanCache(project))
        add(readCatalogEntriesFromBundledManifest())
        return merged.values.distinct()
    }

    fun expressionClassNames(project: Project?, explicitManifestPath: String?): List<String> {
        return expressionClassNames(catalogEntries(project, explicitManifestPath))
    }

    fun expressionClassNames(entries: List<DreamShaderMaterialExpressionInfo>): List<String> {
        val merged = linkedSetOf<String>()
        entries.forEach { entry ->
            expressionClassNameCandidates(entry).forEach { merged.add(it) }
        }
        return merged.toList()
    }

    private fun readCatalogEntriesFromConfiguredPath(path: String?): List<DreamShaderMaterialExpressionInfo> {
        if (path.isNullOrBlank()) return emptyList()
        val file = File(path)
        if (!file.exists() || !file.isFile) return emptyList()
        return runCatching {
            parseCatalogEntries(
                rawJson = file.readText(StandardCharsets.UTF_8),
                source = DreamShaderMaterialExpressionSource.CONFIGURED_MANIFEST
            )
        }
            .getOrDefault(emptyList())
    }

    private fun readCatalogEntriesFromBridgeManifest(project: Project?): List<DreamShaderMaterialExpressionInfo> {
        if (project == null) return emptyList()
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        val root = settings?.projectRoot?.takeIf { it.isNotBlank() } ?: project.basePath
        if (root.isNullOrBlank()) return emptyList()
        val vf = LocalFileSystem.getInstance()
            .findFileByPath(File(root, DEFAULT_BRIDGE_RELATIVE_PATH).path)
            ?: return emptyList()
        if (!vf.isValid || vf.isDirectory) return emptyList()
        return runCatching {
            parseCatalogEntries(
                rawJson = String(vf.contentsToByteArray(), StandardCharsets.UTF_8),
                source = DreamShaderMaterialExpressionSource.BRIDGE_MANIFEST
            )
        }.getOrDefault(emptyList())
    }

    /**
     * Scanned-cache source: prefers a previously written cache JSON, and only
     * falls back to a best-effort live scan of the configured Unreal source root
     * when scanning is explicitly enabled and no cache exists yet.
     */
    private fun readCatalogEntriesFromScanCache(project: Project?): List<DreamShaderMaterialExpressionInfo> {
        if (project == null) return emptyList()
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state ?: return emptyList()

        val cachePath = settings.materialExpressionScanCachePath.takeIf { it.isNotBlank() }
        if (cachePath != null) {
            val cacheFile = File(cachePath)
            if (cacheFile.exists() && cacheFile.isFile) {
                return runCatching {
                    parseCatalogEntries(
                        rawJson = cacheFile.readText(StandardCharsets.UTF_8),
                        source = DreamShaderMaterialExpressionSource.SCANNED_CACHE
                    )
                }.getOrDefault(emptyList())
            }
        }

        if (!settings.materialExpressionScanEnabled) return emptyList()
        val sourceRoot = settings.unrealEngineSourceRoot.takeIf { it.isNotBlank() } ?: return emptyList()
        return runCatching {
            DreamShaderMaterialExpressionScanner.scanDirectory(File(sourceRoot))
        }.getOrDefault(emptyList())
    }

    private fun readCatalogEntriesFromBundledManifest(): List<DreamShaderMaterialExpressionInfo> {
        val stream = javaClass.getResourceAsStream(BUNDLED_RESOURCE_PATH) ?: return emptyList()
        val text = stream.use { String(it.readBytes(), StandardCharsets.UTF_8) }
        return runCatching {
            parseCatalogEntries(
                rawJson = text,
                source = DreamShaderMaterialExpressionSource.BUNDLED_FALLBACK
            )
        }.getOrDefault(emptyList())
    }

    internal fun parseExpressionClassNames(rawJson: String): List<String> {
        return expressionClassNames(parseCatalogEntries(rawJson, DreamShaderMaterialExpressionSource.BUNDLED_FALLBACK))
    }

    internal fun parseCatalogEntries(
        rawJson: String,
        source: DreamShaderMaterialExpressionSource = DreamShaderMaterialExpressionSource.BUNDLED_FALLBACK
    ): List<DreamShaderMaterialExpressionInfo> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) return emptyList()

        val entries = mutableListOf<DreamShaderMaterialExpressionInfo>()
        findNamedArrayContent(trimmed, "expressions")
            ?.let(::splitTopLevelArrayElements)
            ?.filter { it.trim().startsWith("{") }
            ?.mapNotNull { parseRichExpressionObject(it, source) }
            ?.let(entries::addAll)

        parseLegacyClassEntries(trimmed, source).let(entries::addAll)

        if (entries.isNotEmpty()) return entries

        val values = linkedSetOf<String>()
        classNameRegex.findAll(trimmed).forEach { values.add(it.groupValues[1]) }
        nameRegex.findAll(trimmed).forEach { values.add(it.groupValues[1]) }

        if (values.isEmpty() && trimmed.startsWith("[")) {
            simpleArrayEntryRegex.findAll(trimmed).forEach { match ->
                val first = match.groupValues[1]
                val second = match.groupValues[2]
                when {
                    first.isNotBlank() -> values.add(first)
                    second.isNotBlank() && !second.equals("className", ignoreCase = true) && !second.equals("name", ignoreCase = true) -> values.add(second)
                }
            }
        }

        return values.map { legacyEntry(it, source) }
    }

    private fun parseRichExpressionObject(
        objectText: String,
        source: DreamShaderMaterialExpressionSource
    ): DreamShaderMaterialExpressionInfo? {
        val rawClassName = readStringField(objectText, "className")
            ?: readStringField(objectText, "name")
            ?: readStringField(objectText, "ueName")
            ?: return null
        val namespace = readStringField(objectText, "namespace")
            ?.takeIf { it.isNotBlank() }
            ?: "UE"
        val ueName = readStringField(objectText, "ueName")
            ?.takeIf { it.isNotBlank() }
            ?: deriveUeName(rawClassName)
        val parameters = findNamedArrayContent(objectText, "parameters")
            ?.let(::splitTopLevelArrayElements)
            ?.filter { it.trim().startsWith("{") }
            ?.mapNotNull(::parseParameterObject)
            .orEmpty()

        return DreamShaderMaterialExpressionInfo(
            namespace = namespace,
            className = rawClassName,
            ueName = ueName,
            signature = readStringField(objectText, "signature")
                ?.takeIf { it.isNotBlank() }
                ?: defaultSignature(namespace, ueName),
            parameters = parameters,
            outputType = readStringField(objectText, "outputType")?.takeIf { it.isNotBlank() },
            description = readStringField(objectText, "description")
                ?.takeIf { it.isNotBlank() }
                ?: defaultDescription(namespace, ueName, rawClassName),
            source = source
        )
    }

    private fun parseParameterObject(objectText: String): DreamShaderMaterialExpressionParameter? {
        val name = readStringField(objectText, "name")?.takeIf { it.isNotBlank() } ?: return null
        return DreamShaderMaterialExpressionParameter(
            name = name,
            type = readStringField(objectText, "type")?.takeIf { it.isNotBlank() },
            required = readBooleanField(objectText, "required") ?: false
        )
    }

    private fun parseLegacyClassEntries(
        rawJson: String,
        source: DreamShaderMaterialExpressionSource
    ): List<DreamShaderMaterialExpressionInfo> {
        val arrayContent = findNamedArrayContent(rawJson, "classes")
            ?: if (rawJson.trim().startsWith("[")) rawJson.trim().removePrefix("[").removeSuffix("]") else null
            ?: return emptyList()

        return splitTopLevelArrayElements(arrayContent)
            .mapNotNull { element ->
                val trimmed = element.trim()
                when {
                    trimmed.startsWith("{") -> readStringField(trimmed, "className")
                        ?: readStringField(trimmed, "name")
                    trimmed.startsWith("\"") -> unescapeJsonString(trimmed.trim('"'))
                    else -> trimmed.takeIf { IDENTIFIER_PATTERN.matches(it) }
                }
            }
            .filter { it.isNotBlank() }
            .map { legacyEntry(it, source) }
    }

    private fun legacyEntry(
        className: String,
        source: DreamShaderMaterialExpressionSource
    ): DreamShaderMaterialExpressionInfo {
        val ueName = deriveUeName(className)
        return DreamShaderMaterialExpressionInfo(
            namespace = "UE",
            className = className,
            ueName = ueName,
            signature = defaultSignature("UE", ueName),
            description = defaultDescription("UE", ueName, className),
            source = source
        )
    }

    private fun expressionClassNameCandidates(entry: DreamShaderMaterialExpressionInfo): List<String> {
        val candidates = linkedSetOf<String>()
        val raw = entry.className.trim()
        if (raw.isNotBlank()) candidates.add(raw)
        if (raw.startsWith("U") && raw.drop(1).startsWith("MaterialExpression")) {
            candidates.add(raw.drop(1))
        }
        if (entry.ueName.isNotBlank()) candidates.add(entry.ueName)
        return candidates.toList()
    }

    private fun deriveUeName(className: String): String {
        return className
            .removePrefix("UMaterialExpression")
            .removePrefix("MaterialExpression")
            .ifBlank { className }
    }

    private fun defaultSignature(namespace: String, ueName: String): String = "$namespace.$ueName(...)"

    private fun defaultDescription(namespace: String, ueName: String, className: String): String =
        "Material expression '$className' exposed as $namespace.$ueName."

    private fun findNamedArrayContent(text: String, name: String): String? {
        val match = Regex(""""$name"\s*:\s*\[""", RegexOption.IGNORE_CASE).find(text) ?: return null
        val leftBracket = text.indexOf('[', match.range.first)
        if (leftBracket < 0) return null
        val rightBracket = findMatchingBracket(text, leftBracket, '[', ']') ?: return null
        return text.substring(leftBracket + 1, rightBracket)
    }

    private fun splitTopLevelArrayElements(text: String): List<String> {
        val elements = mutableListOf<String>()
        var start = 0
        var objectDepth = 0
        var arrayDepth = 0
        var inString = false
        var escaped = false

        for (index in text.indices) {
            val ch = text[index]
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
                '{' -> objectDepth++
                '}' -> if (objectDepth > 0) objectDepth--
                '[' -> arrayDepth++
                ']' -> if (arrayDepth > 0) arrayDepth--
                ',' -> {
                    if (objectDepth == 0 && arrayDepth == 0) {
                        elements.add(text.substring(start, index))
                        start = index + 1
                    }
                }
            }
        }
        elements.add(text.substring(start))
        return elements.map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun findMatchingBracket(text: String, leftOffset: Int, left: Char, right: Char): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        var index = leftOffset
        while (index < text.length) {
            val ch = text[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                index++
                continue
            }
            when (ch) {
                '"' -> inString = true
                left -> depth++
                right -> {
                    depth--
                    if (depth == 0) return index
                }
            }
            index++
        }
        return null
    }

    private fun readStringField(objectText: String, field: String): String? {
        val match = Regex(
            """"$field"\s*:\s*"((?:[^"\\]|\\.)*)"""",
            RegexOption.IGNORE_CASE
        ).find(objectText) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun readBooleanField(objectText: String, field: String): Boolean? {
        val match = Regex(
            """"$field"\s*:\s*(true|false)""",
            RegexOption.IGNORE_CASE
        ).find(objectText) ?: return null
        return match.groupValues[1].equals("true", ignoreCase = true)
    }

    private fun unescapeJsonString(value: String): String {
        return value
            .replace("\\\"", "\"")
            .replace("\\\\", "\\")
    }

    private val IDENTIFIER_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
