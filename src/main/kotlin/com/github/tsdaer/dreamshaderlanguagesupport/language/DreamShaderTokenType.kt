package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

class DreamShaderTokenType(debugName: String) : IElementType(debugName, DreamShaderLanguage)

object DreamShaderTokenTypes {
    @JvmField
    val KEYWORD = DreamShaderTokenType("KEYWORD")

    @JvmField
    val SECTION = DreamShaderTokenType("SECTION")

    @JvmField
    val TYPE = DreamShaderTokenType("TYPE")

    @JvmField
    val STRING = DreamShaderTokenType("STRING")

    @JvmField
    val NUMBER = DreamShaderTokenType("NUMBER")

    @JvmField
    val LINE_COMMENT = DreamShaderTokenType("LINE_COMMENT")

    @JvmField
    val BLOCK_COMMENT = DreamShaderTokenType("BLOCK_COMMENT")

    @JvmField
    val IDENTIFIER = DreamShaderTokenType("IDENTIFIER")

    @JvmField
    val OPERATOR = DreamShaderTokenType("OPERATOR")

    @JvmField
    val LPAREN = DreamShaderTokenType("LPAREN")

    @JvmField
    val RPAREN = DreamShaderTokenType("RPAREN")

    @JvmField
    val LBRACKET = DreamShaderTokenType("LBRACKET")

    @JvmField
    val RBRACKET = DreamShaderTokenType("RBRACKET")

    @JvmField
    val LBRACE = DreamShaderTokenType("LBRACE")

    @JvmField
    val RBRACE = DreamShaderTokenType("RBRACE")

    @JvmField
    val BAD_CHARACTER = TokenType.BAD_CHARACTER

    @JvmField
    val WHITE_SPACE = TokenType.WHITE_SPACE
}
