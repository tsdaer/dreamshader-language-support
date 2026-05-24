package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter

/**
 * $name 工厂实现。
 */
class DreamShaderSyntaxHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {
    override fun createHighlighter(): SyntaxHighlighter = DreamShaderSyntaxHighlighter()
}
