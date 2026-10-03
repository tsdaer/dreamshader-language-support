package com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2Parameter
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2ParameterList
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2PragmaDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformStub
import com.intellij.extapi.psi.ASTWrapperPsiElement
import com.intellij.extapi.psi.StubBasedPsiElementBase
import com.intellij.lang.ASTNode
import com.intellij.psi.PsiElement
import com.intellij.psi.StubBasedPsiElement
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil

/**
 * AST-backed implementations of the DreamShader 2.0 declaration PSI.
 *
 * The five stub-backed declarations ([plan C1]) are [StubBasedPsiElementBase]s with dual
 * constructors — stub for the index path, AST node for the tree path — and their accessors
 * read the stub first, so an index query resolves names and type spellings without
 * materializing the AST. Everything else walks the leaves the 2.0 parser produced; the tree
 * is the source of truth. The leaf walkers below are shared by the stub and non-stub
 * hierarchies, so both stay in lockstep on what a declaration spells.
 */

internal fun dreamShader2Leafs(element: PsiElement): Sequence<ASTNode> =
    generateSequence(element.node.firstChildNode) { it.treeNext }

/** First identifier/keyword leaf that follows any of [skippedTypes]; the declaration-name walker. */
internal fun dreamShader2FirstLeafAfterType(
    element: PsiElement,
    vararg skippedTypes: IElementType,
): ASTNode? {
    var sawSkippedLeaf = skippedTypes.isEmpty()
    dreamShader2Leafs(element).forEach { leaf ->
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

internal fun dreamShader2DocBlockText(element: PsiElement): String = dreamShader2Leafs(element)
    .filter { it.elementType == DreamShader2ElementTypes.DOC_BLOCK }
    .joinToString("\n") { it.text }

internal fun dreamShader2FirstTypeLeafText(element: PsiElement): String = dreamShader2Leafs(element)
    .firstOrNull { it.elementType == DreamShaderTokenTypes.TYPE }
    ?.text ?: ""

/**
 * Direct child nodes without the whitespace the PsiBuilder emits between tokens — the shape
 * every "=" / operator index-based scan over expression and statement nodes must walk.
 */
internal fun dreamShader2MeaningfulChildren(element: PsiElement): List<ASTNode> =
    element.node.getChildren(null).filter { it.elementType != DreamShaderTokenTypes.WHITE_SPACE }

/**
 * The type spelling of a typed declaration (`uniform float3 a;` → `float3`, `MyStruct v;` →
 * `MyStruct`): the first TYPE/IDENTIFIER leaf after the leading modifier keywords. Only
 * meaningful on uniform/global/local/parameter/field nodes — a struct's own node starts with
 * the `struct` keyword and then its *name*.
 */
internal fun dreamShader2TypeSpellingOf(declaration: PsiElement): String? {
    dreamShader2Leafs(declaration).forEach { leaf ->
        when (leaf.elementType) {
            DreamShaderTokenTypes.TYPE -> return leaf.text
            DreamShaderTokenTypes.IDENTIFIER -> return leaf.text
            DreamShaderTokenTypes.KEYWORD -> Unit // uniform / static / const / export / extern / inout
            else -> if (leaf.elementType != DreamShader2ElementTypes.DOC_BLOCK) {
                // Anything else before a type leaf means the node is not a plain declaration.
                return null
            }
        }
    }
    return null
}

abstract class DreamShader2PsiElementBase(node: ASTNode) : ASTWrapperPsiElement(node) {

    protected fun leafs(): Sequence<ASTNode> = dreamShader2Leafs(this)

    protected fun firstLeafAfterType(vararg skippedTypes: IElementType): ASTNode? =
        dreamShader2FirstLeafAfterType(this, *skippedTypes)

    protected fun docBlockText(): String = dreamShader2DocBlockText(this)

    protected fun firstTypeLeafText(): String = dreamShader2FirstTypeLeafText(this)
}

/**
 * Shared base of the stub-backed 2.0 declarations: the dual-constructor boilerplate plus the
 * [com.intellij.psi.StubBasedPsiElement] contract the platform's stub builder enforces (the
 * 2026+ base class no longer implements it implicitly).
 */
abstract class DreamShader2StubbedDeclarationBase<StubT : StubElement<*>> :
    DreamShader2NamedDeclaration,
    StubBasedPsiElement<StubT>,
    StubBasedPsiElementBase<StubT> {

    constructor(node: ASTNode) : super(node)

    constructor(stub: StubT, nodeType: IElementType) : super(stub, nodeType)

    override fun getStub(): StubT? = super.getStub()
}

open class DreamShader2UniformDeclarationImpl : DreamShader2StubbedDeclarationBase<DreamShader2UniformStub>,
    DreamShader2UniformDeclaration {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2UniformStub, nodeType: IElementType) : super(stub, nodeType)

    override fun declarationName(): String? = stub?.name
        ?: dreamShader2FirstLeafAfterType(this, DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.TYPE)?.text

    override fun typeName(): String = stub?.typeName ?: dreamShader2FirstTypeLeafText(this)

    override fun isStaticSwitch(): Boolean =
        stub?.isStaticSwitch ?: dreamShader2DocBlockText(this).contains("@static")
}

open class DreamShader2GlobalDeclarationImpl : DreamShader2StubbedDeclarationBase<DreamShader2GlobalStub>,
    DreamShader2GlobalDeclaration {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2GlobalStub, nodeType: IElementType) : super(stub, nodeType)

    override fun declarationName(): String? = stub?.name
        ?: dreamShader2FirstLeafAfterType(this, DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.TYPE)?.text

    override fun typeName(): String = stub?.typeName ?: dreamShader2FirstTypeLeafText(this)
}

open class DreamShader2FunctionDeclarationImpl : DreamShader2StubbedDeclarationBase<DreamShader2FunctionStub>,
    DreamShader2FunctionDeclaration {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2FunctionStub, nodeType: IElementType) : super(stub, nodeType)

    override fun declarationName(): String? = stub?.name
        ?: dreamShader2FirstLeafAfterType(this, DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.TYPE)?.text

    override fun isExport(): Boolean = stub?.isExport ?: dreamShader2Leafs(this).any {
        it.elementType == DreamShaderTokenTypes.KEYWORD && it.text == "export"
    }

    override fun isExtern(): Boolean = stub?.isExtern ?: dreamShader2Leafs(this).any {
        it.elementType == DreamShaderTokenTypes.KEYWORD && it.text == "extern"
    }

    override fun returnTypeText(): String? = stub?.typeName
        ?: dreamShader2FirstTypeLeafText(this).takeIf { it.isNotEmpty() }

    override fun parameterList(): DreamShader2ParameterList? =
        PsiTreeUtil.getChildOfType(this, DreamShader2ParameterList::class.java)

    override fun hasCustomBody(): Boolean = stub?.hasCustomBody ?: (
        dreamShader2Leafs(this).any { it.elementType == DreamShader2ElementTypes.CUSTOM_BODY } ||
            dreamShader2DocBlockText(this).contains("@custom")
        )
}

open class DreamShader2StructDeclarationImpl : DreamShader2StubbedDeclarationBase<DreamShader2StructStub>,
    DreamShader2StructDeclaration {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2StructStub, nodeType: IElementType) : super(stub, nodeType)

    override fun declarationName(): String? = stub?.name
        ?: dreamShader2Leafs(this)
            .firstOrNull { it.elementType == DreamShaderTokenTypes.IDENTIFIER }?.text
}

open class DreamShader2StructFieldImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructField {

    override fun fieldName(): String? =
        dreamShader2FirstLeafAfterType(this, DreamShaderTokenTypes.TYPE)?.text

    override fun typeName(): String? = dreamShader2TypeSpellingOf(this)
}

open class DreamShader2IncludeDirectiveImpl : StubBasedPsiElement<DreamShader2IncludeStub>,
    StubBasedPsiElementBase<DreamShader2IncludeStub>,
    DreamShader2IncludeDirective {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2IncludeStub, nodeType: IElementType) : super(stub, nodeType)

    override fun getStub(): DreamShader2IncludeStub? = super.getStub()

    override fun pathText(): String? = stub?.path
        ?: dreamShader2Leafs(this)
            .firstOrNull { it.elementType == DreamShaderTokenTypes.STRING }
            ?.text
            ?.trim('"')
            ?.takeIf { it.isNotEmpty() }
}

/** Parameter list / parameter PSI (not stubbed — only queried inside an already-open file). */
open class DreamShader2ParameterListImpl(node: ASTNode) : DreamShader2PsiElementBase(node), DreamShader2ParameterList {
    override fun parameters(): List<DreamShader2Parameter> =
        PsiTreeUtil.getChildrenOfTypeAsList(this, DreamShader2Parameter::class.java)
}

open class DreamShader2ParameterImpl(node: ASTNode) : DreamShader2PsiElementBase(node), DreamShader2Parameter {
    override fun parameterName(): String? =
        dreamShader2FirstLeafAfterType(this, DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.TYPE)?.text

    override fun typeName(): String? = dreamShader2Leafs(this)
        .firstOrNull {
            it.elementType == DreamShaderTokenTypes.TYPE || it.elementType == DreamShaderTokenTypes.IDENTIFIER
        }
        ?.text
}

open class DreamShader2ArgumentListImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2ArgumentList {

    override fun arguments(): List<PsiElement> = node.getChildren(null)
        .filter {
            it.elementType == DreamShader2ElementTypes.ARGUMENT ||
                it.elementType == DreamShader2ElementTypes.NAMED_ARGUMENT
        }
        .map { it.psi }
}

open class DreamShader2ArgumentImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2Argument

open class DreamShader2NamedArgumentImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedArgument {

    override fun argumentName(): String? = node.firstChildNode
        ?.takeIf { it.elementType == DreamShaderTokenTypes.IDENTIFIER }
        ?.text
}

open class DreamShader2PragmaDirectiveImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), DreamShader2PragmaDirective {

    override fun pragmaName(): String? {
        var sawDirective = false
        dreamShader2Leafs(this).forEach { leaf ->
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
        DreamShader2ElementTypes.STRUCT_FIELD -> DreamShader2StructFieldImpl(node)
        DreamShader2ElementTypes.PARAMETER_LIST -> DreamShader2ParameterListImpl(node)
        DreamShader2ElementTypes.PARAMETER -> DreamShader2ParameterImpl(node)
        DreamShader2ElementTypes.ARGUMENT_LIST -> DreamShader2ArgumentListImpl(node)
        DreamShader2ElementTypes.ARGUMENT -> DreamShader2ArgumentImpl(node)
        DreamShader2ElementTypes.NAMED_ARGUMENT -> DreamShader2NamedArgumentImpl(node)
        else -> null
    }
}
