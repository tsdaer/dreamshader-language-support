package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

/**
 * Implementation of DreamShaderElementType.
 */
class DreamShaderElementType(debugName: String) : IElementType(debugName, DreamShaderLanguage)

/**
 * Singleton for DreamShaderElementTypes.
 */
object DreamShaderElementTypes {
    @JvmField
    val FILE: IFileElementType = IFileElementType(DreamShaderLanguage)

    @JvmField
    val DECLARATION: IElementType = DreamShaderElementType("DECLARATION")

    @JvmField
    val SECTION: IElementType = DreamShaderElementType("SECTION")
}
