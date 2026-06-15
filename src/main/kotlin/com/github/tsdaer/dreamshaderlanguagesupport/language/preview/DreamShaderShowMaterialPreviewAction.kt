package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageNotifier
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.wm.ToolWindowManager

internal const val DREAMSHADER_MATERIAL_PREVIEW_TOOLWINDOW_ID = "DreamShader Material Preview"

class DreamShaderShowMaterialPreviewAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.Tools.ShowMaterialPreview.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.Tools.ShowMaterialPreview.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val contextFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        val selected = contextFile ?: FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
        if (selected?.extension?.lowercase() != "dsm") {
            DreamShaderPackageNotifier.warn(
                project,
                DreamShaderBundle.message("preview.title"),
                DreamShaderBundle.message("preview.notification.noActiveDsm")
            )
            return
        }

        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow(DREAMSHADER_MATERIAL_PREVIEW_TOOLWINDOW_ID)
        if (toolWindow == null) {
            DreamShaderPackageNotifier.warn(
                project,
                DreamShaderBundle.message("preview.title"),
                DreamShaderBundle.message("preview.notification.toolwindowMissing")
            )
            return
        }
        toolWindow.activate {
            project.getService(DreamShaderMaterialPreviewPanelService::class.java).panel?.showCurrentFileAndRequest()
        }
    }
}
