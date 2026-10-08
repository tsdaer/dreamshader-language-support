package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformStub
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2FunctionDeclarationImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2GlobalDeclarationImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2IncludeDirectiveImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2StructDeclarationImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2UniformDeclarationImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.stubs.IndexSink
import com.intellij.psi.stubs.StubElement
import com.intellij.psi.stubs.StubInputStream
import com.intellij.psi.stubs.StubOutputStream
import com.intellij.psi.stubs.IStubElementType

/**
 * Construction bodies of the 2.0 stub element types (plan C1); the fields the platform's
 * `stubElementTypeHolder` extension point scans live in the Java holder interface
 * [DreamShader2StubElementTypes] (an interface is required by the holder scan).
 *
 * The stub tree is a cache of the same facts the AST spells out; serialization writes the
 * payload fields only, and nullable strings are length-prefixed (`-1` = null).
 */
abstract class DreamShader2StubElementType<StubT : StubElement<*>, PsiT : PsiElement>(
    debugName: String,
) : IStubElementType<StubT, PsiT>(debugName, DreamShaderLanguage) {

    protected fun StubOutputStream.writeNullableUtf(value: String?) {
        if (value == null) {
            writeInt(-1)
        } else {
            writeInt(value.length)
            writeUTF(value)
        }
    }

    protected fun StubInputStream.readNullableUtf(): String? {
        val length = readInt()
        if (length < 0) return null
        return readUTF()
    }

    companion object {
        /** Bumped whenever any stub payload's serialization shape changes. */
        const val STUB_VERSION = 3
    }
}

internal object DreamShader2StubElementTypeFactory {

    val UNIFORM_DECLARATION: IStubElementType<DreamShader2UniformStub, DreamShader2UniformDeclaration> =
        object : DreamShader2StubElementType<DreamShader2UniformStub, DreamShader2UniformDeclaration>(
            "UNIFORM_DECLARATION",
        ) {
            override fun getExternalId() = "dreamshader2.UNIFORM_DECLARATION"

            override fun createStub(psi: DreamShader2UniformDeclaration, parentStub: StubElement<*>?) =
                DreamShader2UniformStub(parentStub, this, psi.declarationName(), psi.typeName(), psi.isStaticSwitch())

            override fun createPsi(stub: DreamShader2UniformStub) = DreamShader2UniformDeclarationImpl(stub, this)

            override fun serialize(stub: DreamShader2UniformStub, dataStream: StubOutputStream) {
                dataStream.writeNullableUtf(stub.name)
                dataStream.writeNullableUtf(stub.typeName)
                dataStream.writeBoolean(stub.isStaticSwitch)
            }

            override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): DreamShader2UniformStub =
                DreamShader2UniformStub(
                    parentStub,
                    this,
                    dataStream.readNullableUtf(),
                    dataStream.readNullableUtf(),
                    dataStream.readBoolean(),
                )

            override fun indexStub(stub: DreamShader2UniformStub, sink: IndexSink) {
                stub.name?.let { sink.occurrence(DreamShader2StubIndexesKeys.UNIFORM, it) }
            }
        }

    val GLOBAL_DECLARATION: IStubElementType<DreamShader2GlobalStub, DreamShader2GlobalDeclaration> =
        object : DreamShader2StubElementType<DreamShader2GlobalStub, DreamShader2GlobalDeclaration>(
            "GLOBAL_DECLARATION",
        ) {
            override fun getExternalId() = "dreamshader2.GLOBAL_DECLARATION"

            override fun createStub(psi: DreamShader2GlobalDeclaration, parentStub: StubElement<*>?) =
                DreamShader2GlobalStub(parentStub, this, psi.declarationName(), psi.typeName())

            override fun createPsi(stub: DreamShader2GlobalStub) = DreamShader2GlobalDeclarationImpl(stub, this)

            override fun serialize(stub: DreamShader2GlobalStub, dataStream: StubOutputStream) {
                dataStream.writeNullableUtf(stub.name)
                dataStream.writeNullableUtf(stub.typeName)
            }

            override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): DreamShader2GlobalStub =
                DreamShader2GlobalStub(
                    parentStub,
                    this,
                    dataStream.readNullableUtf(),
                    dataStream.readNullableUtf(),
                )

            // Indexing supports explicit expanded completion; inclusion still controls visibility.
            override fun indexStub(stub: DreamShader2GlobalStub, sink: IndexSink) {
                stub.name?.let { sink.occurrence(DreamShader2StubIndexesKeys.HEADER_GLOBAL, it) }
            }
        }

    val FUNCTION_DECLARATION: IStubElementType<DreamShader2FunctionStub, DreamShader2FunctionDeclaration> =
        object : DreamShader2StubElementType<DreamShader2FunctionStub, DreamShader2FunctionDeclaration>(
            "FUNCTION_DECLARATION",
        ) {
            override fun getExternalId() = "dreamshader2.FUNCTION_DECLARATION"

            override fun createStub(psi: DreamShader2FunctionDeclaration, parentStub: StubElement<*>?) =
                DreamShader2FunctionStub(
                    parentStub,
                    this,
                    psi.declarationName(),
                    psi.returnTypeText(),
                    psi.isExport(),
                    psi.isExtern(),
                    psi.hasCustomBody(),
                )

            override fun createPsi(stub: DreamShader2FunctionStub) = DreamShader2FunctionDeclarationImpl(stub, this)

            override fun serialize(stub: DreamShader2FunctionStub, dataStream: StubOutputStream) {
                dataStream.writeNullableUtf(stub.name)
                dataStream.writeNullableUtf(stub.typeName)
                dataStream.writeBoolean(stub.isExport)
                dataStream.writeBoolean(stub.isExtern)
                dataStream.writeBoolean(stub.hasCustomBody)
            }

            override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): DreamShader2FunctionStub =
                DreamShader2FunctionStub(
                    parentStub,
                    this,
                    dataStream.readNullableUtf(),
                    dataStream.readNullableUtf(),
                    dataStream.readBoolean(),
                    dataStream.readBoolean(),
                    dataStream.readBoolean(),
                )

            override fun indexStub(stub: DreamShader2FunctionStub, sink: IndexSink) {
                stub.name?.let { sink.occurrence(DreamShader2StubIndexesKeys.HEADER_FUNCTION, it) }
                // Workspace-visible linkage only: file-local helpers stay out of the index.
                if (stub.isExport || stub.isExtern) {
                    stub.name?.let { sink.occurrence(DreamShader2StubIndexesKeys.FUNCTION, it) }
                }
            }
        }

    val STRUCT_DECLARATION: IStubElementType<DreamShader2StructStub, DreamShader2StructDeclaration> =
        object : DreamShader2StubElementType<DreamShader2StructStub, DreamShader2StructDeclaration>(
            "STRUCT_DECLARATION",
        ) {
            override fun getExternalId() = "dreamshader2.STRUCT_DECLARATION"

            override fun createStub(psi: DreamShader2StructDeclaration, parentStub: StubElement<*>?) =
                DreamShader2StructStub(parentStub, this, psi.declarationName())

            override fun createPsi(stub: DreamShader2StructStub) = DreamShader2StructDeclarationImpl(stub, this)

            override fun serialize(stub: DreamShader2StructStub, dataStream: StubOutputStream) {
                dataStream.writeNullableUtf(stub.name)
            }

            override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): DreamShader2StructStub =
                DreamShader2StructStub(parentStub, this, dataStream.readNullableUtf())

            override fun indexStub(stub: DreamShader2StructStub, sink: IndexSink) {
                stub.name?.let { sink.occurrence(DreamShader2StubIndexesKeys.STRUCT, it) }
            }
        }

    val INCLUDE_DIRECTIVE: IStubElementType<DreamShader2IncludeStub, DreamShader2IncludeDirective> =
        object : DreamShader2StubElementType<DreamShader2IncludeStub, DreamShader2IncludeDirective>(
            "INCLUDE_DIRECTIVE",
        ) {
            override fun getExternalId() = "dreamshader2.INCLUDE_DIRECTIVE"

            override fun createStub(psi: DreamShader2IncludeDirective, parentStub: StubElement<*>?) =
                DreamShader2IncludeStub(parentStub, this, psi.pathText())

            override fun createPsi(stub: DreamShader2IncludeStub) = DreamShader2IncludeDirectiveImpl(stub, this)

            override fun serialize(stub: DreamShader2IncludeStub, dataStream: StubOutputStream) {
                dataStream.writeNullableUtf(stub.path)
            }

            override fun deserialize(dataStream: StubInputStream, parentStub: StubElement<*>?): DreamShader2IncludeStub =
                DreamShader2IncludeStub(parentStub, this, dataStream.readNullableUtf())

            override fun indexStub(stub: DreamShader2IncludeStub, sink: IndexSink) {
                stub.path?.let { sink.occurrence(DreamShader2StubIndexesKeys.INCLUDE, it) }
            }
        }

    private fun StubOutputStream.writeNullableUtf(value: String?) {
        if (value == null) {
            writeInt(-1)
        } else {
            writeInt(value.length)
            writeUTF(value)
        }
    }

    private fun StubInputStream.readNullableUtf(): String? {
        val length = readInt()
        if (length < 0) return null
        return readUTF()
    }
}
