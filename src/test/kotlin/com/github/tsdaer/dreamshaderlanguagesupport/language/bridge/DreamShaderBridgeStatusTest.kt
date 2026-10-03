package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant

/**
 * A3.5：`status.json` 心跳与 `owner.lock` 的解析与存活判定。
 * 核心规则：busy 先于心跳年龄判断（编译暂停心跳不是崩溃）；文件缺失 = 确定离线。
 */
class DreamShaderBridgeStatusTest {

    @TempDir
    lateinit var tempDir: Path

    private val sampleStatus = """
        {
          "protocol": 1,
          "pid": 4212,
          "project": "Demo",
          "projectDir": "C:/Projects/Demo",
          "engineDir": "C:/Epic/UE_5.6",
          "pluginVersion": "2.0.2",
          "busy": false,
          "heartbeatUtc": "2026-10-03T08:00:00Z"
        }
    """.trimIndent()

    private fun writeBridgeDir(statusJson: String?): Path {
        val bridge = tempDir.resolve("Bridge")
        Files.createDirectories(bridge)
        if (statusJson != null) {
            Files.writeString(bridge.resolve("status.json"), statusJson)
        }
        return bridge
    }

    @Test
    fun `parses status identity fields`() {
        val info = DreamShaderBridgeStatus.parseStatusJson(sampleStatus)
        assertNotNull(info)
        assertEquals(1, info!!.protocol)
        assertEquals(4212L, info.pid)
        assertEquals("2.0.2", info.pluginVersion)
        assertEquals("C:/Epic/UE_5.6", info.engineDir)
        assertFalse(info.busy)
    }

    @Test
    fun `parse failures and blanks yield null`() {
        assertNull(DreamShaderBridgeStatus.parseStatusJson(null))
        assertNull(DreamShaderBridgeStatus.parseStatusJson(""))
        assertNull(DreamShaderBridgeStatus.parseStatusJson("not json"))
    }

    @Test
    fun `missing status file means definitively offline`() {
        val bridge = writeBridgeDir(null)
        val liveness = DreamShaderBridgeStatus.resolveLiveness(bridge.toString())
        assertFalse(liveness.alive)
        assertFalse(liveness.statusFilePresent)
        assertNull(liveness.status)
    }

    @Test
    fun `missing bridge dir is offline`() {
        assertFalse(DreamShaderBridgeStatus.resolveLiveness(null).alive)
        assertFalse(
            DreamShaderBridgeStatus.resolveLiveness(tempDir.resolve("absent").toString()).alive
        )
    }

    @Test
    fun `busy wins over stale heartbeat - a compile is not a crash`() {
        val busy = sampleStatus
            .replace("\"busy\": false", "\"busy\": true, \"busyAction\": \"recompile\"")
            .replace("2026-10-03T08:00:00Z", "2020-01-01T00:00:00Z")
        val bridge = writeBridgeDir(busy)

        val liveness = DreamShaderBridgeStatus.resolveLiveness(bridge.toString())
        assertTrue(liveness.alive)
        assertTrue(liveness.status?.busy == true)
        assertNull(liveness.heartbeatAgeSeconds)
    }

    @Test
    fun `fresh heartbeat is alive`() {
        val fresh = sampleStatus.replace(
            "2026-10-03T08:00:00Z",
            Instant.now().minusSeconds(2).toString()
        )
        val bridge = writeBridgeDir(fresh)
        assertTrue(DreamShaderBridgeStatus.resolveLiveness(bridge.toString()).alive)
    }

    @Test
    fun `stale heartbeat beyond 30s is dead`() {
        val stale = sampleStatus.replace(
            "2026-10-03T08:00:00Z",
            Instant.now().minusSeconds(31).toString()
        )
        val bridge = writeBridgeDir(stale)
        val liveness = DreamShaderBridgeStatus.resolveLiveness(bridge.toString())
        assertFalse(liveness.alive)
        assertTrue((liveness.heartbeatAgeSeconds ?: -1) >= 31)
    }

    @Test
    fun `unparseable heartbeat is treated conservatively as alive`() {
        val odd = sampleStatus.replace("2026-10-03T08:00:00Z", "not-a-timestamp")
        val bridge = writeBridgeDir(odd)
        assertTrue(DreamShaderBridgeStatus.resolveLiveness(bridge.toString()).alive)
    }

    @Test
    fun `parses owner lock pid and heartbeat`() {
        val lock = DreamShaderBridgeStatus.parseOwnerLock("""{ "pid": 8080, "heartbeat": "2026-10-03T07:59:58Z" }""")
        assertNotNull(lock)
        assertEquals(8080L, lock!!.pid)
        assertEquals("2026-10-03T07:59:58Z", lock.heartbeatUtc)
        assertNull(DreamShaderBridgeStatus.parseOwnerLock("garbage"))
    }

    @Test
    fun `parses iso utc heartbeat`() {
        assertEquals(
            Instant.parse("2026-08-02T09:14:22Z"),
            DreamShaderBridgeStatus.parseHeartbeat("2026-08-02T09:14:22Z")
        )
        assertNull(DreamShaderBridgeStatus.parseHeartbeat(null))
        assertNull(DreamShaderBridgeStatus.parseHeartbeat(""))
    }
}
