package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * 全插件统一的 JSON 编解码入口。
 *
 * 所有 JSON 读写都应走这里，替代历史上各文件各自手写的正则 + 括号配对扫描。
 * 解析端采用宽容配置（容忍未知字段、宽松语法、null 强制为默认值），以保留旧手写
 * 解析器「坏数据静默降级」而非抛错的行为。
 */
internal object DreamShaderJson {
    /** 宽容解析实例：用于读取来自 Bridge / 远程索引 / GitHub API 等不完全可控的 JSON。 */
    val lenient: Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    /** 美化输出实例：用于写出插件自身产物（lock / package metadata），保持人类可读。 */
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    val pretty: Json = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        explicitNulls = false
        encodeDefaults = true
    }

    /**
     * 将 [raw] 反序列化为 [T]，失败（空串、非法 JSON、结构不符）时返回 null，
     * 复刻旧解析器「坏数据返回空」的容错语义，免去每个调用点重复 runCatching。
     */
    inline fun <reified T> decodeOrNull(raw: String): T? {
        if (raw.isBlank()) return null
        return runCatching { lenient.decodeFromString<T>(raw) }.getOrNull()
    }

    /** 用美化实例序列化 [value]，供写出插件自身 JSON 产物。 */
    inline fun <reified T> encodePretty(value: T): String = pretty.encodeToString(value)
}
