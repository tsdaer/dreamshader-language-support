package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Unreal“UMaterialExpression”派生类的尽力文本扫描器。
 *
 * 这不是故意的C++解析器。当编辑器运行时反射为可用（通过迭代“UClass”对象生成的桥接生成的清单），数据更丰富且优先。
 * 扫描仪只有在没有时才会填补空隙
 * 存在桥接数据：它读取“.h”文本，识别“UMaterialExpression*”
 * 类，并输出与目录兼容的条目。不完整条目可以接受
 * 只要产生稳定的“ueName”。
 */
internal object DreamShaderMaterialExpressionScanner {
    private val SKIPPED_DIRECTORIES = setOf("intermediate", "generated", "binaries", "saved", ".git")
    private const val MATERIAL_EXPRESSION_PREFIX = "UMaterialExpression"

    /** Matches `UCLASS(...)` capturing the balanced argument text lazily via the body. */
    private val UCLASS_REGEX = Regex("""UCLASS\s*\(""", RegexOption.IGNORE_CASE)

    /**
     * Matches a class declaration line, optionally with an export macro, capturing
     * the class name and the first public base name.
     * e.g. `class ENGINE_API UMaterialExpressionSine : public UMaterialExpression`
     */
    private val CLASS_DECL_REGEX = Regex(
        """class\s+(?:[A-Za-z_][A-Za-z0-9_]*\s+)?(U[A-Za-z0-9_]+)\s*:\s*public\s+(U[A-Za-z0-9_]+)"""
    )

    private val DISPLAY_NAME_REGEX = Regex(
        """DisplayName\s*=\s*"((?:[^"\\]|\\.)*)"""",
        RegexOption.IGNORE_CASE
    )

    /** Recognizes input-like UPROPERTY declarations and captures the member name. */
    private val INPUT_PROPERTY_REGEX = Regex(
        """(FExpressionInput|FMaterialAttributesInput|FScalarMaterialInput|FVectorMaterialInput|FColorMaterialInput)\s+([A-Za-z_][A-Za-z0-9_]*)"""
    )

    /**
     * Scans a directory tree for header files and returns merged catalog entries.
     *
     * Walking is shallow and defensive: generated/intermediate directories are
     * skipped and any unreadable file is ignored rather than aborting the scan.
     */
    fun scanDirectory(root: File): List<DreamShaderMaterialExpressionInfo> {
        if (!root.exists() || !root.isDirectory) return emptyList()
        val merged = linkedMapOf<String, DreamShaderMaterialExpressionInfo>()
        root.walkTopDown()
            .onEnter { dir -> dir.name.lowercase() !in SKIPPED_DIRECTORIES }
            .filter { it.isFile && it.extension.equals("h", ignoreCase = true) }
            .forEach { file ->
                val text = runCatching { file.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return@forEach
                scanHeaderText(text).forEach { entry ->
                    merged.putIfAbsent(entry.className.lowercase(), entry)
                }
            }
        return merged.values.toList()
    }

    /**
     * Parses a single header's text into catalog entries.
     *
     * Anchored on `UCLASS(...)` declarations: each one is paired with the class
     * declaration that follows it, so only reflected `U*` classes are considered.
     */
    fun scanHeaderText(text: String): List<DreamShaderMaterialExpressionInfo> {
        val entries = mutableListOf<DreamShaderMaterialExpressionInfo>()
        UCLASS_REGEX.findAll(text).forEach { uclass ->
            val parenStart = text.indexOf('(', uclass.range.first)
            if (parenStart < 0) return@forEach
            val parenEnd = findMatchingParen(text, parenStart) ?: return@forEach
            val metaArgs = text.substring(parenStart + 1, parenEnd)

            val declMatch = CLASS_DECL_REGEX.find(text, parenEnd) ?: return@forEach
            // Bail if another UCLASS sits between this one and the class decl.
            val nextUclass = UCLASS_REGEX.find(text, parenEnd)
            if (nextUclass != null && nextUclass.range.first < declMatch.range.first) return@forEach

            val className = declMatch.groupValues[1]
            val baseName = declMatch.groupValues[2]
            if (!isMaterialExpressionClass(className, baseName)) return@forEach

            val bodyStart = text.indexOf('{', declMatch.range.last)
            val bodyEnd = if (bodyStart >= 0) findMatchingBrace(text, bodyStart) else null
            val body = if (bodyStart >= 0 && bodyEnd != null) text.substring(bodyStart + 1, bodyEnd) else ""

            entries += buildEntry(
                className = className,
                metaArgs = metaArgs,
                docComment = leadingDocComment(text, uclass.range.first),
                body = body
            )
        }
        return entries
    }

    /**
     * Serializes scanned entries to the rich, manifest-compatible cache JSON the
     * catalog already consumes, so a scan result can be cached and reloaded
     * exactly like a Bridge or bundled manifest.
     */
    fun toManifestJson(entries: List<DreamShaderMaterialExpressionInfo>): String {
        val builder = StringBuilder()
        builder.append("{\n  \"expressions\": [\n")
        entries.forEachIndexed { index, entry ->
            builder.append("    {\n")
            builder.append("      \"namespace\": \"").append(escape(entry.namespace)).append("\",\n")
            builder.append("      \"className\": \"").append(escape(entry.className)).append("\",\n")
            builder.append("      \"ueName\": \"").append(escape(entry.ueName)).append("\"")
            entry.outputType?.let {
                builder.append(",\n      \"outputType\": \"").append(escape(it)).append("\"")
            }
            entry.description?.let {
                builder.append(",\n      \"description\": \"").append(escape(it)).append("\"")
            }
            if (entry.parameters.isNotEmpty()) {
                builder.append(",\n      \"parameters\": [\n")
                entry.parameters.forEachIndexed { paramIndex, param ->
                    builder.append("        { \"name\": \"").append(escape(param.name)).append("\"")
                    param.type?.let { builder.append(", \"type\": \"").append(escape(it)).append("\"") }
                    builder.append(", \"required\": ").append(param.required).append(" }")
                    if (paramIndex != entry.parameters.lastIndex) builder.append(",")
                    builder.append("\n")
                }
                builder.append("      ]")
            }
            builder.append("\n    }")
            if (index != entries.lastIndex) builder.append(",")
            builder.append("\n")
        }
        builder.append("  ]\n}\n")
        return builder.toString()
    }

    private fun buildEntry(
        className: String,
        metaArgs: String,
        docComment: String?,
        body: String
    ): DreamShaderMaterialExpressionInfo {
        val displayName = DISPLAY_NAME_REGEX.find(metaArgs)?.groupValues?.get(1)?.takeIf { it.isNotBlank() }
        val ueName = displayName?.replace(" ", "")?.takeIf { it.isNotBlank() } ?: deriveUeName(className)
        val parameters = INPUT_PROPERTY_REGEX.findAll(body)
            .map { DreamShaderMaterialExpressionParameter(name = it.groupValues[2]) }
            .distinctBy { it.name }
            .toList()
        return DreamShaderMaterialExpressionInfo(
            namespace = "UE",
            className = className,
            ueName = ueName,
            signature = "UE.$ueName(...)",
            parameters = parameters,
            outputType = null,
            description = docComment ?: "Material expression '$className' exposed as UE.$ueName.",
            source = DreamShaderMaterialExpressionSource.SCANNED_CACHE
        )
    }

    private fun isMaterialExpressionClass(className: String, baseName: String): Boolean {
        return className.startsWith(MATERIAL_EXPRESSION_PREFIX) ||
            baseName.startsWith(MATERIAL_EXPRESSION_PREFIX)
    }

    private fun deriveUeName(className: String): String =
        className.removePrefix("U").removePrefix("MaterialExpression").ifBlank { className }

    private fun leadingDocComment(text: String, uclassStart: Int): String? {
        val before = text.substring(0, uclassStart)
        val close = before.lastIndexOf("*/")
        if (close < 0) return null
        // Only attach the comment if nothing but whitespace separates it from UCLASS.
        if (before.substring(close + 2).isNotBlank()) return null
        val open = before.lastIndexOf("/**", close)
        if (open < 0) return null
        return before.substring(open + 3, close)
            .lines()
            .joinToString(" ") { it.trim().trimStart('*').trim() }
            .trim()
            .takeIf { it.isNotBlank() }
    }

    private fun findMatchingParen(text: String, openOffset: Int): Int? =
        findMatching(text, openOffset, '(', ')')

    private fun findMatchingBrace(text: String, openOffset: Int): Int? =
        findMatching(text, openOffset, '{', '}')

    private fun findMatching(text: String, openOffset: Int, open: Char, close: Char): Int? {
        var depth = 0
        var index = openOffset
        var inString = false
        var escaped = false
        while (index < text.length) {
            val ch = text[index]
            if (inString) {
                when {
                    escaped -> escaped = false
                    ch == '\\' -> escaped = true
                    ch == '"' -> inString = false
                }
            } else {
                when (ch) {
                    '"' -> inString = true
                    open -> depth++
                    close -> {
                        depth--
                        if (depth == 0) return index
                    }
                }
            }
            index++
        }
        return null
    }

    private fun escape(value: String): String =
        value.replace("\\", "\\\\").replace("\"", "\\\"")
}
