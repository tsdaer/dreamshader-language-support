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
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.GridLayout
import javax.swing.*

/**
 * Action implementation for DreamShaderOpenHubAction.
 */
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

/**
 * Dialog implementation for DreamShaderHubDialog.
 */
private class DreamShaderHubDialog(
    private val project: Project
) : DialogWrapper(project) {
    init {
        title = DreamShaderBundle.message("hub.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(JBUI.scale(12), JBUI.scale(12)))
        root.preferredSize = Dimension(900, 560)
        DreamShaderUi.installSurface(root)

        val hero = DreamShaderUi.card(BorderLayout(JBUI.scale(12), 0)).apply {
            border = JBUI.Borders.empty(16)
            add(DreamShaderUi.sectionTitle(DreamShaderBundle.message("hub.title")), BorderLayout.NORTH)
            add(DreamShaderUi.mutedLabel(DreamShaderBundle.message("hub.subtitle")), BorderLayout.CENTER)
        }
        root.add(hero, BorderLayout.NORTH)

        val content = JPanel(GridLayout(0, 2, JBUI.scale(12), JBUI.scale(12))).apply {
            isOpaque = false
        }

        content.add(
            createSection(
                DreamShaderBundle.message("hub.section.workspace"),
                listOf(
                    button("hub.button.openWelcome") {
                        close(OK_EXIT_CODE)
                        showWelcomeDialog(project, forceManual = true)
                    },
                    button("hub.button.openSettings") {
                        ShowSettingsUtil.getInstance()
                            .showSettingsDialog(project, DreamShaderSettingsConfigurable::class.java)
                    },
                    button("hub.button.openBridgePanel") {
                        ToolWindowManager.getInstance(project)
                            .getToolWindow(DreamShaderBundle.message("toolwindow.dreamshader.bridge.displayName"))
                            ?.show()
                    },
                    button("hub.button.showMaterialPreview") {
                        invokeActionById("DreamShader.Tools.ShowMaterialPreview")
                    },
                    button("hub.button.refreshBridgeDiagnostics") {
                        invokeActionById("DreamShader.BridgeTools.RefreshDiagnostics")
                    }
                )
            )
        )

        content.add(
            createSection(
                DreamShaderBundle.message("hub.section.bridge"),
                listOf(
                    button("hub.button.recompileCurrent") {
                        invokeActionById("DreamShader.BridgeTools.RecompileCurrent")
                    },
                    button("hub.button.recompileAll") {
                        invokeActionById("DreamShader.BridgeTools.RecompileAll")
                    },
                    button("hub.button.cleanGenerated") {
                        invokeActionById("DreamShader.BridgeTools.CleanGeneratedShaders")
                    }
                )
            )
        )

        content.add(
            createSection(
                DreamShaderBundle.message("hub.section.packages"),
                listOf(
                    button("hub.button.browsePackageStore") {
                        invokeActionById("DreamShader.PackageTools.BrowseStore")
                    },
                    button("hub.button.installFromGitHub") {
                        invokeActionById("DreamShader.PackageTools.InstallFromGitHub")
                    },
                    button("hub.button.openPackagesFolder") {
                        invokeActionById("DreamShader.PackageTools.OpenPackagesFolder")
                    },
                    button("hub.button.manageIndexSources") {
                        invokeActionById("DreamShader.PackageTools.AddIndexSource")
                    }
                )
            )
        )

        content.add(
            createSection(
                DreamShaderBundle.message("hub.section.templates"),
                listOf(
                    button("hub.button.createMaterialTemplate") {
                        invokeActionById("DreamShader.TemplateTools.CreateMaterial")
                    },
                    button("hub.button.createFunctionTemplate") {
                        invokeActionById("DreamShader.TemplateTools.CreateFunction")
                    },
                    button("hub.button.createHeaderTemplate") {
                        invokeActionById("DreamShader.TemplateTools.CreateHeader")
                    },
                    button("hub.button.createTextureSampleTemplate") {
                        invokeActionById("DreamShader.TemplateTools.CreateTextureSample")
                    },
                    button("hub.button.createNoiseMaterialTemplate") {
                        invokeActionById("DreamShader.TemplateTools.CreateNoiseMaterial")
                    },
                    button("hub.button.createPackageWizard") {
                        invokeActionById("DreamShader.TemplateTools.CreatePackageWizard")
                    },
                    button("hub.button.createPackageScaffold") {
                        invokeActionById("DreamShader.TemplateTools.CreatePackageScaffold")
                    }
                )
            )
        )

        root.add(JScrollPane(content).apply {
            border = JBUI.Borders.empty()
            viewport.isOpaque = false
            viewport.background = DreamShaderUi.panelBackground
        }, BorderLayout.CENTER)
        return root
    }

    override fun createActions() = arrayOf(okAction)

    private fun createSection(title: String, buttons: List<JButton>): JPanel {
        val section = DreamShaderUi.card(BorderLayout(JBUI.scale(8), JBUI.scale(10)))
        section.alignmentX = JPanel.LEFT_ALIGNMENT

        val label = DreamShaderUi.titleLabel(title)
        section.add(label, BorderLayout.NORTH)

        val row = JPanel(FlowLayout(FlowLayout.LEFT, JBUI.scale(8), JBUI.scale(8))).apply {
            isOpaque = false
        }
        buttons.forEach(row::add)
        section.add(row, BorderLayout.CENTER)

        return section
    }

    private fun button(key: String, onClick: () -> Unit): JButton {
        return JButton(DreamShaderBundle.message(key)).apply {
            isFocusPainted = false
            margin = JBUI.insets(6, 10)
            addActionListener { onClick() }
        }
    }

    private fun invokeActionById(actionId: String) {
        val action = ActionManager.getInstance().getAction(actionId) ?: return
        val context = DataContext { dataId ->
            when {
                CommonDataKeys.PROJECT.`is`(dataId) -> project
                else -> null
            }
        }
        val event = AnActionEvent.createEvent(
            context,
            action.templatePresentation.clone(),
            "DreamShader.HubDialog",
            ActionUiKind.NONE,
            null
        )
        ActionUtil.performAction(
            action,
            event
        )
    }
}
