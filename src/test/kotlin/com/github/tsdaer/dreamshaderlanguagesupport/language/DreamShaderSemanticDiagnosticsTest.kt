package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertTrue

class DreamShaderSemanticDiagnosticsTest : BasePlatformTestCase() {
    fun testUnknownSettingsKey() {
        val text = """
            Shader Main {
                Settings {
                    DomainX = "Surface";
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_settings_key.dsm", text)
        assertHasError("Unknown settings key 'DomainX'. Did you mean 'Domain'?")
    }

    fun testInvalidSettingsEnumValue() {
        val text = """
            Shader Main {
                Settings {
                    BlendMode = "OpaqueX";
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_settings_value.dsm", text)
        assertHasError("Invalid value 'OpaqueX' for setting 'BlendMode'")
    }

    fun testUnknownBaseOutputMember() {
        val text = """
            Shader Main {
                Outputs {
                    Base.ColorX = float3(1.0, 1.0, 1.0);
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_base_member.dsm", text)
        assertHasError("Unknown material output member 'Base.ColorX'. Did you mean 'Base.BaseColor'?")
    }

    fun testUnknownTypeInInputs() {
        val text = """
            Shader Main {
                Inputs {
                    float9 BadType;
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type.dsm", text)
        assertHasError("Unknown type 'float9'. Did you mean 'float'?")
    }

    fun testUnknownTypeWithoutSuggestionWhenTooFar() {
        val text = """
            Shader Main {
                Inputs {
                    zzzzq VeryBad;
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_no_suggestion.dsm", text)
        assertHasError("Unknown type 'zzzzq'")
    }

    fun testMissingOutArgumentInFunctionCall() {
        val text = """
            Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                result = color * tint;
            }

            Shader Main {
                Graph {
                    ApplyTint(float3(1,1,1), float3(1,0,0));
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg.dsm", text)
        assertHasError("Missing out argument for parameter 'result'")
    }

    fun testUnresolvedImportPath() {
        val text = """import "NotFound/Nope.dsh";"""
        myFixture.configureByText("unresolved_import.dsm", text)
        assertHasError("Cannot resolve import 'NotFound/Nope.dsh'")
    }

    fun testGraphDisallowsForLoopStatement() {
        val text = """
            Shader Main {
                Graph {
                    for (int i = 0; i < 4; i = i + 1) {
                    }
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_for_loop.dsm", text)
        assertHasError("Graph section does not support loop statement 'for'")
    }

    fun testGraphDisallowsWhileLoopStatement() {
        val text = """
            Shader Main {
                Graph {
                    while (true) {
                        break;
                    }
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_while_loop.dsm", text)
        assertHasError("Graph section does not support loop statement 'while'")
    }

    fun testGraphDisallowsDoLoopStatement() {
        val text = """
            Shader Main {
                Graph {
                    do {
                        break;
                    } while (false);
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_do_loop.dsm", text)
        assertHasError("Graph section does not support loop statement 'do'")
    }

    fun testGraphDisallowsSwitchStatement() {
        val text = """
            Shader Main {
                Graph {
                    switch (Mode) {
                        default:
                            break;
                    }
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_switch_statement.dsm", text)
        assertHasError("Graph section does not support switch statement 'switch'")
    }

    fun testGraphDisallowsCaseKeyword() {
        val text = """
            Shader Main {
                Graph {
                    switch (Mode) {
                        case 1:
                            break;
                    }
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_case_keyword.dsm", text)
        assertHasError("Graph section does not support switch statement 'case'")
    }

    fun testGraphDisallowsDefaultKeyword() {
        val text = """
            Shader Main {
                Graph {
                    switch (Mode) {
                        default:
                            break;
                    }
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_default_keyword.dsm", text)
        assertHasError("Graph section does not support switch statement 'default'")
    }

    fun testGraphDisallowsBreakStatement() {
        val text = """
            Shader Main {
                Graph {
                    if (Enabled) {
                        break;
                    }
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_break_statement.dsm", text)
        assertHasError("Graph section does not support control statement 'break'")
    }

    fun testGraphDisallowsContinueStatement() {
        val text = """
            Shader Main {
                Graph {
                    if (Enabled) {
                        continue;
                    }
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_continue_statement.dsm", text)
        assertHasError("Graph section does not support control statement 'continue'")
    }

    fun testGraphDisallowsReturnStatement() {
        val text = """
            Shader Main {
                Graph {
                    if (Enabled) {
                        return;
                    }
                }
            }
        """.trimIndent()
        myFixture.configureByText("graph_return_statement.dsm", text)
        assertHasError("Graph section does not support return statement")
    }

    private fun assertHasError(message: String) {
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        assertTrue(
            "Expected error '$message', actual: ${errors.map { it.description }}",
            errors.any { it.description == message }
        )
    }
}
