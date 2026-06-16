package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.openapi.editor.ElementColorProvider
import com.intellij.psi.PsiElement
import java.awt.Color
import java.util.Locale

class DreamShaderColorProvider : ElementColorProvider {
    override fun getColorFrom(element: PsiElement): Color? {
        if (element.language != DreamShaderLanguage) return null
        val text = element.containingFile?.text ?: return null

        return when (element.node?.elementType) {
            DreamShaderTokenTypes.IDENTIFIER,
            DreamShaderTokenTypes.TYPE -> colorFromCallName(text, element.text, element.textRange.endOffset)
            DreamShaderTokenTypes.NUMBER -> colorFromHexLiteral(text, element.textRange.startOffset)
            else -> null
        }
    }

    override fun setColorTo(element: PsiElement, color: Color) {
    }

    internal fun testColorCall(text: String): Color? {
        return parseColorLiteral(text.trim())
    }

    private fun colorFromCallName(text: String, rawName: String, searchStart: Int): Color? {
        val name = rawName.lowercase(Locale.ROOT)
        if (name !in COLOR_CALLS) return null
        var i = searchStart
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '(') return null
        val end = findMatchingParen(text, i) ?: return null
        return parseConstructorColor(text.substring(i + 1, end))
    }

    private fun colorFromHexLiteral(text: String, startOffset: Int): Color? {
        val match = HEX_LITERAL_REGEX.find(text, startOffset)
            ?.takeIf { it.range.first == startOffset }
            ?: return null
        return parseHexColor(match.value)
    }

    private fun parseColorLiteral(text: String): Color? {
        parseHexColor(text)?.let { return it }
        val nameEnd = text.indexOf('(').takeIf { it > 0 } ?: return null
        val name = text.substring(0, nameEnd).trim().lowercase(Locale.ROOT)
        if (name !in COLOR_CALLS) return null
        val end = findMatchingParen(text, nameEnd) ?: return null
        return parseConstructorColor(text.substring(nameEnd + 1, end))
    }

    private fun parseConstructorColor(argumentsText: String): Color? {
        val values = argumentsText.split(',')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (values.size !in 3..4) return null

        val numericValues = values.map { it.toDoubleOrNull() ?: return null }
        val normalized = when {
            numericValues.all { it in 0.0..1.0 } -> numericValues
            values.all { INTEGER_CHANNEL_REGEX.matches(it) } &&
                numericValues.all { it in 0.0..255.0 } -> numericValues.map { it / 255.0 }
            else -> return null
        }

        return colorFromNormalizedChannels(normalized)
    }

    private fun parseHexColor(text: String): Color? {
        val hex = HEX_LITERAL_REGEX.matchEntire(text)?.groupValues?.getOrNull(1) ?: return null
        return when (hex.length) {
            6 -> Color(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16)
            )
            8 -> Color(
                hex.substring(0, 2).toInt(16),
                hex.substring(2, 4).toInt(16),
                hex.substring(4, 6).toInt(16),
                hex.substring(6, 8).toInt(16)
            )
            else -> null
        }
    }

    private fun colorFromNormalizedChannels(values: List<Double>): Color {
        return Color(
            (values[0] * 255.0).toInt().coerceIn(0, 255),
            (values[1] * 255.0).toInt().coerceIn(0, 255),
            (values[2] * 255.0).toInt().coerceIn(0, 255),
            ((values.getOrNull(3) ?: 1.0) * 255.0).toInt().coerceIn(0, 255)
        )
    }

    private fun findMatchingParen(text: String, leftParen: Int): Int? {
        var depth = 1
        var i = leftParen + 1
        while (i < text.length) {
            when (text[i]) {
                '(' -> depth++
                ')' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
            i++
        }
        return null
    }

    private companion object {
        private val COLOR_CALLS = setOf("float3", "vec3", "float4", "vec4", "color", "linearcolor")
        private val INTEGER_CHANNEL_REGEX = Regex("[0-9]+")
        private val HEX_LITERAL_REGEX = Regex("0x([0-9a-fA-F]{6}|[0-9a-fA-F]{8})\\b")
    }
}
