package com.github.tsdaer.dreamshaderlanguagesupport.language.lexer

/**
 * Singleton for DreamShaderLanguageKeywords.
 */
internal object DreamShaderLanguageKeywords {
    val TOP_LEVEL_KEYWORDS = listOf(
        "import",
        "Shader",
        "ShaderFunction",
        "ShaderLayer",
        "ShaderLayerBlend",
        "VirtualFunction",
        "Function",
        "GraphFunction",
        "Namespace"
    )

    val SECTION_KEYWORDS = listOf(
        "Properties",
        "Inputs",
        "Outputs",
        "Results",
        "Settings",
        "Options",
        "Graph"
    )

    val DECLARATION_KEYWORDS = setOf(
        "shader",
        "shaderfunction",
        "shaderlayer",
        "shaderlayerblend",
        "virtualfunction",
        "function",
        "graphfunction",
        "namespace"
    )

    val FUNCTION_LIKE_DECLARATION_KEYWORDS = setOf(
        "function",
        "graphfunction",
        "shaderfunction",
        "virtualfunction"
    )
}
