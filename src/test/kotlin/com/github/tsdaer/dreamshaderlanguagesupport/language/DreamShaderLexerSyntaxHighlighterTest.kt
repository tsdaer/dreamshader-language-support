package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.tree.IElementType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamShaderLexerSyntaxHighlighterTest {
    @Test
    fun `lexer classifies representative dreamshader tokens`() {
        val text = """
            // line comment
            Shader Main {
                /* block comment */
                Properties = {
                    float3 BaseColor = float3(1.0f, 0.5, .25);
                    stringName = "UE.TexCoord(\"uv\")";
                }
                Results {
                    float3 ResultColor;
                }
                @
            }
        """.trimIndent()

        val tokens = lexTokens(text)
            .filterNot { it.first == DreamShaderTokenTypes.WHITE_SPACE }

        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.LINE_COMMENT })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.BLOCK_COMMENT })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.KEYWORD && it.second == "Shader" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.SECTION && it.second == "Properties" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.SECTION && it.second == "Results" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.TYPE && it.second == "float3" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.STRING && it.second.contains("TexCoord") })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.NUMBER && it.second == "1.0f" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.NUMBER && it.second == ".25" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.OPERATOR && it.second == "=" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.LBRACE })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.RBRACE })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.BAD_CHARACTER && it.second == "@" })
    }

    @Test
    fun `lexer classification is case insensitive for keywords sections and types`() {
        val text = "sHaDeR Demo { pRoPeRtIeS = { FlOaT3 Value = 1; } }"
        val tokens = lexTokens(text).filterNot { it.first == DreamShaderTokenTypes.WHITE_SPACE }

        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.KEYWORD && it.second == "sHaDeR" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.SECTION && it.second == "pRoPeRtIeS" })
        assertTrue(tokens.any { it.first == DreamShaderTokenTypes.TYPE && it.second == "FlOaT3" })
    }

    @Test
    fun `syntax highlighter maps token kinds to expected text attributes`() {
        val highlighter = DreamShaderSyntaxHighlighter()

        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.KEYWORD), DreamShaderTextAttributes.KEYWORD)
        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.SECTION), DreamShaderTextAttributes.SECTION)
        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.TYPE), DreamShaderTextAttributes.TYPE)
        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.STRING), DreamShaderTextAttributes.STRING)
        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.NUMBER), DreamShaderTextAttributes.NUMBER)
        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.LINE_COMMENT), DreamShaderTextAttributes.COMMENT)
        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.LBRACE), DreamShaderTextAttributes.BRACES)
        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.OPERATOR), DreamShaderTextAttributes.OPERATOR)
        assertKeys(highlighter.getTokenHighlights(DreamShaderTokenTypes.BAD_CHARACTER), DreamShaderTextAttributes.BAD_CHARACTER)

        assertTrue(highlighter.getTokenHighlights(DreamShaderTokenTypes.IDENTIFIER).isEmpty())
        assertTrue(highlighter.getTokenHighlights(DreamShaderTokenTypes.WHITE_SPACE).isEmpty())
    }

    private fun assertKeys(actual: Array<TextAttributesKey>, expected: TextAttributesKey) {
        assertEquals(1, actual.size)
        assertEquals(expected.externalName, actual[0].externalName)
    }

    private fun lexTokens(text: String): List<Pair<IElementType, String>> {
        val lexer = DreamShaderLexer()
        lexer.start(text)
        val result = mutableListOf<Pair<IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            result += type to text.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }
        return result
    }
}
