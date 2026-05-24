package com.github.tsdaer.dreamshaderlanguagesupport.language.lexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.psi.tree.TokenSet

object DreamShaderTokenSets {
    @JvmField
    val COMMENTS: TokenSet = TokenSet.create(
        DreamShaderTokenTypes.LINE_COMMENT,
        DreamShaderTokenTypes.BLOCK_COMMENT
    )

    @JvmField
    val STRINGS: TokenSet = TokenSet.create(DreamShaderTokenTypes.STRING)

    @JvmField
    val NUMBERS: TokenSet = TokenSet.create(DreamShaderTokenTypes.NUMBER)

    @JvmField
    val KEYWORDS: TokenSet = TokenSet.create(DreamShaderTokenTypes.KEYWORD)

    @JvmField
    val SECTIONS: TokenSet = TokenSet.create(DreamShaderTokenTypes.SECTION)

    @JvmField
    val TYPES: TokenSet = TokenSet.create(DreamShaderTokenTypes.TYPE)

    @JvmField
    val BRACES: TokenSet = TokenSet.create(
        DreamShaderTokenTypes.LPAREN,
        DreamShaderTokenTypes.RPAREN,
        DreamShaderTokenTypes.LBRACKET,
        DreamShaderTokenTypes.RBRACKET,
        DreamShaderTokenTypes.LBRACE,
        DreamShaderTokenTypes.RBRACE
    )

    @JvmField
    val OPERATORS: TokenSet = TokenSet.create(DreamShaderTokenTypes.OPERATOR)

    @JvmField
    val IDENTIFIERS: TokenSet = TokenSet.create(DreamShaderTokenTypes.IDENTIFIER)
}
