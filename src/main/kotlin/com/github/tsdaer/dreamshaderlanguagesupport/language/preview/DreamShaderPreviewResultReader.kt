package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgePathResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

@Serializable
internal data class DreamShaderPreviewResultDto(
    val sourceFile: String? = null,
    val status: String? = null,
    val message: String? = null,
    val imagePath: String? = null,
    val assetPath: String? = null,
    val updatedAtUtc: String? = null
)

internal class DreamShaderPreviewResultReader {
    fun readPreviewResult(project: Project, sourceFile: String): DreamShaderPreviewResultDto? {
        val normalizedSource = sourceFile.replace('\\', '/')
        val activeFile = LocalFileSystem.getInstance().findFileByPath(normalizedSource)
        val previewPath = DreamShaderBridgePathResolver.resolvePreviewFilePath(project, activeFile)
            ?: return null
        val raw = runCatching {
            Files.readString(Path.of(previewPath), StandardCharsets.UTF_8)
        }.getOrNull() ?: return null
        val dto = DreamShaderJson.decodeOrNull<DreamShaderPreviewResultDto>(raw) ?: return null
        val resultSource = dto.sourceFile?.replace('\\', '/') ?: return null
        if (!resultSource.equals(normalizedSource, ignoreCase = true)) return null
        return dto.copy(imagePath = dto.imagePath?.let { resolveImagePath(project, activeFile, it) })
    }

    private fun resolveImagePath(project: Project, activeFile: VirtualFile?, rawPath: String): String {
        val normalized = rawPath.replace('\\', '/').trim()
        if (normalized.isBlank()) return rawPath

        val direct = runCatching { Path.of(normalized) }.getOrNull()
        if (direct?.isAbsolute == true) {
            return direct.normalize().toString().replace('\\', '/')
        }

        val projectRoot = DreamShaderBridgePathResolver.resolveProjectRoot(project, activeFile)
        val bridgeDirectory = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, activeFile)
        val previewDirectory = DreamShaderBridgePathResolver.resolvePreviewDirectory(project, activeFile)

        val bridgePreviewMarker = "Saved/DreamShader/Bridge/Preview/"
        val bridgePreviewIndex = normalized.indexOf(bridgePreviewMarker, ignoreCase = true)
        if (bridgePreviewIndex >= 0 && projectRoot != null) {
            return Path.of(projectRoot)
                .resolve(normalized.substring(bridgePreviewIndex))
                .normalize()
                .toString()
                .replace('\\', '/')
        }

        val previewMarker = "Preview/"
        val previewIndex = normalized.indexOf(previewMarker, ignoreCase = true)
        if (previewIndex >= 0 && previewDirectory != null) {
            return Path.of(previewDirectory)
                .resolve(normalized.substring(previewIndex + previewMarker.length))
                .normalize()
                .toString()
                .replace('\\', '/')
        }

        val candidates = mutableListOf<Path>()
        direct?.let { candidates.add(it) }
        bridgeDirectory?.let { candidates.add(Path.of(it).resolve(normalized)) }
        projectRoot?.let { candidates.add(Path.of(it).resolve(normalized)) }

        val fileName = direct?.fileName?.toString()
        if (!fileName.isNullOrBlank() && previewDirectory != null) {
            candidates.add(Path.of(previewDirectory).resolve(fileName))
        }

        val normalizedCandidates = candidates
            .map { it.normalize() }
            .distinctBy { it.toString().replace('\\', '/') }

        val existing = normalizedCandidates.firstOrNull { Files.isRegularFile(it) }
        return (existing ?: normalizedCandidates.firstOrNull() ?: direct ?: return rawPath)
            .toString()
            .replace('\\', '/')
    }
}
