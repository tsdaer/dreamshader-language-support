package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

private val COLOR_ATTRIBUTES = arrayOf(
    AttributesDescriptor("DreamShader Keyword", DreamShaderTextAttributes.KEYWORD),
    AttributesDescriptor("DreamShader Section", DreamShaderTextAttributes.SECTION),
    AttributesDescriptor("DreamShader Declaration Name", DreamShaderTextAttributes.DECLARATION_NAME),
    AttributesDescriptor("DreamShader Callable Reference", DreamShaderTextAttributes.CALLABLE_REFERENCE),
    AttributesDescriptor("DreamShader Builtin Namespace", DreamShaderTextAttributes.BUILTIN_NAMESPACE),
    AttributesDescriptor("DreamShader Namespace Qualifier", DreamShaderTextAttributes.NAMESPACE_QUALIFIER),
    AttributesDescriptor("DreamShader Local Symbol", DreamShaderTextAttributes.LOCAL_SYMBOL),
    AttributesDescriptor("DreamShader Local Symbol Declaration", DreamShaderTextAttributes.LOCAL_SYMBOL_DECLARATION),
    AttributesDescriptor("DreamShader Material Output Member", DreamShaderTextAttributes.MATERIAL_OUTPUT_MEMBER),
    AttributesDescriptor("DreamShader Type", DreamShaderTextAttributes.TYPE),
    AttributesDescriptor("DreamShader String", DreamShaderTextAttributes.STRING),
    AttributesDescriptor("DreamShader Number", DreamShaderTextAttributes.NUMBER),
    AttributesDescriptor("DreamShader Comment", DreamShaderTextAttributes.COMMENT),
    AttributesDescriptor("DreamShader Braces", DreamShaderTextAttributes.BRACES),
    AttributesDescriptor("DreamShader Operator", DreamShaderTextAttributes.OPERATOR),
    AttributesDescriptor("DreamShader Bad Character", DreamShaderTextAttributes.BAD_CHARACTER),
    AttributesDescriptor("DreamShader Control Flow", DreamShaderTextAttributes.CONTROL_FLOW),
    AttributesDescriptor("DreamShader Qualifier", DreamShaderTextAttributes.QUALIFIER),
    AttributesDescriptor("DreamShader Constant", DreamShaderTextAttributes.CONSTANT),
    AttributesDescriptor("DreamShader Import", DreamShaderTextAttributes.IMPORT),
    AttributesDescriptor("DreamShader Settings Key", DreamShaderTextAttributes.SETTINGS_KEY),
    AttributesDescriptor("DreamShader Builtin Function", DreamShaderTextAttributes.BUILTIN_FUNCTION)
)

private val DEMO_TAG_MAP: Map<String, TextAttributesKey> = mapOf(
    "import" to DreamShaderTextAttributes.IMPORT,
    "keyword" to DreamShaderTextAttributes.KEYWORD,
    "section" to DreamShaderTextAttributes.SECTION,
    "decl_name" to DreamShaderTextAttributes.DECLARATION_NAME,
    "call_ref" to DreamShaderTextAttributes.CALLABLE_REFERENCE,
    "ue_ns" to DreamShaderTextAttributes.BUILTIN_NAMESPACE,
    "ns_qual" to DreamShaderTextAttributes.NAMESPACE_QUALIFIER,
    "local" to DreamShaderTextAttributes.LOCAL_SYMBOL,
    "ls_decl" to DreamShaderTextAttributes.LOCAL_SYMBOL_DECLARATION,
    "pin" to DreamShaderTextAttributes.MATERIAL_OUTPUT_MEMBER,
    "flow" to DreamShaderTextAttributes.CONTROL_FLOW,
    "qualifier" to DreamShaderTextAttributes.QUALIFIER,
    "const" to DreamShaderTextAttributes.CONSTANT,
    "setting" to DreamShaderTextAttributes.SETTINGS_KEY,
    "bfunc" to DreamShaderTextAttributes.BUILTIN_FUNCTION
)

/**
 * Implementation of DreamShaderColorSettingsPage.
 */
class DreamShaderColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "DreamShaderLang"

    override fun getIcon(): Icon? = DreamShaderIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = DreamShaderSyntaxHighlighter()

    override fun getDemoText(): String = """
        <import>import</import> "Common/Shared.dsh";

        <keyword>Shader</keyword> <decl_name>MyMaterial</decl_name> {
            <section>Settings</section> {
                <setting>Domain</setting> = Surface;
                <setting>BlendMode</setting> = Opaque;
            }

            <section>Inputs</section> {
                float2 <ls_decl>UV</ls_decl>;
                float3 <ls_decl>Albedo</ls_decl>;
                float <ls_decl>Roughness</ls_decl> = 0.5f;
            }

            <section>Outputs</section> {
                Base.<pin>BaseColor</pin> = float4(<local>Albedo</local>, 1.0f);
                Base.<pin>Roughness</pin> = <local>Roughness</local>;
            }

            <section>Graph</section> {
                // Evaluate texture coordinates
                float2 <ls_decl>uv</ls_decl> = <ue_ns>UE</ue_ns>.<bfunc>TexCoord</bfunc>(Index=0);

                <flow>if</flow> (<local>Roughness</local> > 0.5f) {
                    float3 <ls_decl>tinted</ls_decl> = <call_ref>ApplyTint</call_ref>(<local>Albedo</local>, 0.8f);
                    float3 <ls_decl>glow</ls_decl> = <local>uv</local>.xxx * 2.0f + <local>tinted</local>;
                    Base.<pin>EmissiveColor</pin> = <local>glow</local>;
                } <flow>else</flow> {
                    Base.<pin>EmissiveColor</pin> = float3(0, 0, 0);
                }
            }
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = DEMO_TAG_MAP

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = COLOR_ATTRIBUTES

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}
