package com.github.tsdaer.dreamshaderlanguagesupport.language.parser

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Routes the parse by dialect: any V2 content marker builds the 2.0 tree (the V2 parser handles
 * legacy blocks opaquely, so mixed headers parse), everything else keeps the frozen V1 parser.
 *
 * The predicate matches [com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderDispatchingLexer],
 * so the token stream and the parser always agree for a given buffer.
 */
class DreamShaderDialectAwarePsiParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        return if (DreamShaderDialects.routesToV2(builder.originalText)) {
            DreamShader2PsiParser().parse(root, builder)
        } else {
            DreamShaderPsiParser().parse(root, builder)
        }
    }
}
