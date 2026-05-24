package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
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
    AttributesDescriptor("DreamShader Bad Character", DreamShaderTextAttributes.BAD_CHARACTER)
)

class DreamShaderColorSettingsPage : ColorSettingsPage {
    override fun getDisplayName(): String = "DreamShaderLang"

    override fun getIcon(): Icon? = DreamShaderIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = DreamShaderSyntaxHighlighter()

    override fun getDemoText(): String = """
        import "Common/Surface.dsh";
        
        Shader MySurface {
            Settings {
                Domain = Surface;
                BlendMode = Opaque;
            }
        
            Inputs {
                float2 UV;
                float3 Albedo;
            }
        
            Outputs {
                Base.BaseColor = float4(Albedo, 1.0f);
            }

            Graph {
                // simple graph body
                float n = 1.0f;
                if (n > 0.5f) {
                    float3 toWorld = Common::ApplyTint(Albedo);
                    float3 glow = UE.TexCoord(Index=0).xxx;
                    Base.EmissiveColor = glow + toWorld;
                }
            }
        }
    """.trimIndent()

    override fun getAdditionalHighlightingTagToDescriptorMap(): Map<String, TextAttributesKey>? = null

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = COLOR_ATTRIBUTES

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY
}
