package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.intellij.openapi.util.TextRange

data class DreamShaderSymbol(
    val name: String,
    val kind: DreamShaderSymbolKind,
    val range: TextRange,
    val children: List<DreamShaderSymbol> = emptyList()
)
