package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * $name 语法高亮实现。
 */
class DreamShaderSyntaxHighlighter : SyntaxHighlighterBase() {
    override fun getHighlightingLexer(): Lexer = DreamShaderLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return when (tokenType) {
            DreamShaderTokenTypes.KEYWORD -> KEYWORD_KEYS
            DreamShaderTokenTypes.SECTION -> SECTION_KEYS
            DreamShaderTokenTypes.TYPE -> TYPE_KEYS
            DreamShaderTokenTypes.STRING -> STRING_KEYS
            DreamShaderTokenTypes.NUMBER -> NUMBER_KEYS
            DreamShaderTokenTypes.LINE_COMMENT,
            DreamShaderTokenTypes.BLOCK_COMMENT -> COMMENT_KEYS
            DreamShaderTokenTypes.LPAREN,
            DreamShaderTokenTypes.RPAREN,
            DreamShaderTokenTypes.LBRACKET,
            DreamShaderTokenTypes.RBRACKET,
            DreamShaderTokenTypes.LBRACE,
            DreamShaderTokenTypes.RBRACE -> BRACE_KEYS
            DreamShaderTokenTypes.OPERATOR -> OPERATOR_KEYS
            DreamShaderTokenTypes.BAD_CHARACTER -> BAD_CHAR_KEYS
            else -> EMPTY_KEYS
        }
    }

    companion object {
        private val KEYWORD_KEYS = arrayOf(DreamShaderTextAttributes.KEYWORD)
        private val SECTION_KEYS = arrayOf(DreamShaderTextAttributes.SECTION)
        private val TYPE_KEYS = arrayOf(DreamShaderTextAttributes.TYPE)
        private val STRING_KEYS = arrayOf(DreamShaderTextAttributes.STRING)
        private val NUMBER_KEYS = arrayOf(DreamShaderTextAttributes.NUMBER)
        private val COMMENT_KEYS = arrayOf(DreamShaderTextAttributes.COMMENT)
        private val BRACE_KEYS = arrayOf(DreamShaderTextAttributes.BRACES)
        private val OPERATOR_KEYS = arrayOf(DreamShaderTextAttributes.OPERATOR)
        private val BAD_CHAR_KEYS = arrayOf(DreamShaderTextAttributes.BAD_CHARACTER)
        private val EMPTY_KEYS = emptyArray<TextAttributesKey>()
    }
}
