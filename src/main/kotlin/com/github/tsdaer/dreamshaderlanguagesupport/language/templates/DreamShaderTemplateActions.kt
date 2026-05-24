package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageNotifier
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages

private fun notifyTemplateResult(project: Project, result: DreamShaderTemplateOperationResult) {
    if (result.success) {
        DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("templates.title"), result.message)
    } else {
        DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("templates.title"), result.message)
    }
}

/**
 * $name 动作实现。
 */
class DreamShaderCreateMaterialTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateMaterial.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateMaterial.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.material.input"),
            DreamShaderBundle.message("templates.dialog.material.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = DreamShaderTemplateService(project).createMaterialTemplate(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * $name 动作实现。
 */
class DreamShaderCreateFunctionTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateFunction.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateFunction.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.function.input"),
            DreamShaderBundle.message("templates.dialog.function.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = DreamShaderTemplateService(project).createFunctionTemplate(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * $name 动作实现。
 */
class DreamShaderCreateHeaderTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateHeader.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateHeader.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.header.input"),
            DreamShaderBundle.message("templates.dialog.header.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = DreamShaderTemplateService(project).createHeaderTemplate(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * $name 动作实现。
 */
class DreamShaderCreatePackageScaffoldAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreatePackageScaffold.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreatePackageScaffold.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = Messages.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.package.input"),
            DreamShaderBundle.message("templates.dialog.package.title"),
            Messages.getQuestionIcon()
        )?.trim().orEmpty()
        if (input.isBlank()) return

        val result = DreamShaderTemplateService(project).createPackageScaffold(input)
        notifyTemplateResult(project, result)
    }
}
