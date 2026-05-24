package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lang.BracePair
import com.intellij.lang.PairedBraceMatcher
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * Implementation of DreamShaderBraceMatcher.
 */
class DreamShaderBraceMatcher : PairedBraceMatcher {
    override fun getPairs(): Array<BracePair> {
        return arrayOf(
            BracePair(DreamShaderTokenTypes.LPAREN, DreamShaderTokenTypes.RPAREN, false),
            BracePair(DreamShaderTokenTypes.LBRACKET, DreamShaderTokenTypes.RBRACKET, false),
            BracePair(DreamShaderTokenTypes.LBRACE, DreamShaderTokenTypes.RBRACE, true)
        )
    }

    override fun isPairedBracesAllowedBeforeType(lbraceType: IElementType, contextType: IElementType?): Boolean = true

    override fun getCodeConstructStart(file: PsiFile, openingBraceOffset: Int): Int = openingBraceOffset
}
