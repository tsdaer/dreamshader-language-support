package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.codeInsight.editorActions.SimpleTokenSetQuoteHandler
import com.intellij.psi.tree.TokenSet

class DreamShaderQuoteHandler : SimpleTokenSetQuoteHandler(TokenSet.create(DreamShaderTokenTypes.STRING))
