package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.random.Random

/**
 * `reveal-node` 请求/响应（上游 2.0 新动作，`Docs/tools/bridge.md` Reveal 一节）。
 *
 * 请求 `{action:"reveal-node", file, line, requestId}` 写入 `Requests/`（临时文件 + 原子
 * rename）；响应是普通 `Responses/<requestId>.json` 信封外加 `version`（负载版本，独立于
 * `protocol`）、`assetPath`、可选 `instanceAssetPath` 与 `expressions[]`。失败以
 * `ok:false` 回来，原因在 `diagnostics`（`stage:"navigate"`，码 `DSH9050`–`DSH9057`）。
 */
@Serializable
internal data class DreamShaderRevealNodeRequestDto(
    val action: String = "reveal-node",
    val file: String,
    val line: Int,
    val requestId: String
)

/** `Responses/<requestId>.json` 信封 + reveal-node 附加字段。 */
@Serializable
internal data class DreamShaderRevealNodeResponseDto(
    val protocol: Int? = null,
    val requestId: String? = null,
    val ok: Boolean = false,
    val durationMs: Long? = null,
    val message: String? = null,
    val version: Int? = null,
    val assetPath: String? = null,
    val instanceAssetPath: String? = null,
    val expressions: List<String> = emptyList(),
    val diagnostics: List<DreamShaderRevealNodeDiagnosticDto> = emptyList()
) {
    /** 额外负载字段的版本；缺失按 0 处理（旧引擎对未知动作仍回普通信封）。 */
    val payloadVersion: Int get() = version ?: 0

    /** `stage:"navigate"` 的失败诊断（DSH9050–9057）。 */
    val navigateFailureCode: String?
        get() = diagnostics.firstOrNull { it.stage.equals("navigate", ignoreCase = true) }?.code
}

@Serializable
internal data class DreamShaderRevealNodeDiagnosticDto(
    val file: String? = null,
    val line: Int = 1,
    val column: Int = 1,
    val severity: String? = null,
    val code: String? = null,
    val stage: String? = null,
    val message: String? = null,
    val assetPath: String? = null
)

/** reveal-node 请求写入器：与 preview 请求同一套临时文件 + 原子 rename 模式。 */
internal class DreamShaderRevealNodeRequestWriter {

    fun writeRevealNodeRequest(
        project: Project,
        sourceFile: String,
        line: Int,
        requestId: String
    ): Path? {
        val normalizedSource = sourceFile.replace('\\', '/')
        val activeFile = LocalFileSystem.getInstance().findFileByPath(normalizedSource)
        val requestDir = DreamShaderBridgePathResolver.resolveRequestDirectory(project, activeFile)
            ?: return null
        val dto = DreamShaderRevealNodeRequestDto(
            file = normalizedSource,
            line = line.coerceAtLeast(1),
            requestId = requestId
        )
        return runCatching {
            val dir = Path.of(requestDir)
            Files.createDirectories(dir)
            val suffix = Random.nextInt(100000, 999999)
            val target = dir.resolve("request-${System.currentTimeMillis()}-$suffix.json")
            val temporary = Files.createTempFile("dreamshader-reveal-", ".tmp")
            try {
                Files.writeString(temporary, DreamShaderJson.encodePretty(dto) + "\n", StandardCharsets.UTF_8)
                runCatching {
                    Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE)
                }.getOrElse {
                    Files.move(temporary, target)
                }
            } finally {
                Files.deleteIfExists(temporary)
            }
            target
        }.getOrNull()
    }
}

/**
 * reveal-node 响应读取器：轮询 `Responses/<requestId>.json`，读到与请求 id 匹配的信封为止。
 * 轮询参数可注入以便测试；[poll] 无 IO 异常外泄 —— 引擎不回信时返回 null。
 */
internal class DreamShaderRevealNodeResponseReader(
    private val pollIntervalMs: Long = 200,
    private val timeoutMs: Long = 10_000
) {
    fun poll(project: Project, requestId: String): DreamShaderRevealNodeResponseDto? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() <= deadline) {
            readOnce(project, requestId)?.let { return it }
            runCatching { Thread.sleep(pollIntervalMs) }
        }
        return readOnce(project, requestId)
    }

    /** 单次读取；文件缺失、信封 id 不符或 JSON 损坏都返回 null。 */
    fun readOnce(project: Project, requestId: String): DreamShaderRevealNodeResponseDto? {
        if (requestId.isBlank()) return null
        val responsesDir = responsesDirectory(project) ?: return null
        val raw = runCatching {
            Files.readString(Path.of(responsesDir, "$requestId.json"), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        val dto = DreamShaderJson.decodeOrNull<DreamShaderRevealNodeResponseDto>(raw) ?: return null
        if (!dto.requestId.equals(requestId, ignoreCase = false)) return null
        return dto
    }

    private fun responsesDirectory(project: Project): String? {
        val bridge = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, null) ?: return null
        return Path.of(bridge, "Responses").toString().replace('\\', '/')
    }
}
