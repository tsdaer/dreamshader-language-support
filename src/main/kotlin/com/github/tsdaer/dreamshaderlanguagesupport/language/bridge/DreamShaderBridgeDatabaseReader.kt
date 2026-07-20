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
        if (dbPath.isNullOrBlank()) return emptyList()
        val file = File(dbPath)
        if (!file.exists()) return emptyList()

        val conn = DreamShaderBridgeDatabaseConnection.connect(dbPath) ?: return emptyList()
        return try {
            val stmt = conn.createStatement()
            val rs = stmt.executeQuery("SELECT json FROM diagnostics")
            val result = mutableListOf<DreamShaderBridgeDiagnostic>()
            while (rs.next()) {
                rs.getString("json")?.let { result.addAll(parseDiagnosticsFromJson(it)) }
            }
            rs.close()
            stmt.close()
            result
        } catch (_: Exception) {
            emptyList()
        } finally {
            DreamShaderBridgeDatabaseConnection.disconnect(conn)
        }
    }

    private fun parseDiagnosticsFromJson(rawJson: String): List<DreamShaderBridgeDiagnostic> {
        return try {
            val element = jsonParser.parseToJsonElement(rawJson)
            element.jsonObject["diagnostics"]?.jsonArray
                ?.mapNotNull { parseDiagnostic(it) }
                .orEmpty()
        } catch (_: Exception) { emptyList() }
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
