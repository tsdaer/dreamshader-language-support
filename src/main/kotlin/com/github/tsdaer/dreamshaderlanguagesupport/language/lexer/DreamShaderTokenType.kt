package com.github.tsdaer.dreamshaderlanguagesupport.language.lexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Implementation of DreamShaderTokenType.
 */
class DreamShaderTokenType(debugName: String) : IElementType(debugName, DreamShaderLanguage)

/**
 * Singleton for DreamShaderTokenTypes.
 */
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

    // --- DreamShader 2.0 (V2 dialect) tokens; the frozen V1 lexer never emits these. ---

    /** `.` — 2.0 member access/swizzle separator (V1 folds it into OPERATOR). */
    @JvmField
    val DOT = DreamShaderTokenType("DOT")

    /** `,` — 2.0 separator (V1 folds it into OPERATOR). */
    @JvmField
    val COMMA = DreamShaderTokenType("COMMA")

    /** `;` — 2.0 statement terminator (V1 folds it into OPERATOR). */
    @JvmField
    val SEMICOLON = DreamShaderTokenType("SEMICOLON")

    /** `/// ...` doc comment carrying `@key value` directives in the 2.0 dialect. */
    @JvmField
    val DOC_COMMENT = DreamShaderTokenType("DOC_COMMENT")

    /** Line-start `#pragma` / `#include` / `#if` ... directive word (the `#` plus the word). */
    @JvmField
    val PREPROCESSOR_DIRECTIVE = DreamShaderTokenType("PREPROCESSOR_DIRECTIVE")

    @JvmField
    val BAD_CHARACTER = TokenType.BAD_CHARACTER

    @JvmField
    val WHITE_SPACE = TokenType.WHITE_SPACE
}
