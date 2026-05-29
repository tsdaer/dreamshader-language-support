package com.github.tsdaer.dreamshaderlanguagesupport.language.settings

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.FlowLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.util.Locale
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.event.TableModelEvent
import javax.swing.event.TableModelListener
import javax.swing.table.DefaultTableModel

class DreamShaderSettingsConfigurable(
    private val project: Project
) : Configurable {
    private var panel: JPanel? = null
    private var projectRootField: JBTextField? = null
    private var manifestPathField: JBTextField? = null
    private var showStatusBarBox: JBCheckBox? = null
    private var enableCodeLensBox: JBCheckBox? = null
    private var outArgumentPlaceholderSuffixField: JBTextField? = null
    private var preferredImportExtensionCombo: JComboBox<String>? = null
    private var autoUpdatePreferredImportExtensionBox: JBCheckBox? = null
    private var importExtensionPreviewLabel: JLabel? = null
    private var packageSearchGitHubTokenField: JBTextField? = null
    private var hoverDocumentationOverridesTableModel: DefaultTableModel? = null
    private var hoverDocumentationOverridesTable: JTable? = null
    private var hoverDocumentationOverridesStatusLabel: JLabel? = null
    private var hoverDocumentationOverridesRemoveButton: JButton? = null
    private var recompileCurrentCommandField: JBTextField? = null
    private var recompileAllCommandField: JBTextField? = null
    private var cleanGeneratedCommandField: JBTextField? = null

    override fun getDisplayName(): String = DreamShaderBundle.message("settings.title")

    override fun createComponent(): JComponent {
        if (panel != null) return panel as JPanel

        val root = JPanel(GridBagLayout())
        var row = 0

        fun addLabelAndField(label: String, field: JBTextField, tooltip: String? = null) {
            val labelConstraints = GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(4, 4, 2, 8)
            }
            root.add(JLabel(label), labelConstraints)

            val fieldConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(4, 0, 2, 4)
            }
            field.toolTipText = tooltip
            root.add(field, fieldConstraints)
            row++
        }

        fun addLabelAndComboBox(label: String, combo: JComboBox<String>, tooltip: String? = null) {
            val labelConstraints = GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(4, 4, 2, 8)
            }
            root.add(JLabel(label), labelConstraints)

            val comboConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(4, 0, 2, 4)
            }
            combo.toolTipText = tooltip
            root.add(combo, comboConstraints)
            row++
        }

        fun addCheckBox(box: JBCheckBox, tooltip: String? = null) {
            val constraints = GridBagConstraints().apply {
                gridx = 0
                gridy = row
                gridwidth = 2
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(4, 4, 2, 4)
            }
            box.toolTipText = tooltip
            root.add(box, constraints)
            row++
        }

        fun addDescription(text: String) {
            val constraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0, 0, 4, 4)
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }
            val label = JLabel(text)
            label.name = IMPORT_EXTENSION_PREVIEW_LABEL_NAME
            root.add(label, constraints)
            importExtensionPreviewLabel = label
            row++
        }

        fun addHoverOverridesTable(label: String, tooltip: String? = null) {
            val labelConstraints = GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.NORTHWEST
                insets = JBUI.insets(4, 4, 2, 8)
            }
            root.add(JLabel(label), labelConstraints)

            val model = object : DefaultTableModel(arrayOf(
                DreamShaderBundle.message("settings.hoverDocsOverrides.column.path"),
                DreamShaderBundle.message("settings.hoverDocsOverrides.column.content")
            ), 0) {
                override fun isCellEditable(row: Int, column: Int): Boolean = true
            }
            hoverDocumentationOverridesTableModel = model

            val table = JTable(model).apply {
                name = HOVER_DOCS_TABLE_NAME
                fillsViewportHeight = true
                rowSelectionAllowed = true
                columnSelectionAllowed = false
                selectionModel.selectionMode = javax.swing.ListSelectionModel.SINGLE_SELECTION
                toolTipText = tooltip
            }
            hoverDocumentationOverridesTable = table

            val scrollPane = com.intellij.ui.components.JBScrollPane(table).apply {
                border = JBUI.Borders.empty()
                toolTipText = tooltip
                preferredSize = JBUI.size(320, 140)
            }

            val tableConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.BOTH
                insets = JBUI.insets(4, 0, 2, 4)
            }
            root.add(scrollPane, tableConstraints)
            row++

            val actions = JPanel(FlowLayout(FlowLayout.LEFT, 0, 0)).apply {
                isOpaque = false
            }
            val addButton = JButton(DreamShaderBundle.message("settings.hoverDocsOverrides.addRowButton")).apply {
                name = HOVER_DOCS_ADD_ROW_BUTTON_NAME
                addActionListener {
                    hoverDocumentationOverridesTableModel?.addRow(arrayOf("", ""))
                    val rowIndex = (hoverDocumentationOverridesTableModel?.rowCount ?: 1) - 1
                    if (rowIndex >= 0) {
                        hoverDocumentationOverridesTable?.setRowSelectionInterval(rowIndex, rowIndex)
                    }
                    refreshHoverOverridesStatus()
                    refreshHoverOverridesButtonsState()
                }
            }
            val removeButton = JButton(DreamShaderBundle.message("settings.hoverDocsOverrides.removeRowButton")).apply {
                name = HOVER_DOCS_REMOVE_ROW_BUTTON_NAME
                addActionListener {
                    val selectedRow = hoverDocumentationOverridesTable?.selectedRow ?: -1
                    if (selectedRow >= 0) {
                        hoverDocumentationOverridesTableModel?.removeRow(selectedRow)
                        val lastRow = (hoverDocumentationOverridesTableModel?.rowCount ?: 0) - 1
                        if (lastRow >= 0) {
                            val next = selectedRow.coerceAtMost(lastRow)
                            hoverDocumentationOverridesTable?.setRowSelectionInterval(next, next)
                        }
                        refreshHoverOverridesStatus()
                        refreshHoverOverridesButtonsState()
                    }
                }
            }
            hoverDocumentationOverridesRemoveButton = removeButton

            val insertSampleButton = JButton(DreamShaderBundle.message("settings.hoverDocsOverrides.insertSampleButton")).apply {
                name = HOVER_DOCS_INSERT_SAMPLE_BUTTON_NAME
                addActionListener {
                    val sampleLine = DreamShaderBundle.message("settings.hoverDocsOverrides.sampleLine")
                    val separatorIndex = sampleLine.indexOf('=')
                    if (separatorIndex > 0) {
                        val sampleKey = sampleLine.substring(0, separatorIndex).trim()
                        val sampleValue = sampleLine.substring(separatorIndex + 1).trim()
                        upsertHoverOverrideRow(sampleKey, sampleValue)
                    }
                    refreshHoverOverridesStatus()
                    refreshHoverOverridesButtonsState()
                }
            }

            actions.add(addButton)
            actions.add(removeButton)
            actions.add(insertSampleButton)

            val actionsConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0, 0, 2, 4)
            }
            root.add(actions, actionsConstraints)
            row++

            val status = JLabel().apply {
                name = HOVER_DOCS_STATUS_LABEL_NAME
            }
            hoverDocumentationOverridesStatusLabel = status
            val statusConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0, 0, 6, 4)
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }
            root.add(status, statusConstraints)
            row++

            model.addTableModelListener(object : TableModelListener {
                override fun tableChanged(e: TableModelEvent?) {
                    refreshHoverOverridesStatus()
                    refreshHoverOverridesButtonsState()
                }
            })
            table.selectionModel.addListSelectionListener {
                refreshHoverOverridesButtonsState()
            }
        }

        projectRootField = JBTextField()
        manifestPathField = JBTextField()
        showStatusBarBox = JBCheckBox(DreamShaderBundle.message("settings.showStatusBar.checkbox"))
        enableCodeLensBox = JBCheckBox(DreamShaderBundle.message("settings.enableCodeLens.checkbox"))
        outArgumentPlaceholderSuffixField = JBTextField()
        preferredImportExtensionCombo = JComboBox(arrayOf(".dsh", ".dsf", ".dsm"))
        preferredImportExtensionCombo?.name = PREFERRED_IMPORT_EXTENSION_COMBO_NAME
        autoUpdatePreferredImportExtensionBox = JBCheckBox(
            DreamShaderBundle.message("settings.autoUpdatePreferredImportExtension.checkbox")
        )
        autoUpdatePreferredImportExtensionBox?.name = AUTO_UPDATE_PREFERRED_IMPORT_EXTENSION_CHECKBOX_NAME
        packageSearchGitHubTokenField = JBTextField()
        recompileCurrentCommandField = JBTextField()
        recompileAllCommandField = JBTextField()
        cleanGeneratedCommandField = JBTextField()

        addLabelAndField(
            DreamShaderBundle.message("settings.projectRoot.label"),
            projectRootField as JBTextField,
            DreamShaderBundle.message("settings.projectRoot.tooltip")
        )
        addLabelAndField(
            DreamShaderBundle.message("settings.manifestPath.label"),
            manifestPathField as JBTextField,
            DreamShaderBundle.message("settings.manifestPath.tooltip")
        )
        addCheckBox(
            showStatusBarBox as JBCheckBox,
            DreamShaderBundle.message("settings.showStatusBar.tooltip")
        )
        addCheckBox(
            enableCodeLensBox as JBCheckBox,
            DreamShaderBundle.message("settings.enableCodeLens.tooltip")
        )
        addLabelAndField(
            DreamShaderBundle.message("settings.outArgumentPlaceholderSuffix.label"),
            outArgumentPlaceholderSuffixField as JBTextField,
            DreamShaderBundle.message("settings.outArgumentPlaceholderSuffix.tooltip")
        )
        addLabelAndComboBox(
            DreamShaderBundle.message("settings.preferredImportExtension.label"),
            preferredImportExtensionCombo as JComboBox<String>,
            DreamShaderBundle.message("settings.preferredImportExtension.tooltip")
        )
        addCheckBox(
            autoUpdatePreferredImportExtensionBox as JBCheckBox,
            DreamShaderBundle.message("settings.autoUpdatePreferredImportExtension.tooltip")
        )
        addDescription("")
        addLabelAndField(
            DreamShaderBundle.message("settings.packageSearchGitHubToken.label"),
            packageSearchGitHubTokenField as JBTextField,
            DreamShaderBundle.message("settings.packageSearchGitHubToken.tooltip")
        )
        addHoverOverridesTable(
            DreamShaderBundle.message("settings.hoverDocsOverrides.label"),
            DreamShaderBundle.message("settings.hoverDocsOverrides.tooltip")
        )
        addLabelAndField(
            DreamShaderBundle.message("settings.bridgeRecompileCurrent.label"),
            recompileCurrentCommandField as JBTextField,
            DreamShaderBundle.message("settings.bridgeRecompileCurrent.tooltip")
        )
        addLabelAndField(
            DreamShaderBundle.message("settings.bridgeRecompileAll.label"),
            recompileAllCommandField as JBTextField,
            DreamShaderBundle.message("settings.bridgeRecompileAll.tooltip")
        )
        addLabelAndField(
            DreamShaderBundle.message("settings.bridgeCleanGenerated.label"),
            cleanGeneratedCommandField as JBTextField,
            DreamShaderBundle.message("settings.bridgeCleanGenerated.tooltip")
        )

        preferredImportExtensionCombo?.addActionListener {
            refreshImportExtensionPreview()
        }
        autoUpdatePreferredImportExtensionBox?.addActionListener {
            refreshImportExtensionPreview()
        }

        val spacer = JPanel()
        val spacerConstraints = GridBagConstraints().apply {
            gridx = 0
            gridy = row
            gridwidth = 2
            weighty = 1.0
            fill = GridBagConstraints.BOTH
        }
        root.add(spacer, spacerConstraints)

        panel = root
        reset()
        return root
    }

    override fun isModified(): Boolean {
        val state = project.getService(DreamShaderProjectSettings::class.java).state
        return projectRootField?.text.orEmpty() != state.projectRoot ||
            manifestPathField?.text.orEmpty() != state.materialExpressionManifestPath ||
            showStatusBarBox?.isSelected != state.showStatusBar ||
            enableCodeLensBox?.isSelected != state.enableCodeLens ||
            outArgumentPlaceholderSuffixField?.text.orEmpty() != state.outArgumentPlaceholderSuffix ||
            normalizePreferredImportExtension(preferredImportExtensionCombo?.selectedItem as? String) != normalizePreferredImportExtension(state.preferredImportExtension) ||
            (autoUpdatePreferredImportExtensionBox?.isSelected ?: false) != state.autoUpdatePreferredImportExtension ||
            packageSearchGitHubTokenField?.text.orEmpty() != state.packageStoreGitHubToken ||
            buildHoverOverridesRawFromTable() != state.hoverDocumentationOverrides.trim() ||
            recompileCurrentCommandField?.text.orEmpty() != state.bridgeRecompileCurrentCommand ||
            recompileAllCommandField?.text.orEmpty() != state.bridgeRecompileAllCommand ||
            cleanGeneratedCommandField?.text.orEmpty() != state.bridgeCleanGeneratedShadersCommand
    }

    override fun apply() {
        val state = project.getService(DreamShaderProjectSettings::class.java).state
        val oldEnableCodeLens = state.enableCodeLens

        state.projectRoot = projectRootField?.text.orEmpty().trim()
        state.materialExpressionManifestPath = manifestPathField?.text.orEmpty().trim()
        state.showStatusBar = showStatusBarBox?.isSelected ?: true
        state.enableCodeLens = enableCodeLensBox?.isSelected ?: true
        state.outArgumentPlaceholderSuffix = normalizeOutPlaceholderSuffix(outArgumentPlaceholderSuffixField?.text.orEmpty())
        state.preferredImportExtension = normalizePreferredImportExtension(preferredImportExtensionCombo?.selectedItem as? String)
        state.autoUpdatePreferredImportExtension = autoUpdatePreferredImportExtensionBox?.isSelected ?: false
        state.packageStoreGitHubToken = packageSearchGitHubTokenField?.text.orEmpty().trim()
        state.hoverDocumentationOverrides = buildHoverOverridesRawFromTable()
        state.bridgeRecompileCurrentCommand = recompileCurrentCommandField?.text.orEmpty().trim()
        state.bridgeRecompileAllCommand = recompileAllCommandField?.text.orEmpty().trim()
        state.bridgeCleanGeneratedShadersCommand = cleanGeneratedCommandField?.text.orEmpty().trim()

        if (oldEnableCodeLens != state.enableCodeLens) {
            ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
        }
    }

    override fun reset() {
        val state = project.getService(DreamShaderProjectSettings::class.java).state
        projectRootField?.text = state.projectRoot
        manifestPathField?.text = state.materialExpressionManifestPath
        showStatusBarBox?.isSelected = state.showStatusBar
        enableCodeLensBox?.isSelected = state.enableCodeLens
        outArgumentPlaceholderSuffixField?.text = state.outArgumentPlaceholderSuffix
        preferredImportExtensionCombo?.selectedItem = ".${normalizePreferredImportExtension(state.preferredImportExtension)}"
        autoUpdatePreferredImportExtensionBox?.isSelected = state.autoUpdatePreferredImportExtension
        packageSearchGitHubTokenField?.text = state.packageStoreGitHubToken
        loadHoverOverridesFromRaw(state.hoverDocumentationOverrides)
        recompileCurrentCommandField?.text = state.bridgeRecompileCurrentCommand
        recompileAllCommandField?.text = state.bridgeRecompileAllCommand
        cleanGeneratedCommandField?.text = state.bridgeCleanGeneratedShadersCommand
        refreshImportExtensionPreview()
        refreshHoverOverridesStatus()
        refreshHoverOverridesButtonsState()
    }

    override fun disposeUIResources() {
        panel = null
        projectRootField = null
        manifestPathField = null
        showStatusBarBox = null
        enableCodeLensBox = null
        outArgumentPlaceholderSuffixField = null
        preferredImportExtensionCombo = null
        autoUpdatePreferredImportExtensionBox = null
        importExtensionPreviewLabel = null
        packageSearchGitHubTokenField = null
        hoverDocumentationOverridesTableModel = null
        hoverDocumentationOverridesTable = null
        hoverDocumentationOverridesStatusLabel = null
        hoverDocumentationOverridesRemoveButton = null
        recompileCurrentCommandField = null
        recompileAllCommandField = null
        cleanGeneratedCommandField = null
    }

    private fun normalizeOutPlaceholderSuffix(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "Out"
        val cleaned = trimmed.replace(Regex("[^A-Za-z0-9_]"), "_")
        if (cleaned.isBlank()) return "Out"
        return if (cleaned.first().isDigit()) "_$cleaned" else cleaned
    }

    private fun normalizePreferredImportExtension(raw: String?): String {
        val normalized = raw.orEmpty().trim().removePrefix(".").lowercase()
        return if (normalized in SUPPORTED_IMPORT_EXTENSIONS) normalized else "dsh"
    }

    private fun refreshImportExtensionPreview() {
        val extension = ".${normalizePreferredImportExtension(preferredImportExtensionCombo?.selectedItem as? String)}"
        val autoUpdate = autoUpdatePreferredImportExtensionBox?.isSelected ?: false
        importExtensionPreviewLabel?.text = buildImportExtensionPreviewText(extension, autoUpdate)
    }

    private fun refreshHoverOverridesStatus() {
        val parsed = DreamShaderHoverOverrideParser.parse(buildHoverOverridesValidationRawFromTable())
        hoverDocumentationOverridesStatusLabel?.text = buildHoverOverridesStatusText(parsed)
    }

    private fun refreshHoverOverridesButtonsState() {
        hoverDocumentationOverridesRemoveButton?.isEnabled = (hoverDocumentationOverridesTable?.selectedRow ?: -1) >= 0
    }

    private fun upsertHoverOverrideRow(key: String, value: String) {
        val normalizedTarget = key.trim().lowercase(Locale.ROOT)
        val model = hoverDocumentationOverridesTableModel ?: return
        var foundRow = -1
        for (i in 0 until model.rowCount) {
            val existingKey = model.getValueAt(i, 0)?.toString().orEmpty().trim().lowercase(Locale.ROOT)
            if (existingKey == normalizedTarget) {
                foundRow = i
                break
            }
        }
        if (foundRow >= 0) {
            model.setValueAt(key, foundRow, 0)
            model.setValueAt(value, foundRow, 1)
            hoverDocumentationOverridesTable?.setRowSelectionInterval(foundRow, foundRow)
        } else {
            model.addRow(arrayOf(key, value))
            val rowIndex = model.rowCount - 1
            hoverDocumentationOverridesTable?.setRowSelectionInterval(rowIndex, rowIndex)
        }
    }

    private fun buildHoverOverridesRawFromTable(): String {
        return buildHoverOverridesRawFromTable(includeIncompleteRows = false)
    }

    private fun buildHoverOverridesValidationRawFromTable(): String {
        return buildHoverOverridesRawFromTable(includeIncompleteRows = true)
    }

    private fun buildHoverOverridesRawFromTable(includeIncompleteRows: Boolean): String {
        val model = hoverDocumentationOverridesTableModel ?: return ""
        val lines = mutableListOf<String>()
        for (i in 0 until model.rowCount) {
            val key = model.getValueAt(i, 0)?.toString().orEmpty().trim()
            val value = model.getValueAt(i, 1)?.toString().orEmpty().trim()
            if (key.isBlank() && value.isBlank()) continue
            if (key.isBlank() || value.isBlank()) {
                if (!includeIncompleteRows) continue
            }
            lines += "$key=$value"
        }
        return lines.joinToString("\n").trim()
    }

    private fun loadHoverOverridesFromRaw(raw: String) {
        val model = hoverDocumentationOverridesTableModel ?: return
        model.rowCount = 0
        val parsed = DreamShaderHoverOverrideParser.parse(raw)
        parsed.entries.forEach { (key, value) ->
            model.addRow(arrayOf(key, value))
        }
    }

    private fun buildHoverOverridesStatusText(parsed: DreamShaderHoverOverrideParseResult): String {
        if (parsed.issues.isNotEmpty()) {
            val firstIssue = parsed.issues.first()
            val reason = when (firstIssue.type) {
                DreamShaderHoverOverrideIssueType.MISSING_EQUALS ->
                    DreamShaderBundle.message("settings.hoverDocsOverrides.status.issue.missingEquals")
                DreamShaderHoverOverrideIssueType.EMPTY_KEY ->
                    DreamShaderBundle.message("settings.hoverDocsOverrides.status.issue.emptyKey")
                DreamShaderHoverOverrideIssueType.EMPTY_VALUE ->
                    DreamShaderBundle.message("settings.hoverDocsOverrides.status.issue.emptyValue")
            }
            return DreamShaderBundle.message(
                "settings.hoverDocsOverrides.status.invalid",
                parsed.entries.size,
                parsed.issues.size,
                firstIssue.lineNumber,
                reason
            )
        }
        return DreamShaderBundle.message(
            "settings.hoverDocsOverrides.status.valid",
            parsed.entries.size,
            parsed.ignoredLineCount,
            parsed.duplicateKeyCount
        )
    }

    private fun buildImportExtensionPreviewText(extension: String, autoUpdate: Boolean): String {
        val toggleText = DreamShaderBundle.message(
            if (autoUpdate) "settings.toggle.enabled" else "settings.toggle.disabled"
        )
        return DreamShaderBundle.message(
            "settings.preferredImportExtension.preview",
            extension,
            toggleText
        )
    }

    internal fun testNormalizeOutPlaceholderSuffix(raw: String): String = normalizeOutPlaceholderSuffix(raw)
    internal fun testNormalizePreferredImportExtension(raw: String?): String = normalizePreferredImportExtension(raw)
    internal fun testBuildImportExtensionPreviewText(rawExtension: String?, autoUpdate: Boolean): String =
        buildImportExtensionPreviewText(".${normalizePreferredImportExtension(rawExtension)}", autoUpdate)
    internal fun testBuildHoverOverridesStatusText(raw: String): String =
        buildHoverOverridesStatusText(DreamShaderHoverOverrideParser.parse(raw))

    companion object {
        private val SUPPORTED_IMPORT_EXTENSIONS = setOf("dsh", "dsf", "dsm")
        internal const val PREFERRED_IMPORT_EXTENSION_COMBO_NAME = "dreamshader.preferredImportExtensionCombo"
        internal const val AUTO_UPDATE_PREFERRED_IMPORT_EXTENSION_CHECKBOX_NAME = "dreamshader.autoUpdatePreferredImportExtensionCheckbox"
        internal const val IMPORT_EXTENSION_PREVIEW_LABEL_NAME = "dreamshader.importExtensionPreviewLabel"
        internal const val HOVER_DOCS_TABLE_NAME = "dreamshader.hoverDocsOverrides.table"
        internal const val HOVER_DOCS_ADD_ROW_BUTTON_NAME = "dreamshader.hoverDocsOverrides.addRowButton"
        internal const val HOVER_DOCS_REMOVE_ROW_BUTTON_NAME = "dreamshader.hoverDocsOverrides.removeRowButton"
        internal const val HOVER_DOCS_INSERT_SAMPLE_BUTTON_NAME = "dreamshader.hoverDocsOverrides.insertSampleButton"
        internal const val HOVER_DOCS_STATUS_LABEL_NAME = "dreamshader.hoverDocsOverrides.statusLabel"
    }
}
