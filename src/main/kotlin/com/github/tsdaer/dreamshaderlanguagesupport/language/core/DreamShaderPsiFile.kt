package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.extapi.psi.PsiFileBase
import com.intellij.psi.FileViewProvider

/**
 * $name 类型定义。
 */
class DreamShaderPsiFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, DreamShaderLanguage) {
    override fun getFileType() = DreamShaderFileType.INSTANCE

    override fun toString(): String = "DreamShader File"
}
