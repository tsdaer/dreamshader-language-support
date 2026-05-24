package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

class DreamShaderElementType(debugName: String) : IElementType(debugName, DreamShaderLanguage)

object DreamShaderElementTypes {
    @JvmField
    val FILE: IFileElementType = IFileElementType(DreamShaderLanguage)

    @JvmField
    val DECLARATION: IElementType = DreamShaderElementType("DECLARATION")

    @JvmField
    val SECTION: IElementType = DreamShaderElementType("SECTION")
}
