package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
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
