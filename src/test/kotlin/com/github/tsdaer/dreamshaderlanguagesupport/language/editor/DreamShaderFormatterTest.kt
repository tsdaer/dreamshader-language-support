package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.application.options.CodeStyle
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals

class DreamShaderFormatterTest : BasePlatformTestCase() {
    fun testBasicFormattingIndentSpacingAndBraces() {
        val file = myFixture.configureByText(
            "formatter_sample.dsm",
            """
            Shader Main{
            Settings{
            Domain="Surface";
            }
            Graph={
            float2 uv=UE.TexCoord(0);
            float v=saturate(uv.x);
            }
            }
            """.trimIndent()
        )

        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(file)
        }

        assertEquals(
            """
            Shader Main {
                Settings {
                    Domain = "Surface";
                }
                Graph = {
                    float2 uv = UE.TexCoord(0);
                    float v = saturate(uv.x);
                }
            }
            """.trimIndent(),
            file.text
        )
    }

    fun testFormattingRespectsCodeStyleSpacingOptions() {
        val file = myFixture.configureByText(
            "formatter_code_style_options.dsm",
            """
            Shader Main{
            Graph={
            if(ready){
            float2 a=float2(1,2);
            float2 b=UE.TexCoord(0);
            float k=(1+2);
            }
            }
            }
            """.trimIndent()
        )

        @Suppress("DEPRECATION")
        val settings = CodeStyle.getSettings(project).clone()
        val common = settings.getCommonSettings(DreamShaderLanguage)
        common.SPACE_AROUND_ASSIGNMENT_OPERATORS = false
        common.SPACE_AROUND_RELATIONAL_OPERATORS = false
        common.SPACE_AFTER_COMMA = false
        common.SPACE_BEFORE_COMMA = true
        common.SPACE_WITHIN_PARENTHESES = true
        common.SPACE_BEFORE_IF_PARENTHESES = true
        common.SPACE_BEFORE_METHOD_CALL_PARENTHESES = true
        common.SPACE_AROUND_ADDITIVE_OPERATORS = true
        common.SPACE_AROUND_MULTIPLICATIVE_OPERATORS = true

        val manager = com.intellij.psi.codeStyle.CodeStyleSettingsManager.getInstance(project)
        val oldTemporary = manager.temporarySettings
        manager.setTemporarySettings(settings)
        try {
            WriteCommandAction.runWriteCommandAction(project) {
                CodeStyleManager.getInstance(project).reformat(file)
            }
        } finally {
            if (oldTemporary != null) {
                manager.setTemporarySettings(oldTemporary)
            } else {
                manager.dropTemporarySettings()
            }
        }

        assertEquals(
            """
            Shader Main {
                Graph={
                    if ( ready ) {
                    float2 a=float2 ( 1 ,2 );
                    float2 b=UE.TexCoord ( 0 );
                    float k=( 1 + 2 );
                }
                }
            }
            """.trimIndent(),
            file.text
        )
    }
}
