package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.openapi.fileTypes.LanguageFileType
import javax.swing.Icon

class DreamShaderFileType private constructor() : LanguageFileType(DreamShaderLanguage) {
    override fun getName(): String = "DreamShaderLang"

    override fun getDescription(): String = "DreamShaderLang source file"

    override fun getDefaultExtension(): String = "dsm"

    override fun getIcon(): Icon? = DreamShaderIcons.FILE

    companion object {
        @JvmField
        val INSTANCE = DreamShaderFileType()
    }
}
