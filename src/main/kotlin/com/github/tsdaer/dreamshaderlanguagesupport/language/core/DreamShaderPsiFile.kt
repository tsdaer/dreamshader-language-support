package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

/**
 * Implementation of DreamShaderPsiFile.
 */
class DreamShaderPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, DreamShaderLanguage) {
    override fun getFileType() = DreamShaderFileType.INSTANCE

    override fun toString(): String = "DreamShader File"
}
