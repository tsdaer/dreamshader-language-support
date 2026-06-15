package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JComponent
import javax.swing.JLabel
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
        val root = JPanel(GridBagLayout())
        root.preferredSize = Dimension(560, 260)
        var row = 0

        fun addField(labelKey: String, field: JBTextField, tooltipKey: String? = null) {
            val labelConstraints = GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(4, 4, 4, 8)
            }
            root.add(JLabel(DreamShaderBundle.message(labelKey)), labelConstraints)

            val fieldConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(4, 0, 4, 4)
            }
            if (tooltipKey != null) {
                field.toolTipText = DreamShaderBundle.message(tooltipKey)
            }
            root.add(field, fieldConstraints)
            row++
        }

        addField("templates.wizard.name.label", nameField, "templates.wizard.name.tooltip")
        addField("templates.wizard.displayName.label", displayNameField, "templates.wizard.displayName.tooltip")
        addField("templates.wizard.description.label", descriptionField, "templates.wizard.description.tooltip")
        addField("templates.wizard.namespace.label", namespaceField, "templates.wizard.namespace.tooltip")
        addField("templates.wizard.author.label", authorField, "templates.wizard.author.tooltip")
        addField("templates.wizard.repository.label", repositoryField, "templates.wizard.repository.tooltip")

        val checkConstraints = GridBagConstraints().apply {
            gridx = 1
            gridy = row
            anchor = GridBagConstraints.WEST
            insets = JBUI.insets(4, 0, 4, 4)
        }
        includeExampleBox.toolTipText = DreamShaderBundle.message("templates.wizard.includeExample.tooltip")
        root.add(includeExampleBox, checkConstraints)

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
