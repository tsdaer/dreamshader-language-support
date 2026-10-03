package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import com.intellij.openapi.vfs.VirtualFile

/**
 * 派生（生成物）源码路径识别（A9）。
 *
 * 上游把反编译输出写到 `<SourceDirectory>/Decompiled/…`（`Docs/tools/decompiler.md`），
 * 引擎的中间产物在 `Intermediate/DreamShader/`。这些目录里的 `.dss`/`.dsi` 是生成物：
 * 不参与「生成物缺失」类诊断高亮、默认进入阅读模式。识别按路径段匹配，与盘符和
 * 项目布局解耦。
 */
object DreamShaderDerivedFiles {

    private const val DECOMPILED_SEGMENT: String = "Decompiled"

    private val DERIVED_SEGMENT_RUNS: List<List<String>> = listOf(
        listOf(DECOMPILED_SEGMENT),
        listOf("Intermediate", "DreamShader"),
    )

    fun isDerivedPath(path: String): Boolean {
        if (path.isBlank()) return false
        val segments = path.replace('\\', '/').split('/').filter { it.isNotEmpty() }
        return DERIVED_SEGMENT_RUNS.any { run -> segments.containsSublist(run) }
    }

    fun isDerivedFile(file: VirtualFile?): Boolean =
        file != null && isDerivedPath(file.path)

    private fun List<String>.containsSublist(sub: List<String>): Boolean {
        if (sub.isEmpty() || size < sub.size) return false
        return indices.any { start ->
            sub.indices.all { offset -> this[start + offset] == sub[offset] }
        }
    }
}
