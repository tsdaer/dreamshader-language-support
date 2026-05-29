package com.github.tsdaer.dreamshaderlanguagesupport.language.settings

import java.util.Locale

internal enum class DreamShaderHoverOverrideIssueType {
    MISSING_EQUALS,
    EMPTY_KEY,
    EMPTY_VALUE
}

internal data class DreamShaderHoverOverrideIssue(
    val lineNumber: Int,
    val source: String,
    val type: DreamShaderHoverOverrideIssueType
)

internal data class DreamShaderHoverOverrideParseResult(
    val entries: Map<String, String>,
    val ignoredLineCount: Int,
    val duplicateKeyCount: Int,
    val issues: List<DreamShaderHoverOverrideIssue>
)

internal object DreamShaderHoverOverrideParser {
    fun parse(raw: String): DreamShaderHoverOverrideParseResult {
        val parsed = linkedMapOf<String, String>()
        val issues = mutableListOf<DreamShaderHoverOverrideIssue>()
        var ignoredLineCount = 0
        var duplicateKeyCount = 0

        raw.lineSequence().forEachIndexed { index, line ->
            val lineNumber = index + 1
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) {
                ignoredLineCount++
                return@forEachIndexed
            }

            val idx = trimmed.indexOf('=')
            if (idx < 0) {
                issues += DreamShaderHoverOverrideIssue(
                    lineNumber = lineNumber,
                    source = trimmed,
                    type = DreamShaderHoverOverrideIssueType.MISSING_EQUALS
                )
                return@forEachIndexed
            }

            val rawKey = trimmed.substring(0, idx).trim()
            if (rawKey.isBlank()) {
                issues += DreamShaderHoverOverrideIssue(
                    lineNumber = lineNumber,
                    source = trimmed,
                    type = DreamShaderHoverOverrideIssueType.EMPTY_KEY
                )
                return@forEachIndexed
            }

            val rawValue = trimmed.substring(idx + 1).trim()
            if (rawValue.isBlank()) {
                issues += DreamShaderHoverOverrideIssue(
                    lineNumber = lineNumber,
                    source = trimmed,
                    type = DreamShaderHoverOverrideIssueType.EMPTY_VALUE
                )
                return@forEachIndexed
            }

            val key = rawKey.lowercase(Locale.ROOT)
            if (parsed.containsKey(key)) duplicateKeyCount++
            parsed[key] = rawValue
        }

        return DreamShaderHoverOverrideParseResult(
            entries = parsed,
            ignoredLineCount = ignoredLineCount,
            duplicateKeyCount = duplicateKeyCount,
            issues = issues
        )
    }
}
