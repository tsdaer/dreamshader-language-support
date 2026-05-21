package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

internal data class DreamShaderBridgeDiagnostic(
    val sourcePath: String,
    val line: Int,
    val column: Int,
    val severity: String,
    val message: String
)

internal data class DreamShaderBridgeDiagnosticsSnapshot(
    val diagnostics: List<DreamShaderBridgeDiagnostic>,
    val loadedFromPath: String?
)
