package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.openapi.fileTypes.SingleLazyInstanceSyntaxHighlighterFactory
import com.intellij.openapi.fileTypes.SyntaxHighlighter

class DreamShaderSyntaxHighlighterFactory : SingleLazyInstanceSyntaxHighlighterFactory() {
    override fun createHighlighter(): SyntaxHighlighter = DreamShaderSyntaxHighlighter()
}
