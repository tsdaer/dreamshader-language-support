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
        assertHasError("Unknown settings key 'DomainX'")
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
        assertHasError("Unknown material output member 'Base.ColorX'")
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
        assertHasError("Unknown type 'float9'")
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

    private fun assertHasError(message: String) {
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        assertTrue(
            "Expected error '$message', actual: ${errors.map { it.description }}",
            errors.any { it.description == message }
        )
    }
}
