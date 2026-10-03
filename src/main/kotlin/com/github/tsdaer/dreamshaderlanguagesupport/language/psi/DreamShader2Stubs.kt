package com.github.tsdaer.dreamshaderlanguagesupport.language.psi

import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IStubElementType
import com.intellij.psi.stubs.StubBase
import com.intellij.psi.stubs.StubElement

/**
 * Stub payloads for the DreamShader 2.0 declarations (plan C1).
 *
 * The stub tree carries the cheap facts the workspace features need (name, type spelling,
 * linkage flags) so cross-file queries resolve without building the full AST. All fields are
 * re-derivable from the tree; the stub is a cache, never a second source of truth.
 * The file root uses the platform's default file stub.
 */

/** Name + declared type spelling for `uniform` / global / function / struct declarations. */
interface DreamShader2NamedStub {
    val name: String?
    val typeName: String?
}

class DreamShader2UniformStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    override val typeName: String?,
    val isStaticSwitch: Boolean,
) : StubBase<PsiElement>(parent, elementType), DreamShader2NamedStub

class DreamShader2GlobalStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    override val typeName: String?,
) : StubBase<PsiElement>(parent, elementType), DreamShader2NamedStub

class DreamShader2FunctionStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
    /** Return type spelling; the entry `void` for materials. */
    override val typeName: String?,
    val isExport: Boolean,
    val isExtern: Boolean,
    val hasCustomBody: Boolean,
) : StubBase<PsiElement>(parent, elementType), DreamShader2NamedStub

class DreamShader2StructStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    override val name: String?,
) : StubBase<PsiElement>(parent, elementType), DreamShader2NamedStub {
    override val typeName: String? get() = null
}

/** Include edge: the path as written (quotes stripped). */
class DreamShader2IncludeStub(
    parent: StubElement<*>?,
    elementType: IStubElementType<*, *>,
    val path: String?,
) : StubBase<PsiElement>(parent, elementType)
