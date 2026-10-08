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
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiNameIdentifierOwner
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
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

/** A user struct type is an identifier too: skip the type before choosing the name. */
internal fun dreamShader2DeclarationIdentifier(element: PsiElement): PsiElement? {
    var sawType = false
    dreamShader2Leafs(element).forEach { child ->
        when (child.elementType) {
            DreamShaderTokenTypes.TYPE -> sawType = true
            DreamShaderTokenTypes.IDENTIFIER -> if (sawType) return child.psi else sawType = true
        }
    }
    return null
}

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
            DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.WHITE_SPACE, DreamShaderTokenTypes.LINE_COMMENT, DreamShaderTokenTypes.BLOCK_COMMENT -> Unit // modifiers and trivia
            else -> if (leaf.elementType != DreamShader2ElementTypes.DOC_BLOCK) {
                // Anything else before a type leaf means the node is not a plain declaration.
                return null
            }
        }
    }
    return null
}

/** Type identifiers carry a reference range independent of the declaration's own name. */
private fun dreamShader2TypeReferences(element: PsiElement): Array<com.intellij.psi.PsiReference> {
    val owner = element as? PsiNameIdentifierOwner ?: return emptyArray()
    val type = dreamShader2MeaningfulChildren(element).firstOrNull {
        it.elementType == DreamShaderTokenTypes.TYPE || it.elementType == DreamShaderTokenTypes.IDENTIFIER
    }?.takeIf { it.elementType == DreamShaderTokenTypes.IDENTIFIER }?.psi ?: return emptyArray()
    if (type == owner.nameIdentifier) return emptyArray()
    val binding = com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2SemanticResolver.resolveIdentifier(type)
        ?: return emptyArray()
    val target = binding.owner as? DreamShader2StructDeclaration ?: return emptyArray()
    if (target == element || (target as? PsiNameIdentifierOwner)?.nameIdentifier != binding.identifier) return emptyArray()
    return arrayOf(com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2LightReference(element, target, type))
}

abstract class DreamShader2PsiElementBase(node: ASTNode) : ASTWrapperPsiElement(node) {
    override fun getReferences(): Array<com.intellij.psi.PsiReference> =
        if (this is DreamShader2NamedDeclaration) dreamShader2TypeReferences(this) else super.getReferences()
    override fun getReference(): com.intellij.psi.PsiReference? =
        if (this is DreamShader2NamedDeclaration) dreamShader2TypeReferences(this).singleOrNull() else super.getReference()

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
    override fun getReferences(): Array<com.intellij.psi.PsiReference> = dreamShader2TypeReferences(this)
    override fun getReference(): com.intellij.psi.PsiReference? = dreamShader2TypeReferences(this).singleOrNull()
}

open class DreamShader2UniformDeclarationImpl : DreamShader2StubbedDeclarationBase<DreamShader2UniformStub>,
    DreamShader2UniformDeclaration, PsiNameIdentifierOwner {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2UniformStub, nodeType: IElementType) : super(stub, nodeType)

    override fun getName(): String? = declarationName()
    override fun getNameIdentifier(): PsiElement? = dreamShader2DeclarationIdentifier(this)
    override fun setName(name: String): PsiElement = renameLocalIdentifier(this, name)

    override fun declarationName(): String? = stub?.name
        ?: dreamShader2DeclarationIdentifier(this)?.text

    override fun typeName(): String = stub?.typeName ?: dreamShader2TypeSpellingOf(this).orEmpty()

    override fun isStaticSwitch(): Boolean =
        stub?.isStaticSwitch ?: dreamShader2DocBlockText(this).contains("@static")
}

open class DreamShader2GlobalDeclarationImpl : DreamShader2StubbedDeclarationBase<DreamShader2GlobalStub>,
    DreamShader2GlobalDeclaration, PsiNameIdentifierOwner {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2GlobalStub, nodeType: IElementType) : super(stub, nodeType)

    override fun getName(): String? = declarationName()
    override fun getNameIdentifier(): PsiElement? = dreamShader2DeclarationIdentifier(this)
    override fun setName(name: String): PsiElement = renameLocalIdentifier(this, name)

    override fun declarationName(): String? = stub?.name
        ?: dreamShader2DeclarationIdentifier(this)?.text

    override fun typeName(): String = stub?.typeName ?: dreamShader2TypeSpellingOf(this).orEmpty()
}

open class DreamShader2FunctionDeclarationImpl : DreamShader2StubbedDeclarationBase<DreamShader2FunctionStub>,
    DreamShader2FunctionDeclaration,
    PsiNameIdentifierOwner,
    com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderHlslBodyHost {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2FunctionStub, nodeType: IElementType) : super(stub, nodeType)

    override fun getName(): String? = declarationName()

    override fun getNameIdentifier(): PsiElement? =
        dreamShader2DeclarationIdentifier(this)

    override fun setName(name: String): PsiElement {
        val identifier = getNameIdentifier() ?: throw incorrectOperation("Function declaration has no name")
        val newName = PsiFileFactory.getInstance(project)
            .createFileFromText("dummy.dss", DreamShaderLanguage, "export void $name() {}")
            .let { dummy ->
                PsiTreeUtil.findChildOfType(dummy, DreamShader2FunctionDeclaration::class.java)?.let(::dreamShader2DeclarationIdentifier)
            } ?: throw incorrectOperation("Cannot create identifier '$name'")
        return identifier.replace(newName)
    }

    private fun incorrectOperation(message: String) = com.intellij.util.IncorrectOperationException(message)

    /** Bare-HLSL injection (D7): `/// @custom` bodies only, brace-inside range. */
    override fun dreamShaderHlslBodyRange(): com.intellij.openapi.util.TextRange? {
        if (!hasCustomBody()) return null
        return com.github.tsdaer.dreamshaderlanguagesupport.language.editor.customBodyRange(this)
    }

    override fun declarationName(): String? = stub?.name
        ?: dreamShader2DeclarationIdentifier(this)?.text

    override fun isExport(): Boolean = stub?.isExport ?: dreamShader2Leafs(this).any {
        it.elementType == DreamShaderTokenTypes.KEYWORD && it.text == "export"
    }

    override fun isExtern(): Boolean = stub?.isExtern ?: dreamShader2Leafs(this).any {
        it.elementType == DreamShaderTokenTypes.KEYWORD && it.text == "extern"
    }

    override fun returnTypeText(): String? = stub?.typeName
        ?: dreamShader2TypeSpellingOf(this)

    override fun parameterList(): DreamShader2ParameterList? =
        PsiTreeUtil.getChildOfType(this, DreamShader2ParameterList::class.java)

    override fun hasCustomBody(): Boolean = stub?.hasCustomBody ?: (
        dreamShader2Leafs(this).any { it.elementType == DreamShader2ElementTypes.CUSTOM_BODY } ||
            dreamShader2DocBlockText(this).contains("@custom")
        )
}

open class DreamShader2StructDeclarationImpl : DreamShader2StubbedDeclarationBase<DreamShader2StructStub>,
    DreamShader2StructDeclaration, PsiNameIdentifierOwner {

    constructor(node: ASTNode) : super(node)
    constructor(stub: DreamShader2StructStub, nodeType: IElementType) : super(stub, nodeType)

    override fun getName(): String? = declarationName()
    override fun getNameIdentifier(): PsiElement? = dreamShader2Leafs(this).firstOrNull { it.elementType == DreamShaderTokenTypes.IDENTIFIER }?.psi
    override fun setName(name: String): PsiElement = renameLocalIdentifier(this, name)

    override fun declarationName(): String? = stub?.name
        ?: dreamShader2Leafs(this)
            .firstOrNull { it.elementType == DreamShaderTokenTypes.IDENTIFIER }?.text
}

open class DreamShader2StructFieldImpl(node: ASTNode) :
    DreamShader2PsiElementBase(node), com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructField {

    override fun fieldName(): String? =
        dreamShader2DeclarationIdentifier(this)?.text

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

open class DreamShader2ParameterImpl(node: ASTNode) : DreamShader2PsiElementBase(node), DreamShader2Parameter, PsiNameIdentifierOwner {
    override fun getName(): String? = parameterName()
    override fun getNameIdentifier(): PsiElement? = dreamShader2DeclarationIdentifier(this)
    override fun setName(name: String): PsiElement = renameLocalIdentifier(this, name)

    override fun parameterName(): String? =
        dreamShader2DeclarationIdentifier(this)?.text

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

private fun renameLocalIdentifier(owner: PsiNameIdentifierOwner, name: String): PsiElement {
    val id = owner.nameIdentifier ?: return owner
    val dummy = PsiFileFactory.getInstance(owner.project).createFileFromText("rename.dss", DreamShaderLanguage, "void $name() {}")
    val function = PsiTreeUtil.findChildOfType(dummy, DreamShader2FunctionDeclaration::class.java) as? PsiNameIdentifierOwner
    function?.nameIdentifier?.let { id.replace(it) }
    return owner
}

/** A local declaration exposes its first declarator to the platform rename UI. */
class DreamShader2LocalDeclarationImpl(node: ASTNode) : DreamShader2PsiElementBase(node), DreamShader2NamedDeclaration, PsiNameIdentifierOwner {
    override fun declarationName(): String? = dreamShader2DeclarationIdentifier(this)?.text
    override fun getName(): String? = declarationName()
    override fun getNameIdentifier(): PsiElement? = dreamShader2DeclarationIdentifier(this)
    override fun setName(name: String): PsiElement = renameLocalIdentifier(this, name)
}

/** The second and subsequent names in a grouped declaration bind independently. */
class DreamShader2LocalDeclaratorNameImpl(node: ASTNode) : DreamShader2PsiElementBase(node), DreamShader2NamedDeclaration, PsiNameIdentifierOwner {
    override fun declarationName(): String? = nameIdentifier?.text
    override fun getName(): String? = declarationName()
    override fun getNameIdentifier(): PsiElement? = node.findChildByType(DreamShaderTokenTypes.IDENTIFIER)?.psi
    override fun setName(name: String): PsiElement = renameLocalIdentifier(this, name)
}

class DreamShader2ReferenceExpressionImpl(node: ASTNode) : DreamShader2PsiElementBase(node) {
    override fun getReference(): com.intellij.psi.PsiReference? {
        val id = node.firstChildNode?.psi ?: return null
        val binding = com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2SemanticResolver.resolveIdentifier(id) ?: return null
        if (binding.owner !is DreamShader2NamedDeclaration || (binding.owner as? PsiNameIdentifierOwner)?.nameIdentifier != binding.identifier) return null
        return com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2LightReference(this, binding.owner, id)
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
        DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT -> DreamShader2LocalDeclarationImpl(node)
        DreamShader2ElementTypes.LOCAL_DECLARATOR_NAME -> DreamShader2LocalDeclaratorNameImpl(node)
        DreamShader2ElementTypes.REFERENCE_EXPRESSION -> DreamShader2ReferenceExpressionImpl(node)
        DreamShader2ElementTypes.ARGUMENT_LIST -> DreamShader2ArgumentListImpl(node)
        DreamShader2ElementTypes.ARGUMENT -> DreamShader2ArgumentImpl(node)
        DreamShader2ElementTypes.NAMED_ARGUMENT -> DreamShader2NamedArgumentImpl(node)
        else -> null
    }
}
