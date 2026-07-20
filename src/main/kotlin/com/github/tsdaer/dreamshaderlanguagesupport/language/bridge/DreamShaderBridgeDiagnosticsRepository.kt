package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import java.io.File
import java.nio.charset.StandardCharsets

/** 单条诊断的反序列化 DTO，字段别名在 [DreamShaderBridgeDiagnosticsRepository.toDiagnostic] 归一化。 */
@Serializable
private data class BridgeDiagnosticDto(
    val sourcePath: String? = null,
    val file: String? = null,
    val path: String? = null,
    val line: Int? = null,
    val lineNumber: Int? = null,
    val column: Int? = null,
    val col: Int? = null,
    val severity: String? = null,
    val level: String? = null,
    val message: String? = null,
    val text: String? = null,
    val msg: String? = null
)

/** `{ "files": [ { "path": ..., "diagnostics": [...] } ] }` 中的单个 file 分组。 */
@Serializable
private data class BridgeDiagnosticFileGroupDto(
    val path: String? = null,
    val file: String? = null,
    val sourcePath: String? = null,
    val diagnostics: List<BridgeDiagnosticDto> = emptyList()
)

/**
 * Bridge 诊断仓库（项目级服务）。
 *
 * 负责读取 `Saved/DreamShader/Bridge/diagnostics.json`，将诊断项归一化后缓存，
 * 并按文件路径提供过滤后的诊断快照。
 *
 * 支持三种上游 JSON 结构：
 * - 数组根：`[ {...}, ... ]`
 * - 对象根（扁平）：`{ "diagnostics": [ ... ] }`
 * - 对象根（按文件分组）：`{ "files": [ { "path": "...", "diagnostics": [ ... ] } ] }`
 *   此结构下每条诊断自身不带路径，继承所属 file 的 `path`。
 */
@Service(Service.Level.PROJECT)
/**
 * Implementation of DreamShaderBridgeDiagnosticsRepository.
 */
class DreamShaderBridgeDiagnosticsRepository(private val project: Project) {
    @Volatile
    private var snapshot: DreamShaderBridgeDiagnosticsSnapshot = DreamShaderBridgeDiagnosticsSnapshot(
        diagnostics = emptyList(),
        loadedFromPath = null
    )

    internal fun refresh(activeFile: VirtualFile?): DreamShaderBridgeDiagnosticsSnapshot {
        val bridgeDirectory = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeFile)
        if (bridgeDirectory.isNullOrBlank()) {
            snapshot = DreamShaderBridgeDiagnosticsSnapshot(emptyList(), null)
            return snapshot
        }

        // Try SQLite database first (upstream v1.5.0+ transport)
        val dbPath = "$bridgeDirectory/bridge.db"
        val dbFile = java.io.File(dbPath)
        if (dbFile.exists()) {
            val diagnosticsFromDb = DreamShaderBridgeDatabaseReader.readDiagnostics(dbPath)
            if (diagnosticsFromDb.isNotEmpty()) {
                snapshot = DreamShaderBridgeDiagnosticsSnapshot(diagnosticsFromDb, dbPath.replace('\\', '/'))
                return snapshot
            }
            // Database exists but diagnostics table is empty — Unreal-side write may have
            // failed (e.g. relative-path I/O error). Fall through to JSON file.
        }

        // Fall back to JSON file (legacy transport)
        val diagnosticsFilePath = "$bridgeDirectory/diagnostics.json"
        val vf = LocalFileSystem.getInstance().findFileByPath(diagnosticsFilePath)
        if (vf == null || !vf.isValid || vf.isDirectory) {
            snapshot = DreamShaderBridgeDiagnosticsSnapshot(emptyList(), diagnosticsFilePath.replace('\\', '/'))
            return snapshot
        }
        val rawText = runCatching { String(vf.contentsToByteArray(), StandardCharsets.UTF_8) }
            .getOrElse {
                snapshot = DreamShaderBridgeDiagnosticsSnapshot(emptyList(), diagnosticsFilePath.replace('\\', '/'))
                return snapshot
            }
        val parsed = parseDiagnosticsJson(rawText)
        snapshot = DreamShaderBridgeDiagnosticsSnapshot(parsed, diagnosticsFilePath.replace('\\', '/'))
        return snapshot
    }

    internal fun getSnapshot(): DreamShaderBridgeDiagnosticsSnapshot = snapshot

    internal fun diagnosticsForFile(file: VirtualFile): List<DreamShaderBridgeDiagnostic> {
        val currentPath = normalizePath(file.path)
        val currentName = file.name
        return snapshot.diagnostics.filter { diagnostic ->
            val source = normalizePath(diagnostic.sourcePath)
            source == currentPath ||
                source.endsWith("/$currentName") ||
                (File(source).name == currentName)
        }
    }

    internal fun parseDiagnosticsJson(rawJson: String): List<DreamShaderBridgeDiagnostic> {
        val root = DreamShaderJson.decodeOrNull<JsonElement>(rawJson) ?: return emptyList()

        // 按文件分组的结构（Bridge 默认产物）：诊断对象不带路径，继承父级 file 的 path。
        if (root is JsonObject && root["files"] is JsonArray) {
            val grouped = parseGroupedByFile(root["files"] as JsonArray)
            if (grouped.isNotEmpty()) return grouped
        }

        val diagnosticElements: List<JsonElement> = when {
            root is JsonArray -> root
            root is JsonObject && root["diagnostics"] is JsonArray -> root["diagnostics"] as JsonArray
            root is JsonObject -> listOf(root)
            else -> emptyList()
        }

        return diagnosticElements.mapNotNull { element ->
            decodeDiagnostic(element)?.let { toDiagnostic(it, fallbackPath = null) }
        }
    }

    /**
     * 解析 `{ "files": [ { "path": "...", "diagnostics": [...] } ] }` 结构。
     * 每个 file 对象提供 `path`，其下诊断对象若自身缺少路径字段则继承之。
     */
    private fun parseGroupedByFile(filesArray: JsonArray): List<DreamShaderBridgeDiagnostic> {
        val result = mutableListOf<DreamShaderBridgeDiagnostic>()
        filesArray.forEach { element ->
            val group = runCatching {
                DreamShaderJson.lenient.decodeFromJsonElement<BridgeDiagnosticFileGroupDto>(element)
            }.getOrNull() ?: return@forEach
            val filePath = group.path ?: group.file ?: group.sourcePath
            group.diagnostics.forEach { dto ->
                toDiagnostic(dto, fallbackPath = filePath)?.let(result::add)
            }
        }
        return result
    }

    private fun decodeDiagnostic(element: JsonElement): BridgeDiagnosticDto? = runCatching {
        DreamShaderJson.lenient.decodeFromJsonElement<BridgeDiagnosticDto>(element)
    }.getOrNull()

    private fun toDiagnostic(dto: BridgeDiagnosticDto, fallbackPath: String?): DreamShaderBridgeDiagnostic? {
        val source = dto.sourcePath ?: dto.file ?: dto.path ?: fallbackPath ?: return null
        val message = dto.message ?: dto.text ?: dto.msg ?: return null
        val line = dto.line ?: dto.lineNumber ?: 1
        val column = dto.column ?: dto.col ?: 1
        val severity = dto.severity ?: dto.level ?: "error"
        return DreamShaderBridgeDiagnostic(
            sourcePath = normalizePath(source),
            line = if (line < 1) 1 else line,
            column = if (column < 1) 1 else column,
            severity = severity.lowercase(),
            message = message
        )
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/')
    }
}
