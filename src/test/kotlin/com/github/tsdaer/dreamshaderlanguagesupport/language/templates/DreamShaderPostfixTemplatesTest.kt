package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderPostfixTemplatesTest : BasePlatformTestCase() {
    fun testDreamShaderPostfixProviderRegistersIfTemplates() {
        val provider = provider()
        val templates = provider.templates.associateBy { it.key }

        assertEquals(setOf(".if", ".ifel"), templates.keys)
        assertTrue(templates.getValue(".if").example.isNotBlank())
        assertTrue(templates.getValue(".ifel").example.isNotBlank())
    }

    fun testPostfixSelectorAcceptsSimpleExpressionLinesOnly() {
        val provider = provider() as DreamShaderPostfixTemplateProvider
        val file = myFixture.configureByText(
            "sample.dsm",
            """
            Shader(Name="DreamMaterials/Test") {
                Graph = {
                    UE.TexCoord(Index=0)<caret>
                }
            }
            """.trimIndent()
        )

        val editor = myFixture.editor
        val context = file.findElementAt(editor.caretModel.offset - 1)
        assertNotNull(context)

        val template = provider.templates.first { it.key == ".if" }
        assertTrue(template.isApplicable(requireNotNull(context), editor.document, editor.caretModel.offset))
    }

    fun testPostfixSelectorRejectsAssignmentLines() {
        val provider = provider() as DreamShaderPostfixTemplateProvider
        val file = myFixture.configureByText(
            "sample.dsm",
            """
            Shader(Name="DreamMaterials/Test") {
                Graph = {
                    Color = UE.TexCoord(Index=0)<caret>
                }
            }
            """.trimIndent()
        )

        val editor = myFixture.editor
        val context = file.findElementAt(editor.caretModel.offset - 1)
        assertNotNull(context)

        val template = provider.templates.first { it.key == ".if" }
        assertFalse(template.isApplicable(requireNotNull(context), editor.document, editor.caretModel.offset))
    }

    private fun provider(): PostfixTemplateProvider {
        return requireNotNull(LanguagePostfixTemplate.LANG_EP.forLanguage(dreamShaderPostfixTemplateLanguage()))
    }
}
