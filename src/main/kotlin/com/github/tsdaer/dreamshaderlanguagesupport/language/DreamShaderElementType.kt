package com.github.tsdaer.dreamshaderlanguagesupport.language

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
