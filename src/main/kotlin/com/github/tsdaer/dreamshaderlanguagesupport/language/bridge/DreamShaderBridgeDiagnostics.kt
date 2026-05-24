package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

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
