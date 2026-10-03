package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import com.intellij.ide.FileIconProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Per-extension file icons: the shared DreamShader file icon stays the fileType default, while
 * 2.0 compilation units (`.dss`) and material instances (`.dsi`) get their own accent so the two
 * dialects read apart in project views and editor tabs.
 */
class DreamShaderFileIconProvider : FileIconProvider {
    override fun getIcon(file: VirtualFile, flags: Int, project: Project?): Icon? {
        if (!DreamShaderDialects.isDreamShaderExtension(file.extension ?: "")) return null
        return when (DreamShaderDialects.extensionOf(file.name)) {
            "dsi" -> DreamShaderIcons.FILE_DSI
            in DreamShaderDialects.V2_EXTENSIONS -> DreamShaderIcons.FILE_DSS
            else -> null
        }
    }
}
