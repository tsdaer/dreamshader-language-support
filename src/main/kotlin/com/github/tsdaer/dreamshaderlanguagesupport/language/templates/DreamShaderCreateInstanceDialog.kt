package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.ui.DreamShaderUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

internal class DreamShaderCreateInstanceDialog(
    private val project: Project
) : DialogWrapper(project) {
    private val pathField = JBTextField("DShader/Instances/MI_NewInstance.dsi")
    private val parentField = JBTextField()

    init {
        title = DreamShaderBundle.message("templates.dialog.instance.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(JBUI.scale(12), JBUI.scale(12))).apply {
            DreamShaderUi.installSurface(this)
        }

        fun fieldRow(labelKey: String, field: JBTextField, tooltipKey: String): JComponent {
            field.toolTipText = DreamShaderBundle.message(tooltipKey)
            field.emptyText.text = DreamShaderBundle.message(tooltipKey)
            return DreamShaderUi.formRow(DreamShaderBundle.message(labelKey), field, DreamShaderBundle.message(tooltipKey))
        }

        val fields = DreamShaderUi.vertical(
            8,
            fieldRow("templates.dialog.instance.path.label", pathField, "templates.dialog.instance.path.tooltip"),
            fieldRow("templates.dialog.instance.parent.label", parentField, "templates.dialog.instance.parent.tooltip")
        )

        root.add(
            DreamShaderUi.section(
                DreamShaderBundle.message("templates.dialog.instance.title"),
                DreamShaderBundle.message("templates.dialog.instance.parent.tooltip"),
                fields
            ),
            BorderLayout.CENTER
        )

        return root
    }

    override fun doValidate(): ValidationInfo? {
        if (parentField.text.trim().isBlank()) {
            return ValidationInfo(DreamShaderBundle.message("templates.error.parentRequired"), parentField)
        }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = pathField

    fun toRequest(): DreamShaderInstanceRequest {
        return DreamShaderInstanceRequest(
            targetPath = pathField.text.trim(),
            parent = parentField.text.trim()
        )
    }
}

internal data class DreamShaderInstanceRequest(
    val targetPath: String,
    val parent: String
)
