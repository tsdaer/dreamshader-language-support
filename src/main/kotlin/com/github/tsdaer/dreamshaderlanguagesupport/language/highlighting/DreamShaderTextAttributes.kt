package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

/**
 * Singleton for DreamShaderTextAttributes.
 */
object DreamShaderTextAttributes {
    @JvmField
    val KEYWORD: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_KEYWORD", DefaultLanguageHighlighterColors.KEYWORD)

    @JvmField
    val SECTION: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_SECTION", DefaultLanguageHighlighterColors.METADATA)

    @JvmField
    val DECLARATION_NAME: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_DECLARATION_NAME",
        DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
    )

    @JvmField
    val CALLABLE_REFERENCE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_CALLABLE_REFERENCE",
        DefaultLanguageHighlighterColors.FUNCTION_CALL
    )

    @JvmField
    val BUILTIN_NAMESPACE: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_BUILTIN_NAMESPACE",
        DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL
    )

    @JvmField
    val LOCAL_SYMBOL: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_LOCAL_SYMBOL",
        DefaultLanguageHighlighterColors.LOCAL_VARIABLE
    )

    @JvmField
    val LOCAL_SYMBOL_DECLARATION: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_LOCAL_SYMBOL_DECLARATION",
        DefaultLanguageHighlighterColors.PARAMETER
    )

    @JvmField
    val NAMESPACE_QUALIFIER: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_NAMESPACE_QUALIFIER",
        DefaultLanguageHighlighterColors.METADATA
    )

    @JvmField
    val MATERIAL_OUTPUT_MEMBER: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_MATERIAL_OUTPUT_MEMBER",
        DefaultLanguageHighlighterColors.INSTANCE_FIELD
    )

    @JvmField
    val TYPE: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_TYPE", DefaultLanguageHighlighterColors.CLASS_NAME)

    @JvmField
    val STRING: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_STRING", DefaultLanguageHighlighterColors.STRING)

    @JvmField
    val NUMBER: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_NUMBER", DefaultLanguageHighlighterColors.NUMBER)

    @JvmField
    val COMMENT: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT)

    @JvmField
    val BRACES: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_BRACES", DefaultLanguageHighlighterColors.BRACES)

    @JvmField
    val OPERATOR: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_OPERATOR",
        DefaultLanguageHighlighterColors.OPERATION_SIGN
    )

    @JvmField
    val CONTROL_FLOW: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_CONTROL_FLOW",
        DefaultLanguageHighlighterColors.KEYWORD
    )

    @JvmField
    val QUALIFIER: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_QUALIFIER",
        DefaultLanguageHighlighterColors.STATIC_METHOD
    )

    @JvmField
    val CONSTANT: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_CONSTANT",
        DefaultLanguageHighlighterColors.CONSTANT
    )

    @JvmField
    val IMPORT: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_IMPORT",
        DefaultLanguageHighlighterColors.METADATA
    )

    @JvmField
    val SETTINGS_KEY: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_SETTINGS_KEY",
        DefaultLanguageHighlighterColors.INSTANCE_FIELD
    )

    @JvmField
    val BUILTIN_FUNCTION: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_BUILTIN_FUNCTION",
        DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL
    )

    @JvmField
    val PUNCTUATION: TextAttributesKey = TextAttributesKey.createTextAttributesKey(
        "DREAMSHADER_PUNCTUATION",
        DefaultLanguageHighlighterColors.DOT
    )

    @JvmField
    val BAD_CHARACTER: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_BAD_CHAR", HighlighterColors.BAD_CHARACTER)
}
