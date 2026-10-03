package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import com.intellij.psi.stubs.StubIndexKey

/**
 * Keys of the four 2.0 stub indexes (plan C1): uniform names, workspace-visible functions
 * (`export`/`extern`), structs, and include edges. Keys are stable strings — renaming one is a
 * cache-breaking change for every user.
 */
object DreamShader2StubIndexesKeys {
    @JvmField
    val UNIFORM: StubIndexKey<String, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration> =
        StubIndexKey.createIndexKey("dreamshader2.uniform")

    @JvmField
    val FUNCTION: StubIndexKey<String, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration> =
        StubIndexKey.createIndexKey("dreamshader2.function")

    @JvmField
    val STRUCT: StubIndexKey<String, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration> =
        StubIndexKey.createIndexKey("dreamshader2.struct")

    @JvmField
    val INCLUDE: StubIndexKey<String, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective> =
        StubIndexKey.createIndexKey("dreamshader2.include")
}
