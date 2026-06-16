package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderEnterHandlerDelegateTest : BasePlatformTestCase() {
    fun testPlainEnterKeepsPreviousLineIndent() {
        myFixture.configureByText(
            "enter_same_indent.dsm",
            """
            Shader Main {
                Graph = {
                    float Value = 1.0;<caret>
                }
            }
            """.trimIndent()
        )

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        myFixture.checkResult(
            """
            Shader Main {
                Graph = {
                    float Value = 1.0;
                    <caret>
                }
            }
            """.trimIndent()
        )
    }

    fun testPlainEnterAfterOpeningBraceIndentsOneLevel() {
        myFixture.configureByText(
            "enter_after_brace.dsm",
            """
            Shader Main {
                Graph = {<caret>
                }
            }
            """.trimIndent()
        )

        myFixture.performEditorAction(IdeActions.ACTION_EDITOR_ENTER)

        myFixture.checkResult(
            """
            Shader Main {
                Graph = {
                    <caret>
                }
            }
            """.trimIndent()
        )
    }
}

