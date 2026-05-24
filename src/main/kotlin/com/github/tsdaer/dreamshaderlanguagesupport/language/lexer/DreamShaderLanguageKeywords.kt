package com.github.tsdaer.dreamshaderlanguagesupport.language.lexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
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
        "graphfunction"
    )

    val FUNCTION_LIKE_DECLARATION_KEYWORDS = setOf(
        "function",
        "graphfunction",
        "shaderfunction",
        "virtualfunction"
    )
}
