package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShader2Lexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * Syntax highlighter for the DreamShader 2.0 dialect. Shares the V1 color keys so one color
 * scheme serves both dialects; the V2-only tokens (doc comments, preprocessor directives,
 * punctuation) map onto their nearest existing attributes.
 */
class DreamShaderSyntaxHighlighterV2 : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = DreamShader2Lexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            DreamShaderTokenTypes.KEYWORD -> KEYWORD_KEYS
            DreamShaderTokenTypes.TYPE -> TYPE_KEYS
            DreamShaderTokenTypes.STRING -> STRING_KEYS
            DreamShaderTokenTypes.NUMBER -> NUMBER_KEYS
            DreamShaderTokenTypes.DOC_COMMENT -> DOC_COMMENT_KEYS
            DreamShaderTokenTypes.LINE_COMMENT,
            DreamShaderTokenTypes.BLOCK_COMMENT,
            -> COMMENT_KEYS
            DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE -> PREPROCESSOR_KEYS
            DreamShaderTokenTypes.LPAREN,
            DreamShaderTokenTypes.RPAREN,
            DreamShaderTokenTypes.LBRACKET,
            DreamShaderTokenTypes.RBRACKET,
            DreamShaderTokenTypes.LBRACE,
            DreamShaderTokenTypes.RBRACE,
            -> BRACE_KEYS
            DreamShaderTokenTypes.DOT,
            DreamShaderTokenTypes.COMMA,
            DreamShaderTokenTypes.SEMICOLON,
            -> PUNCTUATION_KEYS
            DreamShaderTokenTypes.OPERATOR -> OPERATOR_KEYS
            DreamShaderTokenTypes.BAD_CHARACTER -> BAD_CHAR_KEYS
            else -> EMPTY_KEYS
        }
    }

    private companion object {
        val KEYWORD_KEYS = arrayOf(DreamShaderTextAttributes.KEYWORD)
        val TYPE_KEYS = arrayOf(DreamShaderTextAttributes.TYPE)
        val STRING_KEYS = arrayOf(DreamShaderTextAttributes.STRING)
        val NUMBER_KEYS = arrayOf(DreamShaderTextAttributes.NUMBER)
        val COMMENT_KEYS = arrayOf(DreamShaderTextAttributes.COMMENT)
        val DOC_COMMENT_KEYS = arrayOf(DreamShaderTextAttributes.DOC_COMMENT)
        val PREPROCESSOR_KEYS = arrayOf(DreamShaderTextAttributes.PREPROCESSOR)
        val BRACE_KEYS = arrayOf(DreamShaderTextAttributes.BRACES)
        val PUNCTUATION_KEYS = arrayOf(DreamShaderTextAttributes.PUNCTUATION)
        val OPERATOR_KEYS = arrayOf(DreamShaderTextAttributes.OPERATOR)
        val BAD_CHAR_KEYS = arrayOf(DreamShaderTextAttributes.BAD_CHARACTER)
        val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }
}
