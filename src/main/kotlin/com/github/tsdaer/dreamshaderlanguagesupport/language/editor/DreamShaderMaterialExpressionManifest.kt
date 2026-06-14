package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class DreamShaderMaterialExpressionParameter(
    val name: String,
    val type: String? = null,
    val required: Boolean = false,
    val placeholder: String? = null,
    val qualifier: String? = null
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
    val snippet: String? = null,
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
    private const val BRIDGE_RELATIVE_DIR = "Saved/DreamShader/Bridge"

    /**
     * Bridge 目录下 material-expression catalog 的候选文件名，按优先级排列。
     * `material-expressions.json` 为当前 Bridge 产物；`material-expression-manifest.json`
     * 为兼容旧版的回退名。
     */
    private val BRIDGE_MANIFEST_FILE_NAMES = listOf(
        "material-expressions.json",
        "material-expression-manifest.json"
    )

    private const val SUBSTRATE_BUILTINS_FILE_NAME = "substrate-builtins.json"

    // ---- 反序列化 DTO ----

    /** rich `expressions[]` 元素，覆盖配置/Bridge/scan 多来源字段。 */
    @Serializable
    private data class ExpressionDto(
        val namespace: String? = null,
        val className: String? = null,
        val name: String? = null,
        val ueName: String? = null,
        val signature: String? = null,
        val outputType: String? = null,
        val defaultOutputType: String? = null,
        val description: String? = null,
        val detail: String? = null,
        val parameters: List<ParameterDto> = emptyList(),
        val inputs: List<InputDto> = emptyList(),
        val outputs: List<OutputDto> = emptyList()
    )

    /** 显式 `parameters[]`（旧格式/Substrate）元素。 */
    @Serializable
    private data class ParameterDto(
        val name: String? = null,
        val type: String? = null,
        val required: Boolean = false,
        val placeholder: String? = null,
        val qualifier: String? = null
    )

    /** Bridge `inputs[]` 元素，每个输入名作为一个 `Value` 占位实参。 */
    @Serializable
    private data class InputDto(
        val name: String? = null,
        val type: String? = null
    )

    /** Bridge `outputs[]` 元素，用于推断输出类型。 */
    @Serializable
    private data class OutputDto(
        val outputType: String? = null,
        val componentCount: Int? = null
    )

    /** Substrate `builtins[]` 元素。 */
    @Serializable
    private data class SubstrateBuiltinDto(
        val name: String? = null,
        val className: String? = null,
        val outputType: String? = null,
        val detail: String? = null,
        val example: String? = null,
        val snippet: String? = null,
        val parameters: List<ParameterDto> = emptyList()
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
        add(readSubstrateBuiltinsFromBridge(project))
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
        val bridgeDir = File(root, BRIDGE_RELATIVE_DIR)
        for (fileName in BRIDGE_MANIFEST_FILE_NAMES) {
            val vf = LocalFileSystem.getInstance()
                .findFileByPath(File(bridgeDir, fileName).path)
                ?: continue
            if (!vf.isValid || vf.isDirectory) continue
            val entries = runCatching {
                parseCatalogEntries(
                    rawJson = String(vf.contentsToByteArray(), StandardCharsets.UTF_8),
                    source = DreamShaderMaterialExpressionSource.BRIDGE_MANIFEST
                )
            }.getOrDefault(emptyList())
            if (entries.isNotEmpty()) return entries
        }
        return emptyList()
    }

    /**
     * Reads `Saved/DreamShader/Bridge/substrate-builtins.json` and maps each builtin
     * to a catalog entry under the `Substrate` namespace, so `Substrate.*` completion,
     * signature help, and hover share the same pipeline as `UE.*`.
     */
    private fun readSubstrateBuiltinsFromBridge(project: Project?): List<DreamShaderMaterialExpressionInfo> {
        if (project == null) return emptyList()
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        val root = settings?.projectRoot?.takeIf { it.isNotBlank() } ?: project.basePath
        if (root.isNullOrBlank()) return emptyList()
        val vf = LocalFileSystem.getInstance()
            .findFileByPath(File(File(root, BRIDGE_RELATIVE_DIR), SUBSTRATE_BUILTINS_FILE_NAME).path)
            ?: return emptyList()
        if (!vf.isValid || vf.isDirectory) return emptyList()
        return runCatching {
            parseSubstrateBuiltins(String(vf.contentsToByteArray(), StandardCharsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    internal fun parseSubstrateBuiltins(rawJson: String): List<DreamShaderMaterialExpressionInfo> {
        val root = DreamShaderJson.decodeOrNull<JsonObject>(rawJson) ?: return emptyList()
        val array = root["builtins"] as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val dto = runCatching {
                DreamShaderJson.lenient.decodeFromJsonElement<SubstrateBuiltinDto>(element)
            }.getOrNull() ?: return@mapNotNull null
            substrateBuiltinFromDto(dto)
        }
    }

    private fun substrateBuiltinFromDto(dto: SubstrateBuiltinDto): DreamShaderMaterialExpressionInfo? {
        val name = dto.name?.takeIf { it.isNotBlank() } ?: return null
        val className = dto.className?.takeIf { it.isNotBlank() }
            ?: "MaterialExpressionSubstrate$name"
        val parameters = dto.parameters.mapNotNull(::parameterFromDto)
        val example = dto.example?.takeIf { it.isNotBlank() }
        val snippet = dto.snippet?.takeIf { it.isNotBlank() }
        return DreamShaderMaterialExpressionInfo(
            namespace = "Substrate",
            className = className,
            ueName = name,
            signature = example ?: snippet ?: defaultSignature("Substrate", name),
            parameters = parameters,
            outputType = dto.outputType?.takeIf { it.isNotBlank() },
            description = dto.detail?.takeIf { it.isNotBlank() }
                ?: defaultDescription("Substrate", name, className),
            snippet = snippet,
            source = DreamShaderMaterialExpressionSource.BRIDGE_MANIFEST
        )
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
        val root = DreamShaderJson.decodeOrNull<JsonElement>(rawJson) ?: return emptyList()

        val entries = mutableListOf<DreamShaderMaterialExpressionInfo>()

        // rich `expressions[]`
        ((root as? JsonObject)?.get("expressions") as? JsonArray)?.forEach { element ->
            decodeExpression(element)?.let { richEntry(it, source) }?.let(entries::add)
        }

        // 旧格式 `classes[]` 或裸数组根
        entries.addAll(parseLegacyClassEntries(root, source))

        if (entries.isNotEmpty()) return entries

        // 回退：扫描树中任意 className/name 标识符；空且根为数组时收集裸字符串。
        val values = linkedSetOf<String>()
        collectIdentifierFields(root, values)
        if (values.isEmpty() && root is JsonArray) {
            root.forEach { element ->
                (element as? JsonPrimitive)?.takeIf { it.isString }?.content
                    ?.takeIf { IDENTIFIER_PATTERN.matches(it) }
                    ?.let(values::add)
            }
        }
        return values.map { legacyEntry(it, source) }
    }

    private fun decodeExpression(element: JsonElement): ExpressionDto? = runCatching {
        DreamShaderJson.lenient.decodeFromJsonElement<ExpressionDto>(element)
    }.getOrNull()

    private fun richEntry(
        dto: ExpressionDto,
        source: DreamShaderMaterialExpressionSource
    ): DreamShaderMaterialExpressionInfo? {
        val rawClassName = dto.className?.takeIf { it.isNotBlank() }
            ?: dto.name?.takeIf { it.isNotBlank() }
            ?: dto.ueName?.takeIf { it.isNotBlank() }
            ?: return null
        val namespace = dto.namespace?.takeIf { it.isNotBlank() } ?: "UE"
        val ueName = dto.ueName?.takeIf { it.isNotBlank() }
            ?: dto.name?.takeIf { it.isNotBlank() }
            ?: deriveUeName(rawClassName)

        // 参数来源优先级：显式 `parameters`（旧格式/Substrate）→ Bridge 的 `inputs`（实际产物）。
        val parameters = dto.parameters.mapNotNull(::parameterFromDto)
            .takeIf { it.isNotEmpty() }
            ?: dto.inputs.mapNotNull { input ->
                val name = input.name?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                DreamShaderMaterialExpressionParameter(
                    name = name,
                    type = input.type?.takeIf { it.isNotBlank() },
                    required = false,
                    placeholder = "Value"
                )
            }

        // 输出类型优先级：显式 `outputType` → `defaultOutputType` → 从 `outputs[0]` 推断。
        val outputType = dto.outputType?.takeIf { it.isNotBlank() }
            ?: dto.defaultOutputType?.takeIf { it.isNotBlank() }
            ?: inferOutputTypeFromOutputs(dto.outputs)

        val explicitSignature = dto.signature?.takeIf { it.isNotBlank() }

        return DreamShaderMaterialExpressionInfo(
            namespace = namespace,
            className = rawClassName,
            ueName = ueName,
            signature = explicitSignature ?: synthesizeSignature(namespace, ueName, parameters, outputType),
            parameters = parameters,
            outputType = outputType,
            description = dto.description?.takeIf { it.isNotBlank() }
                ?: dto.detail?.takeIf { it.isNotBlank() }
                ?: defaultDescription(namespace, ueName, rawClassName),
            snippet = synthesizeSnippet(namespace, ueName, parameters, outputType),
            source = source
        )
    }

    /** 从 `outputs[0].outputType` 推断输出类型；缺失时按 componentCount 退化为 floatN。 */
    private fun inferOutputTypeFromOutputs(outputs: List<OutputDto>): String? {
        val first = outputs.firstOrNull() ?: return null
        first.outputType?.takeIf { it.isNotBlank() }?.let { return it }
        val components = first.componentCount?.coerceIn(1, 4) ?: 1
        return "float$components"
    }

    /**
     * 合成具名调用签名：`<ns>.<name>(OutputType="float1", A=Value, B=Value)`。
     * 无参数时退化为 `<ns>.<name>(OutputType="...")` 或纯 `<ns>.<name>()`。
     */
    private fun synthesizeSignature(
        namespace: String,
        ueName: String,
        parameters: List<DreamShaderMaterialExpressionParameter>,
        outputType: String?
    ): String {
        val args = mutableListOf<String>()
        if (outputType != null) args.add("OutputType=\"$outputType\"")
        parameters.forEach { args.add("${it.name}=${it.placeholder ?: "Value"}") }
        return "$namespace.$ueName(${args.joinToString(", ")})"
    }

    /**
     * 为条目合成活动模板 snippet，与 [synthesizeSignature] 保持一致，但把可编辑值包成
     * `${N:default}` 制表位：`<ns>.<name>(OutputType="${1:float1}", A=${2:Value})$0`。
     * 无 OutputType 且无参数时返回 null（由调用方退化为静态插入）。
     */
    private fun synthesizeSnippet(
        namespace: String,
        ueName: String,
        parameters: List<DreamShaderMaterialExpressionParameter>,
        outputType: String?
    ): String? {
        if (outputType == null && parameters.isEmpty()) return null
        val args = mutableListOf<String>()
        var stop = 1
        if (outputType != null) {
            args.add("OutputType=\"\${${stop}:$outputType}\"")
            stop++
        }
        parameters.forEach { parameter ->
            args.add("${parameter.name}=\${${stop}:${parameter.placeholder ?: "Value"}}")
            stop++
        }
        return "$namespace.$ueName(${args.joinToString(", ")})\$0"
    }

    private fun parameterFromDto(dto: ParameterDto): DreamShaderMaterialExpressionParameter? {
        val name = dto.name?.takeIf { it.isNotBlank() } ?: return null
        return DreamShaderMaterialExpressionParameter(
            name = name,
            type = dto.type?.takeIf { it.isNotBlank() },
            required = dto.required,
            placeholder = dto.placeholder?.takeIf { it.isNotBlank() },
            qualifier = dto.qualifier?.takeIf { it.isNotBlank() }
        )
    }

    /** 递归收集树中所有 `className`/`name` 标识符字段值（回退路径用）。 */
    private fun collectIdentifierFields(element: JsonElement, sink: MutableSet<String>) {
        when (element) {
            is JsonObject -> {
                listOf("className", "name").forEach { key ->
                    (element[key] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?.takeIf { IDENTIFIER_PATTERN.matches(it) }
                        ?.let(sink::add)
                }
                element.values.forEach { collectIdentifierFields(it, sink) }
            }
            is JsonArray -> element.forEach { collectIdentifierFields(it, sink) }
            else -> Unit
        }
    }

    private fun parseLegacyClassEntries(
        root: JsonElement,
        source: DreamShaderMaterialExpressionSource
    ): List<DreamShaderMaterialExpressionInfo> {
        val array = when {
            root is JsonObject && root["classes"] is JsonArray -> root["classes"] as JsonArray
            root is JsonArray -> root
            else -> return emptyList()
        }

        return array.mapNotNull { element ->
            when (element) {
                is JsonObject -> {
                    (element["className"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                        ?: (element["name"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                }
                is JsonPrimitive -> if (element.isString) element.content else null
                else -> null
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

    private val IDENTIFIER_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")
}
