package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.wm.ToolWindowManager
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import javax.swing.BoxLayout
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

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
        val root = JPanel(BorderLayout(0, 12))
        root.preferredSize = Dimension(760, 360)

        val titleLabel = JLabel(DreamShaderBundle.message("hub.subtitle"), SwingConstants.LEFT)
        root.add(titleLabel, BorderLayout.NORTH)

        val content = JPanel()
        content.layout = BoxLayout(content, BoxLayout.Y_AXIS)

        content.add(
            createSection(
                DreamShaderBundle.message("hub.section.workspace"),
                listOf(
                    button("hub.button.openSettings") {
                        ShowSettingsUtil.getInstance()
                            .showSettingsDialog(project, DreamShaderSettingsConfigurable::class.java)
                    },
                    button("hub.button.openBridgePanel") {
                        ToolWindowManager.getInstance(project)
                            .getToolWindow("DreamShader Bridge Diagnostics")
                            ?.show()
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
                    button("hub.button.createPackageScaffold") {
                        invokeActionById("DreamShader.TemplateTools.CreatePackageScaffold")
                    }
                )
            )
        )

        root.add(content, BorderLayout.CENTER)
        return root
    }

    override fun createActions() = arrayOf(okAction)

    private fun createSection(title: String, buttons: List<JButton>): JPanel {
        val section = JPanel(BorderLayout(0, 6))
        section.alignmentX = JPanel.LEFT_ALIGNMENT

        val label = JLabel(title)
        section.add(label, BorderLayout.NORTH)

        val row = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0))
        buttons.forEach(row::add)
        section.add(row, BorderLayout.CENTER)

        return section
    }

    private fun button(key: String, onClick: () -> Unit): JButton {
        val button = JButton(DreamShaderBundle.message(key))
        button.addActionListener { onClick() }
        return button
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
            action,
            context,
            action.templatePresentation.clone(),
            ActionPlaces.UNKNOWN,
            ActionUiKind.NONE,
            null
        )
        action.actionPerformed(event)
    }
}
