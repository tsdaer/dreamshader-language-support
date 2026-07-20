package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderSemanticDiagnosticsTest : BasePlatformTestCase() {
    fun testUnknownSettingsKey() {
        val text = """
            Shader Main {
                Settings {
                    <caret>DomainX = "Surface";
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_settings_key.dsm", text)
        assertHasError("Unknown settings key 'DomainX'. Did you mean 'Domain'?")
    }

    fun testUnknownSettingsKeyQuickFixReplacesWithSuggestion() {
        val text = """
            Shader Main {
                Settings {
                    <caret>DomainX = "Surface";
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_settings_key_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace with 'Domain'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""Domain = "Surface";"""))
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

    fun testInvalidBooleanSettingsValue() {
        val text = """
            Shader Main {
                Settings {
                    TwoSided = <caret>"enabled";
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_boolean_settings_value.dsm", text)
        assertHasError("Invalid value 'enabled' for setting 'TwoSided'")
    }

    fun testValidBooleanSettingsValue() {
        val text = """
            Shader Main {
                Settings {
                    TwoSided = true;
                }
            }
        """.trimIndent()
        myFixture.configureByText("valid_boolean_settings_value.dsm", text)
        assertNoError("Invalid value 'true' for setting 'TwoSided'")
    }

    fun testInvalidNumCustomizedUvsValue() {
        val text = """
            Shader Main {
                Settings {
                    NumCustomizedUVs = <caret>9;
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_num_customized_uvs_value.dsm", text)
        assertHasError("Invalid value '9' for setting 'NumCustomizedUVs'")
    }

    fun testInvalidBooleanSettingsValueQuickFixReplacesWithTrue() {
        val text = """
            Shader Main {
                Settings {
                    TwoSided = <caret>"enabled";
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_boolean_settings_value_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace with true")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("TwoSided = true;"))
    }

    fun testInvalidNumCustomizedUvsValueQuickFixReplacesWithZero() {
        val text = """
            Shader Main {
                Settings {
                    NumCustomizedUVs = <caret>9;
                }
            }
        """.trimIndent()
        myFixture.configureByText("invalid_num_customized_uvs_value_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace with 0")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("NumCustomizedUVs = 0;"))
    }

    fun testValidNumCustomizedUvsValue() {
        val text = """
            Shader Main {
                Settings {
                    NumCustomizedUVs = 8;
                }
            }
        """.trimIndent()
        myFixture.configureByText("valid_num_customized_uvs_value.dsm", text)
        assertNoError("Invalid value '8' for setting 'NumCustomizedUVs'")
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

    fun testVirtualFunctionOptionAssetRejectsPathWithoutObjectSegment() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = <caret>Path(Game);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_path_missing_object_segment.dsh", text)
        assertHasError("VirtualFunction Options.Asset must be an asset path (quoted object path or Path(...))")
    }

    fun testVirtualFunctionOptionAssetPathMissingObjectSegmentQuickFixCompletesPath() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = <caret>Path(Game);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_path_missing_object_segment_fix.dsh", text)
        val action = myFixture.findSingleIntention("Complete Path(...) with object segment")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("Asset = Path(Game, Textures/T_AutoAsset);"))
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

    fun testGeneralTextureInitializerRejectsPathWithoutObjectSegment() {
        val text = """
            Shader Main {
                Properties = {
                    Texture2D MainTex = <caret>Path(Engine);
                }
            }
        """.trimIndent()
        myFixture.configureByText("general_texture_path_missing_object_segment.dsm", text)
        assertHasError("VirtualFunction Options.Asset must be an asset path (quoted object path or Path(...))")
    }

    fun testGeneralTextureInitializerRejectsUnknownPathRoot() {
        val text = """
            Shader Main {
                Properties = {
                    Texture2D MainTex = <caret>Path(Project, Textures/T_Main);
                }
            }
        """.trimIndent()
        myFixture.configureByText("general_texture_path_invalid_root.dsm", text)
        assertHasError("VirtualFunction Options.Asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>")
    }

    fun testGeneralTextureInitializerAcceptsSingleArgumentAbsolutePathCall() {
        val text = """
            Shader Main {
                Properties = {
                    Texture2D MainTex = Path("/Engine/EngineResources/DefaultTexture");
                }
            }
        """.trimIndent()
        myFixture.configureByText("general_texture_path_single_argument_absolute.dsm", text)
        assertNoError("VirtualFunction Options.Asset must be an asset path (quoted object path or Path(...))")
        assertNoError("VirtualFunction Options.Asset path root '/Engine' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>")
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

    fun testVirtualFunctionOptionAssetWarnsOnUassetObjectSuffix() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = <caret>Path(Game, Materials/M_VFAsset.uasset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_invalid_object_suffix.dsh", text)
        assertHasWarning("Path object segment should not end with '.uasset'")
    }

    fun testVirtualFunctionOptionAssetRejectsUnknownPathRoot() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = <caret>Path(Project, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_unknown_root.dsh", text)
        assertHasError("VirtualFunction Options.Asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>")
    }

    fun testVirtualFunctionOptionAssetUnknownRootQuickFixReplacesWithGame() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = <caret>Path(Project, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_unknown_root_fix.dsh", text)
        val action = myFixture.findSingleIntention("Replace Path root with Game")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("Asset = Path(Game, Materials/M_VFAsset);"))
    }

    fun testVirtualFunctionOptionAssetUnknownQuotedRootQuickFixReplacesWithGame() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = <caret>"Project/Materials/M_VFAsset";
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_unknown_quoted_root_fix.dsh", text)
        assertHasError("VirtualFunction Options.Asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>")
        val action = myFixture.findSingleIntention("Replace Path root with Game")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""Asset = "Game/Materials/M_VFAsset";"""))
    }

    fun testVirtualFunctionOptionAssetRejectsLeadingSlashUnknownRoot() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = <caret>"/Project/Materials/M_VFAsset";
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_leading_slash_unknown_root.dsh", text)
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

    fun testVirtualFunctionOptionAssetRequiredEvenWhenNoOptionsOrSettingsSection() {
        val text = """
            VirtualFunction BufferWriter {
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_asset_required_no_sections.dsh", text)
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

    fun testVirtualFunctionOptionDescriptionWarnsWhenNotQuoted() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Description = <caret>BridgeCompatible;
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_description_not_quoted.dsh", text)
        assertHasWarning("VirtualFunction Options.Description should be a quoted string literal")
    }

    fun testVirtualFunctionOptionDescriptionNotQuotedQuickFixAddsQuotes() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Description = <caret>BridgeCompatible;
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_description_not_quoted_fix.dsh", text)
        val action = myFixture.findSingleIntention("Quote Description value")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""Description = "BridgeCompatible";"""))
    }

    fun testVirtualFunctionOptionDescriptionWarnsWhenEmpty() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Description = <caret>"";
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_description_empty.dsh", text)
        assertHasWarning("VirtualFunction Options.Description should not be empty")
    }

    fun testVirtualFunctionOptionDescriptionEmptyQuickFixFillsDefault() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Description = <caret>"";
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_description_empty_fix.dsh", text)
        val action = myFixture.findSingleIntention("Fill Description with default text")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""Description = "Bridge-compatible virtual function";"""))
    }

    fun testVirtualFunctionOptionDescriptionSettingsAliasAcceptsQuotedText() {
        val text = """
            VirtualFunction BufferWriter {
                Settings {
                    Description = "Bridge-compatible virtual function";
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_description_settings_alias_valid.dsh", text)
        assertNoWarning("VirtualFunction Options.Description should be a quoted string literal")
        assertNoWarning("VirtualFunction Options.Description should not be empty")
    }

    fun testVirtualFunctionOptionDescriptionRecommendedWhenMissing() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    Asset = Path(Game, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_description_missing_recommended.dsh", text)
        assertHasWarning("VirtualFunction should provide Options.Description (Settings alias is also accepted)")
    }

    fun testVirtualFunctionOptionDescriptionRecommendedEvenWhenNoOptionsOrSettingsSection() {
        val text = """
            VirtualFunction BufferWriter {
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_description_missing_no_sections.dsh", text)
        assertHasWarning("VirtualFunction should provide Options.Description (Settings alias is also accepted)")
    }

    fun testVirtualFunctionOptionDescriptionRecommendedQuickFixAddsDescription() {
        val text = """
            VirtualFunction BufferWriter {
                Options {
                    <caret>
                    Asset = Path(Game, Materials/M_VFAsset);
                }
            }
        """.trimIndent()
        myFixture.configureByText("virtual_function_description_missing_fix.dsh", text)
        val action = myFixture.findSingleIntention("Add Description option")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""Description = "Bridge-compatible virtual function";"""))
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

    fun testOptionalInputRequiresDefaultInInputsSection() {
        val text = """
            ShaderFunction MyFunction {
                Inputs {
                    opt float Strength;
                }
                Outputs {
                    float Out;
                }
                Graph {
                    Out = Strength;
                }
            }
        """.trimIndent()
        myFixture.configureByText("optional_input_requires_default.dsf", text)
        assertHasWarning("Optional input 'Strength' should provide a default value")
    }

    fun testOptionalInputWithDefaultDoesNotWarn() {
        val text = """
            ShaderFunction MyFunction {
                Inputs {
                    opt float Strength = 1.0;
                }
                Outputs {
                    float Out;
                }
                Graph {
                    Out = Strength;
                }
            }
        """.trimIndent()
        myFixture.configureByText("optional_input_with_default.dsf", text)
        assertNoWarning("Optional input 'Strength' should provide a default value")
        assertNoWarning("Input 'Strength' default value appears malformed")
    }

    fun testOptionalInputMalformedDefaultWarns() {
        val text = """
            ShaderFunction MyFunction {
                Inputs {
                    opt float Strength = float2(1.0;
                }
                Outputs {
                    float Out;
                }
                Graph {
                    Out = Strength;
                }
            }
        """.trimIndent()
        myFixture.configureByText("optional_input_malformed_default.dsf", text)
        assertHasWarning("Input 'Strength' default value appears malformed")
    }

    fun testOptionalFunctionParameterRequiresDefault() {
        val text = """
            Function ApplyTint(in float3 Color, opt float Strength) {
                return;
            }
        """.trimIndent()
        myFixture.configureByText("optional_function_parameter_requires_default.dsh", text)
        assertHasWarning("Optional input 'Strength' should provide a default value")
    }

    fun testUnknownBaseOutputMember() {
        val text = """
            Shader Main {
                Outputs {
                    <caret>Base.ColorX = float3(1.0, 1.0, 1.0);
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_base_member.dsm", text)
        assertHasError("Unknown material output member 'Base.ColorX'. Did you mean 'Base.BaseColor'?")
    }

    fun testUnknownBaseOutputMemberQuickFixReplacesWithSuggestion() {
        val text = """
            Shader Main {
                Outputs {
                    <caret>Base.ColorX = float3(1.0, 1.0, 1.0);
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_base_member_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace with 'Base.BaseColor'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("Base.BaseColor = float3(1.0, 1.0, 1.0);"))
    }

    fun testUnknownTypeInInputs() {
        val text = """
            Shader Main {
                Inputs {
                    <caret>float9 BadType;
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type.dsm", text)
        assertHasError("Unknown type 'float9'. Did you mean 'float'?")
    }

    fun testUnknownTypeQuickFixReplacesWithSuggestion() {
        val text = """
            Shader Main {
                Inputs {
                    <caret>float9 BadType;
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace with 'float'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("float BadType;"))
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

    fun testUnknownTypeInFunctionParameter() {
        val text = """
            Function ApplyTint(in <caret>float9 color, out vec3 result) {
                result = float3(color, color, color);
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_function_parameter.dsh", text)
        assertHasError("Unknown type 'float9'. Did you mean 'float'?")
    }

    fun testUnknownTypeInFunctionParameterQuickFixReplacesWithSuggestion() {
        val text = """
            Function ApplyTint(in <caret>float9 color, out vec3 result) {
                result = float3(color, color, color);
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_function_parameter_fix.dsh", text)
        val action = myFixture.findSingleIntention("Replace with 'float'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("Function ApplyTint(in float color, out vec3 result) {"))
    }

    fun testUnknownTypeInGraphFunctionParameter() {
        val text = """
            GraphFunction BuildNoise(in <caret>float9 seed, out float outValue) {
                outValue = seed;
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_graphfunction_parameter.dsf", text)
        assertHasError("Unknown type 'float9'. Did you mean 'float'?")
    }

    fun testUnknownTypeInNamespaceFunctionParameter() {
        val text = """
            Namespace Tools {
                Function ApplyTint(in <caret>float9 color, out vec3 result) {
                    result = float3(color, color, color);
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_namespace_function_parameter.dsh", text)
        assertHasError("Unknown type 'float9'. Did you mean 'float'?")
    }

    fun testUnknownTypeInGraphLocalVariable() {
        val text = """
            Shader Main {
                Graph {
                    <caret>float9 temp = 1.0;
                    Base.BaseColor = float3(temp, temp, temp);
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_graph_local_var.dsm", text)
        assertHasError("Unknown type 'float9'. Did you mean 'float'?")
    }

    fun testUnknownTypeInGraphLocalVariableQuickFixReplacesWithSuggestion() {
        val text = """
            Shader Main {
                Graph {
                    <caret>float9 temp = 1.0;
                    Base.BaseColor = float3(temp, temp, temp);
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_graph_local_var_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace with 'float'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("float temp = 1.0;"))
    }

    fun testUnknownTypeInNamespaceFunctionLocalVariable() {
        val text = """
            Namespace Tools {
                Function ApplyTint(in vec3 color, out vec3 result) {
                    <caret>float9 tmp = 1.0;
                    result = color * tmp;
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_type_namespace_function_local_var.dsh", text)
        assertHasError("Unknown type 'float9'. Did you mean 'float'?")
    }

    fun testKnownTypeInFunctionLocalVariableDoesNotReportUnknownType() {
        val text = """
            Function ApplyTint(in vec3 color, out vec3 result) {
                float localWeight = 1.0;
                result = color * localWeight;
            }
        """.trimIndent()
        myFixture.configureByText("known_type_function_local_var.dsf", text)
        assertNoError("Unknown type 'float'")
    }

    fun testVolumeTextureTypeIsRecognized() {
        val text = """
            Shader Main {
                Properties {
                    VolumeTexture NoiseVolume = Path(Game, Textures/T_NoiseVolume);
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("known_type_volumetexture.dsm", text)
        assertNoError("Unknown type 'VolumeTexture'")
    }

    fun testTexture3DAliasTypeIsRecognized() {
        val text = """
            Shader Main {
                Inputs {
                    Texture3D VolumeTex;
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("known_type_texture3d.dsm", text)
        assertNoError("Unknown type 'Texture3D'")
    }

    fun testConstTextureCubeRequiresExplicitDefaultAsset() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env;
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_requires_default_asset.dsm", text)
        assertHasError("const TextureCube must explicitly specify a default asset (for example = Path(...)).")
    }

    fun testConstVolumeTextureWithPathDoesNotRequireExtraDiagnostic() {
        val text = """
            Shader Main {
                Properties {
                    const VolumeTexture NoiseVolume = Path(Game, Textures/T_NoiseVolume);
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_volumetexture_with_default_asset.dsm", text)
        assertNoError("const VolumeTexture must explicitly specify a default asset (for example = Path(...)).")
    }

    fun testConstTextureCubeDefaultAssetRejectsBareIdentifier() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = <caret>EnvTexture;
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_invalid_identifier.dsm", text)
        assertHasError("const TextureCube default asset must be an asset path (quoted object path or Path(...)).")
    }

    fun testConstTextureCubeDefaultAssetRejectsPathWithoutObjectSegment() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = <caret>Path(Game);
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_path_missing_object_segment.dsm", text)
        assertHasError("const TextureCube default asset must be an asset path (quoted object path or Path(...)).")
    }

    fun testConstTextureCubeDefaultAssetPathMissingObjectSegmentQuickFixCompletesPath() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = <caret>Path(Game);
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_path_missing_object_segment_fix.dsm", text)
        val action = myFixture.findSingleIntention("Complete Path(...) with object segment")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("const TextureCube Env = Path(Game, Textures/T_AutoAsset);"))
    }

    fun testConstTextureCubeDefaultAssetRejectsUnknownPathRoot() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = <caret>Path(Project, Textures/T_Env);
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_invalid_root.dsm", text)
        assertHasError("const TextureCube default asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>.")
    }

    fun testConstTextureCubeDefaultAssetUnknownRootQuickFixReplacesWithGame() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = <caret>Path(Project, Textures/T_Env);
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_invalid_root_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace Path root with Game")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("const TextureCube Env = Path(Game, Textures/T_Env);"))
    }

    fun testConstTextureCubeDefaultAssetUnknownQuotedRootQuickFixReplacesWithGame() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = <caret>"Project/Textures/T_Env.T_Env";
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_unknown_quoted_root_fix.dsm", text)
        assertHasError("const TextureCube default asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>.")
        val action = myFixture.findSingleIntention("Replace Path root with Game")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""const TextureCube Env = "Game/Textures/T_Env.T_Env";"""))
    }

    fun testConstTextureCubeDefaultAssetRejectsLeadingSlashUnknownRoot() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = <caret>"/Project/Textures/T_Env.T_Env";
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_leading_slash_unknown_root.dsm", text)
        assertHasError("const TextureCube default asset path root 'Project' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>.")
    }

    fun testConstTextureCubeDefaultAssetAcceptsQuotedObjectPath() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = "/Game/Textures/T_Env.T_Env";
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_quoted_path_ok.dsm", text)
        assertNoError("const TextureCube default asset must be an asset path (quoted object path or Path(...)).")
        assertNoError("const TextureCube default asset path root '/Game' is not allowed. Use Game, Engine, Plugin.<Name>, or Plugins.<Name>.")
    }

    fun testConstTextureCubeDefaultAssetWarnsOnImageObjectSuffix() {
        val text = """
            Shader Main {
                Properties {
                    const TextureCube Env = <caret>"/Game/Textures/T_Env.png";
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texturecube_default_asset_invalid_object_suffix.dsm", text)
        assertHasWarning("Path object segment should not end with '.png'")
    }

    fun testConstTexture2DStillAllowsOmittedDefaultAsset() {
        val text = """
            Shader Main {
                Properties {
                    const Texture2D MainTex;
                }
                Graph {
                }
            }
        """.trimIndent()
        myFixture.configureByText("const_texture2d_without_default_asset_allowed.dsm", text)
        assertNoError("const Texture2D must explicitly specify a default asset (for example = Path(...)).")
    }

    fun testUnknownExpressionClassInUeExpressionCall() {
        val text = """
            Shader Main {
                Graph {
                    float x = UE.Expression(Class=<caret>"Sinx", OutputType="float1", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_expression_class.dsm", text)
        assertHasError("Unknown UE.Expression class 'Sinx'. Did you mean 'Sine'?")
    }

    fun testUnknownExpressionClassQuickFixReplacesWithSuggestion() {
        val text = """
            Shader Main {
                Graph {
                    float x = UE.Expression(Class=<caret>"Sinx", OutputType="float1", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("unknown_expression_class_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace with 'Sine'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""UE.Expression(Class="Sine""""))
    }

    fun testKnownExpressionClassInUeExpressionCallHasNoError() {
        val text = """
            Shader Main {
                Graph {
                    float x = UE.Expression(Class="Sine", OutputType="float1", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("known_expression_class.dsm", text)
        assertNoError("Unknown UE.Expression class 'Sine'")
    }

    fun testUeExpressionClassRequired() {
        val text = """
            Shader Main {
                Graph {
                    float x = <caret>UE.Expression(OutputType="float1", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_class_required.dsm", text)
        assertHasError("""UE.Expression requires named argument Class="..."""")
    }

    fun testUeExpressionClassRequiredQuickFixAddsClass() {
        val text = """
            Shader Main {
                Graph {
                    float x = <caret>UE.Expression(OutputType="float1", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_class_required_fix.dsm", text)
        val action = myFixture.findSingleIntention("Add Class argument")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""UE.Expression("""))
        assertTrue(myFixture.file.text.contains("""Class=""""))
        assertTrue(myFixture.file.text.contains("""OutputType="float1""""))
    }

    fun testUeExpressionOutputTypeRequired() {
        val text = """
            Shader Main {
                Graph {
                    float x = <caret>UE.Expression(Class="Sine", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_output_type_required.dsm", text)
        assertHasError("""UE.Expression requires named argument OutputType="..."""")
    }

    fun testUeExpressionOutputTypeRequiredQuickFixAddsOutputType() {
        val text = """
            Shader Main {
                Graph {
                    float x = <caret>UE.Expression(Class="Sine", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_output_type_required_fix.dsm", text)
        val action = myFixture.findSingleIntention("Add OutputType argument")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""UE.Expression("""))
        assertTrue(myFixture.file.text.contains("""Class="Sine""""))
        assertTrue(myFixture.file.text.contains("""OutputType="float1""""))
    }

    fun testInvalidUeExpressionOutputTypeWithSuggestion() {
        val text = """
            Shader Main {
                Graph {
                    float x = UE.Expression(Class="Sine", OutputType=<caret>"float5", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_output_type_invalid.dsm", text)
        assertHasError("Invalid UE.Expression OutputType 'float5'. Did you mean 'float'?")
    }

    fun testInvalidUeExpressionOutputTypeQuickFixReplacesWithSuggestion() {
        val text = """
            Shader Main {
                Graph {
                    float x = UE.Expression(Class="Sine", OutputType=<caret>"float5", Input=UE.Time());
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_output_type_invalid_fix.dsm", text)
        val action = myFixture.findSingleIntention("Replace with 'float'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""OutputType="float""""))
    }

    fun testCustomExpressionRejectsSubstrateOutputType() {
        val text = """
            Shader Main {
                Graph {
                    Substrate s = UE.Expression(Class="Custom", OutputType="Substrate");
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_custom_substrate.dsm", text)
        assertHasError("UMaterialExpressionCustom does not support OutputType=\"Substrate\"")
    }

    fun testCustomExpressionRejectsSubstrateOutputTypeWithFullClassName() {
        val text = """
            Shader Main {
                Graph {
                    Substrate s = UE.Expression(Class="UMaterialExpressionCustom", OutputType="Substrate");
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_custom_substrate_full.dsm", text)
        assertHasError("UMaterialExpressionCustom does not support OutputType=\"Substrate\"")
    }

    fun testNonCustomExpressionAllowsSubstrateOutputType() {
        val text = """
            Shader Main {
                Graph {
                    Substrate s = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_substrate_ok.dsm", text)
        assertNoError("UMaterialExpressionCustom does not support OutputType=\"Substrate\"")
    }

    fun testMaterialExpressionOutput() {
        val text = """
            Shader Main {
                Graph {
                    float x = UE.Expression(Class="Custom", OutputType="float1");
                }
            }
        """.trimIndent()
        myFixture.configureByText("ue_expression_custom_float.dsm", text)
        assertNoError("UMaterialExpressionCustom does not support OutputType=\"Substrate\"")
    }

    fun testFrontMaterialIsRecognizedBaseMember() {
        val text = """
            Shader Main {
                Graph {
                    Base.FrontMaterial = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                }
            }
        """.trimIndent()
        myFixture.configureByText("base_front_material.dsm", text)
        assertNoError("Unknown material output member 'Base.FrontMaterial'")
    }

    fun testFrontMaterialAndMaterialAttributesAreMutuallyExclusive() {
        val text = """
            Shader Main {
                Graph {
                    Base.FrontMaterial = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                    Base.MaterialAttributes = UE.MakeMaterialAttributes();
                }
            }
        """.trimIndent()
        myFixture.configureByText("base_front_material_exclusive.dsm", text)
        assertHasError("A Shader cannot bind both Base.FrontMaterial and Base.MaterialAttributes")
    }

    fun testFrontMaterialAloneHasNoExclusivityError() {
        val text = """
            Shader Main {
                Graph {
                    Base.FrontMaterial = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                }
            }
        """.trimIndent()
        myFixture.configureByText("base_front_material_alone.dsm", text)
        assertNoError("A Shader cannot bind both Base.FrontMaterial and Base.MaterialAttributes")
    }

    fun testSubstrateLocalDisallowsArithmetic() {
        val text = """
            Shader Main {
                Graph {
                    Substrate s = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                    float x = s + 1.0;
                }
            }
        """.trimIndent()
        myFixture.configureByText("substrate_arithmetic.dsm", text)
        assertHasError("Substrate values cannot be used in arithmetic expressions")
    }

    fun testSubstrateLocalDisallowsSwizzle() {
        val text = """
            Shader Main {
                Graph {
                    Substrate s = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                    float x = s.x;
                }
            }
        """.trimIndent()
        myFixture.configureByText("substrate_swizzle.dsm", text)
        assertHasError("Substrate values do not support swizzle access")
    }

    fun testSubstrateLocalDisallowsVectorConstructorArgument() {
        val text = """
            Shader Main {
                Graph {
                    Substrate s = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                    float3 color = float3(s, 0.0, 1.0);
                }
            }
        """.trimIndent()
        myFixture.configureByText("substrate_vector_constructor.dsm", text)
        assertHasError("Substrate values cannot be used as vector constructor arguments")
    }

    fun testSubstrateLocalDisallowsTernaryBranchMerge() {
        val text = """
            Shader Main {
                Graph {
                    Substrate a = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                    Substrate b = UE.Expression(Class="MaterialExpressionSubstrateSlabBSDF", OutputType="Substrate");
                    Substrate c = true ? a : b;
                }
            }
        """.trimIndent()
        myFixture.configureByText("substrate_ternary.dsm", text)
        assertHasError("Substrate values cannot be merged through ternary branches")
    }

    fun testNonSubstrateExpressionsDoNotReportSubstrateDiagnostics() {
        val text = """
            Shader Main {
                Graph {
                    float a = 1.0;
                    float b = 2.0;
                    float x = a + b;
                    float y = float3(a, b, 0.0).x;
                    float z = true ? a : b;
                }
            }
        """.trimIndent()
        myFixture.configureByText("non_substrate_expressions.dsm", text)
        assertNoError("Substrate values cannot be used in arithmetic expressions")
        assertNoError("Substrate values do not support swizzle access")
        assertNoError("Substrate values cannot be used as vector constructor arguments")
        assertNoError("Substrate values cannot be merged through ternary branches")
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

    fun testMissingOutArgumentQuickFixAddsPlaceholderArgument() {
        project.getService(DreamShaderProjectSettings::class.java).state.outArgumentPlaceholderSuffix = "Out"
        val text = """
            Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                result = color * tint;
            }

            Shader Main {
                Graph {
                    <caret>ApplyTint(float3(1,1,1), float3(1,0,0));
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_fix.dsm", text)
        val action = myFixture.findSingleIntention("Add missing out arguments")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("ApplyTint(float3(1,1,1), float3(1,0,0), resultOut);"))
    }

    fun testMissingOutArgumentQuickFixAddsFirstArgumentForEmptyCall() {
        project.getService(DreamShaderProjectSettings::class.java).state.outArgumentPlaceholderSuffix = "Out"
        val text = """
            Function Emit(out vec3 output) {
                output = float3(1, 1, 1);
            }

            Shader Main {
                Graph {
                    <caret>Emit();
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_empty_call_fix.dsm", text)
        val action = myFixture.findSingleIntention("Add missing out arguments")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("Emit(outputOut);"))
    }

    fun testMissingOutArgumentQuickFixAddsAllMissingOutArguments() {
        project.getService(DreamShaderProjectSettings::class.java).state.outArgumentPlaceholderSuffix = "Out"
        val text = """
            Function PackData(in float a, out vec3 resultA, out vec3 resultB) {
                resultA = float3(a, a, a);
                resultB = float3(a * 2, a * 2, a * 2);
            }

            Shader Main {
                Graph {
                    <caret>PackData(1.0);
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_multi_fix.dsm", text)
        val action = myFixture.findSingleIntention("Add missing out arguments")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("PackData(1.0, resultAOut, resultBOut);"))
    }

    fun testMissingOutArgumentQuickFixAvoidsNameCollision() {
        project.getService(DreamShaderProjectSettings::class.java).state.outArgumentPlaceholderSuffix = "Out"
        val text = """
            Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                result = color * tint;
            }

            Shader Main {
                Graph {
                    vec3 resultOut = float3(0,0,0);
                    <caret>ApplyTint(float3(1,1,1), float3(1,0,0));
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_collision_fix.dsm", text)
        val action = myFixture.findSingleIntention("Add missing out arguments")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("ApplyTint(float3(1,1,1), float3(1,0,0), resultOut2);"))
    }

    fun testMissingOutArgumentQuickFixUsesConfiguredPlaceholderSuffix() {
        project.getService(DreamShaderProjectSettings::class.java).state.outArgumentPlaceholderSuffix = "_out"
        val text = """
            Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                result = color * tint;
            }

            Shader Main {
                Graph {
                    <caret>ApplyTint(float3(1,1,1), float3(1,0,0));
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_suffix_fix.dsm", text)
        val action = myFixture.findSingleIntention("Add missing out arguments")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("ApplyTint(float3(1,1,1), float3(1,0,0), result_out);"))
    }

    fun testMissingOutArgumentInNamespaceFunctionCall() {
        val text = """
            Namespace Tools {
                Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                    result = color * tint;
                }
            }

            Shader Main {
                Graph {
                    Tools::ApplyTint(float3(1,1,1), float3(1,0,0));
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_namespace_function_call.dsm", text)
        assertHasError("Missing out argument for parameter 'result'")
    }

    fun testMissingOutArgumentInNamespaceFunctionCallQuickFixAddsPlaceholderArgument() {
        project.getService(DreamShaderProjectSettings::class.java).state.outArgumentPlaceholderSuffix = "Out"
        val text = """
            Namespace Tools {
                Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                    result = color * tint;
                }
            }

            Shader Main {
                Graph {
                    <caret>Tools::ApplyTint(float3(1,1,1), float3(1,0,0));
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_namespace_function_call_fix.dsm", text)
        val action = myFixture.findSingleIntention("Add missing out arguments")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("Tools::ApplyTint(float3(1,1,1), float3(1,0,0), resultOut);"))
    }

    fun testMissingOutArgumentResolvesUnqualifiedCallToNearestNamespaceScope() {
        val text = """
            Function Blend(in float x, in float y) {
            }

            Namespace Tools {
                Function Blend(in float x, in float y, out float result) {
                    result = x + y;
                }

                Function Build(in float x, out float output) {
                    <caret>Blend(x, x);
                    output = x;
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_namespace_scope_resolution.dsh", text)
        assertHasError("Missing out argument for parameter 'result'")
    }

    fun testMissingOutArgumentDoesNotReportForTopLevelOverloadWhenNamespaceCallIsComplete() {
        val text = """
            Function Blend(in float x, in float y, out float result) {
                result = x + y;
            }

            Namespace Tools {
                Function Blend(in float x, in float y) {
                }

                Function Build(in float x, out float output) {
                    <caret>Blend(x, x);
                    output = x;
                }
            }
        """.trimIndent()
        myFixture.configureByText("missing_out_arg_namespace_scope_no_false_positive.dsh", text)
        assertNoError("Missing out argument for parameter 'result'")
    }

    fun testUnresolvedImportPath() {
        val text = """import "<caret>NotFound/Nope.dsh";"""
        myFixture.configureByText("unresolved_import.dsm", text)
        assertHasError("Cannot resolve import 'NotFound/Nope.dsh'")
    }

    fun testUnresolvedImportPathWithInvalidWindowsPathCharsDoesNotOfferCreateFileQuickFix() {
        val text = """import "<caret>dreamshader*-.dsh";"""
        myFixture.configureByText("unresolved_import_invalid_windows_path_chars.dsm", text)
        assertHasError("Cannot resolve import 'dreamshader*-.dsh'")

        val actions = myFixture.filterAvailableIntentions("Create missing import file")
        assertTrue("Did not expect create-file quick fix for invalid path characters", actions.isEmpty())
    }

    fun testUnresolvedImportPathQuickFixCreatesMissingFile() {
        val text = """import "<caret>NotFound/Nope";"""
        myFixture.configureByText("unresolved_import_quickfix.dsm", text)
        assertHasError("Cannot resolve import 'NotFound/Nope'")
        val action = myFixture.findSingleIntention("Create missing import file: NotFound/Nope.dsh")
        myFixture.launchAction(action)

        val created = LocalFileSystem.getInstance().findFileByPath("${project.basePath!!.replace('\\', '/')}/NotFound/Nope.dsh")
        assertTrue("Expected created import target file", created != null && created.exists())
        val content = created!!.inputStream.bufferedReader().use { it.readText() }
        assertTrue("Expected header template content", content.contains("Namespace Nope"))
        assertTrue("Expected header template content", content.contains("Function ApplyTint"))
        val selected = FileEditorManager.getInstance(project).selectedEditor?.file
        assertTrue("Expected created file to be opened", selected?.path == created.path)
        assertNoError("Cannot resolve import 'NotFound/Nope'")
    }

    fun testUnresolvedImportPathQuickFixCreatesFunctionTemplateForDsf() {
        val text = """import "<caret>Gen/PackData.dsf";"""
        myFixture.configureByText("unresolved_import_quickfix_dsf.dsm", text)
        assertHasError("Cannot resolve import 'Gen/PackData.dsf'")
        val action = myFixture.findSingleIntention("Create missing import file: Gen/PackData.dsf")
        myFixture.launchAction(action)

        val created = LocalFileSystem.getInstance().findFileByPath("${project.basePath!!.replace('\\', '/')}/Gen/PackData.dsf")
        assertTrue("Expected created dsf file", created != null && created.exists())
        val content = created!!.inputStream.bufferedReader().use { it.readText() }
        assertTrue(content.contains("""ShaderFunction(Name="Functions/PackData")"""))
        assertTrue(content.contains("OutColor = InColor * Strength;"))
        assertNoError("Cannot resolve import 'Gen/PackData.dsf'")
    }

    fun testUnresolvedImportPathQuickFixCreatesShaderTemplateForDsm() {
        val text = """import "<caret>Materials/M_Auto.dsm";"""
        myFixture.configureByText("unresolved_import_quickfix_dsm.dsm", text)
        assertHasError("Cannot resolve import 'Materials/M_Auto.dsm'")
        val action = myFixture.findSingleIntention("Create missing import file: Materials/M_Auto.dsm")
        myFixture.launchAction(action)

        val created = LocalFileSystem.getInstance().findFileByPath("${project.basePath!!.replace('\\', '/')}/Materials/M_Auto.dsm")
        assertTrue("Expected created dsm file", created != null && created.exists())
        val content = created!!.inputStream.bufferedReader().use { it.readText() }
        assertTrue(content.contains("""Shader(Name="Materials/M_Auto")"""))
        assertTrue(content.contains("Graph = {"))
        assertNoError("Cannot resolve import 'Materials/M_Auto.dsm'")
    }

    fun testUnresolvedScopedImportQuickFixCreatesFileUnderPackages() {
        val text = """import "<caret>@typedreammoon/dream-noise/Library/Noise";"""
        myFixture.configureByText("unresolved_scoped_import_quickfix.dsm", text)
        assertHasError("Cannot resolve import '@typedreammoon/dream-noise/Library/Noise'")
        val action = myFixture.findSingleIntention("Create missing import file: @typedreammoon/dream-noise/Library/Noise.dsh")
        myFixture.launchAction(action)

        val created = LocalFileSystem.getInstance().findFileByPath(
            "${project.basePath!!.replace('\\', '/')}/DShader/Packages/@typedreammoon/dream-noise/Library/Noise.dsh"
        )
        assertTrue("Expected created scoped package import file", created != null && created.exists())
        val content = created!!.inputStream.bufferedReader().use { it.readText() }
        assertTrue(content.contains("Namespace Noise"))
        assertNoError("Cannot resolve import '@typedreammoon/dream-noise/Library/Noise'")
    }

    fun testPackageRootImportEntryMissingReportsDedicatedError() {
        val packageName = "@typedreammoon/dream-noise-root-missing"
        val packageRoot = "${project.basePath!!.replace('\\', '/')}/DShader/Packages/@typedreammoon/dream-noise-root-missing"
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(packageRoot)
            val metadata = parent.findOrCreateChildData(this, "dreamshader.package.json")
            VfsUtil.saveText(
                metadata,
                """
                {
                  "name": "$packageName",
                  "dreamshader": { "entry": "Library/NoiseMain.dsh" }
                }
                """.trimIndent()
            )
        }

        val text = """import "<caret>$packageName";"""
        myFixture.configureByText("unresolved_scoped_package_root_missing_entry.dsm", text)
        assertHasError("Cannot resolve package root import '$packageName': package entry file is missing. Expected entry: 'Library/NoiseMain.dsh'")
    }

    fun testPackageRootImportEntryMissingQuickFixCreatesSuggestedEntryFile() {
        val packageName = "@typedreammoon/dream-noise-root-missing-fix"
        val packageRoot = "${project.basePath!!.replace('\\', '/')}/DShader/Packages/@typedreammoon/dream-noise-root-missing-fix"
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(packageRoot)
            val metadata = parent.findOrCreateChildData(this, "dreamshader.package.json")
            VfsUtil.saveText(
                metadata,
                """
                {
                  "name": "$packageName",
                  "dreamshader": { "entry": "Library/NoiseMain.dsh" }
                }
                """.trimIndent()
            )
        }

        val text = """import "<caret>$packageName";"""
        myFixture.configureByText("unresolved_scoped_package_root_missing_entry_fix.dsm", text)
        val action = myFixture.findSingleIntention("Create missing import file: $packageName/Library/NoiseMain.dsh")
        myFixture.launchAction(action)

        val created = LocalFileSystem.getInstance().findFileByPath(
            "${project.basePath!!.replace('\\', '/')}/DShader/Packages/@typedreammoon/dream-noise-root-missing-fix/Library/NoiseMain.dsh"
        )
        assertTrue("Expected created package entry file", created != null && created.exists())
        assertNoError("Cannot resolve package root import '$packageName': package entry file is missing. Expected entry: 'Library/NoiseMain.dsh'")
    }

    fun testPackageRootImportInvalidEntryReportsDedicatedError() {
        val packageName = "@typedreammoon/dream-noise-root-invalid"
        val packageRoot = "${project.basePath!!.replace('\\', '/')}/DShader/Packages/@typedreammoon/dream-noise-root-invalid"
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(packageRoot)
            val metadata = parent.findOrCreateChildData(this, "dreamshader.package.json")
            VfsUtil.saveText(
                metadata,
                """
                {
                  "name": "$packageName",
                  "dreamshader": { "entry": "../Outside.dsh" }
                }
                """.trimIndent()
            )
        }

        val text = """import "<caret>$packageName";"""
        myFixture.configureByText("unresolved_scoped_package_root_invalid_entry.dsm", text)
        assertHasError("Cannot resolve package root import '$packageName': package metadata entry '../Outside.dsh' is invalid or unsafe.")
    }

    fun testUnresolvedImportPathUnsupportedExtensionReportsExplicitError() {
        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext.dsm", text)
        assertHasError("Unsupported import file extension .usf. Only .dsh, .dsf, and .dsm are supported.")
    }

    fun testRemovedBuiltinLibraryFallbackImportPathNowReportsUnresolvedImport() {
        val projectBase = project.basePath ?: error("project base path is null")
        val builtinPath = Paths.get(projectBase, "Plugins", "DreamShader", "Library", "Texture.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(builtinPath.parent.toString())
            val file = parent.findOrCreateChildData(this, builtinPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Namespace BuiltinTexture {
                    Function Sample {
                    }
                }
                """.trimIndent()
            )
        }

        val text = """import "<caret>Texture.dsh";"""
        myFixture.configureByText("unresolved_import_removed_builtin_fallback.dsm", text)
        assertHasError("Cannot resolve import 'Texture.dsh'")
    }

    fun testUnresolvedImportPathUnsupportedExtensionQuickFixChangesToDsh() {
        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext_fix.dsm", text)
        val action = myFixture.filterAvailableIntentions("Change extension to")
            .firstOrNull { it.text.startsWith("Change extension to .dsh") }
            ?: error("Expected extension quick-fix for .dsh")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("""import "Scripts/Auto.dsh";"""))
    }

    fun testUnresolvedImportPathUnsupportedExtensionQuickFixOffersAllSupportedExtensions() {
        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext_all_fixes.dsm", text)
        val actions = myFixture.filterAvailableIntentions("Change extension to")
        assertTrue("Expected .dsh extension fix", actions.any { it.text.startsWith("Change extension to .dsh") })
        assertTrue("Expected .dsf extension fix", actions.any { it.text.startsWith("Change extension to .dsf") })
        assertTrue("Expected .dsm extension fix", actions.any { it.text.startsWith("Change extension to .dsm") })
    }

    fun testUnresolvedImportPathUnsupportedExtensionQuickFixPrioritizesResolvableExtension() {
        val projectBase = project.basePath ?: error("project base path is null")
        val targetPath = Paths.get(projectBase, "Scripts", "Auto.dsf")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(targetPath.parent.toString())
            val file = parent.findOrCreateChildData(this, targetPath.fileName.toString())
            VfsUtil.saveText(file, "ShaderFunction(Name=\"Functions/Auto\") { }")
        }

        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext_prefer_resolvable_fix.dsm", text)
        val actions = myFixture.filterAvailableIntentions("Change extension to")
        assertTrue("Expected at least one extension quick fix", actions.isNotEmpty())
        assertTrue("Expected resolvable .dsf quick-fix to be first", actions.first().text.startsWith("Change extension to .dsf"))
        assertTrue(
            "Expected first quick-fix to indicate existing resolution",
            actions.first().text.contains("(resolves existing file)")
        )
    }

    fun testUnresolvedImportPathUnsupportedExtensionQuickFixMarksPreferredDefault() {
        project.getService(DreamShaderProjectSettings::class.java).state.preferredImportExtension = "dsm"
        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext_preferred_default_fix.dsm", text)
        val actions = myFixture.filterAvailableIntentions("Change extension to")
        val preferred = actions.firstOrNull { it.text.startsWith("Change extension to .dsm") }
        assertTrue("Expected .dsm extension quick-fix", preferred != null)
        assertTrue(
            "Expected preferred default marker on .dsm quick-fix",
            preferred!!.text.contains("(preferred default)")
        )
    }

    fun testUnresolvedImportPathUnsupportedExtensionQuickFixAutoUpdatesPreferredExtensionWhenEnabled() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.preferredImportExtension = "dsh"
        settings.autoUpdatePreferredImportExtension = true

        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext_auto_update_enabled_fix.dsm", text)
        val action = myFixture.filterAvailableIntentions("Change extension to")
            .firstOrNull { it.text.startsWith("Change extension to .dsf") }
            ?: error("Expected extension quick-fix for .dsf")
        assertTrue(
            "Expected quick-fix to indicate preferred default will be updated",
            action.text.contains("(will update preferred default)")
        )
        myFixture.launchAction(action)

        assertEquals("dsf", settings.preferredImportExtension)
    }

    fun testUnresolvedImportPathUnsupportedExtensionQuickFixDoesNotAutoUpdatePreferredExtensionWhenDisabled() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.preferredImportExtension = "dsh"
        settings.autoUpdatePreferredImportExtension = false

        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext_auto_update_disabled_fix.dsm", text)
        val action = myFixture.filterAvailableIntentions("Change extension to")
            .firstOrNull { it.text.startsWith("Change extension to .dsf") }
            ?: error("Expected extension quick-fix for .dsf")
        myFixture.launchAction(action)

        assertEquals("dsh", settings.preferredImportExtension)
    }

    fun testUnresolvedImportPathUnsupportedExtensionQuickFixWillUpdateHintHiddenWhenAlreadyPreferred() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.preferredImportExtension = "dsf"
        settings.autoUpdatePreferredImportExtension = true

        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext_no_will_update_when_same_preferred_fix.dsm", text)
        val action = myFixture.filterAvailableIntentions("Change extension to")
            .firstOrNull { it.text.startsWith("Change extension to .dsf") }
            ?: error("Expected extension quick-fix for .dsf")

        assertTrue("Expected preferred default marker", action.text.contains("(preferred default)"))
        assertFalse(
            "Did not expect will-update marker when extension already preferred",
            action.text.contains("(will update preferred default)")
        )
    }

    fun testUnresolvedImportPathUnsupportedExtensionQuickFixHintOrderIsStable() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.preferredImportExtension = "dsh"
        settings.autoUpdatePreferredImportExtension = true

        val projectBase = project.basePath ?: error("project base path is null")
        val targetPath = Paths.get(projectBase, "Scripts", "Auto.dsf")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(targetPath.parent.toString())
            val file = parent.findOrCreateChildData(this, targetPath.fileName.toString())
            VfsUtil.saveText(file, "ShaderFunction(Name=\"Functions/Auto\") { }")
        }

        val text = """import "<caret>Scripts/Auto.usf";"""
        myFixture.configureByText("unresolved_import_unsupported_ext_hint_order_fix.dsm", text)
        val action = myFixture.filterAvailableIntentions("Change extension to")
            .firstOrNull { it.text.startsWith("Change extension to .dsf") }
            ?: error("Expected extension quick-fix for .dsf")

        val resolvesHint = "(resolves existing file)"
        val updateHint = "(will update preferred default)"
        val resolvesIndex = action.text.indexOf(resolvesHint)
        val updateIndex = action.text.indexOf(updateHint)
        assertTrue("Expected resolves-existing hint", resolvesIndex >= 0)
        assertTrue("Expected will-update hint", updateIndex >= 0)
        assertTrue("Expected resolves-existing hint before will-update hint", resolvesIndex < updateIndex)
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

    fun testDuplicateTopLevelDeclarationNameIsReported() {
        val text = """
            Function BuildNoise(in float X, out float OutValue) {
                OutValue = X;
            }

            Function <caret>BuildNoise(in float X, out float OutValue) {
                OutValue = X;
            }
        """.trimIndent()
        myFixture.configureByText("duplicate_top_level_declaration_name.dsh", text)
        assertHasError("Duplicate declaration name 'BuildNoise' in the same scope")
    }

    fun testDuplicateNamespaceChildDeclarationNameIsReported() {
        val text = """
            Namespace Tools {
                Function BuildNoise(in float X, out float OutValue) {
                    OutValue = X;
                }
                Function <caret>BuildNoise(in float X, out float OutValue) {
                    OutValue = X;
                }
            }
        """.trimIndent()
        myFixture.configureByText("duplicate_namespace_child_declaration_name.dsh", text)
        assertHasError("Duplicate declaration name 'BuildNoise' in the same scope")
    }

    fun testDuplicateTopLevelDeclarationNameQuickFixRenamesToUniqueName() {
        val text = """
            Function BuildNoise(in float X, out float OutValue) {
                OutValue = X;
            }

            Function <caret>BuildNoise(in float X, out float OutValue) {
                OutValue = X;
            }
        """.trimIndent()
        myFixture.configureByText("duplicate_top_level_declaration_name_fix.dsh", text)
        val action = myFixture.findSingleIntention("Rename declaration to 'BuildNoise2'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("Function BuildNoise2(in float X, out float OutValue)"))
    }

    fun testDuplicateNamespaceChildDeclarationNameQuickFixRenamesToUniqueName() {
        val text = """
            Namespace Tools {
                Function BuildNoise(in float X, out float OutValue) {
                    OutValue = X;
                }
                Function <caret>BuildNoise(in float X, out float OutValue) {
                    OutValue = X;
                }
            }
        """.trimIndent()
        myFixture.configureByText("duplicate_namespace_child_declaration_name_fix.dsh", text)
        val action = myFixture.findSingleIntention("Rename declaration to 'BuildNoise2'")
        myFixture.launchAction(action)
        assertTrue(myFixture.file.text.contains("Function BuildNoise2(in float X, out float OutValue)"))
    }

    fun testDuplicateNameAttributePathLeafIsReported() {
        val text = """
            ShaderFunction(Name="Functions/F_Blend") {
                Inputs { in float A; in float B; }
                Outputs { out float Result; }
                Graph { Result = A + B; }
            }

            ShaderFunction(Name="Functions/<caret>F_Blend") {
                Inputs { in float A; in float B; }
                Outputs { out float Result; }
                Graph { Result = A - B; }
            }
        """.trimIndent()
        myFixture.configureByText("duplicate_name_attribute_path_leaf.dsf", text)
        assertHasError("Duplicate declaration name 'F_Blend' in the same scope")
    }

    fun testDuplicateNameAttributePathLeafQuickFixKeepsPrefixAndRenamesLeaf() {
        val text = """
            ShaderFunction(Name="Functions/F_Blend") {
                Inputs { in float A; in float B; }
                Outputs { out float Result; }
                Graph { Result = A + B; }
            }

            ShaderFunction(Name="Functions/<caret>F_Blend") {
                Inputs { in float A; in float B; }
                Outputs { out float Result; }
                Graph { Result = A - B; }
            }
        """.trimIndent()
        myFixture.configureByText("duplicate_name_attribute_path_leaf_fix.dsf", text)
        val action = myFixture.findSingleIntention("Rename declaration to 'F_Blend2'")
        myFixture.launchAction(action)
        assertTrue(Regex("""Name\s*=\s*"Functions/F_Blend2"""").containsMatchIn(myFixture.file.text))
    }

    fun testSameNameAttributeLeafInDifferentNamespacesIsAllowed() {
        val text = """
            Namespace PackA {
                ShaderFunction(Name="Functions/F_Blend") {
                    Inputs { in float A; in float B; }
                    Outputs { out float Result; }
                    Graph { Result = A + B; }
                }
            }

            Namespace PackB {
                ShaderFunction(Name="Functions/F_Blend") {
                    Inputs { in float A; in float B; }
                    Outputs { out float Result; }
                    Graph { Result = A - B; }
                }
            }
        """.trimIndent()
        myFixture.configureByText("same_name_attribute_leaf_in_different_namespaces_allowed.dsh", text)
        assertNoError("Duplicate declaration name 'F_Blend' in the same scope")
    }

    fun testSameNameInDifferentNamespacesIsAllowed() {
        val text = """
            Namespace A {
                Function BuildNoise(in float X, out float OutValue) {
                    OutValue = X;
                }
            }

            Namespace B {
                Function BuildNoise(in float X, out float OutValue) {
                    OutValue = X;
                }
            }
        """.trimIndent()
        myFixture.configureByText("same_name_in_different_namespaces_allowed.dsh", text)
        assertNoError("Duplicate declaration name 'BuildNoise' in the same scope")
    }

    fun testFunctionBodyDisallowsForLoopStatement() {
        val text = """
            Function BuildNoise(in float X, out float OutValue) {
                for (int i = 0; i < 4; i = i + 1) {
                    OutValue = X;
                }
            }
        """.trimIndent()
        myFixture.configureByText("function_for_loop_statement.dsh", text)
        assertHasError("Graph section does not support loop statement 'for'")
    }

    fun testGraphFunctionBodyDisallowsSwitchStatement() {
        val text = """
            GraphFunction BuildNoise(in float X, out float OutValue) {
                switch (Mode) {
                    default:
                        OutValue = X;
                }
            }
        """.trimIndent()
        myFixture.configureByText("graphfunction_switch_statement.dsh", text)
        assertHasError("Graph section does not support switch statement 'switch'")
        assertHasError("Graph section does not support switch statement 'default'")
    }

    fun testFunctionBodyDisallowsBreakContinueAndReturnStatements() {
        val text = """
            Function BuildNoise(in float X, out float OutValue) {
                if (X > 0) {
                    break;
                    continue;
                    return;
                }
            }
        """.trimIndent()
        myFixture.configureByText("function_flow_statements.dsh", text)
        assertHasError("Graph section does not support control statement 'break'")
        assertHasError("Graph section does not support control statement 'continue'")
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

    private fun assertHasWarning(message: String) {
        val warnings = myFixture.doHighlighting(HighlightSeverity.WARNING)
        assertTrue(
            "Expected warning '$message', actual: ${warnings.map { it.description }}",
            warnings.any { it.description == message }
        )
    }

    private fun assertNoWarning(message: String) {
        val warnings = myFixture.doHighlighting(HighlightSeverity.WARNING)
        assertTrue(
            "Expected no warning '$message', actual: ${warnings.map { it.description }}",
            warnings.none { it.description == message }
        )
    }
}
