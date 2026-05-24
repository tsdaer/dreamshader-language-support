package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.psi.PsiElement
import java.util.Locale

class DreamShaderInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        val settings = element.project.getService(DreamShaderProjectSettings::class.java)?.state
        if (settings != null && !settings.enableCodeLens) return emptyList()

        if (element.node.elementType != DreamShaderTokenTypes.IDENTIFIER) return emptyList()

        val callInfo = findCallAtIdentifier(element) ?: return emptyList()
        val signatures = DreamShaderSignatureHelpAnalyzer.resolveSignatures(callInfo.functionName)
        if (signatures.isEmpty()) return emptyList()

        val parameterNames = extractParameterNames(signatures.first().presentableText)
        if (parameterNames.isEmpty()) return emptyList()

        val hintCount = minOf(callInfo.arguments.size, parameterNames.size)
        if (hintCount <= 0) return emptyList()

        val hints = ArrayList<InlayInfo>(hintCount)
        for (index in 0 until hintCount) {
            val argument = callInfo.arguments[index]
            if (isNamedArgument(argument.text)) continue

            val parameterName = parameterNames[index]
            if (parameterName.isBlank()) continue

            hints.add(InlayInfo("$parameterName:", argument.startOffset))
        }
        return hints
    }

    override fun getHintInfo(element: PsiElement): HintInfo? {
        val settings = element.project.getService(DreamShaderProjectSettings::class.java)?.state
        if (settings != null && !settings.enableCodeLens) return null

        val callInfo = findCallAtIdentifier(element) ?: return null
        val signatures = DreamShaderSignatureHelpAnalyzer.resolveSignatures(callInfo.functionName)
        if (signatures.isEmpty()) return null
        val parameterNames = extractParameterNames(signatures.first().presentableText)
        return HintInfo.MethodInfo(callInfo.functionName.lowercase(Locale.ROOT), parameterNames)
    }

    override fun getDefaultBlackList(): Set<String> = emptySet()

    override fun isBlackListSupported(): Boolean = false

    @Deprecated("Required by legacy InlayParameterHintsProvider API.")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getBlacklistExplanationHTML(): String = "DreamShader inlay hints are based on built-in callable signatures."

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getSettingsPreview(): String = """
        Shader Main {
            Graph {
                float2 uv = UE.TexCoord(0);
                float v = saturate(roughness);
            }
        }
    """.trimIndent()

    private fun findCallAtIdentifier(element: PsiElement): ParsedCall? {
        val fileText = element.containingFile.text
        val tokenStart = element.textRange.startOffset
        val tokenEnd = element.textRange.endOffset

        var left = tokenStart
        while (left > 0 && fileText[left - 1].isWhitespace()) left--
        if (left > 0 && fileText[left - 1] == '.') {
            left--
            while (left > 0 && fileText[left - 1].isWhitespace()) left--
            while (left > 0 && isNameChar(fileText[left - 1])) left--
        }

        var i = tokenEnd
        while (i < fileText.length && fileText[i].isWhitespace()) i++
        if (i >= fileText.length || fileText[i] != '(') return null

        val functionName = fileText.substring(left, tokenEnd).trim()
        if (functionName.isBlank()) return null

        val rightParen = findMatchingRightParen(fileText, i) ?: return null
        val arguments = parseArguments(fileText, i + 1, rightParen)

        return ParsedCall(functionName = functionName, arguments = arguments)
    }

    private fun findMatchingRightParen(text: String, leftParenOffset: Int): Int? {
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
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        return i
                    }
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

    private fun parseArguments(text: String, start: Int, endExclusive: Int): List<ArgumentSegment> {
        val args = mutableListOf<ArgumentSegment>()
        var segmentStart = start
        var i = start
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false

        while (i < endExclusive) {
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
                        addArgumentSegment(args, text, segmentStart, i)
                        segmentStart = i + 1
                    }
                }
            }
            i++
        }
        addArgumentSegment(args, text, segmentStart, endExclusive)
        return args
    }

    private fun addArgumentSegment(
        out: MutableList<ArgumentSegment>,
        text: String,
        rawStart: Int,
        rawEndExclusive: Int
    ) {
        var start = rawStart
        while (start < rawEndExclusive && text[start].isWhitespace()) start++
        var end = rawEndExclusive
        while (end > start && text[end - 1].isWhitespace()) end--
        if (start >= end) return
        out.add(ArgumentSegment(text = text.substring(start, end), startOffset = start))
    }

    private fun extractParameterNames(presentableSignature: String): List<String> {
        val leftParen = presentableSignature.indexOf('(')
        val rightParen = presentableSignature.lastIndexOf(')')
        if (leftParen < 0 || rightParen <= leftParen) return emptyList()

        val body = presentableSignature.substring(leftParen + 1, rightParen)
        if (body.isBlank()) return emptyList()

        return splitTopLevelCommaSegments(body)
            .mapNotNull { segment ->
                val trimmed = segment.trim()
                if (trimmed.isBlank()) return@mapNotNull null

                val equalsIndex = trimmed.indexOf('=')
                if (equalsIndex > 0) {
                    trimmed.substring(0, equalsIndex).trim().takeIf { it.isNotBlank() }
                } else {
                    trimmed.takeIf { it.all(::isNameChar) }
                }
            }
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

    private fun isNamedArgument(argumentText: String): Boolean {
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false

        for (ch in argumentText) {
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
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '{' -> braceDepth++
                '}' -> if (braceDepth > 0) braceDepth--
                '=' -> if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) return true
            }
        }
        return false
    }

    private fun isNameChar(ch: Char): Boolean = ch == '_' || ch == '.' || ch.isLetterOrDigit()

    private data class ParsedCall(
        val functionName: String,
        val arguments: List<ArgumentSegment>
    )

    private data class ArgumentSegment(
        val text: String,
        val startOffset: Int
    )
}
