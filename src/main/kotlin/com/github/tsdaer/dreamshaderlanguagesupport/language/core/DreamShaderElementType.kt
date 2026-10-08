package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.psi.PsiFile
import com.intellij.psi.stubs.PsiFileStub
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.IStubFileElementType

/**
 * Implementation of DreamShaderElementType.
 */
class DreamShaderElementType(debugName: String) : IElementType(debugName, DreamShaderLanguage)

/**
 * Singleton for DreamShaderElementTypes.
 *
 * The file root is stub-backed (plan C1): the platform builds a stub tree for every
 * DreamShader file through the default stub builder, and the 2.0 stub element types populate
 * the stub indexes. 1.x element types are plain [IElementType]s and contribute no stubs, so
 * the frozen 1.x pipeline keeps its tree shape and behavior untouched.
 */
object DreamShaderElementTypes {
    @JvmField
    val FILE: IFileElementType = object : IStubFileElementType<PsiFileStub<PsiFile>>(DreamShaderLanguage) {
        override fun getExternalId(): String = "dreamshader.FILE"

        override fun doParseContents(chameleon: com.intellij.lang.ASTNode, psi: com.intellij.psi.PsiElement): com.intellij.lang.ASTNode? {
            val name = (psi as? PsiFile)?.name ?: psi.containingFile?.name.orEmpty()
            if (DreamShaderDialects.extensionOf(name) !in DreamShaderDialects.V2_EXTENSIONS) return super.doParseContents(chameleon, psi)
            val builder = com.intellij.lang.PsiBuilderFactory.getInstance().createBuilder(psi.project, chameleon,
                com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShader2Lexer(), DreamShaderLanguage, chameleon.chars)
            return com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShader2PsiParser().parse(this, builder).firstChildNode
        }

        override fun getStubVersion(): Int = DreamShader2StubElementType.STUB_VERSION
    }

    @JvmField
    val DECLARATION: IElementType = DreamShaderElementType("DECLARATION")

    @JvmField
    val SECTION: IElementType = DreamShaderElementType("SECTION")
}
