package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageNotifier
import com.github.tsdaer.dreamshaderlanguagesupport.language.ui.DreamShaderUi
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project

private fun notifyTemplateResult(project: Project, result: DreamShaderTemplateOperationResult) {
    if (result.success) {
        DreamShaderPackageNotifier.info(project, DreamShaderBundle.message("templates.title"), result.message)
    } else {
        DreamShaderPackageNotifier.error(project, DreamShaderBundle.message("templates.title"), result.message)
    }
}

/**
 * 2.0 actions (`.dss` / `.dsi`), mirroring the upstream Content Browser's New menu,
 * where the 2.0 section comes before the 1.x block templates.
 */
class DreamShaderCreateMaterial2TemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateMaterial2.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateMaterial2.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.material2.title"),
            DreamShaderBundle.message("templates.dialog.material2.input"),
            "DShader/Materials/M_NewMaterial.dss"
        ) ?: return

        val result = DreamShaderTemplateService(project).createMaterial2Template(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreateFunction2TemplateAction.
 */
class DreamShaderCreateFunction2TemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateFunction2.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateFunction2.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.function2.title"),
            DreamShaderBundle.message("templates.dialog.function2.input"),
            "DShader/Functions/MF_NewFunction.dss"
        ) ?: return

        val result = DreamShaderTemplateService(project).createFunction2Template(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreateInstanceTemplateAction.
 */
class DreamShaderCreateInstanceTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateInstance.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateInstance.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = DreamShaderCreateInstanceDialog(project)
        if (!dialog.showAndGet()) return

        val request = dialog.toRequest()
        val result = DreamShaderTemplateService(project).createInstanceTemplate(request.targetPath, request.parent)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreateMaterialTemplateAction.
 */
class DreamShaderCreateMaterialTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateMaterial.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateMaterial.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.material.title"),
            DreamShaderBundle.message("templates.dialog.material.input"),
            "DShader/Materials/NewMaterial.dsm"
        ) ?: return

        val result = DreamShaderTemplateService(project).createMaterialTemplate(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreateFunctionTemplateAction.
 */
class DreamShaderCreateFunctionTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateFunction.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateFunction.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.function.title"),
            DreamShaderBundle.message("templates.dialog.function.input"),
            "DShader/Functions/NewFunction.dsf"
        ) ?: return

        val result = DreamShaderTemplateService(project).createFunctionTemplate(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreateHeaderTemplateAction.
 */
class DreamShaderCreateHeaderTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateHeader.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateHeader.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.header.title"),
            DreamShaderBundle.message("templates.dialog.header.input"),
            "DShader/Headers/Common.dsh"
        ) ?: return

        val result = DreamShaderTemplateService(project).createHeaderTemplate(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreateTextureSampleTemplateAction.
 */
class DreamShaderCreateTextureSampleTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateTextureSample.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateTextureSample.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.texture.title"),
            DreamShaderBundle.message("templates.dialog.texture.input"),
            "DShader/Materials/TextureSample.dsm"
        ) ?: return

        val result = DreamShaderTemplateService(project).createTextureSampleTemplate(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreateNoiseMaterialTemplateAction.
 */
class DreamShaderCreateNoiseMaterialTemplateAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateNoiseMaterial.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreateNoiseMaterial.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.noise.title"),
            DreamShaderBundle.message("templates.dialog.noise.input"),
            "DShader/Materials/NoiseMaterial.dsm"
        ) ?: return

        val result = DreamShaderTemplateService(project).createNoiseMaterialTemplate(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreatePackageScaffoldAction.
 */
class DreamShaderCreatePackageScaffoldAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreatePackageScaffold.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreatePackageScaffold.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val input = DreamShaderUi.showInputDialog(
            project,
            DreamShaderBundle.message("templates.dialog.package.title"),
            DreamShaderBundle.message("templates.dialog.package.input"),
            "@scope/my-package"
        ) ?: return

        val result = DreamShaderTemplateService(project).createPackageScaffold(input)
        notifyTemplateResult(project, result)
    }
}

/**
 * Action implementation for DreamShaderCreatePackageWizardAction.
 */
class DreamShaderCreatePackageWizardAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreatePackageWizard.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.TemplateTools.CreatePackageWizard.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val dialog = DreamShaderCreatePackageWizardDialog(project)
        if (!dialog.showAndGet()) return

        val result = DreamShaderTemplateService(project).createPackageScaffold(dialog.toRequest())
        notifyTemplateResult(project, result)
    }
}
