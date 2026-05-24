package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class DreamShaderPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, DreamShaderLanguage) {
    override fun getFileType() = DreamShaderFileType.INSTANCE

    override fun toString(): String = "DreamShader File"
}
