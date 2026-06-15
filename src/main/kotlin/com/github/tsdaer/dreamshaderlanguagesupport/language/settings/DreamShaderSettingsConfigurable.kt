package com.github.tsdaer.dreamshaderlanguagesupport.language.settings

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgePathResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderUnrealSourceLocator
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderDocumentationData
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.*
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.io.File
import java.util.*
import javax.swing.*
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class DreamShaderSettingsConfigurable(
    private val project: Project
) : Configurable {
    private var panel: JPanel? = null
    private var projectRootField: TextFieldWithBrowseButton? = null
    private var manifestPathField: TextFieldWithBrowseButton? = null
    private var unrealSourceRootField: TextFieldWithBrowseButton? = null
    private var materialExpressionScanEnabledBox: JBCheckBox? = null
    private var materialExpressionScanCachePathField: TextFieldWithBrowseButton? = null
    private var showStatusBarBox: JBCheckBox? = null
    private var enableCodeLensBox: JBCheckBox? = null
    private var enableInlayParameterHintsBox: JBCheckBox? = null
    private var outArgumentPlaceholderSuffixField: JBTextField? = null
    private var preferredImportExtensionCombo: JComboBox<String>? = null
    private var autoUpdatePreferredImportExtensionBox: JBCheckBox? = null
    private var importExtensionPreviewLabel: JLabel? = null
    private var packageSearchGitHubTokenField: JBTextField? = null
    private var hoverDocumentationOverridesTableModel: HoverOverrideTableModel? = null
    private var hoverDocumentationOverridesTable: JTable? = null
    private var hoverDocumentationOverridesStatusLabel: JLabel? = null
    private var hoverDocumentationOverridesRemoveButton: JButton? = null
    private var hoverDocumentationOverridesEditPathButton: JButton? = null
    private var hoverDocumentationOverridesResetBuiltinsButton: JButton? = null
    private var recompileCurrentCommandField: JBTextField? = null
    private var recompileAllCommandField: JBTextField? = null
    private var cleanGeneratedCommandField: JBTextField? = null
    private var previewAutoRefreshDelayField: JBTextField? = null
    private var bridgeStatusLabel: JLabel? = null

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

        fun addLabelAndPathField(
            label: String,
            field: TextFieldWithBrowseButton,
            tooltip: String? = null
        ) {
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

        fun addLabelPathFieldAndButton(
            label: String,
            field: TextFieldWithBrowseButton,
            button: JButton,
            tooltip: String? = null
        ) {
            val labelConstraints = GridBagConstraints().apply {
                gridx = 0
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(4, 4, 2, 8)
            }
            root.add(JLabel(label), labelConstraints)

            val rowPanel = JPanel(GridBagLayout())
            val fieldConstraints = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
            }
            field.toolTipText = tooltip
            rowPanel.add(field, fieldConstraints)
            val buttonConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = 0
                insets = JBUI.insetsLeft(6)
            }
            rowPanel.add(button, buttonConstraints)

            val panelConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(4, 0, 2, 4)
            }
            root.add(rowPanel, panelConstraints)
            row++
        }

        fun addInlineInfoLabel(name: String): JLabel {
            val constraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0, 0, 4, 4)
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }
            val label = JLabel().apply {
                this.name = name
                foreground = UIUtil.getContextHelpForeground()
            }
            root.add(label, constraints)
            row++
            return label
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

            val model = HoverOverrideTableModel(
                resetLabel = DreamShaderBundle.message("settings.hoverDocsOverrides.resetRowButton")
            )
            hoverDocumentationOverridesTableModel = model

            val table = object : JTable(model) {
                override fun prepareRenderer(renderer: TableCellRenderer, row: Int, column: Int): Component {
                    val component = super.prepareRenderer(renderer, row, column)
                    if (!isRowSelected(row)) {
                        val modelRow = convertRowIndexToModel(row)
                        component.background = if (model.isBuiltinChanged(modelRow)) {
                            HOVER_DOCS_CHANGED_BACKGROUND
                        } else {
                            background
                        }
                    }
                    return component
                }
            }.apply {
                name = HOVER_DOCS_TABLE_NAME
                fillsViewportHeight = true
                rowSelectionAllowed = true
                columnSelectionAllowed = false
                selectionModel.selectionMode = ListSelectionModel.SINGLE_SELECTION
                toolTipText = tooltip
                putClientProperty("terminateEditOnFocusLost", true)
            }
            hoverDocumentationOverridesTable = table
            table.addMouseListener(object : MouseAdapter() {
                override fun mouseClicked(e: MouseEvent) {
                    if (e.button != MouseEvent.BUTTON1 || e.clickCount != 2) return
                    val viewRow = table.rowAtPoint(e.point)
                    val viewColumn = table.columnAtPoint(e.point)
                    if (viewRow < 0 || viewColumn != HoverOverrideTableModel.COLUMN_PATH) return
                    val modelRow = table.convertRowIndexToModel(viewRow)
                    openPathEditorDialog(modelRow)
                }
            })

            val resetRenderer = HoverResetButtonRenderer()
            val resetEditor = HoverResetButtonEditor { modelRow ->
                model.resetBuiltinAt(modelRow)
                refreshHoverOverridesStatus()
                refreshHoverOverridesButtonsState()
                table.repaint()
            }
            val editRenderer = HoverEditButtonRenderer(
                label = DreamShaderBundle.message("settings.hoverDocsOverrides.editContentButton")
            )
            val editEditor = HoverEditButtonEditor(
                label = DreamShaderBundle.message("settings.hoverDocsOverrides.editContentButton")
            ) { modelRow ->
                val rowData = model.rowAt(modelRow) ?: return@HoverEditButtonEditor
                val edited = HoverDocContentEditorDialog(
                    initialText = rowData.content
                ).showAndGetText()
                if (edited != null) {
                    model.setContentAt(modelRow, edited)
                    refreshHoverOverridesStatus()
                    refreshHoverOverridesButtonsState()
                    table.repaint()
                }
            }

            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_PATH).preferredWidth = 280
            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_CONTENT).preferredWidth = 120
            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_CONTENT).maxWidth = 160
            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_RESET).preferredWidth = 70
            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_RESET).maxWidth = 90
            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_CONTENT).cellRenderer = editRenderer
            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_CONTENT).cellEditor = editEditor
            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_RESET).cellRenderer = resetRenderer
            table.columnModel.getColumn(HoverOverrideTableModel.COLUMN_RESET).cellEditor = resetEditor
            table.setDefaultRenderer(String::class.java, object : DefaultTableCellRenderer() {
                override fun getTableCellRendererComponent(
                    table: JTable,
                    value: Any?,
                    isSelected: Boolean,
                    hasFocus: Boolean,
                    row: Int,
                    column: Int
                ): Component {
                    val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
                    if (!isSelected) {
                        val modelRow = table.convertRowIndexToModel(row)
                        foreground = if (column == HoverOverrideTableModel.COLUMN_PATH && model.isBuiltinRow(modelRow)) {
                            HOVER_DOCS_BUILTIN_PATH_FOREGROUND
                        } else {
                            table.foreground
                        }
                    }
                    return component
                }
            })

            val scrollPane = com.intellij.ui.components.JBScrollPane(table).apply {
                border = JBUI.Borders.empty()
                toolTipText = tooltip
                preferredSize = JBUI.size(320, 180)
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
                    stopHoverOverridesTableEditing()
                    val insertedRow = model.addCustomRow()
                    if (insertedRow >= 0) {
                        hoverDocumentationOverridesTable?.setRowSelectionInterval(insertedRow, insertedRow)
                    }
                    refreshHoverOverridesStatus()
                    refreshHoverOverridesButtonsState()
                }
            }
            val removeButton = JButton(DreamShaderBundle.message("settings.hoverDocsOverrides.removeRowButton")).apply {
                name = HOVER_DOCS_REMOVE_ROW_BUTTON_NAME
                addActionListener {
                    stopHoverOverridesTableEditing()
                    val selectedRow = hoverDocumentationOverridesTable?.selectedRow ?: -1
                    if (selectedRow >= 0) {
                        val modelRow = hoverDocumentationOverridesTable?.convertRowIndexToModel(selectedRow) ?: -1
                        if (modelRow >= 0 && model.removeCustomRow(modelRow)) {
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
            }
            hoverDocumentationOverridesRemoveButton = removeButton

            val editPathButton = JButton(DreamShaderBundle.message("settings.hoverDocsOverrides.editPathButton")).apply {
                name = HOVER_DOCS_EDIT_PATH_BUTTON_NAME
                addActionListener {
                    stopHoverOverridesTableEditing()
                    val selectedRow = hoverDocumentationOverridesTable?.selectedRow ?: -1
                    if (selectedRow < 0) return@addActionListener
                    openPathEditorDialog(hoverDocumentationOverridesTable?.convertRowIndexToModel(selectedRow) ?: -1)
                }
            }
            hoverDocumentationOverridesEditPathButton = editPathButton

            val insertSampleButton = JButton(DreamShaderBundle.message("settings.hoverDocsOverrides.insertSampleButton")).apply {
                name = HOVER_DOCS_INSERT_SAMPLE_BUTTON_NAME
                addActionListener {
                    stopHoverOverridesTableEditing()
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

            val resetBuiltinsButton = JButton(DreamShaderBundle.message("settings.hoverDocsOverrides.resetBuiltinsButton")).apply {
                name = HOVER_DOCS_RESET_BUILTINS_BUTTON_NAME
                addActionListener {
                    stopHoverOverridesTableEditing()
                    model.resetAllBuiltinOverrides()
                    refreshHoverOverridesStatus()
                    refreshHoverOverridesButtonsState()
                    hoverDocumentationOverridesTable?.repaint()
                }
            }
            hoverDocumentationOverridesResetBuiltinsButton = resetBuiltinsButton

            actions.add(addButton)
            actions.add(removeButton)
            actions.add(editPathButton)
            actions.add(insertSampleButton)
            actions.add(resetBuiltinsButton)

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

            val syntaxHint = JLabel(DreamShaderBundle.message("settings.hoverDocsOverrides.syntaxHint")).apply {
                name = HOVER_DOCS_SYNTAX_HINT_LABEL_NAME
                foreground = UIUtil.getContextHelpForeground()
            }
            val syntaxHintConstraints = GridBagConstraints().apply {
                gridx = 1
                gridy = row
                anchor = GridBagConstraints.WEST
                insets = JBUI.insets(0, 0, 6, 4)
                fill = GridBagConstraints.HORIZONTAL
                weightx = 1.0
            }
            root.add(syntaxHint, syntaxHintConstraints)
            row++

            model.addTableModelListener {
                refreshHoverOverridesStatus()
                refreshHoverOverridesButtonsState()
                hoverDocumentationOverridesTable?.repaint()
            }
            table.selectionModel.addListSelectionListener {
                refreshHoverOverridesButtonsState()
            }
        }

        projectRootField = TextFieldWithBrowseButton().apply {
            textField.name = PROJECT_ROOT_FIELD_NAME
            addBrowseFolderListener(
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withTitle(DreamShaderBundle.message("settings.projectRoot.browseTitle"))
            )
        }
        manifestPathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor("json")
                    .withTitle(DreamShaderBundle.message("settings.manifestPath.browseTitle"))
            )
        }
        unrealSourceRootField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                project,
                FileChooserDescriptorFactory.createSingleFolderDescriptor()
                    .withTitle(DreamShaderBundle.message("settings.unrealSourceRoot.browseTitle"))
            )
        }
        materialExpressionScanEnabledBox = JBCheckBox(
            DreamShaderBundle.message("settings.materialExpressionScanEnabled.checkbox")
        )
        materialExpressionScanCachePathField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                project,
                FileChooserDescriptorFactory.createSingleFileDescriptor("json")
                    .withTitle(DreamShaderBundle.message("settings.materialExpressionScanCachePath.browseTitle"))
            )
        }
        showStatusBarBox = JBCheckBox(DreamShaderBundle.message("settings.showStatusBar.checkbox"))
        enableCodeLensBox = JBCheckBox(DreamShaderBundle.message("settings.enableCodeLens.checkbox"))
        enableInlayParameterHintsBox = JBCheckBox(DreamShaderBundle.message("settings.enableInlayParameterHints.checkbox"))
        outArgumentPlaceholderSuffixField = JBTextField()
        preferredImportExtensionCombo = com.intellij.openapi.ui.ComboBox(arrayOf(".dsh", ".dsf", ".dsm"))
        preferredImportExtensionCombo?.name = PREFERRED_IMPORT_EXTENSION_COMBO_NAME
        autoUpdatePreferredImportExtensionBox = JBCheckBox(
            DreamShaderBundle.message("settings.autoUpdatePreferredImportExtension.checkbox")
        )
        autoUpdatePreferredImportExtensionBox?.name = AUTO_UPDATE_PREFERRED_IMPORT_EXTENSION_CHECKBOX_NAME
        packageSearchGitHubTokenField = JBTextField()
        recompileCurrentCommandField = JBTextField()
        recompileAllCommandField = JBTextField()
        cleanGeneratedCommandField = JBTextField()
        previewAutoRefreshDelayField = JBTextField()

        addLabelAndPathField(
            DreamShaderBundle.message("settings.projectRoot.label"),
            projectRootField as TextFieldWithBrowseButton,
            DreamShaderBundle.message("settings.projectRoot.tooltip")
        )
        bridgeStatusLabel = addInlineInfoLabel(BRIDGE_STATUS_LABEL_NAME)
        addLabelAndPathField(
            DreamShaderBundle.message("settings.manifestPath.label"),
            manifestPathField as TextFieldWithBrowseButton,
            DreamShaderBundle.message("settings.manifestPath.tooltip")
        )
        addLabelPathFieldAndButton(
            DreamShaderBundle.message("settings.unrealSourceRoot.label"),
            unrealSourceRootField as TextFieldWithBrowseButton,
            JButton(DreamShaderBundle.message("settings.unrealSourceRoot.autoDetectButton")).apply {
                name = UNREAL_SOURCE_ROOT_AUTO_DETECT_BUTTON_NAME
                toolTipText = DreamShaderBundle.message("settings.unrealSourceRoot.autoDetect.tooltip")
                addActionListener { autoDetectUnrealSourceRoot() }
            },
            DreamShaderBundle.message("settings.unrealSourceRoot.tooltip")
        )
        addCheckBox(
            materialExpressionScanEnabledBox as JBCheckBox,
            DreamShaderBundle.message("settings.materialExpressionScanEnabled.tooltip")
        )
        addLabelAndPathField(
            DreamShaderBundle.message("settings.materialExpressionScanCachePath.label"),
            materialExpressionScanCachePathField as TextFieldWithBrowseButton,
            DreamShaderBundle.message("settings.materialExpressionScanCachePath.tooltip")
        )
        addCheckBox(
            showStatusBarBox as JBCheckBox,
            DreamShaderBundle.message("settings.showStatusBar.tooltip")
        )
        addCheckBox(
            enableCodeLensBox as JBCheckBox,
            DreamShaderBundle.message("settings.enableCodeLens.tooltip")
        )
        addCheckBox(
            enableInlayParameterHintsBox as JBCheckBox,
            DreamShaderBundle.message("settings.enableInlayParameterHints.tooltip")
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
        addLabelAndField(
            DreamShaderBundle.message("settings.previewAutoRefreshDelay.label"),
            previewAutoRefreshDelayField as JBTextField,
            DreamShaderBundle.message("settings.previewAutoRefreshDelay.tooltip")
        )

        preferredImportExtensionCombo?.addActionListener {
            refreshImportExtensionPreview()
        }
        autoUpdatePreferredImportExtensionBox?.addActionListener {
            refreshImportExtensionPreview()
        }
        projectRootField?.textField?.document?.addDocumentListener(SimpleDocumentListener {
            refreshProjectRootAutoResolvedHint()
            refreshBridgeStatusHint()
        })

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
        stopHoverOverridesTableEditing()
        return projectRootField?.text.orEmpty() != state.projectRoot ||
            manifestPathField?.text.orEmpty() != state.materialExpressionManifestPath ||
            unrealSourceRootField?.text.orEmpty() != state.unrealEngineSourceRoot ||
            (materialExpressionScanEnabledBox?.isSelected ?: false) != state.materialExpressionScanEnabled ||
            materialExpressionScanCachePathField?.text.orEmpty() != state.materialExpressionScanCachePath ||
            showStatusBarBox?.isSelected != state.showStatusBar ||
            enableCodeLensBox?.isSelected != state.enableCodeLens ||
            enableInlayParameterHintsBox?.isSelected != state.enableInlayParameterHints ||
            outArgumentPlaceholderSuffixField?.text.orEmpty() != state.outArgumentPlaceholderSuffix ||
            normalizePreferredImportExtension(preferredImportExtensionCombo?.selectedItem as? String) != normalizePreferredImportExtension(state.preferredImportExtension) ||
            (autoUpdatePreferredImportExtensionBox?.isSelected ?: false) != state.autoUpdatePreferredImportExtension ||
            packageSearchGitHubTokenField?.text.orEmpty() != state.packageStoreGitHubToken ||
            buildHoverOverridesRawFromTable() != state.hoverDocumentationOverrides.trim() ||
            recompileCurrentCommandField?.text.orEmpty() != state.bridgeRecompileCurrentCommand ||
            recompileAllCommandField?.text.orEmpty() != state.bridgeRecompileAllCommand ||
            cleanGeneratedCommandField?.text.orEmpty() != state.bridgeCleanGeneratedShadersCommand ||
            normalizePreviewDelay(previewAutoRefreshDelayField?.text.orEmpty()) != state.previewAutoRefreshDelayMs.coerceIn(250, 10000)
    }

    override fun apply() {
        val state = project.getService(DreamShaderProjectSettings::class.java).state
        val oldEnableCodeLens = state.enableCodeLens
        val oldEnableInlayParameterHints = state.enableInlayParameterHints

        stopHoverOverridesTableEditing()

        state.projectRoot = projectRootField?.text.orEmpty().trim()
        state.materialExpressionManifestPath = manifestPathField?.text.orEmpty().trim()
        state.unrealEngineSourceRoot = unrealSourceRootField?.text.orEmpty().trim()
        state.materialExpressionScanEnabled = materialExpressionScanEnabledBox?.isSelected ?: false
        state.materialExpressionScanCachePath = materialExpressionScanCachePathField?.text.orEmpty().trim()
        state.showStatusBar = showStatusBarBox?.isSelected ?: true
        state.enableCodeLens = enableCodeLensBox?.isSelected ?: true
        state.enableInlayParameterHints = enableInlayParameterHintsBox?.isSelected ?: true
        state.outArgumentPlaceholderSuffix = normalizeOutPlaceholderSuffix(outArgumentPlaceholderSuffixField?.text.orEmpty())
        state.preferredImportExtension = normalizePreferredImportExtension(preferredImportExtensionCombo?.selectedItem as? String)
        state.autoUpdatePreferredImportExtension = autoUpdatePreferredImportExtensionBox?.isSelected ?: false
        state.packageStoreGitHubToken = packageSearchGitHubTokenField?.text.orEmpty().trim()
        state.hoverDocumentationOverrides = buildHoverOverridesRawFromTable()
        state.bridgeRecompileCurrentCommand = recompileCurrentCommandField?.text.orEmpty().trim()
        state.bridgeRecompileAllCommand = recompileAllCommandField?.text.orEmpty().trim()
        state.bridgeCleanGeneratedShadersCommand = cleanGeneratedCommandField?.text.orEmpty().trim()
        state.previewTransport = "file"
        state.previewAutoRefreshDelayMs = normalizePreviewDelay(previewAutoRefreshDelayField?.text.orEmpty())

        if (oldEnableCodeLens != state.enableCodeLens || oldEnableInlayParameterHints != state.enableInlayParameterHints) {
            ParameterHintsPassFactory.forceHintsUpdateOnNextPass()
        }
    }

    override fun reset() {
        val state = project.getService(DreamShaderProjectSettings::class.java).state
        projectRootField?.text = state.projectRoot
        manifestPathField?.text = state.materialExpressionManifestPath
        unrealSourceRootField?.text = state.unrealEngineSourceRoot
        materialExpressionScanEnabledBox?.isSelected = state.materialExpressionScanEnabled
        materialExpressionScanCachePathField?.text = state.materialExpressionScanCachePath
        showStatusBarBox?.isSelected = state.showStatusBar
        enableCodeLensBox?.isSelected = state.enableCodeLens
        enableInlayParameterHintsBox?.isSelected = state.enableInlayParameterHints
        outArgumentPlaceholderSuffixField?.text = state.outArgumentPlaceholderSuffix
        preferredImportExtensionCombo?.selectedItem = ".${normalizePreferredImportExtension(state.preferredImportExtension)}"
        autoUpdatePreferredImportExtensionBox?.isSelected = state.autoUpdatePreferredImportExtension
        packageSearchGitHubTokenField?.text = state.packageStoreGitHubToken
        loadHoverOverridesFromRaw(state.hoverDocumentationOverrides)
        recompileCurrentCommandField?.text = state.bridgeRecompileCurrentCommand
        recompileAllCommandField?.text = state.bridgeRecompileAllCommand
        cleanGeneratedCommandField?.text = state.bridgeCleanGeneratedShadersCommand
        previewAutoRefreshDelayField?.text = state.previewAutoRefreshDelayMs.coerceIn(250, 10000).toString()
        refreshImportExtensionPreview()
        refreshProjectRootAutoResolvedHint()
        refreshBridgeStatusHint()
        refreshHoverOverridesStatus()
        refreshHoverOverridesButtonsState()
    }

    override fun disposeUIResources() {
        panel = null
        projectRootField = null
        manifestPathField = null
        unrealSourceRootField = null
        materialExpressionScanEnabledBox = null
        materialExpressionScanCachePathField = null
        showStatusBarBox = null
        enableCodeLensBox = null
        enableInlayParameterHintsBox = null
        outArgumentPlaceholderSuffixField = null
        preferredImportExtensionCombo = null
        autoUpdatePreferredImportExtensionBox = null
        importExtensionPreviewLabel = null
        packageSearchGitHubTokenField = null
        hoverDocumentationOverridesTableModel = null
        hoverDocumentationOverridesTable = null
        hoverDocumentationOverridesStatusLabel = null
        hoverDocumentationOverridesRemoveButton = null
        hoverDocumentationOverridesEditPathButton = null
        hoverDocumentationOverridesResetBuiltinsButton = null
        recompileCurrentCommandField = null
        recompileAllCommandField = null
        cleanGeneratedCommandField = null
        previewAutoRefreshDelayField = null
        bridgeStatusLabel = null
    }

    private fun refreshProjectRootAutoResolvedHint() {
        val field = projectRootField ?: return
        val emptyText = (field.textField as? JBTextField)?.emptyText ?: return
        val autoResolved = DreamShaderBridgePathResolver.resolveProjectRootAutoFallback(project, null)
            ?: DreamShaderBundle.message("common.unknown")
        emptyText.text = DreamShaderBundle.message("settings.projectRoot.autoResolvedHint", autoResolved)
    }

    /**
     * 刷新 Bridge 识别状态提示：展示自动检测到的 Bridge 目录及四个已知文件的存在情况，
     * 让用户无需手动配置即可确认插件已识别到 Bridge 产物。
     */
    private fun refreshBridgeStatusHint() {
        val label = bridgeStatusLabel ?: return
        val bridgeDir = DreamShaderBridgePathResolver.resolveBridgeDirectory(project, null)
        if (bridgeDir.isNullOrBlank() || !File(bridgeDir).isDirectory) {
            label.text = DreamShaderBundle.message("settings.bridgeStatus.notDetected")
            return
        }
        val parts = BRIDGE_STATUS_FILE_NAMES.map { fileName ->
            val present = File(bridgeDir, fileName).isFile
            val mark = if (present) "✓" else "✗"
            "$fileName $mark"
        }
        label.text = DreamShaderBundle.message(
            "settings.bridgeStatus.detected",
            bridgeDir,
            parts.joinToString("  ·  ")
        )
    }

    private fun autoDetectUnrealSourceRoot() {
        val startPath = sequenceOf(
            projectRootField?.text?.trim().orEmpty(),
            DreamShaderBridgePathResolver.resolveProjectRootAutoFallback(project, null).orEmpty(),
            project.basePath.orEmpty()
        ).firstOrNull { it.isNotBlank() }
        val start = startPath?.let { File(it) }
        val candidates = DreamShaderUnrealSourceLocator.locate(start)

        val chosen = when {
            candidates.isEmpty() -> {
                Messages.showInfoMessage(
                    project,
                    DreamShaderBundle.message("settings.unrealSourceRoot.autoDetect.notFound"),
                    DreamShaderBundle.message("settings.title")
                )
                return
            }
            candidates.size == 1 -> candidates.first()
            else -> {
                val labels = candidates
                    .map { describeCandidate(it) }
                    .toTypedArray()
                val index = Messages.showChooseDialog(
                    project,
                    DreamShaderBundle.message("settings.unrealSourceRoot.autoDetect.chooseMessage"),
                    DreamShaderBundle.message("settings.unrealSourceRoot.autoDetect.chooseTitle"),
                    null,
                    labels,
                    labels.first()
                )
                if (index < 0) return
                candidates[index]
            }
        }

        unrealSourceRootField?.text = chosen.sourceRoot
        val versionSuffix = chosen.version
            ?.let { DreamShaderBundle.message("settings.unrealSourceRoot.autoDetect.versionSuffix", it) }
            .orEmpty()
        Messages.showInfoMessage(
            project,
            DreamShaderBundle.message("settings.unrealSourceRoot.autoDetect.filled", versionSuffix),
            DreamShaderBundle.message("settings.title")
        )
    }

    private fun describeCandidate(candidate: DreamShaderUnrealSourceLocator.Candidate): String {
        val versionSuffix = candidate.version
            ?.let { DreamShaderBundle.message("settings.unrealSourceRoot.autoDetect.versionSuffix", it) }
            .orEmpty()
        return "${candidate.sourceRoot}$versionSuffix"
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

    private fun normalizePreviewDelay(raw: String): Int {
        return raw.trim().toIntOrNull()?.coerceIn(250, 10000) ?: 1200
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
        val model = hoverDocumentationOverridesTableModel
        val selectedModelRow = hoverDocumentationOverridesTable
            ?.selectedRow
            ?.takeIf { it >= 0 }
            ?.let { hoverDocumentationOverridesTable?.convertRowIndexToModel(it) ?: -1 }
            ?: -1
        hoverDocumentationOverridesRemoveButton?.isEnabled = selectedModelRow >= 0 && (model?.isCustomRow(selectedModelRow) == true)
        hoverDocumentationOverridesEditPathButton?.isEnabled = selectedModelRow >= 0 && (model?.isCustomRow(selectedModelRow) == true)
        hoverDocumentationOverridesResetBuiltinsButton?.isEnabled = model?.hasBuiltinOverrides() == true
    }

    private fun stopHoverOverridesTableEditing() {
        val table = hoverDocumentationOverridesTable ?: return
        val editor = table.cellEditor ?: return
        editor.stopCellEditing()
    }

    private fun upsertHoverOverrideRow(key: String, value: String) {
        val model = hoverDocumentationOverridesTableModel ?: return
        val rowIndex = model.upsert(key, value)
        if (rowIndex >= 0) {
            hoverDocumentationOverridesTable?.setRowSelectionInterval(rowIndex, rowIndex)
        }
    }

    private fun openPathEditorDialog(modelRow: Int) {
        if (modelRow < 0) return
        val model = hoverDocumentationOverridesTableModel ?: return
        if (!model.isCustomRow(modelRow)) return
        val row = model.rowAt(modelRow) ?: return
        val edited = HoverDocPathEditorDialog(
            initialText = row.key
        ).showAndGetText() ?: return
        model.setPathAt(modelRow, edited)
        refreshHoverOverridesStatus()
        refreshHoverOverridesButtonsState()
        hoverDocumentationOverridesTable?.repaint()
    }

    private fun buildHoverOverridesRawFromTable(): String {
        return buildHoverOverridesRawFromTable(includeIncompleteRows = false, commitEditing = true)
    }

    private fun buildHoverOverridesValidationRawFromTable(): String {
        return buildHoverOverridesRawFromTable(includeIncompleteRows = true, commitEditing = false)
    }

    private fun buildHoverOverridesRawFromTable(includeIncompleteRows: Boolean, commitEditing: Boolean): String {
        if (commitEditing) {
            stopHoverOverridesTableEditing()
        }
        val model = hoverDocumentationOverridesTableModel ?: return ""
        return model.buildRaw(includeIncompleteRows)
    }

    private fun loadHoverOverridesFromRaw(raw: String) {
        val model = hoverDocumentationOverridesTableModel ?: return
        model.loadFromOverrides(raw)
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
    internal fun testNormalizePreviewDelay(raw: String): Int = normalizePreviewDelay(raw)
    internal fun testBuildImportExtensionPreviewText(rawExtension: String?, autoUpdate: Boolean): String =
        buildImportExtensionPreviewText(".${normalizePreferredImportExtension(rawExtension)}", autoUpdate)
    internal fun testBuildHoverOverridesStatusText(raw: String): String =
        buildHoverOverridesStatusText(DreamShaderHoverOverrideParser.parse(raw))

    private data class HoverOverrideRow(
        var key: String,
        var content: String,
        val builtinDefault: String?
    ) {
        val normalizedKey: String
            get() = key.trim().lowercase(Locale.ROOT)

        fun isBuiltin(): Boolean = builtinDefault != null

        fun isBuiltinChanged(): Boolean {
            val defaultValue = builtinDefault ?: return false
            return content.trim() != defaultValue.trim()
        }
    }

    private class HoverOverrideTableModel(
        private val resetLabel: String
    ) : AbstractTableModel() {
        private val rows = mutableListOf<HoverOverrideRow>()
        private val builtinEntries: List<Pair<String, String>> = DreamShaderDocumentationData.builtinOverrideEntries().entries
            .map { it.key to it.value }

        override fun getRowCount(): Int = rows.size

        override fun getColumnCount(): Int = 3

        override fun getColumnName(column: Int): String {
            return when (column) {
                COLUMN_PATH -> DreamShaderBundle.message("settings.hoverDocsOverrides.column.path")
                COLUMN_CONTENT -> DreamShaderBundle.message("settings.hoverDocsOverrides.column.content")
                COLUMN_RESET -> DreamShaderBundle.message("settings.hoverDocsOverrides.column.reset")
                else -> ""
            }
        }

        override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
            val row = rows[rowIndex]
            return when (columnIndex) {
                COLUMN_PATH -> row.key
                COLUMN_CONTENT -> DreamShaderBundle.message("settings.hoverDocsOverrides.editContentButton")
                COLUMN_RESET -> if (row.isBuiltinChanged()) resetLabel else ""
                else -> ""
            }
        }

        override fun isCellEditable(rowIndex: Int, columnIndex: Int): Boolean {
            val row = rows[rowIndex]
            return when (columnIndex) {
                COLUMN_PATH -> false
                COLUMN_CONTENT -> true
                COLUMN_RESET -> row.isBuiltinChanged()
                else -> false
            }
        }

        override fun setValueAt(aValue: Any?, rowIndex: Int, columnIndex: Int) {
            val row = rows.getOrNull(rowIndex) ?: return
            when (columnIndex) {
                COLUMN_PATH -> if (!row.isBuiltin()) row.key = aValue?.toString().orEmpty()
                COLUMN_CONTENT -> row.content = aValue?.toString().orEmpty()
                else -> return
            }
            fireTableRowsUpdated(rowIndex, rowIndex)
        }

        override fun getColumnClass(columnIndex: Int): Class<*> = String::class.java

        fun loadFromOverrides(raw: String) {
            val parsed = DreamShaderHoverOverrideParser.parse(raw)
            val overrides = parsed.entries
            rows.clear()

            val builtinNormalizedKeys = linkedSetOf<String>()
            for ((canonicalKey, defaultContent) in builtinEntries) {
                val normalized = canonicalKey.trim().lowercase(Locale.ROOT)
                builtinNormalizedKeys.add(normalized)
                val overrideContent = overrides[normalized]
                rows += HoverOverrideRow(
                    key = canonicalKey,
                    content = overrideContent ?: defaultContent,
                    builtinDefault = defaultContent
                )
            }

            overrides.keys
                .filter { it !in builtinNormalizedKeys }
                .sorted()
                .forEach { customKey ->
                    rows += HoverOverrideRow(
                        key = customKey,
                        content = overrides[customKey].orEmpty(),
                        builtinDefault = null
                    )
                }

            fireTableDataChanged()
        }

        fun addCustomRow(): Int {
            rows += HoverOverrideRow("", "", null)
            val rowIndex = rows.size - 1
            fireTableRowsInserted(rowIndex, rowIndex)
            return rowIndex
        }

        fun removeCustomRow(rowIndex: Int): Boolean {
            val row = rows.getOrNull(rowIndex) ?: return false
            if (row.isBuiltin()) return false
            rows.removeAt(rowIndex)
            fireTableRowsDeleted(rowIndex, rowIndex)
            return true
        }

        fun upsert(key: String, value: String): Int {
            val normalizedTarget = key.trim().lowercase(Locale.ROOT)
            if (normalizedTarget.isBlank()) return -1

            rows.indexOfFirst { it.normalizedKey == normalizedTarget }
                .takeIf { it >= 0 }
                ?.let { rowIndex ->
                    val row = rows[rowIndex]
                    if (!row.isBuiltin()) {
                        row.key = key.trim()
                    }
                    row.content = value
                    fireTableRowsUpdated(rowIndex, rowIndex)
                    return rowIndex
                }

            rows += HoverOverrideRow(key.trim(), value, null)
            val rowIndex = rows.size - 1
            fireTableRowsInserted(rowIndex, rowIndex)
            return rowIndex
        }

        fun resetBuiltinAt(rowIndex: Int) {
            val row = rows.getOrNull(rowIndex) ?: return
            val defaultValue = row.builtinDefault ?: return
            row.content = defaultValue
            fireTableRowsUpdated(rowIndex, rowIndex)
        }

        fun resetAllBuiltinOverrides() {
            rows.forEachIndexed { index, row ->
                val defaultValue = row.builtinDefault ?: return@forEachIndexed
                if (row.content.trim() != defaultValue.trim()) {
                    row.content = defaultValue
                    fireTableRowsUpdated(index, index)
                }
            }
        }

        fun isBuiltinRow(rowIndex: Int): Boolean = rows.getOrNull(rowIndex)?.isBuiltin() == true

        fun isCustomRow(rowIndex: Int): Boolean = rows.getOrNull(rowIndex)?.isBuiltin() == false

        fun isBuiltinChanged(rowIndex: Int): Boolean = rows.getOrNull(rowIndex)?.isBuiltinChanged() == true

        fun hasBuiltinOverrides(): Boolean = rows.any { it.isBuiltinChanged() }

        fun rowAt(rowIndex: Int): HoverOverrideRow? = rows.getOrNull(rowIndex)

        fun setContentAt(rowIndex: Int, value: String) {
            val row = rows.getOrNull(rowIndex) ?: return
            row.content = value
            fireTableRowsUpdated(rowIndex, rowIndex)
        }

        fun setPathAt(rowIndex: Int, value: String) {
            val row = rows.getOrNull(rowIndex) ?: return
            if (row.isBuiltin()) return
            row.key = value
            fireTableRowsUpdated(rowIndex, rowIndex)
        }

        fun buildRaw(includeIncompleteRows: Boolean): String {
            val lines = mutableListOf<String>()
            rows.forEach { row ->
                val key = row.key.trim()
                val rawValue = row.content.replace("\r\n", "\n").replace('\r', '\n')
                val hasValue = rawValue.isNotBlank()
                if (key.isBlank() && !hasValue) return@forEach

                val shouldInclude = if (row.isBuiltin()) {
                    row.isBuiltinChanged() || (includeIncompleteRows && (key.isBlank() || !hasValue))
                } else {
                    if (key.isBlank() || !hasValue) includeIncompleteRows else true
                }
                if (!shouldInclude) return@forEach

                val encodedValue = DreamShaderHoverOverrideParser.encodeValue(rawValue)
                lines += "$key=$encodedValue"
            }
            return lines.joinToString("\n").trim()
        }

        companion object {
            const val COLUMN_PATH = 0
            const val COLUMN_CONTENT = 1
            const val COLUMN_RESET = 2
        }
    }

    private class HoverResetButtonRenderer : JButton(), TableCellRenderer {
        init {
            margin = JBUI.insets(1, 6)
            isFocusPainted = false
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            text = value?.toString().orEmpty()
            isEnabled = text.isNotBlank()
            return this
        }
    }

    private class HoverEditButtonRenderer(
        private val label: String
    ) : JButton(), TableCellRenderer {
        init {
            margin = JBUI.insets(1, 6)
            isFocusPainted = false
            text = label
        }

        override fun getTableCellRendererComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            hasFocus: Boolean,
            row: Int,
            column: Int
        ): Component {
            text = label
            isEnabled = true
            return this
        }
    }

    private class HoverEditButtonEditor(
        private val label: String,
        private val onEdit: (modelRow: Int) -> Unit
    ) : AbstractCellEditor(), TableCellEditor {
        private val button = JButton(label).apply {
            margin = JBUI.insets(1, 6)
            isFocusPainted = false
            addActionListener {
                val row = editingModelRow
                if (row >= 0) {
                    onEdit(row)
                }
                fireEditingStopped()
            }
        }
        private var editingModelRow: Int = -1

        override fun getCellEditorValue(): Any = label

        override fun getTableCellEditorComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int
        ): Component {
            editingModelRow = table.convertRowIndexToModel(row)
            button.text = label
            return button
        }
    }

    private class HoverDocContentEditorDialog(
        initialText: String
    ) : DialogWrapper(true) {
        private val textArea = JBTextArea(initialText).apply {
            lineWrap = true
            wrapStyleWord = true
        }

        init {
            title = DreamShaderBundle.message("settings.hoverDocsOverrides.editor.title")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(GridBagLayout())
            val constraints = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                weighty = 1.0
                fill = GridBagConstraints.BOTH
                insets = JBUI.insets(6)
            }
            val scroll = com.intellij.ui.components.JBScrollPane(textArea).apply {
                preferredSize = Dimension(760, 320)
            }
            panel.add(scroll, constraints)
            return panel
        }

        override fun getPreferredFocusedComponent(): JComponent = textArea

        fun showAndGetText(): String? {
            val ok = showAndGet()
            if (!ok) return null
            return textArea.text.replace("\r\n", "\n").replace('\r', '\n')
        }
    }

    private class HoverDocPathEditorDialog(
        initialText: String
    ) : DialogWrapper(true) {
        private val textField = JBTextField(initialText)

        init {
            title = DreamShaderBundle.message("settings.hoverDocsOverrides.pathEditor.title")
            init()
        }

        override fun createCenterPanel(): JComponent {
            val panel = JPanel(GridBagLayout())
            val constraints = GridBagConstraints().apply {
                gridx = 0
                gridy = 0
                weightx = 1.0
                fill = GridBagConstraints.HORIZONTAL
                insets = JBUI.insets(6)
            }
            panel.add(textField, constraints)
            panel.preferredSize = Dimension(680, 84)
            return panel
        }

        override fun getPreferredFocusedComponent(): JComponent = textField

        fun showAndGetText(): String? {
            val ok = showAndGet()
            if (!ok) return null
            return textField.text.trim()
        }
    }

    private class HoverResetButtonEditor(
        private val onReset: (modelRow: Int) -> Unit
    ) : AbstractCellEditor(), TableCellEditor {
        private val button = JButton().apply {
            margin = JBUI.insets(1, 6)
            isFocusPainted = false
            addActionListener {
                val row = editingModelRow
                if (row >= 0) {
                    onReset(row)
                }
                fireEditingStopped()
            }
        }
        private var editingModelRow: Int = -1

        override fun getCellEditorValue(): Any = button.text

        override fun getTableCellEditorComponent(
            table: JTable,
            value: Any?,
            isSelected: Boolean,
            row: Int,
            column: Int
        ): Component {
            editingModelRow = table.convertRowIndexToModel(row)
            button.text = value?.toString().orEmpty()
            button.isEnabled = button.text.isNotBlank()
            return button
        }
    }

    private fun interface SimpleDocumentListener : javax.swing.event.DocumentListener {
        fun onChange()
        override fun insertUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
        override fun removeUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
        override fun changedUpdate(e: javax.swing.event.DocumentEvent?) = onChange()
    }

    companion object {
        private val SUPPORTED_IMPORT_EXTENSIONS = setOf("dsh", "dsf", "dsm")
        private val HOVER_DOCS_CHANGED_BACKGROUND = JBColor(Color(255, 247, 220), Color(68, 61, 42))
        private val HOVER_DOCS_BUILTIN_PATH_FOREGROUND = JBColor(Color(78, 87, 104), Color(167, 179, 200))

        internal const val PREFERRED_IMPORT_EXTENSION_COMBO_NAME = "dreamshader.preferredImportExtensionCombo"
        internal const val PROJECT_ROOT_FIELD_NAME = "dreamshader.projectRootField"
        internal const val AUTO_UPDATE_PREFERRED_IMPORT_EXTENSION_CHECKBOX_NAME = "dreamshader.autoUpdatePreferredImportExtensionCheckbox"
        internal const val IMPORT_EXTENSION_PREVIEW_LABEL_NAME = "dreamshader.importExtensionPreviewLabel"
        internal const val HOVER_DOCS_TABLE_NAME = "dreamshader.hoverDocsOverrides.table"
        internal const val HOVER_DOCS_ADD_ROW_BUTTON_NAME = "dreamshader.hoverDocsOverrides.addRowButton"
        internal const val HOVER_DOCS_REMOVE_ROW_BUTTON_NAME = "dreamshader.hoverDocsOverrides.removeRowButton"
        internal const val HOVER_DOCS_EDIT_PATH_BUTTON_NAME = "dreamshader.hoverDocsOverrides.editPathButton"
        internal const val HOVER_DOCS_INSERT_SAMPLE_BUTTON_NAME = "dreamshader.hoverDocsOverrides.insertSampleButton"
        internal const val HOVER_DOCS_RESET_BUILTINS_BUTTON_NAME = "dreamshader.hoverDocsOverrides.resetBuiltinsButton"
        internal const val HOVER_DOCS_STATUS_LABEL_NAME = "dreamshader.hoverDocsOverrides.statusLabel"
        internal const val HOVER_DOCS_SYNTAX_HINT_LABEL_NAME = "dreamshader.hoverDocsOverrides.syntaxHintLabel"
        internal const val BRIDGE_STATUS_LABEL_NAME = "dreamshader.bridgeStatus.label"
        internal const val UNREAL_SOURCE_ROOT_AUTO_DETECT_BUTTON_NAME = "dreamshader.unrealSourceRoot.autoDetectButton"
        private val BRIDGE_STATUS_FILE_NAMES = listOf(
            "diagnostics.json",
            "settings.json",
            "material-expressions.json",
            "substrate-builtins.json"
        )
    }
}
