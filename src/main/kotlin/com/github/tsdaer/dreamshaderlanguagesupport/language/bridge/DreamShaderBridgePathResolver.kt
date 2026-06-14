package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * Bridge 路径解析器。
 *
 * 负责解析 DreamShader 诊断与相关集成所使用的项目根与 Bridge 目录。
 *
 * 项目根回退顺序：
 * 1. 设置项 `projectRoot`
 * 2. 从活动文件（其次 `project.basePath`）向上查找含 `*.uproject` 的目录
 * 3. 当活动文件位于项目内时使用 `project.basePath`
 * 4. 活动文件路径中 `/DShader/` 之前的前缀
 * 5. `project.basePath`
 * 6. 活动文件父目录
 */
internal object DreamShaderBridgePathResolver {
    private const val BRIDGE_RELATIVE_PATH = "Saved/DreamShader/Bridge"

    fun resolveProjectRoot(project: Project, activeFile: VirtualFile?): String? {
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        val configuredRoot = settings?.projectRoot?.trim().orEmpty()
        if (configuredRoot.isNotBlank()) {
            return normalizePath(configuredRoot)
        }

        return resolveProjectRootAutoFallback(project, activeFile)
    }

    /**
     * Resolve project root using automatic fallback logic only, ignoring configured settings.
     */
    fun resolveProjectRootAutoFallback(project: Project, activeFile: VirtualFile?): String? {
        return resolveProjectRootWithoutConfigured(project, activeFile)
    }

    private fun resolveProjectRootWithoutConfigured(project: Project, activeFile: VirtualFile?): String? {

        val projectBase = project.basePath?.takeIf { it.isNotBlank() }
        val activePath = activeFile?.path?.takeIf { it.isNotBlank() }

        // 优先以 `.uproject` 作为 UE 项目根锚点：Bridge 目录就在 `.uproject` 同级的
        // `Saved/DreamShader/Bridge` 下，比 `/DShader/` 路径标记更可靠。
        findUprojectRoot(activePath)?.let { return it }
        findUprojectRoot(projectBase)?.let { return it }

        if (projectBase != null && activePath != null) {
            val normalizedBase = normalizePath(projectBase)
            val normalizedActive = normalizePath(activePath)
            if (normalizedActive.startsWith("$normalizedBase/") || normalizedActive == normalizedBase) {
                return normalizedBase
            }
        }

        if (activePath != null) {
            val normalizedActive = normalizePath(activePath)
            val dshaderIndex = normalizedActive.indexOf("/DShader/")
            if (dshaderIndex > 0) {
                return normalizedActive.substring(0, dshaderIndex)
            }
        }

        if (projectBase != null) {
            return normalizePath(projectBase)
        }

        if (activePath != null) {
            return normalizePath(File(activePath).parent ?: activePath)
        }

        return null
    }

    /**
     * 从给定路径（文件则取其父目录）逐级向上查找含 `*.uproject` 文件的目录，
     * 命中即返回该目录的规范化路径；到达文件系统根仍未命中返回 null。
     */
    private fun findUprojectRoot(startPath: String?): String? {
        val raw = startPath?.takeIf { it.isNotBlank() } ?: return null
        var dir: File? = File(raw).let { if (it.isFile) it.parentFile else it }
        while (dir != null) {
            val hasUproject = runCatching {
                dir.listFiles { file -> file.isFile && file.name.endsWith(".uproject", ignoreCase = true) }
                    ?.isNotEmpty() == true
            }.getOrDefault(false)
            if (hasUproject) {
                return normalizePath(dir.path)
            }
            dir = dir.parentFile
        }
        return null
    }

    fun resolveBridgeDirectory(project: Project, activeFile: VirtualFile?): String? {
        val root = resolveProjectRoot(project, activeFile) ?: return null
        return normalizePath(File(root, BRIDGE_RELATIVE_PATH).path)
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }
}
