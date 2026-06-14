package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.nio.charset.StandardCharsets
import java.util.Locale

/**
 * 单条枚举映射项，对应 Bridge `settings.json` 中 `mappings.<Key>[]` 的一个元素。
 */
internal data class DreamShaderSettingMapping(
    val alias: String,
    val value: Int?,
    val name: String?,
    val displayName: String?
)

/** `mappings.<Key>[]` 元素的反序列化 DTO。 */
@Serializable
private data class SettingMappingDto(
    val alias: String? = null,
    val value: Int? = null,
    val name: String? = null,
    val displayName: String? = null
)

/**
 * Bridge 设置仓库（项目级服务）。
 *
 * 读取 `Saved/DreamShader/Bridge/settings.json`，解析 `mappings` 下按设置键
 * （ShadingModel / BlendMode / MaterialDomain 等）分组的枚举映射。
 * 解析结果按小写键缓存，供补全与诊断作为「优先来源」使用，缺失时回退到硬编码默认值。
 */
@Service(Service.Level.PROJECT)
class DreamShaderBridgeSettingsRepository(private val project: Project) {
    @Volatile
    private var mappings: Map<String, List<DreamShaderSettingMapping>> = emptyMap()

    @Volatile
    private var loadedFromPath: String? = null

    @Volatile
    private var loaded: Boolean = false

    /** 按设置键（小写）返回 Bridge 提供的枚举映射，未加载或缺失时为空。 */
    internal fun mappingsForKey(key: String): List<DreamShaderSettingMapping> {
        if (!loaded) refresh(null)
        return mappings[key.lowercase(Locale.ROOT)].orEmpty()
    }

    /** 按设置键（小写）返回 Bridge 提供的合法别名集合，未提供时为空。 */
    internal fun allowedAliasesForKey(key: String): Set<String> {
        return mappingsForKey(key).map { it.alias }.filter { it.isNotBlank() }.toSet()
    }

    /** 标记缓存失效，下次访问时强制重读 `settings.json`（供 VFS 监听调用）。 */
    internal fun invalidate() {
        loaded = false
    }

    internal fun getLoadedFromPath(): String? = loadedFromPath

    internal fun refresh(activeFile: VirtualFile?): Map<String, List<DreamShaderSettingMapping>> {
        loaded = true
        val bridgeDirectory = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeFile)
        if (bridgeDirectory.isNullOrBlank()) {
            mappings = emptyMap()
            loadedFromPath = null
            return mappings
        }
        val settingsFilePath = "$bridgeDirectory/settings.json"
        loadedFromPath = settingsFilePath
        val vf: VirtualFile? = LocalFileSystem.getInstance().findFileByPath(settingsFilePath)
        if (vf == null || !vf.isValid || vf.isDirectory) {
            mappings = emptyMap()
            return mappings
        }
        val rawText = runCatching { String(vf.contentsToByteArray(), StandardCharsets.UTF_8) }
            .getOrElse {
                mappings = emptyMap()
                return mappings
            }
        mappings = parseSettingsJson(rawText)
        return mappings
    }

    internal fun parseSettingsJson(rawJson: String): Map<String, List<DreamShaderSettingMapping>> {
        val root = DreamShaderJson.decodeOrNull<JsonObject>(rawJson) ?: return emptyMap()
        val mappingsObject = (root["mappings"] as? JsonObject) ?: return emptyMap()
        val result = linkedMapOf<String, List<DreamShaderSettingMapping>>()
        for ((key, arrayElement) in mappingsObject) {
            val dtos = runCatching {
                DreamShaderJson.lenient.decodeFromJsonElement<List<SettingMappingDto>>(arrayElement)
            }.getOrNull() ?: continue
            val entries = dtos.mapNotNull { dto ->
                val alias = dto.alias?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                DreamShaderSettingMapping(
                    alias = alias,
                    value = dto.value,
                    name = dto.name,
                    displayName = dto.displayName
                )
            }
            if (entries.isNotEmpty()) result[key.lowercase(Locale.ROOT)] = entries
        }
        return result
    }
}
