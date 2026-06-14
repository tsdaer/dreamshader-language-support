package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import java.util.regex.Pattern
import java.util.*

/**
 * 参数信息界面显示的一个签名候选人。
 *
 * 'parameterRanges' 是 'presentableText' 内的字符范围，用于
 * 高亮激活参数。
 */
data class DreamShaderCallSignature(
    val presentableText: String,
    val parameterRanges: List<IntRange>
)

/**
 * 解析显示了 Caret 周围的呼叫站点位置。
 */
data class DreamShaderCallContext(
    val functionName: String,
    val nameStartOffset: Int,
    val leftParenOffset: Int
)

/**
 * 用于调用上下文和参数索引解析的无状态分析器。
 *
 * 查找表目前涵盖了 UE 内置和 HLSL 内在文件的使用
 * 完成子系统，用于保持编辑器功能间行为一致。
 */
object DreamShaderSignatureHelpAnalyzer {
    private val signatureLookup: Map<String, List<DreamShaderCallSignature>> = buildMap {
        put(
            "ue.texcoord",
            listOf(signature("UE.TexCoord(Index=0)", "Index"))
        )
        put(
            "ue.time",
            listOf(signature("UE.Time(Period=4.0)", "Period"))
        )
        put(
            "ue.panner",
            listOf(signature("UE.Panner(Coordinate=UV, Time=UE.Time(), Speed=float2(0.1, 0.0))", "Coordinate", "Time", "Speed"))
        )
        put(
            "ue.worldposition",
            listOf(signature("UE.WorldPosition()"))
        )
        put(
            "ue.objectpositionws",
            listOf(signature("UE.ObjectPositionWS()"))
        )
        put(
            "ue.cameravectorws",
            listOf(signature("UE.CameraVectorWS()"))
        )
        put(
            "ue.screenposition",
            listOf(signature("UE.ScreenPosition()"))
        )
        put(
            "ue.vertexcolor",
            listOf(signature("UE.VertexColor()"))
        )
        put(
            "ue.transformvector",
            listOf(signature("UE.TransformVector(Input=NormalTS, Source=\"Tangent\", Destination=\"World\")", "Input", "Source", "Destination"))
        )
        put(
            "ue.transformposition",
            listOf(signature("UE.TransformPosition(Input=WorldPos, Source=\"Local\", Destination=\"World\")", "Input", "Source", "Destination"))
        )
        put(
            "ue.expression",
            listOf(signature("UE.Expression(Class=\"Sine\", OutputType=\"float1\", Input=UE.Time())", "Class", "OutputType", "Input"))
        )
        put(
            "ue.collectionparam",
            listOf(signature("UE.CollectionParam(Collection=Path(...), Parameter=\"Value\")", "Collection", "Parameter"))
        )
        put(
            "ue.staticswitchparameter",
            listOf(signature("UE.StaticSwitchParameter(Name=\"UseDetail\", Default=true, True=Detail, False=Base)", "Name", "Default", "True", "False"))
        )
        put("abs", listOf(signature("abs(x)", "x")))
        put("acos", listOf(signature("acos(x)", "x")))
        put("asin", listOf(signature("asin(x)", "x")))
        put("atan", listOf(signature("atan(x)", "x")))
        put("atan2", listOf(signature("atan2(y, x)", "y", "x")))
        put("ceil", listOf(signature("ceil(x)", "x")))
        put("clamp", listOf(signature("clamp(x, min, max)", "x", "min", "max")))
        put("cos", listOf(signature("cos(x)", "x")))
        put("cross", listOf(signature("cross(x, y)", "x", "y")))
        put("ddx", listOf(signature("ddx(x)", "x")))
        put("ddy", listOf(signature("ddy(x)", "x")))
        put("distance", listOf(signature("distance(x, y)", "x", "y")))
        put("dot", listOf(signature("dot(x, y)", "x", "y")))
        put("exp", listOf(signature("exp(x)", "x")))
        put("exp2", listOf(signature("exp2(x)", "x")))
        put("floor", listOf(signature("floor(x)", "x")))
        put("frac", listOf(signature("frac(x)", "x")))
        put("fmod", listOf(signature("fmod(x, y)", "x", "y")))
        put("length", listOf(signature("length(x)", "x")))
        put("lerp", listOf(signature("lerp(x, y, s)", "x", "y", "s")))
        put("log", listOf(signature("log(x)", "x")))
        put("log2", listOf(signature("log2(x)", "x")))
        put("max", listOf(signature("max(x, y)", "x", "y")))
        put("min", listOf(signature("min(x, y)", "x", "y")))
        put("mul", listOf(signature("mul(x, y)", "x", "y")))
        put("normalize", listOf(signature("normalize(x)", "x")))
        put("pow", listOf(signature("pow(x, y)", "x", "y")))
        put("reflect", listOf(signature("reflect(i, n)", "i", "n")))
        put("rsqrt", listOf(signature("rsqrt(x)", "x")))
        put("saturate", listOf(signature("saturate(x)", "x")))
        put("sin", listOf(signature("sin(x)", "x")))
        put("smoothstep", listOf(signature("smoothstep(min, max, x)", "min", "max", "x")))
        put("sqrt", listOf(signature("sqrt(x)", "x")))
        put("step", listOf(signature("step(edge, x)", "edge", "x")))
        put("tan", listOf(signature("tan(x)", "x")))
    }

    fun resolveSignatures(
        functionName: String,
        sourceText: String? = null,
        additionalSourceTexts: List<String> = emptyList()
    ): List<DreamShaderCallSignature> {
        val declaredSignatures = resolveDeclaredSignatures(functionName, sourceText, additionalSourceTexts)
        if (declaredSignatures.isNotEmpty()) return declaredSignatures

        val key = functionName.lowercase(Locale.ROOT)
        return signatureLookup[key].orEmpty()
    }

    fun resolveDeclaredSignatures(
        functionName: String,
        sourceText: String? = null,
        additionalSourceTexts: List<String> = emptyList()
    ): List<DreamShaderCallSignature> {
        return resolveDeclaredSignaturesInternal(functionName, sourceText, additionalSourceTexts)
    }

    /**
     * 从 catalog entries 解析 `Namespace.Member(...)` 形态的签名。
     *
     * 当 [requireComplete] 为 true 时，仅当条目带有显式 signature 文本或参数列表
     * 才返回，确保数据不完整时调用方能回退到硬编码签名表。
     */
    internal fun resolveCatalogSignatures(
        functionName: String,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
        requireComplete: Boolean
    ): List<DreamShaderCallSignature> {
        val trimmed = functionName.trim()
        if (trimmed.isBlank() || catalogEntries.isEmpty()) return emptyList()
        return catalogEntries
            .asSequence()
            .filter { it.qualifiedName.equals(trimmed, ignoreCase = true) }
            .filter { !requireComplete || isCompleteCatalogEntry(it) }
            .map(::catalogSignature)
            .toList()
    }

    private fun isCompleteCatalogEntry(entry: DreamShaderMaterialExpressionInfo): Boolean {
        val hasExplicitSignature = entry.signature
            ?.takeIf { it.isNotBlank() }
            ?.let { it != defaultCatalogSignature(entry) }
            ?: false
        return hasExplicitSignature || entry.parameters.isNotEmpty()
    }

    private fun catalogSignature(entry: DreamShaderMaterialExpressionInfo): DreamShaderCallSignature {
        val presentable = entry.signature?.takeIf { it.isNotBlank() } ?: defaultCatalogSignature(entry)
        val parameterNames = entry.parameters.map { it.name }.toTypedArray()
        return signature(presentable, *parameterNames)
    }

    private fun defaultCatalogSignature(entry: DreamShaderMaterialExpressionInfo): String =
        "${entry.qualifiedName}(...)"

    fun findCallContext(text: String, offset: Int): DreamShaderCallContext? {
        if (text.isEmpty()) return null
        val safeOffset = offset.coerceIn(0, text.length)
        var depth = 0
        var i = safeOffset - 1

        while (i >= 0) {
            when (text[i]) {
                ')' -> depth++
                '(' -> {
                    if (depth == 0) {
                        val nameRange = findFunctionNameRange(text, i) ?: return null
                        val functionName = text.substring(nameRange.first, nameRange.last + 1)
                        return DreamShaderCallContext(
                            functionName = functionName,
                            nameStartOffset = nameRange.first,
                            leftParenOffset = i
                        )
                    }
                    depth--
                }
            }
            i--
        }
        return null
    }

    fun parameterIndex(text: String, call: DreamShaderCallContext, offset: Int): Int {
        val safeOffset = offset.coerceIn(0, text.length)
        if (safeOffset <= call.leftParenOffset) return -1

        var nestedDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false
        var parameterIndex = 0

        var i = call.leftParenOffset + 1
        while (i < safeOffset) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }

            when (ch) {
                '"' -> inString = true
                '(' -> nestedDepth++
                ')' -> {
                    if (nestedDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        return -1
                    }
                    if (nestedDepth > 0) nestedDepth--
                }
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '{' -> braceDepth++
                '}' -> if (braceDepth > 0) braceDepth--
                ',' -> {
                    if (nestedDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        parameterIndex++
                    }
                }
            }
            i++
        }
        return parameterIndex
    }

    private fun findFunctionNameRange(text: String, leftParenOffset: Int): IntRange? {
        var end = leftParenOffset - 1
        while (end >= 0 && text[end].isWhitespace()) end--
        if (end < 0) return null

        var start = end
        while (start >= 0) {
            val ch = text[start]
            val isNameChar = ch == '.' || ch == '_' || ch.isLetterOrDigit()
            if (!isNameChar) break
            start--
        }
        val actualStart = start + 1
        if (actualStart > end) return null
        val name = text.substring(actualStart, end + 1)
        if (name.isBlank()) return null
        if (!name.any { it.isLetter() }) return null
        return actualStart..end
    }

    private fun signature(presentableText: String, vararg parameters: String): DreamShaderCallSignature {
        val ranges = mutableListOf<IntRange>()
        var searchStart = 0
        for (parameter in parameters) {
            val index = presentableText.indexOf(parameter, startIndex = searchStart)
            if (index >= 0) {
                ranges.add(index until (index + parameter.length))
                searchStart = index + parameter.length
            }
        }
        return DreamShaderCallSignature(
            presentableText = presentableText,
            parameterRanges = ranges
        )
    }

    private fun resolveDeclaredSignaturesInternal(
        functionName: String,
        sourceText: String?,
        additionalSourceTexts: List<String>
    ): List<DreamShaderCallSignature> {
        val declarationSignaturesByName = linkedMapOf<String, DreamShaderCallSignature>()
        appendDeclaredSignatures(declarationSignaturesByName, sourceText)
        additionalSourceTexts.forEach { text ->
            appendDeclaredSignatures(declarationSignaturesByName, text)
        }
        if (declarationSignaturesByName.isEmpty()) return emptyList()

        val lookupCandidates = buildLookupCandidates(functionName)
        val resolved = linkedSetOf<DreamShaderCallSignature>()
        lookupCandidates.forEach { candidate ->
            declarationSignaturesByName[candidate]?.let { resolved.add(it) }
        }
        return resolved.toList()
    }

    private fun appendDeclaredSignatures(
        out: MutableMap<String, DreamShaderCallSignature>,
        sourceText: String?
    ) {
        if (sourceText.isNullOrBlank()) return
        parseDeclaredFunctionSignatures(sourceText).forEach { (name, signature) ->
            out.putIfAbsent(name, signature)
        }
    }

    private fun parseDeclaredFunctionSignatures(sourceText: String): Map<String, DreamShaderCallSignature> {
        val signatures = linkedMapOf<String, DreamShaderCallSignature>()
        val matcher = USER_FUNCTION_DECLARATION_HEAD_PATTERN.matcher(sourceText)
        while (matcher.find()) {
            val functionName = matcher.group(2)?.trim().orEmpty()
            if (functionName.isBlank()) continue

            val leftParenOffset = matcher.end() - 1
            val rightParenOffset = findMatchingRightParen(sourceText, leftParenOffset) ?: continue
            val rawParameters = sourceText.substring(leftParenOffset + 1, rightParenOffset)
            val parameterNames = splitTopLevelCommaSegments(rawParameters)
                .mapNotNull { extractDeclaredParameterName(it) }

            val presentableText = buildString {
                append(functionName)
                append("(")
                append(parameterNames.joinToString(", "))
                append(")")
            }
            signatures[functionName.lowercase(Locale.ROOT)] = signature(presentableText, *parameterNames.toTypedArray())
        }
        return signatures
    }

    private fun splitTopLevelCommaSegments(text: String): List<String> {
        val parts = mutableListOf<String>()
        var start = 0
        var i = 0
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false

        while (i < text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }

            when (ch) {
                '"' -> inString = true
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '{' -> braceDepth++
                '}' -> if (braceDepth > 0) braceDepth--
                ',' -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        parts.add(text.substring(start, i))
                        start = i + 1
                    }
                }
            }
            i++
        }
        parts.add(text.substring(start))
        return parts
    }

    private fun findMatchingRightParen(text: String, leftParenOffset: Int): Int? {
        if (leftParenOffset !in text.indices || text[leftParenOffset] != '(') return null

        var parenDepth = 1
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false
        var i = leftParenOffset + 1

        while (i < text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }

            when (ch) {
                '"' -> inString = true
                '(' -> parenDepth++
                ')' -> {
                    parenDepth--
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) return i
                }
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '{' -> braceDepth++
                '}' -> if (braceDepth > 0) braceDepth--
            }
            i++
        }
        return null
    }

    private fun extractDeclaredParameterName(rawParameter: String): String? {
        val head = rawParameter.substringBefore('=').trim()
        if (head.isBlank()) return null

        val identifiers = mutableListOf<String>()
        val matcher = DECLARATION_IDENTIFIER_PATTERN.matcher(head)
        while (matcher.find()) {
            matcher.group()?.let { identifiers.add(it) }
        }
        if (identifiers.isEmpty()) return null

        val name = identifiers.last()
        if (identifiers.size == 1 && name.lowercase(Locale.ROOT) in PARAMETER_QUALIFIERS) return null
        return name
    }

    private fun buildLookupCandidates(functionName: String): List<String> {
        val trimmed = functionName.trim()
        if (trimmed.isBlank()) return emptyList()

        val candidates = linkedSetOf<String>()
        candidates.add(trimmed.lowercase(Locale.ROOT))
        trimmed.substringAfterLast("::", missingDelimiterValue = "").takeIf { it.isNotBlank() }?.let {
            candidates.add(it.lowercase(Locale.ROOT))
        }
        trimmed.substringAfterLast('.', missingDelimiterValue = "").takeIf { it.isNotBlank() }?.let {
            candidates.add(it.lowercase(Locale.ROOT))
        }
        return candidates.toList()
    }

    private val USER_FUNCTION_DECLARATION_HEAD_PATTERN: Pattern = Pattern.compile(
        "(?is)\\b(function|graphfunction|virtualfunction)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\("
    )
    private val DECLARATION_IDENTIFIER_PATTERN: Pattern = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*")
    private val PARAMETER_QUALIFIERS = setOf("in", "out", "inout", "const", "static", "opt")
}
