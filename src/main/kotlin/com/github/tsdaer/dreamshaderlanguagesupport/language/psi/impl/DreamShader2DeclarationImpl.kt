package com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2PragmaDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.lang.ASTNode
import com.intellij.psi.tree.IElementType

/**
 * AST-backed implementations of the DreamShader 2.0 declaration PSI. All accessors walk the
 * leaves the 2.0 parser produced; nothing is cached here (the tree is the source of truth).
 */
abstract class DreamShader2PsiElementBase(node: ASTNode) : ASTWrapperPsiElement(node) {

    protected fun leafs(): Sequence<ASTNode> = generateSequence(node.firstChildNode) { it.treeNext }

    protected fun firstLeafAfterType(vararg skippedTypes: IElementType): ASTNode? {
        var sawSkippedLeaf = skippedTypes.isEmpty()
        leafs().forEach { leaf ->
            val type = leaf.elementType
            if (skippedTypes.any { it === type }) {
                sawSkippedLeaf = true
            } else if (sawSkippedLeaf &&
                (type == DreamShaderTokenTypes.IDENTIFIER || type == DreamShaderTokenTypes.KEYWORD)
            ) {
                return leaf
            }
        }
        return null
    }

    protected fun docBlockText(): String = leafs()
        .filter { it.elementType == DreamShader2ElementTypes.DOC_BLOCK }
        .joinToString("\n") { it.text }

    protected fun firstTypeLeafText(): String = leafs()
        .firstOrNull { it.elementType == DreamShaderTokenTypes.TYPE }
        ?.text ?: ""
}

open class DreamShader2UniformDeclarationImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), DreamShader2UniformDeclaration {

    override fun declarationName(): String? =
        firstLeafAfterType(DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.TYPE)?.text

    override fun typeName(): String = firstTypeLeafText()

    override fun isStaticSwitch(): Boolean = docBlockText().contains("@static")
}

open class DreamShader2GlobalDeclarationImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), DreamShader2GlobalDeclaration {

    override fun declarationName(): String? =
        firstLeafAfterType(DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.TYPE)?.text

    override fun typeName(): String = firstTypeLeafText()
}

open class DreamShader2FunctionDeclarationImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), DreamShader2FunctionDeclaration {

    override fun declarationName(): String? =
        firstLeafAfterType(DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.TYPE)?.text

    override fun isExport(): Boolean = leafs().any {
        it.elementType == DreamShaderTokenTypes.KEYWORD && it.text == "export"
    }

    override fun isExtern(): Boolean = leafs().any {
        it.elementType == DreamShaderTokenTypes.KEYWORD && it.text == "extern"
    }

    override fun hasCustomBody(): Boolean = leafs().any {
        it.elementType == DreamShader2ElementTypes.CUSTOM_BODY
    } || docBlockText().contains("@custom")
}

open class DreamShader2StructDeclarationImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), DreamShader2StructDeclaration {

    override fun declarationName(): String? = leafs()
        .firstOrNull { it.elementType == DreamShaderTokenTypes.IDENTIFIER }
        ?.text
}

open class DreamShader2IncludeDirectiveImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), DreamShader2IncludeDirective {

    override fun pathText(): String? = leafs()
        .firstOrNull { it.elementType == DreamShaderTokenTypes.STRING }
        ?.text
        ?.trim('"')
        ?.takeIf { it.isNotEmpty() }
}

open class DreamShader2PragmaDirectiveImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), DreamShader2PragmaDirective {

    override fun pragmaName(): String? {
        var sawDirective = false
        leafs().forEach { leaf ->
            when {
                leaf.elementType == DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE -> sawDirective = true
                sawDirective &&
                    (leaf.elementType == DreamShaderTokenTypes.IDENTIFIER ||
                        leaf.elementType == DreamShaderTokenTypes.TYPE ||
                        leaf.elementType == DreamShaderTokenTypes.KEYWORD)
                -> return leaf.text
            }
        }
        return null
    }
}

/**
 * PSI factory for the 2.0 element types; wired through the shared parser definition so one
 * language serves both dialects.
 */
object DreamShader2PsiElementFactory {
    fun createElement(node: ASTNode) = when (node.elementType) {
        DreamShader2ElementTypes.UNIFORM_DECLARATION -> DreamShader2UniformDeclarationImpl(node)
        DreamShader2ElementTypes.GLOBAL_DECLARATION -> DreamShader2GlobalDeclarationImpl(node)
        DreamShader2ElementTypes.FUNCTION_DECLARATION -> DreamShader2FunctionDeclarationImpl(node)
        DreamShader2ElementTypes.STRUCT_DECLARATION -> DreamShader2StructDeclarationImpl(node)
        DreamShader2ElementTypes.INCLUDE_DIRECTIVE -> DreamShader2IncludeDirectiveImpl(node)
        DreamShader2ElementTypes.PRAGMA_DIRECTIVE -> DreamShader2PragmaDirectiveImpl(node)
        else -> null
    }
}
