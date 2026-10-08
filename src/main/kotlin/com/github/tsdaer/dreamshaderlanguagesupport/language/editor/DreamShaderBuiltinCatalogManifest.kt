package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * 上游 2.0 绑定器节点目录（`dreamshader-builtin-catalog.json`）解析器。
 *
 * schema 为 `dreamshader-builtin-catalog` / version 1（上游 `IR/IRCatalog.cpp` 的
 * `SaveBuiltinCatalogToJson` 产物，`dsc export-catalog` 与 Bridge 启动各写一份）。
 * 条目的类/引脚/属性/别名/位置参数顺序驱动 `UE.*` / `Substrate.*` 补全与签名帮助。
 *
 * 按 6.4 红线 6：schema 字符串或版本不符即返回空列表，降级到合并链的下一数据源，不抛错。
 */
internal object DreamShaderBuiltinCatalogManifest {
    const val SCHEMA_NAME = "dreamshader-builtin-catalog"
    const val SCHEMA_VERSION = 1

    /** B2：内嵌的完整 V2 兜底目录（离线安装时替代引擎产物），来源版本见资源内 `engine` 字段。 */
    private const val BUNDLED_FALLBACK_RESOURCE = "/messages/dreamshader-builtin-catalog-fallback.json"

    @Serializable
    private data class CatalogExpressionDto(
        val namespace: String? = null,
        val shortName: String? = null,
        val className: String? = null,
        val classPathName: String? = null,
        val aliases: List<String> = emptyList(),
        val positional: List<String> = emptyList(),
        val inputs: List<CatalogPinDto> = emptyList(),
        val outputs: List<CatalogPinDto> = emptyList(),
        val properties: List<CatalogPinDto> = emptyList(),
        val isAbstract: Boolean = false,
        val hasInstanceDependentPins: Boolean = false,
        val isParameter: Boolean = false,
        val isCustomOutput: Boolean = false
    )

    /** 引脚与属性共用形态：`{ "name", "type", "required"? }`（其余字段宽容忽略）。 */
    @Serializable
    private data class CatalogPinDto(
        val name: String? = null,
        val type: String? = null,
        val required: Boolean = false,
        val enumValues: List<String> = emptyList(),
        @SerialName("default") val defaultValue: JsonElement? = null,
        val aliases: List<String> = emptyList(),
        val constProperty: String? = null,
        val property: String? = null
    )

    fun parseBundledFallbackEntries(): List<DreamShaderMaterialExpressionInfo> {
        val stream = DreamShaderBuiltinCatalogManifest::class.java.getResourceAsStream(BUNDLED_FALLBACK_RESOURCE)
            ?: return emptyList()
        val text = stream.use { String(it.readBytes(), Charsets.UTF_8) }
        return parseCatalogEntries(text, DreamShaderMaterialExpressionSource.BUNDLED_FALLBACK)
    }

    /**
     * 解析目录 JSON。schema 校验失败、根结构不对或没有任何可调用条目时返回空列表。
     * `isAbstract = true` 的条目不能被直接调用，跳过。
     */
    fun parseCatalogEntries(
        rawJson: String,
        source: DreamShaderMaterialExpressionSource
    ): List<DreamShaderMaterialExpressionInfo> {
        val root = DreamShaderJson.decodeOrNull<JsonObject>(rawJson) ?: return emptyList()
        if (!isSchemaCompatible(root)) return emptyList()
        val expressions = DreamShaderJson.decodeOrNull<CatalogRootDto>(rawJson) ?: return emptyList()
        val attributes = expressions.materialAttributes.mapNotNull { attr ->
            attr.name?.let { DreamShaderMaterialAttribute(it, attr.type, attr.property, attr.aliases) }
        }
        return expressions.expressions.mapNotNull { entryFromDto(it, source)?.copy(materialAttributes = attributes) }
    }

    /** schema 字符串与版本号都匹配才接受（上游按精确匹配拒绝，插件侧降级而不是报错）。 */
    fun isSchemaCompatible(root: JsonObject): Boolean {
        val schema = (root["schema"] as? JsonPrimitive)?.takeIf { it.isString }?.content
        if (schema != SCHEMA_NAME) return false
        val version = (root["version"] as? JsonPrimitive)?.content?.toIntOrNull()
        return version == SCHEMA_VERSION
    }

    @Serializable
    private data class CatalogRootDto(
        val expressions: List<CatalogExpressionDto> = emptyList(),
        val materialAttributes: List<CatalogPinDto> = emptyList()
    )

    private fun entryFromDto(
        dto: CatalogExpressionDto,
        source: DreamShaderMaterialExpressionSource
    ): DreamShaderMaterialExpressionInfo? {
        if (dto.isAbstract) return null
        val rawClassName = dto.className?.takeIf { it.isNotBlank() }
            ?: dto.classPathName?.takeIf { it.isNotBlank() }
            ?: return null
        val namespace = dto.namespace?.takeIf { it.isNotBlank() } ?: "UE"
        val ueName = dto.shortName?.takeIf { it.isNotBlank() }
            ?: DreamShaderMaterialExpressionManifest.deriveUeName(rawClassName)

        val parameters = orderedParameters(dto)

        // 输出类型：catalog 的 value-type 拼写（Float3/Bool/Substrate/…），数值/布尔/整型
        // 归一为源码小写拼写（float3），其余保持原样（`Substrate`、`MaterialAttributes`）。
        val outputs = dto.outputs.mapNotNull { output -> output.name?.takeIf { it.isNotBlank() }?.let {
            DreamShaderMaterialExpressionOutput(it, output.type?.let(::normalizeOutputType))
        } }
        val outputType = dto.outputs.singleOrNull()?.type?.takeIf { it.isNotBlank() }?.let(::normalizeOutputType)

        return DreamShaderMaterialExpressionInfo(
            namespace = namespace,
            className = rawClassName,
            ueName = ueName,
            signature = DreamShaderMaterialExpressionManifest.synthesizeSignature(namespace, ueName, parameters, null),
            parameters = parameters,
            outputType = outputType,
            description = DreamShaderMaterialExpressionManifest.defaultDescription(namespace, ueName, rawClassName),
            snippet = DreamShaderMaterialExpressionManifest.synthesizeSnippet(namespace, ueName, parameters, null),
            aliases = dto.aliases.filter { it.isNotBlank() },
            source = source,
            outputs = outputs,
            hasInstanceDependentPins = dto.hasInstanceDependentPins,
            isParameter = dto.isParameter,
            isCustomOutput = dto.isCustomOutput
        )
    }

    /**
     * 参数列表：`inputs[]`（图引脚）在前、`properties[]`（字面量属性，同名去重）在后，
     * 再按 `positional[]` 的名字顺序把可位置传递的参数提到最前，驱动签名帮助的实参顺序。
     */
    private fun orderedParameters(dto: CatalogExpressionDto): List<DreamShaderMaterialExpressionParameter> {
        val seen = linkedSetOf<String>()
        val parameters = mutableListOf<DreamShaderMaterialExpressionParameter>()
        fun addPin(pin: CatalogPinDto, qualifier: String?) {
            val name = pin.name?.takeIf { it.isNotBlank() } ?: return
            if (!seen.add(name.lowercase())) return
            parameters.add(
                DreamShaderMaterialExpressionParameter(
                    name = name,
                    type = pin.type?.takeIf { it.isNotBlank() }?.let(::normalizeOutputType),
                    required = pin.required,
                    placeholder = "Value",
                    qualifier = qualifier,
                    enumValues = pin.enumValues,
                    defaultValue = (pin.defaultValue as? JsonPrimitive)?.content ?: pin.defaultValue?.toString(),
                    aliases = pin.aliases,
                    constProperty = pin.constProperty
                )
            )
        }
        dto.inputs.forEach { addPin(it, qualifier = null) }
        dto.properties.forEach { addPin(it, qualifier = "property") }

        val byLowerName = parameters.associateBy { it.name.lowercase() }
        if (dto.positional.isEmpty()) return parameters
        // 按 catalog `positional[]` 的列表顺序排位（如 StaticSwitchParameter 的 True/False），
        // 未列入的参数保持原顺序跟在后面。
        val positionalOrdered = dto.positional.mapNotNull { byLowerName[it.lowercase()] }
        return positionalOrdered + parameters.filter { it !in positionalOrdered }
    }

    private fun normalizeOutputType(type: String): String =
        if (type in NUMERIC_TYPE_SPELLINGS) type.lowercase() else type

    private val NUMERIC_TYPE_SPELLINGS = setOf("Float1", "Float2", "Float3", "Float4", "Bool", "Int")
}
