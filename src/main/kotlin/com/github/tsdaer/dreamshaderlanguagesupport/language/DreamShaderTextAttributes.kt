package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey

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
    val BAD_CHARACTER: TextAttributesKey =
        TextAttributesKey.createTextAttributesKey("DREAMSHADER_BAD_CHAR", HighlighterColors.BAD_CHARACTER)
}
