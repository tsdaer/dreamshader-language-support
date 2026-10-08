package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * `dreamshader-symbol-index` v1（上游 2.0 每次编译后刷新的符号索引工件）的只读 DTO 与解析。
 *
 * schema 为 `dreamshader-symbol-index` / version 1（上游 `Semantic/LangSymbolIndex.cpp`
 * 的 `BuildDreamShaderSymbolIndexJson` 产物）。按 6.4 红线 6：schema 或版本不符即视为
 * 无数据，调用方降级到 PSI 自身，不抛错。
 */
internal object DreamShaderSymbolIndexFormat {
    const val SCHEMA_NAME = "dreamshader-symbol-index"
    const val SCHEMA_VERSION = 1
}

/** 索引内所有 span 共用的定位信息（1-based 行列 + 文本区间）。 */
@Serializable
internal data class DreamShaderSymbolIndexSpan(
    val file: String? = null,
    val line: Int = 1,
    val column: Int = 1,
    val offset: Int = 0,
    val length: Int = 0
)

/** `declarations[]` 元素：struct/field/uniform/constant/function/parameter 六种 kind。 */
@Serializable
internal data class DreamShaderSymbolIndexDeclaration(
    val kind: String? = null,
    val name: String? = null,
    val signature: String? = null,
    val type: String? = null,
    val doc: String? = null,
    val container: String? = null,
    val role: String? = null,
    val asset: String? = null,
    val library: String? = null,
    val pin: String? = null,
    val file: String? = null,
    val line: Int = 1,
    val column: Int = 1,
    val offset: Int = 0,
    val length: Int = 0
)

/** `references[]` 分组：一个名字与它的全部提及 span。 */
@Serializable
internal data class DreamShaderSymbolIndexReference(
    val name: String? = null,
    val spans: List<DreamShaderSymbolIndexSpan> = emptyList()
)

/** `parameters[]` 元素：uniform 参数的 @ 指令折叠结果与名字 span。 */
@Serializable
internal data class DreamShaderSymbolIndexParameter(
    val name: String? = null,
    val variable: String? = null,
    val type: String? = null,
    val group: String? = null,
    val desc: String? = null,
    val sampler: String? = null,
    val defaultAsset: String? = null,
    val static: Boolean = false,
    val file: String? = null,
    val line: Int = 1,
    val column: Int = 1,
    val offset: Int = 0,
    val length: Int = 0
)

/** `.dsi` 专属 `instance` 节（父材质引用、settings、overrides、父参数 schema）。 */
@Serializable
internal data class DreamShaderSymbolIndexInstance(
    val parent: String? = null,
    val parentObject: String? = null,
    val overrides: List<DreamShaderSymbolIndexOverride> = emptyList(),
    val schema: DreamShaderSymbolIndexInstanceSchema? = null
)

@Serializable
internal data class DreamShaderSymbolIndexOverride(
    val name: String? = null,
    val variable: String? = null,
    val kind: String? = null,
    val schemaIndex: Int = -1
)

@Serializable
internal data class DreamShaderSymbolIndexInstanceSchema(
    val valid: Boolean = false,
    val origin: String? = null,
    val parameters: List<DreamShaderSymbolIndexSchemaParameter> = emptyList()
)

@Serializable
internal data class DreamShaderSymbolIndexSchemaParameter(
    val name: String? = null,
    val kind: String? = null,
    val type: String? = null,
    val group: String? = null
)

/** 索引根 DTO；`instance` 仅 `.dsi` 源携带。 */
@Serializable
internal data class DreamShaderSymbolIndexData(
    val schema: String? = null,
    val version: Int = 0,
    val file: String? = null,
    val declarations: List<DreamShaderSymbolIndexDeclaration> = emptyList(),
    val references: List<DreamShaderSymbolIndexReference> = emptyList(),
    val includes: List<String> = emptyList(),
    val parameters: List<DreamShaderSymbolIndexParameter> = emptyList(),
    val instance: DreamShaderSymbolIndexInstance? = null
) {
    fun isSchemaCompatible(): Boolean =
        schema == DreamShaderSymbolIndexFormat.SCHEMA_NAME && version == DreamShaderSymbolIndexFormat.SCHEMA_VERSION
}

/** 一次解析的结果：数据 + 来源路径；`data == null` 表示工件缺失或 schema 不符。 */
internal data class DreamShaderSymbolIndexSnapshot(
    val data: DreamShaderSymbolIndexData?,
    val loadedFromPath: String?
)

/**
 * 符号索引仓库（项目级服务，只读）。
 *
 * 解析 `<projectRoot>/Saved/DreamShader/Index/<root>/<source>.index.json`（`<root>` 为
 * `Project` 或插件名）。按 (路径, mtime, size) 缓存，工件被引擎重写后自动重新解析；
 * 不存在或 schema 不符时返回空快照，绝不落诊断（权威在引擎侧）。
 */
@Service(Service.Level.PROJECT)
class DreamShaderSymbolIndexRepository(private val project: Project) {

    private val cache = java.util.Collections.synchronizedMap(object : LinkedHashMap<String, CacheEntry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, CacheEntry>?): Boolean = size > 128
    })

    private data class CacheEntry(val path: String, val stamp: String, val snapshot: DreamShaderSymbolIndexSnapshot)

    /** 解析 [sourceFile] 的符号索引；无 Bridge 数据时返回 `data == null` 的快照。 */
    internal fun indexForSource(sourceFile: VirtualFile?): DreamShaderSymbolIndexSnapshot {
        val path = symbolIndexPath(sourceFile) ?: return DreamShaderSymbolIndexSnapshot(null, null)
        return indexForPath(path)
    }

    /** 按磁盘路径读取索引，带 (路径, mtime, size) 缓存。 */
    internal fun indexForPath(indexPath: String): DreamShaderSymbolIndexSnapshot {
        val file = File(indexPath)
        if (!file.exists() || !file.isFile) {
            cache.remove(indexPath)
            return DreamShaderSymbolIndexSnapshot(null, indexPath)
        }
        val stamp = "${file.lastModified()}:${file.length()}"
        cache[indexPath]?.let { cached ->
            if (cached.path == indexPath && cached.stamp == stamp) return cached.snapshot
        }
        val raw = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull()
            ?: return DreamShaderSymbolIndexSnapshot(null, indexPath)
        val data = DreamShaderJson.decodeOrNull<DreamShaderSymbolIndexData>(raw)
            ?.takeIf { it.isSchemaCompatible() }
        val snapshot = DreamShaderSymbolIndexSnapshot(data, indexPath)
        cache[indexPath] = CacheEntry(indexPath, stamp, snapshot)
        return snapshot
    }

    /**
     * 源文件对应的索引文件路径：`<root>/Saved/DreamShader/Index/<rootName>/<rel>.index.json`。
     * `<rootName>` 上游取 `Project` 或插件名；插件侧一律按 `Project` 解析（引擎产生的
     * 插件内容源不落在用户项目内），未锚定到 UE 项目根时返回 null。
     */
    fun symbolIndexPath(sourceFile: VirtualFile?): String? {
        if (sourceFile == null) return null
        val root = DreamShaderBridgePathResolver.resolveProjectRoot(project, sourceFile) ?: return null
        val relative = relativePathUnderRoot(sourceFile.path, root) ?: return null
        return normalizePath(File(File(File(root, "Saved"), "DreamShader"), "Index").path) +
            "/Project/$relative.index.json"
    }

    private fun relativePathUnderRoot(sourcePath: String, projectRoot: String): String? {
        val normalizedSource = normalizePath(sourcePath)
        val normalizedRoot = normalizePath(projectRoot)
        return when {
            normalizedSource.equals(normalizedRoot, ignoreCase = true) -> null
            normalizedSource.startsWith("$normalizedRoot/", ignoreCase = true) ->
                normalizedSource.substring(normalizedRoot.length + 1)
            else -> null
        }
    }

    private fun normalizePath(path: String): String = path.replace('\\', '/').trimEnd('/')
}
