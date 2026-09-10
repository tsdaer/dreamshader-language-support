package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

/**
 * 单条 Bridge 诊断项，映射到源码位置。
 */
internal data class DreamShaderBridgeDiagnostic(
    val sourcePath: String,
    val line: Int,
    val column: Int,
    val severity: String,
    val message: String,
    val code: String? = null,
    val stage: String? = null,
    val detail: String? = null,
    val assetPath: String? = null,
    val source: String? = null,
    val shaderPlatform: String? = null,
    val qualityLevel: String? = null
)

/**
 * 诊断仓库每次刷新后产出的不可变快照。
 */
internal data class DreamShaderBridgeDiagnosticsSnapshot(
    val diagnostics: List<DreamShaderBridgeDiagnostic>,
    val loadedFromPath: String?
)
