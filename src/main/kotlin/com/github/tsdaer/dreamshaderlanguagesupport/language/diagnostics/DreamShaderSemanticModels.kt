package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.openapi.util.TextRange
import com.intellij.psi.tree.IElementType

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

