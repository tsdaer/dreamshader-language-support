package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

/**
 * 从包索引源归一化后的包条目。
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
 * 包条目的安装源解析结果。
 */
internal data class DreamShaderPackageInstallSource(
    val sourcePathOrUrl: String,
    val resolvedFromLocalPath: Boolean
)

/**
 * 单个索引源的加载/解析错误。
 *
 * 该错误是非致命的，不会阻断其它源继续加载。
 */
internal data class DreamShaderPackageIndexLoadError(
    val source: String,
    val message: String
)

/**
 * 多索引源聚合后的加载结果。
 */
internal data class DreamShaderPackageIndexLoadResult(
    val entries: List<DreamShaderPackageIndexEntry>,
    val errors: List<DreamShaderPackageIndexLoadError>
)
