package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.sql.DriverManager

/**
 * B6/A3.3：`bridge.db` 的 `meta` 表读取。上游 meta 是 `key/value` 两列表，
 * 缺表、坏库、缺失文件都应静默降级为空 map。
 */
class DreamShaderBridgeMetaReaderTest {

    @TempDir
    lateinit var tempDir: Path

    private fun createDbWithMeta(): Path {
        val db = tempDir.resolve("bridge.db")
        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("CREATE TABLE meta(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                stmt.executeUpdate("INSERT INTO meta(key, value) VALUES ('diagnostics.updatedAt', '2026-10-03T08:00:00Z')")
                stmt.executeUpdate("INSERT INTO meta(key, value) VALUES ('settings.generatedAt', '2026-10-03T07:59:00Z')")
                stmt.executeUpdate("INSERT INTO meta(key, value) VALUES ('protocol', '1')")
                stmt.executeUpdate("INSERT INTO meta(key, value) VALUES ('pluginVersion', '2.0.2')")
                stmt.executeUpdate("INSERT INTO meta(key, value) VALUES ('engineDir', 'C:/Epic/UE_5.6')")
            }
        }
        return db
    }

    @Test
    fun `reads all meta key-value pairs`() {
        val meta = DreamShaderBridgeMetaReader.readMeta(createDbWithMeta().toAbsolutePath().toString())

        assertEquals("1", meta["protocol"])
        assertEquals("2.0.2", meta["pluginVersion"])
        assertEquals("C:/Epic/UE_5.6", meta["engineDir"])
        assertEquals("2026-10-03T08:00:00Z", meta[DreamShaderBridgeMetaReader.KEY_DIAGNOSTICS_UPDATED_AT])
        assertEquals(5, meta.size)
    }

    @Test
    fun `missing meta table degrades to empty map`() {
        val db = tempDir.resolve("bare.db")
        DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath()}").use { conn ->
            conn.createStatement().use { stmt ->
                stmt.executeUpdate("CREATE TABLE diagnostics(path TEXT PRIMARY KEY, json TEXT NOT NULL, updated_at_utc TEXT NOT NULL)")
            }
        }
        val meta = DreamShaderBridgeMetaReader.readMeta(db.toAbsolutePath().toString())
        assertTrue(meta.isEmpty())
    }

    @Test
    fun `missing file and blank path degrade to empty map`() {
        assertTrue(DreamShaderBridgeMetaReader.readMeta(tempDir.resolve("nope.db").toString()).isEmpty())
        assertTrue(DreamShaderBridgeMetaReader.readMeta(null).isEmpty())
        assertTrue(DreamShaderBridgeMetaReader.readMeta("  ").isEmpty())
    }
}
