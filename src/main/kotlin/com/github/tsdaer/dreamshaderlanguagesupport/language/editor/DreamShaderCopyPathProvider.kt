package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.ide.actions.DumbAwareCopyPathProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.util.Locale

/**
 * Language-aware Copy Path (D6): the Copy Reference popup offers the project-relative
 * file path for DreamShader sources, and the engine asset path (`/Game/...`) for files
 * living under the project's source root (default `DShader/`, upstream Content Browser
 * convention - see `Docs/tools/material-browser.md`).
 */
class DreamShaderCopyPathProvider : DumbAwareCopyPathProvider() {
    override fun getPathToElement(project: Project, virtualFile: VirtualFile?, editor: Editor?): String? {
        val file = virtualFile ?: return null
        return copyPathFor(
            projectBasePath = project.basePath,
            sourceDirectorySetting = sourceDirectorySetting(project),
            fileUrl = file.path,
            extension = file.extension,
        ) ?: return null
    }

    private fun sourceDirectorySetting(project: Project): String {
        val configured = project.getService(DreamShaderProjectSettings::class.java)?.state
            ?.sourceDirectory?.trim().orEmpty()
        return configured.ifBlank { DEFAULT_SOURCE_DIRECTORY }
    }

    companion object {
        internal const val DEFAULT_SOURCE_DIRECTORY = "DShader"

        /** Registered DreamShader source extensions (mirrors the `extensions=` list in plugin.xml). */
        private val SOURCE_EXTENSIONS = setOf("dsm", "dsf", "dsh", "dss", "dsi")

        /**
         * Pure path logic, kept static for direct testing:
         * - under the source root -> `/Game/<relative dirs>/<stem>` (no extension; the
         *   asset stem keeps the file stem verbatim, matching the upstream templates)
         * - otherwise -> the project-relative path (absolute as a last resort)
         */
        internal fun copyPathFor(
            projectBasePath: String?,
            sourceDirectorySetting: String,
            fileUrl: String,
            extension: String?,
        ): String? {
            if (extension?.lowercase(Locale.ROOT) !in SOURCE_EXTENSIONS) return null
            val normalizedPath = fileUrl.replace('\\', '/')
            val fileStem = normalizedPath.substringAfterLast('/').substringBeforeLast('.')
            val normalizedBase = projectBasePath?.replace('\\', '/')?.trimEnd('/')

            val sourceRoot = normalizedBase
                ?.let { "$it/${sourceDirectorySetting.trim('/')}" }
            if (sourceRoot != null && normalizedPath.startsWith("$sourceRoot/")) {
                val relativeDirs = normalizedPath.removePrefix("$sourceRoot/")
                    .substringBeforeLast('/')
                val relative = relativeDirs.takeIf { it.isNotBlank() && it != normalizedPath }?.let { "$it/" }.orEmpty()
                return "/Game/$relative$fileStem"
            }

            if (normalizedBase != null && normalizedPath.startsWith("$normalizedBase/")) {
                return normalizedPath.removePrefix("$normalizedBase/")
            }
            return normalizedPath
        }
    }
}
