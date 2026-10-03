package com.github.tsdaer.dreamshaderlanguagesupport.language.lexer

import com.intellij.psi.tree.IElementType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Token-level checks for the DreamShader 2.0 lexer (plan B1 punctuation granularity and the
 * upstream `Docs/language-v2/index.md` literal/directive forms).
 */
class DreamShader2LexerTest {

    private data class Token(val type: IElementType?, val text: String)

    private fun lex(text: String): List<Token> = buildList {
        val lexer = DreamShader2Lexer()
        lexer.start(text)
        while (lexer.tokenType != null) {
            add(Token(lexer.tokenType, text.substring(lexer.tokenStart, lexer.tokenEnd)))
            lexer.advance()
        }
    }

    private fun tokens(text: String): List<String> = lex(text).map { it.text }

    private fun types(text: String): List<IElementType?> = lex(text).map { it.type }

    private fun assertSingleToken(text: String, expectedType: IElementType) {
        val lexed = lex(text.trim())
        assertEquals(1, lexed.size, "Expected one token for '$text', got $lexed")
        assertEquals(expectedType, lexed[0].type)
        assertEquals(text.trim(), lexed[0].text)
    }

    // ------------------------------------------------------------------
    // B1: punctuation granularity
    // ------------------------------------------------------------------

    @Test
    fun punctuationGetsDistinctTokens() {
        val lexed = lex("m.UV; a, b")
        assertTrue(lexed.any { it.type == DreamShaderTokenTypes.DOT && it.text == "." }, "DOT expected: $lexed")
        assertTrue(lexed.any { it.type == DreamShaderTokenTypes.SEMICOLON && it.text == ";" }, "SEMICOLON expected: $lexed")
        assertTrue(lexed.any { it.type == DreamShaderTokenTypes.COMMA && it.text == "," }, "COMMA expected: $lexed")
    }

    // ------------------------------------------------------------------
    // doc comments and directives
    // ------------------------------------------------------------------

    @Test
    fun tripleSlashIsDocCommentAndDoubleSlashIsLineComment() {
        assertSingleToken("/// @group Surface", DreamShaderTokenTypes.DOC_COMMENT)
        assertSingleToken("// plain comment", DreamShaderTokenTypes.LINE_COMMENT)
    }

    @Test
    fun lineStartDirectivesBecomeDirectiveTokens() {
        assertSingleToken("#pragma", DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE)
        assertSingleToken("#include", DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE)
        assertSingleToken("#if", DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE)
        assertSingleToken("#endif", DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE)
    }

    @Test
    fun hashNotAtLineStartIsBadCharacter() {
        val lexed = lex("float x = a # b;")
        assertTrue(
            lexed.any { it.type == DreamShaderTokenTypes.BAD_CHARACTER && it.text == "#" },
            "BAD_CHARACTER expected for mid-line #: $lexed",
        )
    }

    @Test
    fun regionDirectiveLexesAsDirectiveWord() {
        // `#region`/`#endregion` lex fine; dialect routing (not the lexer) keeps them out of V2.
        assertSingleToken("#region", DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE)
    }

    // ------------------------------------------------------------------
    // operators: maximal munch
    // ------------------------------------------------------------------

    @Test
    fun multiCharOperatorsStaySingleTokens() {
        assertEquals(
            listOf("==", "!=", "<=", ">=", "&&", "||", "<<=", ">>=", "+=", "++"),
            lex("== != <= >= && || <<= >>= += ++").filter { it.type != DreamShaderTokenTypes.WHITE_SPACE }.map { it.text },
        )
    }

    @Test
    fun singleEqualsIsOneOperatorToken() {
        assertEquals(
            listOf("Index", " ", "=", " ", "0"),
            tokens("Index = 0"),
        )
    }

    // ------------------------------------------------------------------
    // literals keep their lexeme
    // ------------------------------------------------------------------

    @Test
    fun literalFormsKeepTheirLexeme() {
        val forms = listOf("1", "1u", "0x1F", "1L", "1.0", "1.f", ".5", "1e3", "1.5e-3", "2.5h")
        for (form in forms) {
            assertSingleToken(form, DreamShaderTokenTypes.NUMBER)
        }
    }

    @Test
    fun memberAccessAfterNumberDoesNotSwallowTheDot() {
        assertEquals(
            listOf(
                DreamShaderTokenTypes.NUMBER,
                DreamShaderTokenTypes.DOT,
                DreamShaderTokenTypes.IDENTIFIER,
            ),
            types("1.5f.x"),
        )
    }

    // ------------------------------------------------------------------
    // case-sensitive classification
    // ------------------------------------------------------------------

    @Test
    fun v2KeywordsAndTypesAreCaseSensitive() {
        assertEquals(DreamShaderTokenTypes.KEYWORD, types("uniform").single())
        assertEquals(DreamShaderTokenTypes.TYPE, types("Substrate").single())
        // The one true collision: lowercase `substrate` is a user identifier in the 2.0 dialect.
        assertEquals(DreamShaderTokenTypes.IDENTIFIER, types("substrate").single())
        assertEquals(DreamShaderTokenTypes.TYPE, types("Texture2D").single())
        // Legacy spellings stay recognized for mixed headers; `Texture2D` vs `texture2d` is 1.x.
        assertEquals(DreamShaderTokenTypes.TYPE, types("texture2d").single())
    }

    @Test
    fun v1KeywordsStillHighlightInsideMixedHeaders() {
        assertEquals(DreamShaderTokenTypes.KEYWORD, types("ShaderFunction").single())
        assertEquals(DreamShaderTokenTypes.KEYWORD, types("Outputs").single())
        assertEquals(DreamShaderTokenTypes.TYPE, types("vec3").single())
    }

    // ------------------------------------------------------------------
    // strings
    // ------------------------------------------------------------------

    @Test
    fun escapedQuotesStayOneStringToken() {
        assertSingleToken("\"((LayerName=\\\"Grass\\\"))\"", DreamShaderTokenTypes.STRING)
    }

    @Test
    fun unterminatedStringStopsAtLineEnd() {
        val lexed = lex("float x = \"oops\nfloat y;")
        val string = lexed.first { it.type == DreamShaderTokenTypes.STRING }
        assertEquals("\"oops", string.text)
    }
}
