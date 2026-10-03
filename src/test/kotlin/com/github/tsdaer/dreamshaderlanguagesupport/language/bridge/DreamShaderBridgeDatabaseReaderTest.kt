package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files

/**
 * B4 test debt: the bridge.db diagnostics reader gets a dedicated suite backed by a real
 * (temporary-file) SQLite database, mirroring the upstream table shape
 * `diagnostics(path, json, updated_at_utc)`.
 */
class DreamShaderBridgeDatabaseReaderTest {

    @Test
    fun readsDiagnosticRowsFromRealiteDatabase() {
        val db = Files.createTempFile("dreamshader-bridge", ".db").also { Files.delete(it) }
        try {
            newConnection(db).use { conn ->
                conn.createStatement().execute("CREATE TABLE diagnostics (path TEXT, json TEXT, updated_at_utc TEXT)")
                insertRow(
                    conn,
                    "I:/Proj/DShader/Materials/M_Red.dsm",
                    """
                    {
                      "path": "I:/Proj/DShader/Materials/M_Red.dsm",
                      "diagnostics": [
                        {
                          "line": 12, "column": 5, "severity": "error",
                          "message": "Unknown Graph identifier 'Tin'.",
                          "code": "DSH4301", "stage": "graph",
                          "detail": "declared names: Tin2", "assetPath": "/Game/Materials/M_Red",
                          "source": "compile", "shaderPlatform": "SM5", "qualityLevel": "Medium"
                        },
                        { "line": 3, "message": "missing severity defaults to error" }
                      ]
                    }
                    """.trimIndent()
                )
                insertRow(
                    conn,
                    "I:/Proj/DShader/Functions/MF_Scale.dsf",
                    """{"path": "I:/Proj/DShader/Functions/MF_Scale.dsf", "diagnostics": []}"""
                )
                insertRow(conn, "broken-row", "not json at all")
            }

            val diagnostics = DreamShaderBridgeDatabaseReader.readDiagnostics(db.toString())

            assertEquals(2, diagnostics.size)
            val first = diagnostics.first { it.code == "DSH4301" }
            assertEquals("I:/Proj/DShader/Materials/M_Red.dsm", first.sourcePath)
            assertEquals(12, first.line)
            assertEquals(5, first.column)
            assertEquals("error", first.severity)
            assertEquals("graph", first.stage)
            assertEquals("/Game/Materials/M_Red", first.assetPath)
            assertEquals("SM5", first.shaderPlatform)
            assertEquals("Medium", first.qualityLevel)

            val second = diagnostics.first { it.code == null }
            assertEquals(3, second.line)
            assertEquals(1, second.column)
            assertEquals("error", second.severity, "missing severity falls back to error")
        } finally {
            Files.deleteIfExists(db)
        }
    }

    @Test
    fun missingOrBlankPathAndMissingFileDegradeToEmpty() {
        assertTrue(DreamShaderBridgeDatabaseReader.readDiagnostics(null).isEmpty())
        assertTrue(DreamShaderBridgeDatabaseReader.readDiagnostics("  ").isEmpty())
        assertTrue(DreamShaderBridgeDatabaseReader.readDiagnostics("Z:/no/such/bridge.db").isEmpty())
    }

    @Test
    fun nonDiagnosticsSchemaDegradesToEmptyWithoutThrowing() {
        val db = Files.createTempFile("dreamshader-bridge-wrong", ".db").also { Files.delete(it) }
        try {
            newConnection(db).use { conn ->
                conn.createStatement().execute("CREATE TABLE unrelated (a TEXT)")
                conn.createStatement().execute("INSERT INTO unrelated VALUES ('x')")
            }
            assertTrue(DreamShaderBridgeDatabaseReader.readDiagnostics(db.toString()).isEmpty())
        } finally {
            Files.deleteIfExists(db)
        }
    }

    private fun newConnection(db: java.nio.file.Path): java.sql.Connection =
        java.sql.DriverManager.getConnection("jdbc:sqlite:${db.toAbsolutePath().toString().replace('\\', '/')}")

    private fun insertRow(conn: java.sql.Connection, path: String, json: String) {
        conn.prepareStatement("INSERT INTO diagnostics (path, json, updated_at_utc) VALUES (?, ?, ?)").use { stmt ->
            stmt.setString(1, path)
            stmt.setString(2, json)
            stmt.setString(3, "2026-10-03T00:00:00Z")
            stmt.executeUpdate()
        }
    }
}
