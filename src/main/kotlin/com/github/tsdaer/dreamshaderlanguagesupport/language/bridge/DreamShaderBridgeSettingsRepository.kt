package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
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
        val text = rawJson.trim()
        if (text.isBlank() || !text.contains("\"mappings\"")) return emptyMap()
        val mappingsObject = extractObjectForField(text, "mappings") ?: return emptyMap()
        val result = linkedMapOf<String, List<DreamShaderSettingMapping>>()
        extractObjectArrayFields(mappingsObject).forEach { (key, arrayText) ->
            val entries = extractTopLevelObjects(arrayText).mapNotNull(::parseMappingObject)
            if (entries.isNotEmpty()) result[key.lowercase(Locale.ROOT)] = entries
        }
        return result
    }

    private fun parseMappingObject(objText: String): DreamShaderSettingMapping? {
        val alias = findStringField(objText, "alias") ?: return null
        return DreamShaderSettingMapping(
            alias = alias,
            value = findIntField(objText, "value"),
            name = findStringField(objText, "name"),
            displayName = findStringField(objText, "displayName")
        )
    }

    /** 提取 `"<field>": { ... }` 对象体（含外层花括号）。 */
    private fun extractObjectForField(text: String, field: String): String? {
        val index = text.indexOf("\"$field\"")
        if (index < 0) return null
        val openBrace = text.indexOf('{', index)
        if (openBrace < 0) return null
        val close = findMatchingBracket(text, openBrace, '{', '}') ?: return null
        return text.substring(openBrace, close + 1)
    }

    /**
     * 在一个对象体内，找出所有 `"<key>": [ ... ]` 顶层字段，返回 key→数组体（含方括号）。
     * 用于解析 `mappings` 下按设置键分组的数组。
     */
    private fun extractObjectArrayFields(objectText: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val regex = Regex(""""([A-Za-z_][A-Za-z0-9_]*)"\s*:\s*\[""")
        var searchFrom = 0
        while (true) {
            val match = regex.find(objectText, searchFrom) ?: break
            val openBracket = objectText.indexOf('[', match.range.first)
            val close = findMatchingBracket(objectText, openBracket, '[', ']')
            if (close == null) {
                searchFrom = match.range.last + 1
                continue
            }
            result.add(match.groupValues[1] to objectText.substring(openBracket, close + 1))
            searchFrom = close + 1
        }
        return result
    }

    private fun extractTopLevelObjects(arrayOrObjectText: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var objStart = -1
        var inString = false
        var escaped = false
        for (i in arrayOrObjectText.indices) {
            val ch = arrayOrObjectText[i]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objStart >= 0) {
                        result.add(arrayOrObjectText.substring(objStart, i + 1))
                        objStart = -1
                    }
                }
            }
        }
        return result
    }

    private fun findMatchingBracket(text: String, leftOffset: Int, left: Char, right: Char): Int? {
        var depth = 0
        var inString = false
        var escaped = false
        var index = leftOffset
        while (index < text.length) {
            val ch = text[index]
            if (inString) {
                if (escaped) escaped = false
                else if (ch == '\\') escaped = true
                else if (ch == '"') inString = false
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

    private fun findStringField(text: String, name: String): String? {
        val regex = Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""", setOf(RegexOption.IGNORE_CASE))
        val match = regex.find(text) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun findIntField(text: String, name: String): Int? {
        val regex = Regex(""""$name"\s*:\s*(-?\d+)""", setOf(RegexOption.IGNORE_CASE))
        val match = regex.find(text) ?: return null
        return match.groupValues[1].toIntOrNull()
    }

    private fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }
}
