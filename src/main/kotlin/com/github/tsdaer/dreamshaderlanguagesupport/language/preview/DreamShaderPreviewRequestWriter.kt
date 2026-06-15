package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgePathResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.serialization.Serializable
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.random.Random

@Serializable
internal data class DreamShaderPreviewRequestDto(
    val action: String = "previewMaterial",
    val sourceFile: String,
    val width: Int = 512,
    val height: Int = 512,
    val mesh: String,
    val requestId: String
)

internal class DreamShaderPreviewRequestWriter {
    fun writePreviewMaterialRequest(
        project: Project,
        sourceFile: String,
        mesh: String,
        requestId: String
    ): Path? {
        val normalizedSource = sourceFile.replace('\\', '/')
        val activeFile = LocalFileSystem.getInstance().findFileByPath(normalizedSource)
        val requestDir = DreamShaderBridgePathResolver.resolveRequestDirectory(project, activeFile)
            ?: return null
        val safeMesh = mesh.takeIf { it in SUPPORTED_MESHES } ?: DEFAULT_MESH
        val dto = DreamShaderPreviewRequestDto(
            sourceFile = normalizedSource,
            mesh = safeMesh,
            requestId = requestId
        )
        return runCatching {
            val dir = Path.of(requestDir)
            Files.createDirectories(dir)
            val suffix = Random.nextInt(100000, 999999)
            val target = dir.resolve("request-${System.currentTimeMillis()}-$suffix.json")
            Files.writeString(target, DreamShaderJson.encodePretty(dto) + "\n", StandardCharsets.UTF_8)
            target
        }.getOrNull()
    }

    companion object {
        internal const val DEFAULT_MESH = "sphere"
        internal val SUPPORTED_MESHES = setOf("sphere", "plane", "cube")
    }
}
