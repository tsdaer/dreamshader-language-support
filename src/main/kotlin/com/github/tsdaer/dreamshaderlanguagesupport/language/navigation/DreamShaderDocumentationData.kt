package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import java.util.*

/**
 * Data model for DreamShaderSettingInfo.
 */
internal data class DreamShaderSettingInfo(
    val key: String,
    val description: String,
    val commonValues: List<String> = emptyList()
)

/**
 * Singleton for DreamShaderDocumentationData.
 */
internal object DreamShaderDocumentationData {
    private val data: Map<String, Any> = mapOf(
        "declaration.shader.description" to DreamShaderBundle.message("docs.declaration.shader"),
        "declaration.shaderfunction.description" to DreamShaderBundle.message("docs.declaration.shaderfunction"),
        "declaration.shaderlayer.description" to DreamShaderBundle.message("docs.declaration.shaderlayer"),
        "declaration.shaderlayerblend.description" to DreamShaderBundle.message("docs.declaration.shaderlayerblend"),
        "declaration.virtualfunction.description" to DreamShaderBundle.message("docs.declaration.virtualfunction"),
        "declaration.function.description" to DreamShaderBundle.message("docs.declaration.function"),
        "declaration.graphfunction.description" to DreamShaderBundle.message("docs.declaration.graphfunction"),
        "declaration.namespace.description" to DreamShaderBundle.message("docs.declaration.namespace"),
        "declaration.import.description" to DreamShaderBundle.message("docs.declaration.import"),

        "section.properties.description" to DreamShaderBundle.message("docs.section.properties"),
        "section.inputs.description" to DreamShaderBundle.message("docs.section.inputs"),
        "section.outputs.description" to DreamShaderBundle.message("docs.section.outputs"),
        "section.results.description" to DreamShaderBundle.message("docs.section.results"),
        "section.settings.description" to DreamShaderBundle.message("docs.section.settings"),
        "section.options.description" to DreamShaderBundle.message("docs.section.options"),
        "section.graph.description" to DreamShaderBundle.message("docs.section.graph"),

        "settings.domain.key" to "Domain",
        "settings.domain.description" to DreamShaderBundle.message("docs.settings.domain.description"),
        "settings.domain.commonValues" to listOf("Surface", "DeferredDecal", "LightFunction", "PostProcess", "UserInterface"),
        "settings.materialdomain.key" to "MaterialDomain",
        "settings.materialdomain.description" to DreamShaderBundle.message("docs.settings.materialdomain.description"),
        "settings.materialdomain.commonValues" to listOf("Surface", "DeferredDecal", "LightFunction", "PostProcess", "UserInterface"),
        "settings.shadingmodel.key" to "ShadingModel",
        "settings.shadingmodel.description" to DreamShaderBundle.message("docs.settings.shadingmodel.description"),
        "settings.shadingmodel.commonValues" to listOf("DefaultLit", "Unlit", "ClearCoat", "Subsurface", "Hair"),
        "settings.blendmode.key" to "BlendMode",
        "settings.blendmode.description" to DreamShaderBundle.message("docs.settings.blendmode.description"),
        "settings.blendmode.commonValues" to listOf("Opaque", "Masked", "Translucent", "Additive", "Modulate"),
        "settings.rendertype.key" to "RenderType",
        "settings.rendertype.description" to DreamShaderBundle.message("docs.settings.rendertype.description"),
        "settings.rendertype.commonValues" to listOf("Opaque", "Masked", "Translucent", "Additive", "Modulate"),
        "settings.lightingmode.key" to "LightingMode",
        "settings.lightingmode.description" to DreamShaderBundle.message("docs.settings.lightingmode.description"),
        "settings.twosided.key" to "TwoSided",
        "settings.twosided.description" to DreamShaderBundle.message("docs.settings.twosided.description"),
        "settings.twosided.commonValues" to listOf("true", "false"),
        "settings.wireframe.key" to "Wireframe",
        "settings.wireframe.description" to DreamShaderBundle.message("docs.settings.wireframe.description"),
        "settings.wireframe.commonValues" to listOf("true", "false")
    )

    fun declarationDescription(keyword: String): String? =
        readString("declaration.${keyword.lowercase(Locale.ROOT)}.description")

    fun sectionDescription(sectionName: String): String? =
        readString("section.${sectionName.lowercase(Locale.ROOT)}.description")

    fun settingInfo(token: String): DreamShaderSettingInfo? {
        val id = settingsByKey[token.lowercase(Locale.ROOT)] ?: return null
        return readSettingInfo(id)
    }

    private val settingsByKey: Map<String, String> by lazy {
        settingIds().mapNotNull { id ->
            val key = readString("settings.$id.key") ?: return@mapNotNull null
            key.lowercase(Locale.ROOT) to id
        }.toMap()
    }

    private fun readSettingInfo(id: String): DreamShaderSettingInfo? {
        val key = readString("settings.$id.key") ?: return null
        val description = readString("settings.$id.description") ?: return null
        val commonValues = readStringList("settings.$id.commonValues")
        return DreamShaderSettingInfo(key = key, description = description, commonValues = commonValues)
    }

    private fun settingIds(): Set<String> = collectIds("settings")

    private fun collectIds(prefix: String): Set<String> {
        val rootPrefix = "$prefix."
        return data.keys.asSequence()
            .filter { it.startsWith(rootPrefix) }
            .map { it.removePrefix(rootPrefix).substringBefore('.') }
            .toSet()
    }

    private fun readString(path: String): String? = data[path] as? String

    private fun readStringList(path: String): List<String> {
        val value = data[path] as? List<*> ?: return emptyList()
        return value.mapNotNull { it as? String }
    }

    fun valueOwners(valueLiteral: String): List<String> {
        if (valueLiteral.isBlank()) return emptyList()
        val lowered = valueLiteral.lowercase(Locale.ROOT)
        val settings = settingIds().mapNotNull { readSettingInfo(it) }
        return settings
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

    /**
     * Built-in documentation entries that can be overridden from settings.
     * Keys are returned in their canonical bundle-data form.
     */
    fun builtinOverrideEntries(): Map<String, String> {
        return data.entries.asSequence()
            .mapNotNull { (key, value) ->
                val text = value as? String ?: return@mapNotNull null
                if (!key.endsWith(".description")) return@mapNotNull null
                if (!key.startsWith("declaration.") &&
                    !key.startsWith("settings.")
                ) {
                    return@mapNotNull null
                }
                key to text
            }
            .sortedBy { it.first.lowercase(Locale.ROOT) }
            .toMap(linkedMapOf())
    }
}
