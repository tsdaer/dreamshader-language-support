package com.github.tsdaer.dreamshaderlanguagesupport.language.lexer

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Chooses the token source by dialect: any V2 marker routes to [DreamShader2Lexer], everything
 * else keeps the frozen V1 lexer. The dispatch uses the same predicate as the parser dispatch, so
 * lexer tokens and parser expectations always agree for a given buffer.
 */
class DreamShaderDispatchingLexer : LexerBase() {
    private var delegate: LexerBase = DreamShaderLexer()

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        delegate = if (DreamShaderDialects.routesToV2(buffer)) DreamShader2Lexer() else DreamShaderLexer()
        delegate.start(buffer, startOffset, endOffset, 0)
    }

    override fun getState(): Int = delegate.state

    override fun getTokenType(): IElementType? = delegate.tokenType

    override fun getTokenStart(): Int = delegate.tokenStart

    override fun getTokenEnd(): Int = delegate.tokenEnd

    override fun getBufferSequence(): CharSequence = delegate.bufferSequence

    override fun getBufferEnd(): Int = delegate.bufferEnd

    override fun advance() = delegate.advance()
}
