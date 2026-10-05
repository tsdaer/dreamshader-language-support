package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.intellij.codeInsight.template.postfix.templates.LanguagePostfixTemplate
import com.intellij.codeInsight.template.postfix.templates.PostfixTemplateProvider
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderPostfixTemplatesTest : BasePlatformTestCase() {

    private fun provider(): PostfixTemplateProvider {
        return requireNotNull(LanguagePostfixTemplate.LANG_EP.forLanguage(dreamShaderPostfixTemplateLanguage()))
    }

    private fun stringTemplate(key: String): com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate =
        provider().templates.first { it.key == key } as
            com.intellij.codeInsight.template.postfix.templates.StringBasedPostfixTemplate

    fun testDreamShaderPostfixProviderRegistersStatementAndExpressionTemplates() {
        val templates = provider().templates.associateBy { it.key }

        assertEquals(setOf(".if", ".ifel", ".lerp", ".saturate", ".sample", ".swizzle"), templates.keys)
        templates.values.forEach { assertTrue(it.example.isNotBlank()) }
    }

    fun testExpressionTemplatesCarryThePlannedTemplateStrings() {
        val file = myFixture.configureByText("M_Templates.dss", "export void M() { }")

        assertEquals(
            "lerp(\$expr\$, \$y\$, \$s\$)\$END\$",
            stringTemplate(".lerp").getTemplateString(file),
        )
        assertEquals(
            "saturate(\$expr\$)\$END\$",
            stringTemplate(".saturate").getTemplateString(file),
        )
        assertEquals(
            "\$expr\$.Sample(\$END\$)",
            stringTemplate(".sample").getTemplateString(file),
        )
        assertEquals(
            "\$expr\$.\$swz\$\$END\$",
            stringTemplate(".swizzle").getTemplateString(file),
        )
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

    // ------------------------------------------------------------------
    // C18 — the 2.0 dialect: assignment lines host the candidate expression
    // ------------------------------------------------------------------

    private fun applicableInV2(text: String, key: String = ".if"): Boolean {
        val provider = provider()
        val file = myFixture.configureByText("M_Postfix.dss", text)
        val editor = myFixture.editor
        val context = file.findElementAt(editor.caretModel.offset - 1)
        assertNotNull(context)
        val template = provider.templates.first { it.key == key }
        return template.isApplicable(requireNotNull(context), editor.document, editor.caretModel.offset)
    }

    fun testPostfixSelectorAcceptsExpressionsInsideV2Declarations() {
        assertTrue(
            applicableInV2(
                """
                export void M_Entry()
                {
                    float2 UV = UE.TexCoord(Index = 0)<caret>
                }
                """.trimIndent(),
            ),
        )
        assertTrue(
            applicableInV2(
                """
                export void M_Entry()
                {
                    float3 C = float3(1, 0, 0)<caret>
                }
                """.trimIndent(),
                key = ".saturate",
            ),
        )
    }

    fun testPostfixSelectorStillRejectsEmptyAndStatementContextsInV2() {
        // nothing typed after the `=` — no candidate expression
        assertFalse(
            applicableInV2(
                """
                export void M_Entry()
                {
                    float2 UV = <caret>
                }
                """.trimIndent(),
            ),
        )
        // the statement is already closed — the line ends at `;`
        assertFalse(
            applicableInV2(
                """
                export void M_Entry()
                {
                    float2 UV = UE.TexCoord(Index = 0);<caret>
                }
                """.trimIndent(),
            ),
        )
    }

    fun testV2AssignmentSplitSkipsCompoundAndComparisonOperators() {
        // `==` is not an assignment: the split lands on the real `=` after the declarator
        assertTrue(
            applicableInV2(
                """
                export void M_Entry()
                {
                    bool Flag = Amount == UE.Time(Period = 4.0)<caret>
                }
                """.trimIndent(),
            ),
        )
        // compound assignment: the value after `+=` is the candidate
        assertTrue(
            applicableInV2(
                """
                export void M_Entry()
                {
                    float3 Color = float3(1, 0, 0);
                    Color += Other<caret>
                }
                """.trimIndent(),
                key = ".saturate",
            ),
        )
    }
}
