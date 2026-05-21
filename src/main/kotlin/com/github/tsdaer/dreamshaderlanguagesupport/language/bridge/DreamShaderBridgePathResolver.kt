package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

internal object DreamShaderBridgePathResolver {
    private const val BRIDGE_RELATIVE_PATH = "Saved/DreamShader/Bridge"

    fun resolveProjectRoot(project: Project, activeFile: VirtualFile?): String? {
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        val configuredRoot = settings?.projectRoot?.trim().orEmpty()
        if (configuredRoot.isNotBlank()) {
            return normalizePath(configuredRoot)
        }

        val projectBase = project.basePath?.takeIf { it.isNotBlank() }
        val activePath = activeFile?.path?.takeIf { it.isNotBlank() }

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

    fun resolveBridgeDirectory(project: Project, activeFile: VirtualFile?): String? {
        val root = resolveProjectRoot(project, activeFile) ?: return null
        return normalizePath(File(root, BRIDGE_RELATIVE_PATH).path)
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }
}
