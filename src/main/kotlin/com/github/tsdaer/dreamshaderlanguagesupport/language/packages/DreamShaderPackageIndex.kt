package com.github.tsdaer.dreamshaderlanguagesupport.language.packages
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

/**
 * Normalized package record loaded from a package store index source.
 */
internal data class DreamShaderPackageIndexEntry(
    val name: String,
    val displayName: String?,
    val description: String?,
    val version: String? = null,
    val repository: String,
    val source: String,
    val path: String? = null,
    val tags: List<String> = emptyList()
)

/**
 * Resolved install source for a package entry.
 */
internal data class DreamShaderPackageInstallSource(
    val sourcePathOrUrl: String,
    val resolvedFromLocalPath: Boolean
)

/**
 * Per-source load/parse error, kept non-fatal so other sources can still load.
 */
internal data class DreamShaderPackageIndexLoadError(
    val source: String,
    val message: String
)

/**
 * Aggregated package index load result across all configured sources.
 */
internal data class DreamShaderPackageIndexLoadResult(
    val entries: List<DreamShaderPackageIndexEntry>,
    val errors: List<DreamShaderPackageIndexLoadError>
)
