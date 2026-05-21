package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

class DreamShaderPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, DreamShaderLanguage) {
    override fun getFileType() = DreamShaderFileType.INSTANCE

    override fun toString(): String = "DreamShader File"
}
