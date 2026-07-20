package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.contentOrNull
import java.io.File

internal object DreamShaderBridgeDatabaseReader {

    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun readDiagnostics(
        dbPath: String?,
        sourceFileFilter: String? = null
    ): List<DreamShaderBridgeDiagnostic> {
        if (dbPath.isNullOrBlank() || !File(dbPath).exists()) {
            return emptyList()
        }

        return try {
            val conn = DreamShaderBridgeDatabaseConnection.connect(dbPath) ?: return emptyList()
            val sql = if (sourceFileFilter != null) {
                "SELECT json FROM diagnostics WHERE path = ?"
            } else {
                "SELECT json FROM diagnostics"
            }

            val stmt = if (sourceFileFilter != null) {
                conn.prepareStatement(sql).also { it.setString(1, sourceFileFilter) }
            } else {
                conn.createStatement()
            }

            val resultSet = if (sourceFileFilter != null) {
                (stmt as java.sql.PreparedStatement).executeQuery()
            } else {
                (stmt as java.sql.Statement).executeQuery(sql)
            }

            val diagnostics = mutableListOf<DreamShaderBridgeDiagnostic>()
            while (resultSet.next()) {
                val rawJson = resultSet.getString("json")
                if (rawJson != null) {
                    diagnostics.addAll(parseDiagnosticsFromJson(rawJson))
                }
            }
            resultSet.close()
            stmt.close()
            diagnostics
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun readUpdatedAt(dbPath: String?): String? {
        if (dbPath.isNullOrBlank() || !File(dbPath).exists()) {
            return null
        }
        return try {
            val conn = DreamShaderBridgeDatabaseConnection.connect(dbPath) ?: return null
            val stmt = conn.createStatement()
            val resultSet = stmt.executeQuery("SELECT value FROM meta WHERE key = 'diagnostics.updatedAt'")
            val value = if (resultSet.next()) resultSet.getString("value") else null
            resultSet.close()
            stmt.close()
            value
        } catch (_: Exception) {
            null
        }
    }

    private fun parseDiagnosticsFromJson(rawJson: String): List<DreamShaderBridgeDiagnostic> {
        return try {
            val element = jsonParser.parseToJsonElement(rawJson)
            val diagnosticsArray = element.jsonObject["diagnostics"]?.jsonArray
                ?: return emptyList()
            diagnosticsArray.mapNotNull { parseDiagnostic(it) }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseDiagnostic(element: JsonElement): DreamShaderBridgeDiagnostic? {
        val obj = element.jsonObject
        val message = obj["message"]?.jsonPrimitive?.contentOrNull ?: return null
        return DreamShaderBridgeDiagnostic(
            sourcePath = obj["path"]?.jsonPrimitive?.contentOrNull ?: "",
            line = obj["line"]?.jsonPrimitive?.intOrNull ?: 1,
            column = obj["column"]?.jsonPrimitive?.intOrNull ?: 1,
            severity = obj["severity"]?.jsonPrimitive?.contentOrNull ?: "error",
            message = message
        )
    }
}
