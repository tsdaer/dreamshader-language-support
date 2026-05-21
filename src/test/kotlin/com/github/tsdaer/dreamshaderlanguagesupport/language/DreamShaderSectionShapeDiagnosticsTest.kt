package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

class DreamShaderSectionShapeDiagnosticsTest : BasePlatformTestCase() {
    fun testDsfDisallowsTopLevelShader() {
        val text = """
            Shader Main {
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_shader.dsf", text)

        assertHasError("Top-level Shader declaration is not allowed in .dsf files")
    }

    fun testVirtualFunctionDisallowsGraphSection() {
        val text = """
            VirtualFunction BufferWriter {
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_graph.dsh", text)

        assertHasError("VirtualFunction does not support Graph/Code sections")
    }

    fun testNamespaceAllowsOnlyFunctionDeclarations() {
        val text = """
            Namespace Tools {
                ShaderFunction BufferWriter {
                }
            }
        """.trimIndent()
        myFixture.configureByText("namespace_invalid.dsh", text)

        assertHasError("Namespace can only contain Function or GraphFunction declarations")
    }

    fun testLayerRequiresSingleMaterialAttributesOutput() {
        val text = """
            ShaderLayer LayerA {
                Outputs {
                    float3 Color;
                    MaterialAttributes Attr;
                }
            }
        """.trimIndent()
        myFixture.configureByText("layer_invalid_outputs.dsf", text)

        assertHasError("ShaderLayer/ShaderLayerBlend must declare exactly one MaterialAttributes output")
    }

    fun testLayerBlendRequiresTwoMaterialAttributesInputs() {
        val text = """
            ShaderLayerBlend BlendA {
                Inputs {
                    MaterialAttributes A;
                }
                Outputs {
                    MaterialAttributes Out;
                }
            }
        """.trimIndent()
        myFixture.configureByText("layer_blend_invalid_inputs.dsf", text)

        assertHasError("ShaderLayerBlend requires at least two MaterialAttributes inputs")
    }

    fun testValidLayerBlendHasNoShapeErrors() {
        val text = """
            ShaderLayerBlend BlendA {
                Inputs {
                    MaterialAttributes A;
                    MaterialAttributes B;
                }
                Outputs {
                    MaterialAttributes Out;
                }
            }
        """.trimIndent()
        myFixture.configureByText("layer_blend_valid.dsf", text)

        assertNoError(
            "ShaderLayerBlend requires at least two MaterialAttributes inputs",
            "ShaderLayer/ShaderLayerBlend must declare exactly one MaterialAttributes output"
        )
    }

    private fun assertHasError(message: String) {
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        assertTrue(
            "Expected error '$message', actual: ${errors.map { it.description }}",
            errors.any { it.description == message }
        )
    }

    private fun assertNoError(vararg messages: String) {
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        val descriptions = errors.mapNotNull { it.description }
        assertFalse(
            "Expected no matching errors in $descriptions",
            messages.any { it in descriptions }
        )
    }
}
