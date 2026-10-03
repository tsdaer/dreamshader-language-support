package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import kotlinx.serialization.Serializable
import java.io.File
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeParseException

/**
 * `bridge.db` 的 `meta` 表读取（B6 修复：架构文档声称读 meta，此前代码未读）。
 *
 * meta 表是 `key TEXT PRIMARY KEY, value TEXT NOT NULL` 的键值对（上游写入
 * `settings.generatedAt` / `materialExpressions.generatedAt` / `substrateBuiltins.generatedAt`
 * / `diagnostics.updatedAt` 等行）。读取为通用 map：未知键原样暴露，缺表/坏库返回空 map。
 */
internal object DreamShaderBridgeMetaReader {

    /** 已知的 meta 键；仅作常量文档，读取对任意键开放。 */
    const val KEY_SETTINGS_GENERATED_AT = "settings.generatedAt"
    const val KEY_MATERIAL_EXPRESSIONS_GENERATED_AT = "materialExpressions.generatedAt"
    const val KEY_SUBSTRATE_BUILTINS_GENERATED_AT = "substrateBuiltins.generatedAt"
    const val KEY_DIAGNOSTICS_UPDATED_AT = "diagnostics.updatedAt"

    fun readMeta(dbPath: String?): Map<String, String> {
        if (dbPath.isNullOrBlank()) return emptyMap()
        val file = File(dbPath)
        if (!file.exists() || !file.isFile) return emptyMap()
        val conn = DreamShaderBridgeDatabaseConnection.connect(dbPath) ?: return emptyMap()
        return try {
            val stmt = conn.createStatement()
            stmt.executeQuery("SELECT key, value FROM meta").use { rs ->
                val result = linkedMapOf<String, String>()
                while (rs.next()) {
                    val key = rs.getString("key") ?: continue
                    val value = rs.getString("value") ?: continue
                    if (key.isNotBlank()) result[key] = value
                }
                result
            }.also {
                stmt.close()
            }
        } catch (_: Exception) {
            emptyMap()
        } finally {
            DreamShaderBridgeDatabaseConnection.disconnect(conn)
        }
    }
}

/**
 * `status.json` 心跳与 `owner.lock` 的解析与存活判定（A3.5）。
 *
 * 上游规则（`Docs/tools/bridge.md`）：
 * - `status.json` 空闲时每 2s 重写、关停即删除 —— 文件缺失意味着「确定未运行」；
 * - 读 `busy` 要先于心跳年龄判断：编译会阻塞游戏线程并暂停心跳，只看时间戳会把每次真实
 *   编译误读为崩溃；
 * - `owner.lock` 在持有进程仍在运行且心跳不超过 30s 时有效。
 */
internal object DreamShaderBridgeStatus {

    /** `owner.lock` 心跳有效窗口（上游：under 30s old）。 */
    val HEARTBEAT_STALE_AFTER: Duration = Duration.ofSeconds(30)

    @Serializable
    internal data class StatusInfo(
        val protocol: Int? = null,
        val pid: Long? = null,
        val project: String? = null,
        val projectDir: String? = null,
        val engineDir: String? = null,
        val pluginVersion: String? = null,
        val busy: Boolean = false,
        val busyAction: String? = null,
        val lastResult: String? = null,
        val heartbeatUtc: String? = null
    )

    internal data class OwnerLockInfo(
        val pid: Long? = null,
        val heartbeatUtc: String? = null
    )

    /** 编辑器存活判定结果。 */
    internal data class Liveness(
        /** status.json 存在（心跳文件在）。 */
        val statusFilePresent: Boolean,
        /** 综合判定：status.json 在且（busy 或心跳未过期）。 */
        val alive: Boolean,
        val status: StatusInfo?,
        /** status.json 在但心跳过期的秒数；null 表示未过期或不适用。 */
        val heartbeatAgeSeconds: Long? = null
    )

    fun parseStatusJson(raw: String?): StatusInfo? {
        if (raw.isNullOrBlank()) return null
        return DreamShaderJson.decodeOrNull<StatusInfo>(raw)
    }

    fun parseOwnerLock(raw: String?): OwnerLockInfo? {
        if (raw.isNullOrBlank()) return null
        val dto = DreamShaderJson.decodeOrNull<OwnerLockDto>(raw) ?: return null
        return OwnerLockInfo(pid = dto.pid, heartbeatUtc = dto.heartbeat)
    }

    @Serializable
    private data class OwnerLockDto(val pid: Long? = null, val heartbeat: String? = null)

    /** 解析 ISO-8601 UTC 时间戳（`2026-08-02T09:14:22Z`）；非法返回 null。 */
    fun parseHeartbeat(rawUtc: String?): Instant? {
        if (rawUtc.isNullOrBlank()) return null
        return runCatching { Instant.parse(rawUtc) }
            .recoverCatching { OffsetDateTime.parse(rawUtc).toInstant() }
            .getOrNull()
    }

    /**
     * 存活判定。`bridgeDir` 下读 `status.json`：
     * - 缺失 → 死（上游关停即删除，缺席是确定信号）；
     * - `busy = true` → 活（编译暂停心跳不是崩溃）；
     * - 心跳缺失 → 活（保守：文件在即视作在，避免把刚启动的编辑器误判为死）；
     * - 心跳年龄 ≤ 30s → 活；否则死（硬崩溃残留）。
     */
    fun resolveLiveness(bridgeDir: String?, now: Instant = Instant.now()): Liveness {
        if (bridgeDir.isNullOrBlank()) return Liveness(false, false, null, null)
        val raw = runCatching {
            File(bridgeDir, "status.json").readText(Charsets.UTF_8)
        }.getOrNull()
        val info = parseStatusJson(raw)
        if (raw == null || info == null) {
            return Liveness(false, false, null, null)
        }
        if (info.busy) return Liveness(true, true, info, null)

        val heartbeat = parseHeartbeat(info.heartbeatUtc)
            ?: return Liveness(true, true, info, null)
        val age = Duration.between(heartbeat, now)
        if (age.isNegative) return Liveness(true, true, info, null)
        val alive = age <= HEARTBEAT_STALE_AFTER
        return Liveness(true, alive, info, if (alive) null else age.seconds)
    }
}
