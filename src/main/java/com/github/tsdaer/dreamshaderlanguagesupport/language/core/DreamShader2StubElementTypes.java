package com.github.tsdaer.dreamshaderlanguagesupport.language.core;

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionStub;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalStub;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeStub;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructStub;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration;
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformStub;
import com.intellij.psi.stubs.IStubElementType;

/**
 * Holder interface of the DreamShader 2.0 stub element types (plan C1).
 *
 * The platform's {@code stubElementTypeHolder} extension point requires a holder
 * <em>interface</em> whose static fields are the element types — it scans
 * {@code getDeclaredFields()} during index initialization so the types exist before any
 * persisted stub data is read. The construction bodies live in
 * {@code DreamShader2StubElementTypeFactory} (Kotlin); this interface only pins the fields.
 */
public interface DreamShader2StubElementTypes {
    IStubElementType<DreamShader2UniformStub, DreamShader2UniformDeclaration> UNIFORM_DECLARATION =
            DreamShader2StubElementTypeFactory.INSTANCE.getUNIFORM_DECLARATION();

    IStubElementType<DreamShader2GlobalStub, DreamShader2GlobalDeclaration> GLOBAL_DECLARATION =
            DreamShader2StubElementTypeFactory.INSTANCE.getGLOBAL_DECLARATION();

    IStubElementType<DreamShader2FunctionStub, DreamShader2FunctionDeclaration> FUNCTION_DECLARATION =
            DreamShader2StubElementTypeFactory.INSTANCE.getFUNCTION_DECLARATION();

    IStubElementType<DreamShader2StructStub, DreamShader2StructDeclaration> STRUCT_DECLARATION =
            DreamShader2StubElementTypeFactory.INSTANCE.getSTRUCT_DECLARATION();

    IStubElementType<DreamShader2IncludeStub, DreamShader2IncludeDirective> INCLUDE_DIRECTIVE =
            DreamShader2StubElementTypeFactory.INSTANCE.getINCLUDE_DIRECTIVE();
}
