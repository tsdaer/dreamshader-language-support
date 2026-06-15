package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderSmartEnterProcessorTest : BasePlatformTestCase() {
    fun testCompletesTopLevelDeclarationBlock() {
        configureAndSmartEnter("smart_decl.dsm", "Shader Main<caret>")

        myFixture.checkResult(
            """
            Shader Main {
                <caret>
            }
            """.trimIndent()
        )
    }

    fun testCompletesSectionBlockInsideDeclaration() {
        configureAndSmartEnter(
            "smart_section.dsm",
            """
            Shader Main {
                Settings<caret>
            }
            """.trimIndent()
        )

        myFixture.checkResult(
            """
            Shader Main {
                Settings {
                    <caret>
                }
            }
            """.trimIndent()
        )
    }

    fun testTerminatesImport() {
        configureAndSmartEnter("smart_import.dsh", "import \"Shared/Common.dsh\"<caret>")

        myFixture.checkResult("import \"Shared/Common.dsh\";<caret>")
    }

    fun testCompletesIfHeaderWithMissingParenthesis() {
        configureAndSmartEnter(
            "smart_if.dsm",
            """
            Shader Main {
                Graph {
                    if (ready<caret>
                }
            }
            """.trimIndent()
        )

        myFixture.checkResult(
            """
            Shader Main {
                Graph {
                    if (ready) {
                        <caret>
                    }
                }
            }
            """.trimIndent()
        )
    }

    fun testCompletesGraphStatementDelimiters() {
        configureAndSmartEnter(
            "smart_statement.dsm",
            """
            Shader Main {
                Graph {
                    float3 color = float3(1, 0.5, 0<caret>
                }
            }
            """.trimIndent()
        )

        myFixture.checkResult(
            """
            Shader Main {
                Graph {
                    float3 color = float3(1, 0.5, 0);<caret>
                }
            }
            """.trimIndent()
        )
    }

    fun testTerminatesBraceInitializerStatement() {
        configureAndSmartEnter(
            "smart_initializer.dsm",
            """
            Shader Main {
                Graph {
                    float3 color = {1, 0.5, 0<caret>
                }
            }
            """.trimIndent()
        )

        myFixture.checkResult(
            """
            Shader Main {
                Graph {
                    float3 color = {1, 0.5, 0};<caret>
                }
            }
            """.trimIndent()
        )
    }

    private fun configureAndSmartEnter(fileName: String, text: String) {
        val file = myFixture.configureByText(fileName, text)
        val processed = WriteCommandAction.writeCommandAction(project).compute<Boolean, RuntimeException> {
            DreamShaderSmartEnterProcessor().process(project, myFixture.editor, file)
        }
        assertTrue(processed)
    }
}
