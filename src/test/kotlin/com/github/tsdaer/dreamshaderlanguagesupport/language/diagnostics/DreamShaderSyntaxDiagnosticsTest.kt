package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderSyntaxDiagnosticsTest : BasePlatformTestCase() {
    fun testUnclosedStringLiteral() {
        val text = """
            Shader Main {
                Settings {
                    Domain = "Surface
                }
            }
        """.trimIndent()
        myFixture.configureByText("unclosed_string.dsm", text)
        assertHasError("Unclosed string literal")
    }

    fun testUnclosedBlockComment() {
        val text = """
            Shader Main {
                /* comment
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("unclosed_comment.dsm", text)
        assertHasError("Unclosed block comment")
    }

    fun testUnmatchedBraceInGraph() {
        val text = """
            Shader Main {
                Graph {
                    float x = 1.0;
            }
        """.trimIndent()
        myFixture.configureByText("unmatched_brace.dsm", text)
        assertHasError("Unmatched brace")
    }

    fun testMalformedTopLevelDeclaration() {
        val text = """
            Shader {
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("malformed_decl.dsm", text)
        assertHasError("Malformed declaration: expected declaration name or argument list")
    }

    fun testMalformedSectionHeader() {
        val text = """
            Shader Main {
                Outputs
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("malformed_section.dsm", text)
        assertHasError("Malformed section: expected '{'")
    }

    private fun assertHasError(message: String) {
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        assertTrue(
            "Expected error '$message', actual: ${errors.map { it.description }}",
            errors.any { it.description == message }
        )
    }
}
