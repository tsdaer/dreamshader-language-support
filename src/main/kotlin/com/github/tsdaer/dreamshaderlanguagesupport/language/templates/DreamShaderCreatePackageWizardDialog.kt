package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.ui.DreamShaderUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel

internal class DreamShaderCreatePackageWizardDialog(
    private val project: Project
) : DialogWrapper(project) {
    private val nameField = JBTextField()
    private val displayNameField = JBTextField()
    private val descriptionField = JBTextField()
    private val namespaceField = JBTextField()
    private val authorField = JBTextField()
    private val repositoryField = JBTextField()
    private val includeExampleBox = JBCheckBox(DreamShaderBundle.message("templates.wizard.includeExample.label"), true)

    init {
        title = DreamShaderBundle.message("templates.wizard.title")
        init()
    }

    override fun createCenterPanel(): JComponent {
        val root = JPanel(BorderLayout(JBUI.scale(12), JBUI.scale(12))).apply {
            preferredSize = Dimension(680, 480)
            DreamShaderUi.installSurface(this)
        }
        fun fieldRow(labelKey: String, field: JBTextField, tooltipKey: String? = null): JComponent {
            if (tooltipKey != null) {
                field.toolTipText = DreamShaderBundle.message(tooltipKey)
                field.emptyText.text = DreamShaderBundle.message(tooltipKey)
            }
            return DreamShaderUi.formRow(DreamShaderBundle.message(labelKey), field, tooltipKey?.let(DreamShaderBundle::message))
        }

        val identity = DreamShaderUi.vertical(
            8,
            fieldRow("templates.wizard.name.label", nameField, "templates.wizard.name.tooltip"),
            fieldRow("templates.wizard.displayName.label", displayNameField, "templates.wizard.displayName.tooltip"),
            fieldRow("templates.wizard.description.label", descriptionField, "templates.wizard.description.tooltip")
        )

        val metadata = DreamShaderUi.vertical(
            8,
            fieldRow("templates.wizard.namespace.label", namespaceField, "templates.wizard.namespace.tooltip"),
            fieldRow("templates.wizard.author.label", authorField, "templates.wizard.author.tooltip"),
            fieldRow("templates.wizard.repository.label", repositoryField, "templates.wizard.repository.tooltip")
        )
        includeExampleBox.toolTipText = DreamShaderBundle.message("templates.wizard.includeExample.tooltip")
        val options = DreamShaderUi.checkRow(includeExampleBox, DreamShaderBundle.message("templates.wizard.includeExample.tooltip"))

        root.add(DreamShaderUi.vertical(
            12,
            DreamShaderUi.section(
                DreamShaderBundle.message("templates.wizard.title"),
                DreamShaderBundle.message("templates.wizard.name.tooltip"),
                identity
            ),
            DreamShaderUi.section(
                DreamShaderBundle.message("templates.wizard.namespace.label"),
                DreamShaderBundle.message("templates.wizard.repository.tooltip"),
                metadata
            ),
            DreamShaderUi.section(
                DreamShaderBundle.message("templates.wizard.includeExample.label"),
                DreamShaderBundle.message("templates.wizard.includeExample.tooltip"),
                options
            )
        ), BorderLayout.CENTER)

        return root
    }

    override fun doValidate(): ValidationInfo? {
        val rawName = nameField.text.trim()
        if (rawName.isBlank()) {
            return ValidationInfo(DreamShaderBundle.message("templates.wizard.error.nameRequired"), nameField)
        }
        if (DreamShaderTemplateService(project).normalizePackageName(rawName) == null) {
            return ValidationInfo(DreamShaderBundle.message("templates.error.invalidPackageName", rawName), nameField)
        }
        return null
    }

    override fun getPreferredFocusedComponent(): JComponent = nameField

    fun toRequest(): DreamShaderPackageScaffoldRequest {
        return DreamShaderPackageScaffoldRequest(
            name = nameField.text.trim(),
            displayName = displayNameField.text.trim(),
            description = descriptionField.text.trim(),
            namespaceName = namespaceField.text.trim(),
            author = authorField.text.trim(),
            repository = repositoryField.text.trim(),
            includeExample = includeExampleBox.isSelected
        )
    }
}
