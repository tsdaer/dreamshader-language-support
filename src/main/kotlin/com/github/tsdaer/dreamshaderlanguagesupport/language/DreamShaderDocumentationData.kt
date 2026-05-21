package com.github.tsdaer.dreamshaderlanguagesupport.language

import java.util.Locale

internal data class DreamShaderSettingInfo(
    val key: String,
    val description: String,
    val commonValues: List<String> = emptyList()
)

internal data class DreamShaderBuiltinInfo(
    val name: String,
    val signature: String,
    val description: String
)

internal object DreamShaderDocumentationData {
    val declarationKeywordDescriptions: Map<String, String> = mapOf(
        "shader" to "Defines a material shader entry declaration.",
        "shaderfunction" to "Defines a reusable shader function declaration.",
        "shaderlayer" to "Defines a material layer declaration.",
        "shaderlayerblend" to "Defines a layer blend declaration.",
        "virtualfunction" to "Defines a virtual function declaration that can be overridden.",
        "function" to "Defines a local function declaration.",
        "graphfunction" to "Defines a graph-oriented function declaration.",
        "namespace" to "Defines a symbol namespace scope.",
        "import" to "Imports DreamShader declarations from another file."
    )

    val settings: Map<String, DreamShaderSettingInfo> = listOf(
        DreamShaderSettingInfo(
            key = "Domain",
            description = "Selects the material domain used for this shader.",
            commonValues = listOf("Surface", "DeferredDecal", "LightFunction", "PostProcess", "UserInterface")
        ),
        DreamShaderSettingInfo(
            key = "MaterialDomain",
            description = "Alias of Domain for material domain selection.",
            commonValues = listOf("Surface", "DeferredDecal", "LightFunction", "PostProcess", "UserInterface")
        ),
        DreamShaderSettingInfo(
            key = "ShadingModel",
            description = "Controls the lighting model used to shade this material.",
            commonValues = listOf("DefaultLit", "Unlit", "ClearCoat", "Subsurface", "Hair")
        ),
        DreamShaderSettingInfo(
            key = "BlendMode",
            description = "Controls how the material is blended with the scene.",
            commonValues = listOf("Opaque", "Masked", "Translucent", "Additive", "Modulate")
        ),
        DreamShaderSettingInfo(
            key = "RenderType",
            description = "Legacy alias used for blend/render mode classification.",
            commonValues = listOf("Opaque", "Masked", "Translucent", "Additive", "Modulate")
        ),
        DreamShaderSettingInfo(
            key = "LightingMode",
            description = "Configures lighting behavior for translucent shading paths."
        ),
        DreamShaderSettingInfo(
            key = "TwoSided",
            description = "Enables two-sided rendering for the material.",
            commonValues = listOf("true", "false")
        ),
        DreamShaderSettingInfo(
            key = "Wireframe",
            description = "Renders the material using wireframe mode.",
            commonValues = listOf("true", "false")
        )
    ).associateBy { it.key.lowercase(Locale.ROOT) }

    val ueBuiltins: Map<String, DreamShaderBuiltinInfo> = listOf(
        DreamShaderBuiltinInfo(
            name = "TexCoord",
            signature = "UE.TexCoord(Index=0)",
            description = "Samples mesh UV coordinates for the given UV channel index."
        ),
        DreamShaderBuiltinInfo(
            name = "Time",
            signature = "UE.Time(Period=4.0)",
            description = "Provides a time-based value useful for animation effects."
        ),
        DreamShaderBuiltinInfo(
            name = "Panner",
            signature = "UE.Panner(Coordinate=UV, Time=UE.Time(), Speed=float2(0.1, 0.0))",
            description = "Offsets UV coordinates over time using a speed vector."
        ),
        DreamShaderBuiltinInfo(
            name = "WorldPosition",
            signature = "UE.WorldPosition()",
            description = "Returns current pixel world position."
        ),
        DreamShaderBuiltinInfo(
            name = "Expression",
            signature = "UE.Expression(Class=\"Sine\", OutputType=\"float1\", Input=UE.Time())",
            description = "Creates a raw Unreal material expression node by class name."
        )
    ).associateBy { it.name.lowercase(Locale.ROOT) }

    fun valueOwners(valueLiteral: String): List<String> {
        if (valueLiteral.isBlank()) return emptyList()
        val lowered = valueLiteral.lowercase(Locale.ROOT)
        return settings.values
            .filter { info -> info.commonValues.any { it.equals(valueLiteral, ignoreCase = true) } }
            .map { it.key }
            .sortedBy { it.lowercase(Locale.ROOT) }
            .ifEmpty {
                if (lowered == "true" || lowered == "false") {
                    listOf("TwoSided", "Wireframe")
                } else {
                    emptyList()
                }
            }
    }
}
