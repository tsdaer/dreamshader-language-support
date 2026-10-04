package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem

/**
 * The "Open at line" half of the two-way navigation loop (M12 C7).
 *
 * The forward direction (source line -> material node) is `reveal-node`; the reverse
 * surfaces in the IDE as a jump to the source line carried by a reveal response
 * diagnostic (`stage:"navigate"`, DSH9050-9057) or by any Bridge diagnostic row. The
 * navigator resolves the file and reuses the same editor-location hook the 1.x
 * diagnostic actions use.
 */
internal object DreamShaderSourceSpanNavigator {

    /**
     * Opens [filePath] at the 1-based [line]/[column]. Returns false when the file
     * cannot be resolved - the caller keeps its notification, nothing is thrown.
     */
    fun openAtLine(
        project: Project,
        filePath: String,
        line: Int,
        column: Int = 1,
    ): Boolean {
        val normalized = filePath.replace('\\', '/')
        val vf = LocalFileSystem.getInstance().findFileByPath(normalized) ?: return false
        if (!vf.isValid || vf.isDirectory) return false
        DreamShaderBridgeActionTestHooks.locationNavigator?.invoke(project, vf, line, column)
            ?: OpenFileDescriptor(
                project,
                vf,
                (line - 1).coerceAtLeast(0),
                (column - 1).coerceAtLeast(0),
            ).navigate(true)
        return true
    }
}
