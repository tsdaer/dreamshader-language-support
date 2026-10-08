package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2StubIndexesKeys
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.intellij.psi.stubs.StringStubIndexExtension
import com.intellij.psi.stubs.StubIndexKey

/**
 * The four 2.0 stub indexes (plan C1, StubIndex route).
 *
 * Plain platform indexes over the 2.0 stub tree — goto-symbol/find-usages/rename get the
 * platform's persistence and invalidation for free, and nothing here maintains its own store.
 * Population happens on the background stub-indexing machinery; queries are dumb-aware.
 *
 * - [DreamShader2UniformIndex]: every `uniform` declarator by name.
 * - [DreamShader2FunctionIndex]: workspace-visible functions only (`export`/`extern`);
 *   file-local helpers stay file-local.
 * - [DreamShader2StructIndex]: every `struct` by name.
 * - [DreamShader2IncludeIndex]: include edges keyed by the path as written.
 */
class DreamShader2UniformIndex : StringStubIndexExtension<DreamShader2UniformDeclaration>() {
    override fun getKey() = DreamShader2StubIndexesKeys.UNIFORM

    companion object {
        val KEY: StubIndexKey<String, DreamShader2UniformDeclaration> = DreamShader2StubIndexesKeys.UNIFORM
    }
}

class DreamShader2FunctionIndex : StringStubIndexExtension<DreamShader2FunctionDeclaration>() {
    override fun getKey() = DreamShader2StubIndexesKeys.FUNCTION

    companion object {
        val KEY: StubIndexKey<String, DreamShader2FunctionDeclaration> = DreamShader2StubIndexesKeys.FUNCTION
    }
}

class DreamShader2StructIndex : StringStubIndexExtension<DreamShader2StructDeclaration>() {
    override fun getKey() = DreamShader2StubIndexesKeys.STRUCT

    companion object {
        val KEY: StubIndexKey<String, DreamShader2StructDeclaration> = DreamShader2StubIndexesKeys.STRUCT
    }
}

class DreamShader2IncludeIndex : StringStubIndexExtension<DreamShader2IncludeDirective>() {
    override fun getKey() = DreamShader2StubIndexesKeys.INCLUDE

    companion object {
        val KEY: StubIndexKey<String, DreamShader2IncludeDirective> = DreamShader2StubIndexesKeys.INCLUDE
    }
}

/** All callable names for explicitly expanded, filtered auto-import completion. */
class DreamShader2HeaderFunctionIndex : StringStubIndexExtension<DreamShader2FunctionDeclaration>() {
    override fun getKey() = DreamShader2StubIndexesKeys.HEADER_FUNCTION
}

class DreamShader2HeaderGlobalIndex : StringStubIndexExtension<com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration>() {
    override fun getKey() = DreamShader2StubIndexesKeys.HEADER_GLOBAL
}
