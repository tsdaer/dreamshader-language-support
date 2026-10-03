package com.github.tsdaer.dreamshaderlanguagesupport.language.psi

import com.intellij.psi.PsiElement

/**
 * Typed PSI for the DreamShader 2.0 declarations the editor features consume first.
 *
 * Names resolve from the tree shape the 2.0 parser builds: the first identifier leaf after the
 * modifiers/type leaves. `uniform float a, b = 2, c;` is one node whose name is the first
 * declarator (`a`), mirroring how the file was written.
 */
interface DreamShader2NamedDeclaration : PsiElement {
    /** First declarator / function / struct name, or null when the tree is malformed. */
    fun declarationName(): String?
}

interface DreamShader2UniformDeclaration : DreamShader2NamedDeclaration {
    /** Spelling of the declared type, e.g. `float3`, `Texture2D`. */
    fun typeName(): String?

    /** True when the doc block carries `/// @static`. */
    fun isStaticSwitch(): Boolean
}

interface DreamShader2GlobalDeclaration : DreamShader2NamedDeclaration {
    fun typeName(): String?
}

interface DreamShader2FunctionDeclaration : DreamShader2NamedDeclaration {
    fun isExport(): Boolean

    fun isExtern(): Boolean

    /** Return type spelling (`void` for material entries), or null on a malformed header. */
    fun returnTypeText(): String?

    /** Parameter list of the declaration, or null when the header is malformed. */
    fun parameterList(): DreamShader2ParameterList?

    /** True when the doc block carries `/// @custom` (body captured verbatim). */
    fun hasCustomBody(): Boolean
}

interface DreamShader2ParameterList : PsiElement {
    /** Direct PARAMETER children. */
    fun parameters(): List<DreamShader2Parameter>
}

interface DreamShader2Parameter : PsiElement {
    fun parameterName(): String?

    fun typeName(): String?
}

interface DreamShader2ArgumentList : PsiElement {
    /** Direct children in call order: [DreamShader2Argument] and [DreamShader2NamedArgument]. */
    fun arguments(): List<PsiElement>
}

/** A positional argument. */
interface DreamShader2Argument : PsiElement

/** `Name = expr` (also `Pin[0] = expr`). */
interface DreamShader2NamedArgument : PsiElement {
    fun argumentName(): String?
}

interface DreamShader2StructDeclaration : DreamShader2NamedDeclaration

/** Struct member: `///` doc block? type name array-dims? `;` */
interface DreamShader2StructField : PsiElement {
    fun fieldName(): String?

    fun typeName(): String?
}

interface DreamShader2IncludeDirective : PsiElement {
    /** The path as written (quotes stripped), or null when absent. */
    fun pathText(): String?
}

interface DreamShader2PragmaDirective : PsiElement {
    /** `material`, `instance`, `layout`, `region`, ... or null. */
    fun pragmaName(): String?
}
