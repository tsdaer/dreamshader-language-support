package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import java.util.*

/**
 * $name 信息模型。
 */
internal data class DreamShaderSettingInfo(
    val key: String,
    val description: String,
    val commonValues: List<String> = emptyList()
)

/**
 * $name 信息模型。
 */
internal data class DreamShaderBuiltinInfo(
    val name: String,
    val signature: String,
    val description: String
)

/**
 * $name 单例对象。
 */
internal object DreamShaderDocumentationData {
    val declarationKeywordDescriptions: Map<String, String> = mapOf(
        "shader" to DreamShaderBundle.message("docs.declaration.shader"),
        "shaderfunction" to DreamShaderBundle.message("docs.declaration.shaderfunction"),
        "shaderlayer" to DreamShaderBundle.message("docs.declaration.shaderlayer"),
        "shaderlayerblend" to DreamShaderBundle.message("docs.declaration.shaderlayerblend"),
        "virtualfunction" to DreamShaderBundle.message("docs.declaration.virtualfunction"),
        "function" to DreamShaderBundle.message("docs.declaration.function"),
        "graphfunction" to DreamShaderBundle.message("docs.declaration.graphfunction"),
        "namespace" to DreamShaderBundle.message("docs.declaration.namespace"),
        "import" to DreamShaderBundle.message("docs.declaration.import")
    )

    val settings: Map<String, DreamShaderSettingInfo> = listOf(
        DreamShaderSettingInfo(
            key = "Domain",
            description = DreamShaderBundle.message("docs.settings.domain.description"),
            commonValues = listOf("Surface", "DeferredDecal", "LightFunction", "PostProcess", "UserInterface")
        ),
        DreamShaderSettingInfo(
            key = "MaterialDomain",
            description = DreamShaderBundle.message("docs.settings.materialdomain.description"),
            commonValues = listOf("Surface", "DeferredDecal", "LightFunction", "PostProcess", "UserInterface")
        ),
        DreamShaderSettingInfo(
            key = "ShadingModel",
            description = DreamShaderBundle.message("docs.settings.shadingmodel.description"),
            commonValues = listOf("DefaultLit", "Unlit", "ClearCoat", "Subsurface", "Hair")
        ),
        DreamShaderSettingInfo(
            key = "BlendMode",
            description = DreamShaderBundle.message("docs.settings.blendmode.description"),
            commonValues = listOf("Opaque", "Masked", "Translucent", "Additive", "Modulate")
        ),
        DreamShaderSettingInfo(
            key = "RenderType",
            description = DreamShaderBundle.message("docs.settings.rendertype.description"),
            commonValues = listOf("Opaque", "Masked", "Translucent", "Additive", "Modulate")
        ),
        DreamShaderSettingInfo(
            key = "LightingMode",
            description = DreamShaderBundle.message("docs.settings.lightingmode.description")
        ),
        DreamShaderSettingInfo(
            key = "TwoSided",
            description = DreamShaderBundle.message("docs.settings.twosided.description"),
            commonValues = listOf("true", "false")
        ),
        DreamShaderSettingInfo(
            key = "Wireframe",
            description = DreamShaderBundle.message("docs.settings.wireframe.description"),
            commonValues = listOf("true", "false")
        )
    ).associateBy { it.key.lowercase(Locale.ROOT) }

    val ueBuiltins: Map<String, DreamShaderBuiltinInfo> = listOf(
        DreamShaderBuiltinInfo(
            name = "TexCoord",
            signature = "UE.TexCoord(Index=0)",
            description = DreamShaderBundle.message("docs.ueBuiltin.texcoord.description")
        ),
        DreamShaderBuiltinInfo(
            name = "Time",
            signature = "UE.Time(Period=4.0)",
            description = DreamShaderBundle.message("docs.ueBuiltin.time.description")
        ),
        DreamShaderBuiltinInfo(
            name = "Panner",
            signature = "UE.Panner(Coordinate=UV, Time=UE.Time(), Speed=float2(0.1, 0.0))",
            description = DreamShaderBundle.message("docs.ueBuiltin.panner.description")
        ),
        DreamShaderBuiltinInfo(
            name = "WorldPosition",
            signature = "UE.WorldPosition()",
            description = DreamShaderBundle.message("docs.ueBuiltin.worldposition.description")
        ),
        DreamShaderBuiltinInfo(
            name = "Expression",
            signature = "UE.Expression(Class=\"Sine\", OutputType=\"float1\", Input=UE.Time())",
            description = DreamShaderBundle.message("docs.ueBuiltin.expression.description")
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
