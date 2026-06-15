package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderFileType
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
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
