package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLanguageKeywords
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import java.util.Locale

/**
 * DreamShader source dialects.
 *
 * Upstream DreamShader 2.0 ships two front ends over one compiler: a `.dss`/`.dsi` is read by the
 * 2.0 parser, a `.dsm`/`.dsf` by the legacy front end, and a `.dsh` may hold both dialects side by
 * side (judged per declaration, see upstream `Docs/language/source-files.md`). The plugin models
 * that as a per-file dialect hint used for parsing/highlighting dispatch; it is a conservative
 * heuristic and never an authority — the engine bridge stays the semantic referee.
 */
enum class DreamShaderDialect {
    /** Legacy 1.x syntax: `Shader(`/`Function(`/section blocks, as in `.dsm`/`.dsf`. */
    V1,

    /** 2.0 declarative syntax: `#pragma material(...)`, `uniform`, `export`/`extern`, `struct`. */
    V2,

    /** Both dialects detected in one file (headers may mix them; flagged files are DSH2248 territory). */
    HEADER_MIXED,
}

/**
 * Dialect detection helpers.
 *
 * Marker scanning runs the frozen V1 lexer over the text, so comments and string literals cannot
 * trigger a misdetection, and the 1.x `#Region`/`#EndRegion` layout directives are excluded from
 * the V2 `#` marker (upstream keeps them in 1.x files).
 */
object DreamShaderDialects {
    /** Extensions parsed by the legacy front end. */
    val V1_EXTENSIONS: Set<String> = setOf("dsm", "dsf")

    /** Extensions parsed by the 2.0 front end. */
    val V2_EXTENSIONS: Set<String> = setOf("dss", "dsi")

    /** Extensions that may hold both dialects. */
    val MIXED_EXTENSIONS: Set<String> = setOf("dsh")

    /** 1.x block keywords, as classified by the frozen V1 lexer (KEYWORD tokens). */
    private val V1_BLOCK_KEYWORDS: Set<String> = DreamShaderLanguageKeywords.DECLARATION_KEYWORDS

    /** 2.0 declaration keywords, matched case-sensitively on V1 identifier tokens. */
    private val V2_DECLARATION_MARKERS: Set<String> = setOf("uniform", "export", "extern")

    /** The legacy lexer consumes the 2.2 shared file pragmas before this marker scan. */
    private val V2_PREPROCESSOR_MARKERS: Set<String> = setOf(
        "pragma", "include", "if", "ifdef", "ifndef", "elif", "else", "endif", "define", "undef",
    )

    fun extensionOf(fileName: String): String {
        val lowered = fileName.lowercase(Locale.ROOT)
        return if (lowered.contains('.')) lowered.substringAfterLast('.') else lowered
    }

    /** The dialect a file of [extension] defaults to when its content carries no markers. */
    fun defaultByExtension(extension: String): DreamShaderDialect? = when (extensionOf(extension)) {
        in V1_EXTENSIONS -> DreamShaderDialect.V1
        in V2_EXTENSIONS -> DreamShaderDialect.V2
        in MIXED_EXTENSIONS -> DreamShaderDialect.V1
        else -> null
    }

    fun isDreamShaderExtension(extension: String): Boolean = defaultByExtension(extension) != null

    /**
     * Full detection: extension baseline + content markers.
     *
     * Content markers override the extension baseline; with no markers at all a file keeps its
     * extension default (a bare `.dss` of helper functions stays V2, a bare `.dsh` stays V1).
     */
    fun detect(fileName: String, text: CharSequence): DreamShaderDialect {
        val hasV2 = hasV2Markers(text)
        val hasV1 = hasV1Markers(text)
        return when {
            hasV2 && hasV1 -> DreamShaderDialect.HEADER_MIXED
            hasV2 -> DreamShaderDialect.V2
            else -> defaultByExtension(fileName) ?: DreamShaderDialect.V1
        }
    }

    /**
     * Parsing/highlighting dispatch predicate: any V2 marker routes the file to the 2.0 front end
     * (which falls back to opaque legacy blocks); everything else keeps the frozen 1.x pipeline.
     */
    fun routesToV2(text: CharSequence): Boolean = hasV2Markers(text)

    fun hasV2Markers(text: CharSequence): Boolean {
        val lexer = DreamShaderLexer()
        lexer.start(text)
        var braces = 0
        var parentheses = 0
        var previous: String? = null
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            val tokenStart = lexer.tokenStart
            val tokenEnd = lexer.tokenEnd
            if (braces == 0 && parentheses == 0 && previous?.lowercase(Locale.ROOT) !in V1_BLOCK_KEYWORDS && (type == DreamShaderTokenTypes.TYPE ||
                    type == DreamShaderTokenTypes.IDENTIFIER && text.subSequence(tokenStart, tokenEnd).toString() == "struct")) {
                val ahead = DreamShaderLexer(); ahead.start(text, tokenEnd, text.length, 0)
                val next = mutableListOf<Pair<com.intellij.psi.tree.IElementType?, String>>()
                while (ahead.tokenType != null && next.size < 2) {
                    if (ahead.tokenType !in setOf(DreamShaderTokenTypes.WHITE_SPACE, DreamShaderTokenTypes.LINE_COMMENT, DreamShaderTokenTypes.BLOCK_COMMENT))
                        next += ahead.tokenType to text.subSequence(ahead.tokenStart, ahead.tokenEnd).toString()
                    ahead.advance()
                }
                if (next.size == 2 && next[0].first == DreamShaderTokenTypes.IDENTIFIER && next[1].second in setOf("(", "=", "{")) return true
            }
            when (type) {
                DreamShaderTokenTypes.LBRACE -> braces++
                DreamShaderTokenTypes.RBRACE -> braces--
                DreamShaderTokenTypes.LPAREN -> parentheses++
                DreamShaderTokenTypes.RPAREN -> parentheses--
            }

            if (type === DreamShaderTokenTypes.IDENTIFIER &&
                text.subSequence(tokenStart, tokenEnd).toString() in V2_DECLARATION_MARKERS
            ) {
                return true
            }
            if ((type === DreamShaderTokenTypes.OPERATOR || type === DreamShaderTokenTypes.BAD_CHARACTER) &&
                tokenEnd - tokenStart == 1 &&
                text[tokenStart] == '#' &&
                isAtLineStart(text, tokenStart)
            ) {
                // The V1 lexer has no `#` token: a line-start `#` arrives as BAD_CHARACTER.
                val word = directiveWordAt(text, tokenEnd)
                if (word in V2_PREPROCESSOR_MARKERS) {
                    return true
                }
            }
            if (type !in setOf(DreamShaderTokenTypes.WHITE_SPACE, DreamShaderTokenTypes.LINE_COMMENT, DreamShaderTokenTypes.BLOCK_COMMENT))
                previous = text.subSequence(tokenStart, tokenEnd).toString()
            lexer.advance()
        }
        return false
    }

    fun hasV1Markers(text: CharSequence): Boolean {
        val lexer = DreamShaderLexer()
        lexer.start(text)
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            val isBlockKeyword = type === DreamShaderTokenTypes.KEYWORD &&
                lexer.tokenText?.lowercase(Locale.ROOT) in V1_BLOCK_KEYWORDS
            val isSection = type === DreamShaderTokenTypes.SECTION
            if ((isBlockKeyword || isSection) &&
                nextMeaningfulTokenStartsBlock(lexer, text)
            ) {
                return true
            }
            lexer.advance()
        }
        return false
    }

    /** True when the next non-trivia token after the lexer's current one opens a 1.x block head. */
    private fun nextMeaningfulTokenStartsBlock(lexer: DreamShaderLexer, text: CharSequence): Boolean {
        val lookahead = DreamShaderLexer()
        lookahead.start(text, lexer.tokenEnd, lexer.bufferEnd, 0)
        while (lookahead.tokenType != null) {
            val type = lookahead.tokenType
            if (type == DreamShaderTokenTypes.WHITE_SPACE ||
                type == DreamShaderTokenTypes.LINE_COMMENT ||
                type == DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                lookahead.advance()
                continue
            }
            return type == DreamShaderTokenTypes.IDENTIFIER ||
                type == DreamShaderTokenTypes.LPAREN ||
                type == DreamShaderTokenTypes.LBRACE
        }
        return false
    }

    private fun directiveWordAt(text: CharSequence, start: Int): String {
        var end = start
        while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_')) {
            end++
        }
        return text.subSequence(start, end).toString().lowercase(Locale.ROOT)
    }

    private fun isAtLineStart(text: CharSequence, offset: Int): Boolean {
        var i = offset - 1
        while (i >= 0) {
            val c = text[i]
            if (c == '\n' || c == '\r') return true
            if (!c.isWhitespace()) return false
            i--
        }
        return true
    }
}
