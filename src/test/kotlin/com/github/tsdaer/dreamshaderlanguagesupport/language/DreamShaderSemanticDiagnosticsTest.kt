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

    fun testVirtualFunctionOptionAssetRequiresPath() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = "invalid";
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_invalid.dsh", text)
        assertHasError("VirtualFunction Options.Asset must be an asset path (quoted object path or Path(...))")
    }

    fun testVirtualFunctionOptionAssetAcceptsPathCall() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = Path(Game, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_path_call.dsh", text)
        assertNoError("VirtualFunction Options.Asset must be an asset path (quoted object path or Path(...))")
    }

    fun testVirtualFunctionOptionAssetRejectsBareIdentifier() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = true;
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_bare_identifier.dsh", text)
        assertHasError("VirtualFunction Options.Asset must be an asset path (quoted object path or Path(...))")
    }

    fun testVirtualFunctionOptionAssetAcceptsQuotedObjectPath() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = "Game/Materials/M_VFAsset";
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_quoted_object_path.dsh", text)
        assertNoError("VirtualFunction Options.Asset must be an asset path (quoted object path or Path(...))")
    }

    fun testVirtualFunctionOptionAssetAcceptsEngineRootPathCall() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = Path(Engine, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_engine_path_call.dsh", text)
        assertNoError("VirtualFunction Options.Asset path root 'Engine' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>")
    }

    fun testVirtualFunctionOptionAssetRejectsUnknownPathRoot() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = Path(Project, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_unknown_root.dsh", text)
        assertHasError("VirtualFunction Options.Asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>")
    }

    fun testVirtualFunctionOptionAssetRequiresOptionEntry() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    bExpose = true;
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_required.dsh", text)
        assertHasError("VirtualFunction requires Asset option in Options (Settings alias is also accepted)")
    }

    fun testVirtualFunctionOptionAssetAcceptsSettingsAlias() {
        val text = """
            VirtualFunction BufferWriter {
                Settings {
                    Asset = Path(Game, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_settings_alias_valid.dsh", text)
        assertNoError("VirtualFunction requires Asset option in Options (Settings alias is also accepted)")
        assertNoError("Unknown settings key 'Asset'")
    }

    fun testVirtualFunctionOptionAssetSettingsAliasRejectsUnknownRoot() {
        val text = """
            VirtualFunction BufferWriter {
                Settings {
                    Asset = Path(Project, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_settings_alias_unknown_root.dsh", text)
        assertHasError("VirtualFunction Options.Asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>")
    }

    fun testShaderRootRejectsEngineRoot() {
        val text = """
            Shader Root = "Engine", Name="Materials/M_InvalidRoot" {
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("shader_root_engine_invalid.dsm", text)
        assertHasError("Shader Root value 'Engine' is not allowed. Use Game, Plugin.<Name>, or Plugins.<Name>")
    }

    fun testShaderRootAcceptsPluginRoot() {
        val text = """
            Shader Root = "Plugin.MyPack", Name="Materials/M_ValidRoot" {
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("shader_root_plugin_valid.dsm", text)
        assertNoError("Shader Root value 'Plugin.MyPack' is not allowed. Use Game, Plugin.<Name>, or Plugins.<Name>")
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

    private fun assertNoError(message: String) {
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR)
        assertTrue(
            "Expected no error '$message', actual: ${errors.map { it.description }}",
            errors.none { it.description == message }
        )
    }
}
