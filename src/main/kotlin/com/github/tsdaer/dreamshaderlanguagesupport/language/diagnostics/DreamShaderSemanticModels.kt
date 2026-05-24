package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType

/**
 * $name Token 模型。
 */
internal data class DreamShaderLexedToken(
    val type: IElementType,
    val text: String,
    val range: TextRange,
    val depthBefore: Int
) {
    val isTrivia: Boolean
        get() = type == DreamShaderTokenTypes.WHITE_SPACE ||
            type == DreamShaderTokenTypes.LINE_COMMENT ||
            type == DreamShaderTokenTypes.BLOCK_COMMENT
}

