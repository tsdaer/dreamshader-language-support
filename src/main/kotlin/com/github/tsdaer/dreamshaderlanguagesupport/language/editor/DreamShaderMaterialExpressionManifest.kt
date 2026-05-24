package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * $name 单例对象。
 */
internal object DreamShaderMaterialExpressionManifest {
    private const val BUNDLED_RESOURCE_PATH = "/messages/material-expression-manifest.json"
    private const val DEFAULT_BRIDGE_RELATIVE_PATH = "Saved/DreamShader/Bridge/material-expression-manifest.json"

    private val classNameRegex = Regex(
        """"className"\s*:\s*"([A-Za-z_][A-Za-z0-9_]*)"""",
        RegexOption.IGNORE_CASE
    )
    private val nameRegex = Regex(
        """"name"\s*:\s*"([A-Za-z_][A-Za-z0-9_]*)"""",
        RegexOption.IGNORE_CASE
    )
    private val simpleArrayEntryRegex = Regex(
        """"(?:className|name)"\s*:\s*"([A-Za-z_][A-Za-z0-9_]*)"|"([A-Za-z_][A-Za-z0-9_]*)"""",
        RegexOption.IGNORE_CASE
    )

    fun expressionClassNames(project: Project?, explicitManifestPath: String?): List<String> {
        val merged = linkedSetOf<String>()
        readExpressionClassNamesFromConfiguredPath(explicitManifestPath).forEach { merged.add(it) }
        readExpressionClassNamesFromBridgeManifest(project).forEach { merged.add(it) }
        readExpressionClassNamesFromBundledManifest().forEach { merged.add(it) }
        return merged.toList()
    }

    private fun readExpressionClassNamesFromConfiguredPath(path: String?): List<String> {
        if (path.isNullOrBlank()) return emptyList()
        val file = File(path)
        if (!file.exists() || !file.isFile) return emptyList()
        return runCatching { parseExpressionClassNames(file.readText(StandardCharsets.UTF_8)) }
            .getOrDefault(emptyList())
    }

    private fun readExpressionClassNamesFromBridgeManifest(project: Project?): List<String> {
        if (project == null) return emptyList()
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        val root = settings?.projectRoot?.takeIf { it.isNotBlank() } ?: project.basePath
        if (root.isNullOrBlank()) return emptyList()
        val vf = LocalFileSystem.getInstance()
            .findFileByPath(File(root, DEFAULT_BRIDGE_RELATIVE_PATH).path)
            ?: return emptyList()
        if (!vf.isValid || vf.isDirectory) return emptyList()
        return runCatching {
            parseExpressionClassNames(String(vf.contentsToByteArray(), StandardCharsets.UTF_8))
        }.getOrDefault(emptyList())
    }

    private fun readExpressionClassNamesFromBundledManifest(): List<String> {
        val stream = javaClass.getResourceAsStream(BUNDLED_RESOURCE_PATH) ?: return emptyList()
        val text = stream.use { String(it.readBytes(), StandardCharsets.UTF_8) }
        return runCatching { parseExpressionClassNames(text) }.getOrDefault(emptyList())
    }

    internal fun parseExpressionClassNames(rawJson: String): List<String> {
        val trimmed = rawJson.trim()
        if (trimmed.isBlank()) return emptyList()
        val values = linkedSetOf<String>()

        classNameRegex.findAll(trimmed).forEach { values.add(it.groupValues[1]) }
        nameRegex.findAll(trimmed).forEach { values.add(it.groupValues[1]) }

        if (values.isEmpty() && trimmed.startsWith("[")) {
            simpleArrayEntryRegex.findAll(trimmed).forEach { match ->
                val first = match.groupValues[1]
                val second = match.groupValues[2]
                when {
                    first.isNotBlank() -> values.add(first)
                    second.isNotBlank() && !second.equals("className", ignoreCase = true) && !second.equals("name", ignoreCase = true) -> values.add(second)
                }
            }
        }

        return values.toList()
    }
}
