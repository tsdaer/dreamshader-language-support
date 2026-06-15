package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgePathResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
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
        return dto
    }
}
