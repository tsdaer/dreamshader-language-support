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
        if (element.node?.elementType != DreamShaderTokenTypes.IDENTIFIER &&
            element.node?.elementType != DreamShaderTokenTypes.TYPE
        ) {
            return null
        }
        val text = element.containingFile?.text ?: return null
        val name = element.text.lowercase(Locale.ROOT)
        if (name !in COLOR_CALLS) return null
        var i = element.textRange.endOffset
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length || text[i] != '(') return null
        val end = findMatchingParen(text, i) ?: return null
        val values = text.substring(i + 1, end)
            .split(',')
            .map { it.trim().toDoubleOrNull() ?: return null }
        if (values.size !in 3..4 || values.any { it !in 0.0..1.0 }) return null
        return Color(
            (values[0] * 255.0).toInt().coerceIn(0, 255),
            (values[1] * 255.0).toInt().coerceIn(0, 255),
            (values[2] * 255.0).toInt().coerceIn(0, 255),
            ((values.getOrNull(3) ?: 1.0) * 255.0).toInt().coerceIn(0, 255)
        )
    }

    override fun setColorTo(element: PsiElement, color: Color) {
    }

    internal fun testColorCall(text: String): Color? {
        val nameEnd = text.indexOf('(').takeIf { it > 0 } ?: return null
        val name = text.substring(0, nameEnd).trim().lowercase(Locale.ROOT)
        if (name !in COLOR_CALLS) return null
        val end = findMatchingParen(text, nameEnd) ?: return null
        val values = text.substring(nameEnd + 1, end)
            .split(',')
            .map { it.trim().toDoubleOrNull() ?: return null }
        if (values.size !in 3..4 || values.any { it !in 0.0..1.0 }) return null
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
        private val COLOR_CALLS = setOf("float3", "vec3", "float4", "vec4")
    }
}
