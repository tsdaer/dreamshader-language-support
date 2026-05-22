package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

/**
 * Single Bridge diagnostic item mapped to source location.
 */
internal data class DreamShaderBridgeDiagnostic(
    val sourcePath: String,
    val line: Int,
    val column: Int,
    val severity: String,
    val message: String
)

/**
 * Immutable snapshot produced by the diagnostics repository on each refresh.
 */
internal data class DreamShaderBridgeDiagnosticsSnapshot(
    val diagnostics: List<DreamShaderBridgeDiagnostic>,
    val loadedFromPath: String?
)
