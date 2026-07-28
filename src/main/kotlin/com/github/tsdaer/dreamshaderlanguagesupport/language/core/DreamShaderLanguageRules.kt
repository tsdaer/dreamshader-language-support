package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import java.util.Locale

/**
 * Shared DreamShader language rules used by diagnostics, completion, and docs-facing editor intelligence.
 */
internal object DreamShaderLanguageRules {
    val displayDeclarationKeywords = mapOf(
        "shader" to "Shader",
        "shaderfunction" to "ShaderFunction",
        "shaderlayer" to "ShaderLayer",
        "shaderlayerblend" to "ShaderLayerBlend",
        "virtualfunction" to "VirtualFunction",
        "function" to "Function",
        "graphfunction" to "GraphFunction",
        "template" to "Template",
        "namespace" to "Namespace",
        "group" to "Group",
        "propgroup" to "PropGroup"
    )

    val displaySectionNames = mapOf(
        "properties" to "Properties",
        "inputs" to "Inputs",
        "outputs" to "Outputs",
        "results" to "Results",
        "settings" to "Settings",
        "options" to "Options",
        "graph" to "Graph",
        "layout" to "Layout"
    )

    val sectionNameAliases = mapOf(
        "results" to "outputs"
    )

    val declarationsAllowResultsAlias = setOf(
        "shaderfunction",
        "virtualfunction"
    )

    val declarationAllowedSections = mapOf(
        "shader" to setOf("properties", "outputs", "settings", "graph", "layout"),
        "shaderfunction" to setOf("properties", "inputs", "outputs", "settings", "graph", "layout"),
        "shaderlayer" to setOf("properties", "inputs", "outputs", "settings", "graph", "layout"),
        "shaderlayerblend" to setOf("properties", "inputs", "outputs", "settings", "graph", "layout"),
        "virtualfunction" to setOf("properties", "inputs", "outputs", "settings", "options"),
        "function" to emptySet(),
        "graphfunction" to emptySet(),
        "template" to setOf("properties", "inputs", "outputs", "settings", "graph", "layout")
    )

    val declarationRequiredSections = mapOf(
        "shader" to setOf("graph"),
        "shaderfunction" to setOf("graph"),
        "shaderlayer" to setOf("outputs"),
        "shaderlayerblend" to setOf("inputs", "outputs")
    )

    val dshDisallowedTopLevelDeclarations = setOf(
        "shader",
        "shaderfunction",
        "shaderlayer",
        "shaderlayerblend",
        "template"
    )

    val assetDeclarationKeywords = setOf(
        "shader",
        "shaderfunction",
        "shaderlayer",
        "shaderlayerblend",
        "template"
    )

    val dsfAllowedTopLevelDeclarations = setOf(
        "shaderfunction",
        "shaderlayer",
        "shaderlayerblend",
        "virtualfunction",
        "function",
        "graphfunction",
        "template"
    )

    val dsmDisallowedTopLevelDeclarations = setOf(
        "shaderfunction",
        "shaderlayer",
        "shaderlayerblend"
    )

    fun displayDeclarationKeyword(keyword: String): String {
        return displayDeclarationKeywords[keyword] ?: keyword.replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

    fun displaySectionName(sectionName: String): String {
        return displaySectionNames[sectionName] ?: sectionName.replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

    fun canonicalSectionName(sectionName: String?): String? {
        if (sectionName == null) return null
        return sectionNameAliases[sectionName] ?: sectionName
    }

    fun canonicalSectionNameForDeclaration(declarationKeyword: String, sectionName: String?): String? {
        val name = canonicalSectionName(sectionName) ?: return null
        if (sectionName == "results" && declarationKeyword !in declarationsAllowResultsAlias) {
            return sectionName
        }
        return name
    }

    fun completionSectionsForDeclaration(declarationKeyword: String?): List<String> {
        val keyword = declarationKeyword?.lowercase(Locale.ROOT)
        if (keyword == null) return displaySectionNames.values.toList()
        val allowed = declarationAllowedSections[keyword] ?: return displaySectionNames.values.toList()
        return displaySectionNames
            .filter { (canonicalName, _) ->
                canonicalName in allowed ||
                    (canonicalName == "results" && "outputs" in allowed && keyword in declarationsAllowResultsAlias)
            }
            .values
            .toList()
    }
}
