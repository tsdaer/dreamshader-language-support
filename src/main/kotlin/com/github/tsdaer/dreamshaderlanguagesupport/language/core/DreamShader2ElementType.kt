package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import com.intellij.psi.tree.IElementType

/**
 * Element type for the DreamShader 2.0 dialect PSI.
 *
 * The 2.0 parser builds these nodes; the frozen 1.x pipeline keeps its own
 * [DreamShaderElementTypes.DECLARATION] / [DreamShaderElementTypes.SECTION] nodes untouched.
 * The file root stays [DreamShaderElementTypes.FILE] for both dialects: one language, one
 * translation unit per file.
 */
class DreamShader2ElementType(debugName: String) : IElementType(debugName, DreamShaderLanguage)

/**
 * Element types produced by `DreamShader2PsiParser` (upstream `Docs/language-v2/index.md`).
 */
object DreamShader2ElementTypes {
    /** `#pragma material(...)` / `#pragma instance(...)` / `#pragma layout(...)` / `#pragma region`. */
    @JvmField
    val PRAGMA_DIRECTIVE: IElementType = DreamShader2ElementType("PRAGMA_DIRECTIVE")

    /** Generic preprocessor line (`#if` / `#define` / ...) kept verbatim, upstream DSH3201 territory. */
    @JvmField
    val PREPROCESSOR_DIRECTIVE: IElementType = DreamShader2ElementType("PREPROCESSOR_DIRECTIVE")

    /** `#include "path"` and `import "path";`. */
    @JvmField
    val INCLUDE_DIRECTIVE: IElementType = DreamShader2StubElementTypes.INCLUDE_DIRECTIVE

    /** A run of `///` doc lines; carried inside the declaration it documents. */
    @JvmField
    val DOC_BLOCK: IElementType = DreamShader2ElementType("DOC_BLOCK")

    /** `uniform <Type> Name (= <init>)? (, Name ...)? ;` with its `///` attribute block. */
    @JvmField
    val UNIFORM_DECLARATION: IElementType = DreamShader2StubElementTypes.UNIFORM_DECLARATION

    /** `static const` / `const` / `static` / bare global with an initializer. */
    @JvmField
    val GLOBAL_DECLARATION: IElementType = DreamShader2StubElementTypes.GLOBAL_DECLARATION

    /** `struct Name { fields };` - fields may carry their own `///` block and array dimensions. */
    @JvmField
    val STRUCT_DECLARATION: IElementType = DreamShader2StubElementTypes.STRUCT_DECLARATION

    /** Struct member: doc block? type name array-dims? `;` */
    @JvmField
    val STRUCT_FIELD: IElementType = DreamShader2ElementType("STRUCT_FIELD")

    /** `export` / `extern` / helper function declaration; a `/// @custom` body becomes CUSTOM_BODY. */
    @JvmField
    val FUNCTION_DECLARATION: IElementType = DreamShader2StubElementTypes.FUNCTION_DECLARATION

    /** Verbatim `/// @custom` function body: brace-balanced, opaque to the rest of the grammar. */
    @JvmField
    val CUSTOM_BODY: IElementType = DreamShader2ElementType("CUSTOM_BODY")

    @JvmField
    val PARAMETER_LIST: IElementType = DreamShader2ElementType("PARAMETER_LIST")

    @JvmField
    val PARAMETER: IElementType = DreamShader2ElementType("PARAMETER")

    // --- statements ---

    @JvmField
    val BLOCK: IElementType = DreamShader2ElementType("BLOCK")

    @JvmField
    val VARIABLE_DECLARATION_STATEMENT: IElementType = DreamShader2ElementType("VARIABLE_DECLARATION_STATEMENT")

    @JvmField
    val EXPRESSION_STATEMENT: IElementType = DreamShader2ElementType("EXPRESSION_STATEMENT")

    @JvmField
    val IF_STATEMENT: IElementType = DreamShader2ElementType("IF_STATEMENT")

    @JvmField
    val FOR_STATEMENT: IElementType = DreamShader2ElementType("FOR_STATEMENT")

    @JvmField
    val WHILE_STATEMENT: IElementType = DreamShader2ElementType("WHILE_STATEMENT")

    @JvmField
    val DO_WHILE_STATEMENT: IElementType = DreamShader2ElementType("DO_WHILE_STATEMENT")

    @JvmField
    val RETURN_STATEMENT: IElementType = DreamShader2ElementType("RETURN_STATEMENT")

    @JvmField
    val JUMP_STATEMENT: IElementType = DreamShader2ElementType("JUMP_STATEMENT")

    @JvmField
    val EMPTY_STATEMENT: IElementType = DreamShader2ElementType("EMPTY_STATEMENT")

    // --- expressions ---

    @JvmField
    val EXPRESSION: IElementType = DreamShader2ElementType("EXPRESSION")

    @JvmField
    val CALL_EXPRESSION: IElementType = DreamShader2ElementType("CALL_EXPRESSION")

    @JvmField
    val CONSTRUCTOR_EXPRESSION: IElementType = DreamShader2ElementType("CONSTRUCTOR_EXPRESSION")

    @JvmField
    val MEMBER_ACCESS_EXPRESSION: IElementType = DreamShader2ElementType("MEMBER_ACCESS_EXPRESSION")

    @JvmField
    val INDEX_EXPRESSION: IElementType = DreamShader2ElementType("INDEX_EXPRESSION")

    @JvmField
    val CAST_EXPRESSION: IElementType = DreamShader2ElementType("CAST_EXPRESSION")

    @JvmField
    val PARENTHESIZED_EXPRESSION: IElementType = DreamShader2ElementType("PARENTHESIZED_EXPRESSION")

    @JvmField
    val LITERAL_EXPRESSION: IElementType = DreamShader2ElementType("LITERAL_EXPRESSION")

    @JvmField
    val REFERENCE_EXPRESSION: IElementType = DreamShader2ElementType("REFERENCE_EXPRESSION")

    @JvmField
    val INITIALIZER_LIST: IElementType = DreamShader2ElementType("INITIALIZER_LIST")

    @JvmField
    val ARGUMENT_LIST: IElementType = DreamShader2ElementType("ARGUMENT_LIST")

    @JvmField
    val ARGUMENT: IElementType = DreamShader2ElementType("ARGUMENT")

    @JvmField
    val NAMED_ARGUMENT: IElementType = DreamShader2ElementType("NAMED_ARGUMENT")

    /** Opaque 1.x block inside a mixed file: balanced-brace capture, no interpretation. */
    @JvmField
    val LEGACY_BLOCK: IElementType = DreamShader2ElementType("LEGACY_BLOCK")
}
