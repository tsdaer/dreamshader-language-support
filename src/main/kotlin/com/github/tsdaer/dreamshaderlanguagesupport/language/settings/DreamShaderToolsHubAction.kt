package com.github.tsdaer.dreamshaderlanguagesupport.language.settings
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.ui.DreamShaderUi
import com.github.tsdaer.dreamshaderlanguagesupport.language.welcome.showWelcomeDialog
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.ui.TitledSeparator
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class DreamShaderOpenHubAction : DumbAwareAction() {
    init {
        templatePresentation.text = DreamShaderBundle.message("action.DreamShader.Tools.OpenHub.text")
        templatePresentation.description = DreamShaderBundle.message("action.DreamShader.Tools.OpenHub.description")
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        DreamShaderHubDialog(project).show()
    }
}

private class DreamShaderHubDialog(
    private val project: Project
) : DialogWrapper(project) {
    init {
        title = DreamShaderBundle.message("hub.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(0, 0))
        DreamShaderUi.installSurface(root)

        root.add(DreamShaderUi.vertical(0,
            buildSection(DreamShaderBundle.message("hub.section.workspace"), listOf(
                "hub.button.openWelcome" to {
                    close(OK_EXIT_CODE)
                    showWelcomeDialog(project, forceManual = true)
                },
                "hub.button.openSettings" to {
                    close(OK_EXIT_CODE)
                    ShowSettingsUtil.getInstance().showSettingsDialog(project, DreamShaderSettingsConfigurable::class.java)
                },
                "hub.button.openBridgePanel" to {
                    close(OK_EXIT_CODE)
                    ToolWindowManager.getInstance(project)
                        .getToolWindow(DreamShaderBundle.message("toolwindow.dreamshader.bridge.displayName"))?.show()
                },
                "hub.button.showMaterialPreview" to { invokeAndClose("DreamShader.Tools.ShowMaterialPreview") },
                "hub.button.refreshBridgeDiagnostics" to { invokeAndClose("DreamShader.BridgeTools.RefreshDiagnostics") }
            )),
            buildSection(DreamShaderBundle.message("hub.section.bridge"), listOf(
                "hub.button.recompileCurrent" to { invokeAndClose("DreamShader.BridgeTools.RecompileCurrent") },
                "hub.button.recompileAll" to { invokeAndClose("DreamShader.BridgeTools.RecompileAll") },
                "hub.button.cleanGenerated" to { invokeAndClose("DreamShader.BridgeTools.CleanGeneratedShaders") }
            )),
            buildSection(DreamShaderBundle.message("hub.section.packages"), listOf(
                "hub.button.browsePackageStore" to { invokeAndClose("DreamShader.PackageTools.BrowseStore") },
                "hub.button.installFromGitHub" to { invokeAndClose("DreamShader.PackageTools.InstallFromGitHub") },
                "hub.button.openPackagesFolder" to { invokeAndClose("DreamShader.PackageTools.OpenPackagesFolder") },
                "hub.button.manageIndexSources" to { invokeAndClose("DreamShader.PackageTools.AddIndexSource") }
            )),
            buildSection(DreamShaderBundle.message("hub.section.templates"), listOf(
                "hub.button.createMaterialTemplate" to { invokeAndClose("DreamShader.TemplateTools.CreateMaterial") },
                "hub.button.createFunctionTemplate" to { invokeAndClose("DreamShader.TemplateTools.CreateFunction") },
                "hub.button.createHeaderTemplate" to { invokeAndClose("DreamShader.TemplateTools.CreateHeader") },
                "hub.button.createTextureSampleTemplate" to { invokeAndClose("DreamShader.TemplateTools.CreateTextureSample") },
                "hub.button.createNoiseMaterialTemplate" to { invokeAndClose("DreamShader.TemplateTools.CreateNoiseMaterial") },
                "hub.button.createPackageWizard" to { invokeAndClose("DreamShader.TemplateTools.CreatePackageWizard") },
                "hub.button.createPackageScaffold" to { invokeAndClose("DreamShader.TemplateTools.CreatePackageScaffold") }
            ))), BorderLayout.CENTER)
        return root
    }

    override fun createActions() = arrayOf(okAction)

    private fun buildSection(title: String, buttons: List<Pair<String, () -> Unit>>): JPanel {
        val panel = JPanel(BorderLayout(0, JBUI.scale(4))).apply { isOpaque = false }
        panel.add(TitledSeparator(title), BorderLayout.NORTH)
        val row = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(4), JBUI.scale(4))).apply { isOpaque = false }
        buttons.forEach { (key, action) ->
            row.add(JButton(DreamShaderBundle.message(key)).apply {
                margin = JBUI.insets(3, 8)
                addActionListener { action() }
            })
        }
        panel.add(row, BorderLayout.CENTER)
        return panel
    }

    private fun invokeAndClose(actionId: String) {
        invokeActionById(actionId)
        close(OK_EXIT_CODE)
    }

    private fun invokeActionById(actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId) ?: return
        val context = DataContext { dataId ->
            when { CommonDataKeys.PROJECT.`is`(dataId) -> project; else -> null }
        }
        val event = AnActionEvent.createEvent(
            context, action.templatePresentation.clone(), "DreamShader.HubDialog", ActionUiKind.NONE, null
        )
        ActionUtil.performAction(action, event)
    }
}
