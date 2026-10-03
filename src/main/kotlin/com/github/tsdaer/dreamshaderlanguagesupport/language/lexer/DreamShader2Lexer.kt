package com.github.tsdaer.dreamshaderlanguagesupport.language.lexer

import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType
import java.util.Locale

/**
 * Lexer for the DreamShader 2.0 dialect (`.dss` / `.dsi`, and the 2.0 parts of a `.dsh`).
 *
 * Differences from the frozen V1 lexer (upstream `Docs/language-v2/index.md`):
 * - independent `DOT` / `COMMA` / `SEMICOLON` tokens (B1);
 * - `///` doc comments carrying `@key value` directives;
 * - line-start `#pragma` / `#include` / `#if` ... directive words; a `#` anywhere else is a bad
 *   character (upstream DSH2106 semantics);
 * - multi-character operators, so `=` is distinguishable from `==` for named arguments;
 * - case-SENSITIVE keyword/type classification (`export`/`uniform` are keywords, `Substrate` is a
 *   type, `substrate` is an identifier);
 * - literal forms `.5`, `1.f`, `1e3`, `1.5e-3`, `2.5h`, `1u`, `0x1F`, `1L` keep their lexeme.
 *
 * The 2.0 dialect also inherits HLSL-ish V1 keywords/types on a best-effort basis so that a mixed
 * `.dsh` highlights reasonably under this single lexer.
 */
class DreamShader2Lexer : LexerBase() {
    private var buffer: CharSequence = ""
    private var startOffset: Int = 0
    private var endOffset: Int = 0
    private var state: Int = 0
    private var tokenStart: Int = 0
    private var tokenEnd: Int = 0
    private var tokenType: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset
        state = initialState
        tokenStart = startOffset
        tokenEnd = startOffset
        tokenType = null
        advance()
    }

    override fun getState(): Int = state

    override fun getTokenType(): IElementType? = tokenType

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    override fun advance() {
        if (tokenEnd >= endOffset) {
            tokenType = null
            return
        }

        val i = tokenEnd
        tokenStart = i
        val c = buffer[i]

        if (c.isWhitespace()) {
            var j = i + 1
            while (j < endOffset && buffer[j].isWhitespace()) {
                j++
            }
            tokenEnd = j
            tokenType = DreamShaderTokenTypes.WHITE_SPACE
            return
        }

        if (c == '/') {
            if (i + 1 < endOffset) {
                val n = buffer[i + 1]
                if (n == '/') {
                    val isDoc = i + 2 < endOffset && buffer[i + 2] == '/'
                    var j = i + 2
                    while (j < endOffset && buffer[j] != '\n' && buffer[j] != '\r') {
                        j++
                    }
                    tokenEnd = j
                    tokenType = if (isDoc) DreamShaderTokenTypes.DOC_COMMENT else DreamShaderTokenTypes.LINE_COMMENT
                    return
                }
                if (n == '*') {
                    var j = i + 2
                    while (j + 1 < endOffset && !(buffer[j] == '*' && buffer[j + 1] == '/')) {
                        j++
                    }
                    tokenEnd = if (j + 1 < endOffset) j + 2 else endOffset
                    tokenType = DreamShaderTokenTypes.BLOCK_COMMENT
                    return
                }
            }
        }

        if (c == '"' || c == '\'') {
            tokenEnd = scanQuoted(i, c)
            tokenType = DreamShaderTokenTypes.STRING
            return
        }

        if (c == '#' && isAtLineStart(i)) {
            var j = i + 1
            while (j < endOffset && (buffer[j].isLetterOrDigit() || buffer[j] == '_')) {
                j++
            }
            tokenEnd = j
            tokenType = DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE
            return
        }

        if (c.isDigit() || (c == '.' && i + 1 < endOffset && buffer[i + 1].isDigit())) {
            tokenEnd = scanNumber(i)
            tokenType = DreamShaderTokenTypes.NUMBER
            return
        }

        if (isIdentifierStart(c)) {
            var j = i + 1
            while (j < endOffset && isIdentifierPart(buffer[j])) {
                j++
            }
            tokenEnd = j
            tokenType = classifyIdentifier(buffer.subSequence(i, j).toString())
            return
        }

        classifyBrace(c)?.let { brace ->
            tokenEnd = i + 1
            tokenType = brace
            return
        }

        val operatorLength = matchOperator(i)
        if (operatorLength > 0) {
            tokenEnd = i + operatorLength
            tokenType = DreamShaderTokenTypes.OPERATOR
            return
        }

        if (c == '.' || c == ',' || c == ';') {
            tokenEnd = i + 1
            tokenType = when (c) {
                '.' -> DreamShaderTokenTypes.DOT
                ',' -> DreamShaderTokenTypes.COMMA
                else -> DreamShaderTokenTypes.SEMICOLON
            }
            return
        }

        tokenEnd = i + 1
        tokenType = DreamShaderTokenTypes.BAD_CHARACTER
    }

    /** Quoted literal scan; an unterminated literal stops at the end of the line or buffer. */
    private fun scanQuoted(start: Int, quote: Char): Int {
        var j = start + 1
        while (j < endOffset) {
            val ch = buffer[j]
            if (ch == '\\' && j + 1 < endOffset) {
                j += 2
                continue
            }
            if (ch == quote) {
                return j + 1
            }
            if (ch == '\n') {
                return j
            }
            j++
        }
        return endOffset
    }

    /**
     * Number scan per upstream `Docs/language-v2/index.md`: the lexeme is kept exactly as written.
     * Covers decimal/hex integers with `u`/`U`/`l`/`L` suffixes and floats with fraction,
     * exponent and `f`/`h` suffixes, plus a leading-dot form (`.5`).
     */
    private fun scanNumber(start: Int): Int {
        var j = start
        if (buffer[j] == '0' && j + 1 < endOffset && (buffer[j + 1] == 'x' || buffer[j + 1] == 'X')) {
            j += 2
            while (j < endOffset && buffer[j].isHexDigit()) {
                j++
            }
            return consumeIntegerSuffix(j)
        }
        while (j < endOffset && buffer[j].isDigit()) {
            j++
        }
        if (j < endOffset && buffer[j] == '.') {
            j++
            while (j < endOffset && buffer[j].isDigit()) {
                j++
            }
        }
        if (j < endOffset && (buffer[j] == 'e' || buffer[j] == 'E')) {
            var k = j + 1
            if (k < endOffset && (buffer[k] == '+' || buffer[k] == '-')) {
                k++
            }
            if (k < endOffset && buffer[k].isDigit()) {
                j = k
                while (j < endOffset && buffer[j].isDigit()) {
                    j++
                }
            }
        }
        if (j < endOffset) {
            when (buffer[j].lowercaseChar()) {
                'f', 'h' -> return j + 1
                'u', 'l' -> return consumeIntegerSuffix(j)
            }
        }
        return j
    }

    private fun consumeIntegerSuffix(j: Int): Int {
        var k = j
        if (k < endOffset && (buffer[k] == 'u' || buffer[k] == 'U')) {
            k++
        }
        if (k < endOffset && (buffer[k] == 'l' || buffer[k] == 'L')) {
            k++
            if (k < endOffset && (buffer[k] == 'l' || buffer[k] == 'L')) {
                k++
            }
        }
        return k
    }

    private fun classifyIdentifier(word: String): IElementType = when {
        word in V2_KEYWORDS -> DreamShaderTokenTypes.KEYWORD
        word in V2_TYPES -> DreamShaderTokenTypes.TYPE
        word.lowercase(Locale.ROOT) in V1_KEYWORDS_LOWER -> DreamShaderTokenTypes.KEYWORD
        word.lowercase(Locale.ROOT) in V1_TYPES_LOWER -> DreamShaderTokenTypes.TYPE
        else -> DreamShaderTokenTypes.IDENTIFIER
    }

    private fun isIdentifierStart(c: Char): Boolean = c == '_' || c.isLetter()

    private fun isIdentifierPart(c: Char): Boolean = c == '_' || c.isLetterOrDigit()

    private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

    private fun classifyBrace(c: Char): IElementType? = when (c) {
        '(' -> DreamShaderTokenTypes.LPAREN
        ')' -> DreamShaderTokenTypes.RPAREN
        '[' -> DreamShaderTokenTypes.LBRACKET
        ']' -> DreamShaderTokenTypes.RBRACKET
        '{' -> DreamShaderTokenTypes.LBRACE
        '}' -> DreamShaderTokenTypes.RBRACE
        else -> null
    }

    /** Maximal-munch operator match so `=` never shadows `==`, `<=`, `+=`, ... */
    private fun matchOperator(start: Int): Int {
        val c = buffer[start]
        if (c !in OPERATOR_CHARS) return 0
        val next = if (start + 1 < endOffset) buffer[start + 1] else ' '
        val third = if (start + 2 < endOffset) buffer[start + 2] else ' '
        if ((c == '<' || c == '>') && next == c && third == '=') return 3 // <<= >>=
        return when {
            c == '<' && next == '<' -> 2
            c == '>' && next == '>' -> 2
            next == '=' && c in "=!<>+-*/%&|^" -> 2
            next == c && c in "&|+-" -> 2 // && || ++ --
            else -> 1
        }
    }

    private fun isAtLineStart(offset: Int): Boolean {
        var i = offset - 1
        while (i >= 0) {
            val ch = buffer[i]
            if (ch == '\n' || ch == '\r') return true
            if (!ch.isWhitespace()) return false
            i--
        }
        return true
    }

    companion object {
        private val OPERATOR_CHARS = "+-*/%=<>!&|^~?:"

        /** Case-sensitive 2.0 keywords (upstream `Docs/language-v2/index.md`, declarations table). */
        private val V2_KEYWORDS: Set<String> = setOf(
            "import",
            "uniform",
            "export",
            "extern",
            "static",
            "const",
            "struct",
            "if",
            "else",
            "for",
            "while",
            "do",
            "return",
            "break",
            "continue",
            "discard",
            "in",
            "out",
            "inout",
            "true",
            "false",
        )

        /** Case-sensitive 2.0 built-in types; anything else is a user struct or a later judgement. */
        private val V2_TYPES: Set<String> = setOf(
            "void",
            "float", "half", "double", "int", "uint", "bool",
            "float1", "float2", "float3", "float4",
            "half1", "half2", "half3", "half4",
            "double1", "double2", "double3", "double4",
            "int1", "int2", "int3", "int4",
            "uint1", "uint2", "uint3", "uint4",
            "bool1", "bool2", "bool3", "bool4",
            "float1x1", "float1x2", "float1x3", "float1x4",
            "float2x1", "float2x2", "float2x3", "float2x4",
            "float3x1", "float3x2", "float3x3", "float3x4",
            "float4x1", "float4x2", "float4x3", "float4x4",
            "half2x2", "half3x3", "half4x4",
            "int2x2", "int3x3", "int4x4",
            "Texture2D", "TextureCube", "Texture2DArray", "Texture3D", "VolumeTexture",
            "SamplerState", "material", "Substrate",
            "Font", "RuntimeVirtualTexture", "SparseVolumeTexture", "TextureCollection",
            "ParameterCollection",
        )

        /** V1 keywords kept for mixed-header highlighting; lowercase match only. */
        private val V1_KEYWORDS_LOWER: Set<String> = setOf(
            "shader", "shaderfunction", "shaderlayer", "shaderlayerblend",
            "virtualfunction", "namespace", "function", "graphfunction", "template",
            "group", "propgroup", "opt", "selfcontained", "inline",
            "properties", "inputs", "outputs", "results", "settings", "options", "graph", "layout",
        )

        /**
         * V1 legacy type spellings for mixed headers. `substrate` is excluded on purpose: the 2.0
         * type is case-sensitively `Substrate`, and a lowercase spelling is a user identifier.
         */
        private val V1_TYPES_LOWER: Set<String> =
            DreamShaderLexer.TYPES - setOf("substrate")
    }
}
