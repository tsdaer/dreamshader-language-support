package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter

/**
 * Factory implementation for DreamShaderSyntaxHighlighterFactory.
 */
class DreamShaderSyntaxHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {
    override fun createHighlighter(): SyntaxHighlighter = DreamShaderSyntaxHighlighter()
}
