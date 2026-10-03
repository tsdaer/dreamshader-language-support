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

    /** True when the doc block carries `/// @custom` (body captured verbatim). */
    fun hasCustomBody(): Boolean
}

interface DreamShader2StructDeclaration : DreamShader2NamedDeclaration

interface DreamShader2IncludeDirective : PsiElement {
    /** The path as written (quotes stripped), or null when absent. */
    fun pathText(): String?
}

interface DreamShader2PragmaDirective : PsiElement {
    /** `material`, `instance`, `layout`, `region`, ... or null. */
    fun pragmaName(): String?
}
