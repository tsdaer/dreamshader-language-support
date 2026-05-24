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

    fun testDsfDisallowsTopLevelNamespace() {
        val text = """
            Namespace Tools {
                Function Helper(in float x, out float y) {
                    y = x;
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_namespace.dsf", text)

        assertHasError("Top-level Namespace declaration is not allowed in .dsf files")
    }

    fun testDsfAllowsCoreFunctionAssetDeclarations() {
        val text = """
            ShaderFunction BuildNoise {
                Outputs {
                    float Out = 0.0;
                }
            }

            ShaderLayer LayerA {
                Outputs {
                    MaterialAttributes Out;
                }
            }

            ShaderLayerBlend BlendA {
                Inputs {
                    MaterialAttributes A;
                    MaterialAttributes B;
                }
                Outputs {
                    MaterialAttributes Out;
                }
            }

            VirtualFunction VF {
            }

            Function F(in float x, out float y) {
                y = x;
            }

            GraphFunction G(in float x, out float y) {
                y = x;
            }
        """.trimIndent()
        myFixture.configureByText("valid_dsf_allowed_decls.dsf", text)

        assertNoError(
            "Top-level Shader declaration is not allowed in .dsf files",
            "Top-level Namespace declaration is not allowed in .dsf files"
        )
    }

    fun testDsmDisallowsTopLevelShaderLayer() {
        val text = """
            ShaderLayer LayerA {
                Outputs {
                    MaterialAttributes Out;
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_shader_layer.dsm", text)

        assertHasError("Top-level ShaderLayer declaration is not allowed in .dsm files")
    }

    fun testDsmDisallowsTopLevelShaderFunction() {
        val text = """
            ShaderFunction BuildNoise {
                Outputs {
                    float Out = 0.0;
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_shader_function.dsm", text)

        assertHasError("Top-level ShaderFunction declaration is not allowed in .dsm files")
    }

    fun testDsmDisallowsTopLevelShaderLayerBlend() {
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
        myFixture.configureByText("invalid_shader_layer_blend.dsm", text)

        assertHasError("Top-level ShaderLayerBlend declaration is not allowed in .dsm files")
    }

    fun testDshDisallowsTopLevelShaderFunction() {
        val text = """
            ShaderFunction BuildNoise {
                Outputs {
                    float Out = 0.0;
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_shader_function.dsh", text)

        assertHasError("Top-level ShaderFunction declaration is not allowed in .dsh files")
    }

    fun testDshDisallowsTopLevelShaderLayer() {
        val text = """
            ShaderLayer LayerA {
                Outputs {
                    MaterialAttributes Out;
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_shader_layer.dsh", text)

        assertHasError("Top-level ShaderLayer declaration is not allowed in .dsh files")
    }

    fun testDshDisallowsTopLevelShaderLayerBlend() {
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
        myFixture.configureByText("invalid_shader_layer_blend.dsh", text)

        assertHasError("Top-level ShaderLayerBlend declaration is not allowed in .dsh files")
    }

    fun testDshAllowsFunctionAndGraphFunctionDeclarations() {
        val text = """
            Function SharedMath(in float a, in float b, out float result) {
                result = a + b;
            }

            GraphFunction SampleGraph(in float x, out float y) {
                y = x;
            }
        """.trimIndent()
        myFixture.configureByText("valid_header_declarations.dsh", text)

        assertNoError(
            "Top-level Shader declaration is not allowed in .dsh files",
            "Top-level ShaderFunction declaration is not allowed in .dsh files",
            "Top-level ShaderLayer declaration is not allowed in .dsh files",
            "Top-level ShaderLayerBlend declaration is not allowed in .dsh files"
        )
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

    fun testShaderRequiresGraphSection() {
        val text = """
            Shader Main {
                Settings {
                    Domain = "Surface";
                }
            }
        """.trimIndent()
        myFixture.configureByText("shader_missing_graph.dsm", text)

        assertHasError("Shader declaration requires Graph section")
    }

    fun testShaderFunctionRequiresGraphSection() {
        val text = """
            ShaderFunction BuildNoise {
                Outputs {
                    float Out = 0.0;
                }
            }
        """.trimIndent()
        myFixture.configureByText("shader_function_missing_graph.dsf", text)

        assertHasError("ShaderFunction declaration requires Graph section")
    }

    fun testShaderDisallowsInputsSection() {
        val text = """
            Shader Main {
                Inputs {
                    float UV;
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("shader_disallow_inputs.dsm", text)

        assertHasError("Section 'Inputs' is not allowed in Shader declarations")
    }

    fun testVirtualFunctionDisallowsGraphBySectionSchema() {
        val text = """
            VirtualFunction BufferWriter {
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_graph_schema.dsh", text)

        assertHasError("Section 'Graph' is not allowed in VirtualFunction declarations")
    }

    fun testShaderDuplicateSectionIsReported() {
        val text = """
            Shader Main {
                Graph {
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("shader_duplicate_graph.dsm", text)

        assertHasError("Duplicate section 'Graph' in Shader declaration")
    }

    fun testShaderFunctionResultsSectionIsAcceptedAsOutputsAlias() {
        val text = """
            ShaderFunction BuildNoise {
                Results {
                    float Out = 0.0;
                }
                Graph {
                    Out = 1.0;
                }
            }
        """.trimIndent()
        myFixture.configureByText("shader_function_results_alias.dsf", text)

        assertNoError(
            "Section 'Results' is not allowed in ShaderFunction declarations",
            "ShaderFunction declaration requires Graph section"
        )
    }

    fun testShaderLayerBlendResultsSectionDoesNotAliasOutputs() {
        val text = """
            ShaderLayerBlend BlendA {
                Inputs {
                    MaterialAttributes A;
                    MaterialAttributes B;
                }
                Results {
                    MaterialAttributes Out;
                }
            }
        """.trimIndent()
        myFixture.configureByText("layer_blend_results_alias.dsf", text)

        assertHasError("Section 'Results' is not allowed in ShaderLayerBlend declarations")
    }

    fun testShaderLayerBlendRequiresInputsSectionBySchema() {
        val text = """
            ShaderLayerBlend BlendA {
                Outputs {
                    MaterialAttributes Out;
                }
            }
        """.trimIndent()
        myFixture.configureByText("layer_blend_missing_inputs_schema.dsf", text)

        assertHasError("ShaderLayerBlend declaration requires Inputs section")
    }

    fun testFunctionDisallowsSettingsSection() {
        val text = """
            Function ApplyTint(in float3 InColor, out float3 OutColor) {
                Settings {
                    Domain = Surface;
                }
                Graph {
                    OutColor = InColor;
                }
            }
        """.trimIndent()
        myFixture.configureByText("function_disallow_settings.dsh", text)

        assertHasError("Section 'Settings' is not allowed in Function declarations")
    }

    fun testFunctionDisallowsAnySectionBySchema() {
        val text = """
            Function ApplyTint(in float3 InColor, out float3 OutColor) {
                Graph {
                    OutColor = InColor;
                }
            }
        """.trimIndent()
        myFixture.configureByText("function_disallow_graph_section.dsh", text)

        assertHasError("Section 'Graph' is not allowed in Function declarations")
    }

    fun testGraphFunctionDisallowsAnySectionBySchema() {
        val text = """
            GraphFunction BuildNoise(in float X, out float OutValue) {
                Inputs {
                    float Seed;
                }
                OutValue = X + Seed;
            }
        """.trimIndent()
        myFixture.configureByText("graph_function_disallow_section.dsh", text)

        assertHasError("Section 'Inputs' is not allowed in GraphFunction declarations")
    }

    fun testVirtualFunctionResultsSectionIsAcceptedAsOutputsAlias() {
        val text = """
            VirtualFunction BufferWriter {
                Results {
                    float OutValue;
                }
                Options {
                    Asset = true;
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_results_alias.dsh", text)

        assertNoError("Section 'Results' is not allowed in VirtualFunction declarations")
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
