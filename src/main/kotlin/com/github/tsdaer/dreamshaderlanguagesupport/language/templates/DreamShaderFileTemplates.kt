package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderFileType
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.ide.actions.CreateFileFromTemplateAction
import com.intellij.ide.actions.CreateFileFromTemplateDialog
import com.intellij.ide.fileTemplates.DefaultTemplatePropertiesProvider
import com.intellij.ide.fileTemplates.FileTemplateDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import java.util.Properties

internal object DreamShaderTemplateNames {
    const val MATERIAL2 = "DreamShader 2.0 Material.dss"
    const val FUNCTION2 = "DreamShader 2.0 Function.dss"
    const val INSTANCE = "DreamShader Instance.dsi"
    const val MATERIAL = "DreamShader Material.dsm"
    const val FUNCTION = "DreamShader Function.dsf"
    const val HEADER = "DreamShader Header.dsh"
    const val GRAPH_SECTION = "DreamShader Graph Section.dsm"
    const val TEXTURE_SAMPLE = "DreamShader Texture Sample.dsm"
}

class DreamShaderFileTemplateGroupFactory : FileTemplateGroupDescriptorFactory {
    override fun getFileTemplatesDescriptor(): FileTemplateGroupDescriptor {
        return FileTemplateGroupDescriptor(
            DreamShaderBundle.message("templates.fileGroup.name"),
            DreamShaderIcons.FILE
        ).apply {
            addTemplate(FileTemplateDescriptor(DreamShaderTemplateNames.MATERIAL2, DreamShaderIcons.SHADER))
            addTemplate(FileTemplateDescriptor(DreamShaderTemplateNames.FUNCTION2, DreamShaderIcons.FUNCTION))
            addTemplate(FileTemplateDescriptor(DreamShaderTemplateNames.INSTANCE, DreamShaderIcons.FILE))
            addTemplate(FileTemplateDescriptor(DreamShaderTemplateNames.MATERIAL, DreamShaderIcons.SHADER))
            addTemplate(FileTemplateDescriptor(DreamShaderTemplateNames.FUNCTION, DreamShaderIcons.FUNCTION))
            addTemplate(FileTemplateDescriptor(DreamShaderTemplateNames.HEADER, DreamShaderIcons.FILE))
            addTemplate(FileTemplateDescriptor(DreamShaderTemplateNames.GRAPH_SECTION, DreamShaderIcons.SECTION_GRAPH))
            addTemplate(FileTemplateDescriptor(DreamShaderTemplateNames.TEXTURE_SAMPLE, DreamShaderIcons.SHADER))
        }
    }
}

class DreamShaderDefaultTemplatePropertiesProvider : DefaultTemplatePropertiesProvider {
    override fun fillProperties(directory: PsiDirectory, props: Properties) {
        val rawName = props.getProperty("NAME").orEmpty()
        val symbol = toIdentifier(rawName.substringBeforeLast('.', rawName), "DreamShaderAsset")
        props.setProperty("DREAMSHADER_SYMBOL_NAME", symbol)
        props.setProperty("DREAMSHADER_ASSET_NAME", "DreamMaterials/$symbol")
        props.setProperty("DREAMSHADER_NAMESPACE_NAME", symbol.ifBlank { "Shared" })
        props.setProperty("DREAMSHADER_FILE_NAME", rawName)
        props.setProperty("DREAMSHADER_ASSET_PATH", assetPath(directory, symbol))
        props.setProperty("DREAMSHADER_INSTANCE_PARENT", "M_ParentMaterial")
    }

    /** Upstream convention: a file under `<root>/Materials/` compiles to `/Game/Materials/<name>`. */
    private fun assetPath(directory: PsiDirectory, symbol: String): String {
        val project = directory.project
        val basePath = project.basePath ?: return "/Game/$symbol"
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state
        val sourceRoot = runCatching {
            java.nio.file.Path.of(basePath).toAbsolutePath().normalize()
                .resolve(settings?.sourceDirectory?.trim().orEmpty().ifBlank { "DShader" })
        }.getOrNull() ?: return "/Game/$symbol"
        val directoryPath = runCatching {
            java.nio.file.Path.of(directory.virtualFile.path).toAbsolutePath().normalize()
        }.getOrNull() ?: return "/Game/$symbol"
        val relative = runCatching { sourceRoot.relativize(directoryPath) }.getOrNull()
            ?.toString()?.replace('\\', '/')
        return if (relative.isNullOrBlank() || relative.startsWith("..")) {
            "/Game/$symbol"
        } else {
            "/Game/$relative/$symbol"
        }
    }

    private fun toIdentifier(input: String, fallback: String): String {
        val cleaned = buildString(input.length) {
            input.forEach { ch ->
                when {
                    ch.isLetterOrDigit() || ch == '_' -> append(ch)
                    ch == '-' || ch == ' ' || ch == '.' -> append('_')
                }
            }
        }.trim('_')
        val value = cleaned.ifBlank { fallback }
        return if (value.first().isDigit()) "_$value" else value
    }

    internal fun testIdentifier(input: String, fallback: String = "DreamShaderAsset"): String {
        return toIdentifier(input, fallback)
    }
}

class DreamShaderCreateFileFromTemplateAction : CreateFileFromTemplateAction(
    DreamShaderBundle.message("action.DreamShader.NewFile.text"),
    DreamShaderBundle.message("action.DreamShader.NewFile.description"),
    DreamShaderIcons.FILE
) {
    override fun buildDialog(
        project: Project,
        directory: PsiDirectory,
        builder: CreateFileFromTemplateDialog.Builder
    ) {
        builder
            .setTitle(DreamShaderBundle.message("action.DreamShader.NewFile.text"))
            // Upstream New-menu convention: the 2.0 section first, the 1.x block templates below.
            .addKind(
                DreamShaderBundle.message("templates.kind.material2"),
                DreamShaderIcons.SHADER,
                DreamShaderTemplateNames.MATERIAL2
            )
            .addKind(
                DreamShaderBundle.message("templates.kind.function2"),
                DreamShaderIcons.FUNCTION,
                DreamShaderTemplateNames.FUNCTION2
            )
            .addKind(
                DreamShaderBundle.message("templates.kind.instance"),
                DreamShaderIcons.FILE,
                DreamShaderTemplateNames.INSTANCE
            )
            .addKind(
                DreamShaderBundle.message("templates.kind.material"),
                DreamShaderIcons.SHADER,
                DreamShaderTemplateNames.MATERIAL
            )
            .addKind(
                DreamShaderBundle.message("templates.kind.function"),
                DreamShaderIcons.FUNCTION,
                DreamShaderTemplateNames.FUNCTION
            )
            .addKind(
                DreamShaderBundle.message("templates.kind.header"),
                DreamShaderIcons.FILE,
                DreamShaderTemplateNames.HEADER
            )
    }

    override fun getActionName(directory: PsiDirectory, newName: String, templateName: String): String {
        return DreamShaderBundle.message("action.DreamShader.NewFile.text")
    }

    override fun isAvailable(dataContext: DataContext): Boolean = true
}
