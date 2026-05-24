package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType

/**
 * $name 类型定义。
 */
class DreamShaderElementType(debugName: String) : IElementType(debugName, DreamShaderLanguage)

/**
 * $name 常量定义对象。
 */
object DreamShaderElementTypes {
    @JvmField
    val FILE: IFileElementType = IFileElementType(DreamShaderLanguage)

    @JvmField
    val DECLARATION: IElementType = DreamShaderElementType("DECLARATION")

    @JvmField
    val SECTION: IElementType = DreamShaderElementType("SECTION")
}
