package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Project service that reads `Saved/DreamShader/Bridge/diagnostics.json`,
 * normalizes entries, and exposes per-file diagnostics snapshots.
 *
 * Parser accepts two upstream JSON shapes:
 * - array root: `[ {...}, ... ]`
 * - object root containing `"diagnostics": [ ... ]`
 */
@Service(Service.Level.PROJECT)
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
        val text = rawJson.trim()
        if (text.isBlank()) return emptyList()

        val objectTexts = when {
            text.startsWith("[") -> extractTopLevelObjects(text)
            text.startsWith("{") && text.contains("\"diagnostics\"") -> {
                val diagnosticsArray = extractArrayForField(text, "diagnostics")
                if (diagnosticsArray.isNullOrBlank()) emptyList() else extractTopLevelObjects(diagnosticsArray)
            }
            text.startsWith("{") -> listOf(text)
            else -> emptyList()
        }
        if (objectTexts.isEmpty()) return emptyList()

        return objectTexts.mapNotNull { parseDiagnosticObject(it) }
    }

    private fun parseDiagnosticObject(objText: String): DreamShaderBridgeDiagnostic? {
        val source = findStringField(objText, listOf("sourcePath", "file", "path")) ?: return null
        val line = findIntField(objText, listOf("line", "lineNumber")) ?: 1
        val column = findIntField(objText, listOf("column", "col")) ?: 1
        val severity = findStringField(objText, listOf("severity", "level")) ?: "error"
        val message = findStringField(objText, listOf("message", "text", "msg")) ?: return null
        return DreamShaderBridgeDiagnostic(
            sourcePath = normalizePath(source),
            line = if (line < 1) 1 else line,
            column = if (column < 1) 1 else column,
            severity = severity.lowercase(),
            message = message
        )
    }

    private fun extractArrayForField(text: String, field: String): String? {
        val index = text.indexOf("\"$field\"")
        if (index < 0) return null
        val openBracket = text.indexOf('[', index)
        if (openBracket < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in openBracket until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(openBracket, i + 1)
                }
            }
        }
        return null
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
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
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

    private fun findStringField(text: String, names: List<String>): String? {
        names.forEach { name ->
            val regex = Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""", setOf(RegexOption.IGNORE_CASE))
            val match = regex.find(text) ?: return@forEach
            return unescapeJsonString(match.groupValues[1])
        }
        return null
    }

    private fun findIntField(text: String, names: List<String>): Int? {
        names.forEach { name ->
            val regex = Regex(""""$name"\s*:\s*(-?\d+)""", setOf(RegexOption.IGNORE_CASE))
            val match = regex.find(text) ?: return@forEach
            return match.groupValues[1].toIntOrNull()
        }
        return null
    }

    private fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/')
    }
}
