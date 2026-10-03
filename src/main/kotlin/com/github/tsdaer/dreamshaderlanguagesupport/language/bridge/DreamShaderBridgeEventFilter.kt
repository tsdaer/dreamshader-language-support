package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

/**
 * `DreamShaderBridgeFileWatcher` 的可测内核（B4）：判断一个 VFS 路径是否属于会触发
 * Bridge 刷新的文件。纯字符串分类，便于在单测中直接验证（VFileEvent 依赖平台桩）。
 */
internal object DreamShaderBridgeEventFilter {

    const val BRIDGE_RELATIVE_DIR: String = "Saved/DreamShader/Bridge"
    const val PREVIEW_SUBDIR: String = "Preview"

    val BRIDGE_FILE_NAMES: Set<String> = setOf(
        "diagnostics.json",
        "settings.json",
        "material-expressions.json",
        "substrate-builtins.json",
        "preview.json",
        "bridge.db"
    )

    fun isBridgeFileEvent(path: String): Boolean {
        val normalized = path.replace('\\', '/')
        if (!normalized.contains("/$BRIDGE_RELATIVE_DIR/")) return false
        val fileName = normalized.substringAfterLast('/')
        if (normalized.contains("/$BRIDGE_RELATIVE_DIR/$PREVIEW_SUBDIR/") &&
            fileName.endsWith(".png", ignoreCase = true)
        ) {
            return true
        }
        return fileName in BRIDGE_FILE_NAMES
    }
}
