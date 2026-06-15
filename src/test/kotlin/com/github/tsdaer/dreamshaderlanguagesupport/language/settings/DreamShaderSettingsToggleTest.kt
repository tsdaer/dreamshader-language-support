package com.github.tsdaer.dreamshaderlanguagesupport.language.settings
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCodeVisionProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderInlayParameterHintsProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.ui.components.JBTextField
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JTable
import javax.swing.table.TableModel

class DreamShaderSettingsToggleTest : DreamShaderSettingsUiTestBase() {
    fun testInlayParameterHintsUseIndependentSetting() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        val provider = DreamShaderInlayParameterHintsProvider()
        val file = myFixture.configureByText(
            "inlay_parameter_hints_toggle.dsm",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )

        settings.enableCodeLens = false
        settings.enableInlayParameterHints = true
        val enabledHints = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
            .flatMap { provider.getParameterHints(it) }
        assertTrue("Expected hints when enableInlayParameterHints=true even if enableCodeLens=false", enabledHints.isNotEmpty())

        settings.enableInlayParameterHints = false
        val disabledHints = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
            .flatMap { provider.getParameterHints(it) }
        assertTrue("Expected no hints when enableInlayParameterHints=false", disabledHints.isEmpty())
    }

    fun testOutArgumentPlaceholderSuffixNormalization() {
        val configurable = DreamShaderSettingsConfigurable(project)
        assertTrue(configurable.testNormalizeOutPlaceholderSuffix("") == "Out")
        assertTrue(configurable.testNormalizeOutPlaceholderSuffix("123") == "_123")
        assertTrue(configurable.testNormalizeOutPlaceholderSuffix("out-suffix") == "out_suffix")
    }

    fun testPreferredImportExtensionNormalization() {
        val configurable = DreamShaderSettingsConfigurable(project)
        assertTrue(configurable.testNormalizePreferredImportExtension(null) == "dsh")
        assertTrue(configurable.testNormalizePreferredImportExtension(".dsf") == "dsf")
        assertTrue(configurable.testNormalizePreferredImportExtension("DSM") == "dsm")
        assertTrue(configurable.testNormalizePreferredImportExtension("invalid") == "dsh")
    }

    fun testImportExtensionPreviewTextUsesLocalizedToggleText() {
        val configurable = DreamShaderSettingsConfigurable(project)
        val enabledPreview = configurable.testBuildImportExtensionPreviewText("dsm", autoUpdate = true)
        val disabledPreview = configurable.testBuildImportExtensionPreviewText("dsh", autoUpdate = false)
        assertTrue("Expected localized enabled toggle text", enabledPreview.contains("Enabled"))
        assertTrue("Expected localized disabled toggle text", disabledPreview.contains("Disabled"))
    }

    fun testPreferredImportExtensionStateDefaults() {
        val settingsService = project.getService(DreamShaderProjectSettings::class.java)
        settingsService.loadState(DreamShaderProjectSettings.State())
        val settings = settingsService.state
        assertEquals("dsh", settings.preferredImportExtension)
        assertFalse(settings.autoUpdatePreferredImportExtension)
    }

    fun testCodeVisionRespectsEnableCodeLensSetting() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        val provider = DreamShaderCodeVisionProvider()
        val file = myFixture.configureByText(
            "code_vision_toggle.dsm",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )

        settings.enableCodeLens = true
        assertTrue("Expected Code Vision enabled when enableCodeLens=true", provider.acceptsFile(file))

        settings.enableCodeLens = false
        assertFalse("Expected Code Vision disabled when enableCodeLens=false", provider.acceptsFile(file))
    }

    fun testImportExtensionPreviewUpdatesOnUiInteractions() {
        val settingsService = project.getService(DreamShaderProjectSettings::class.java)
        settingsService.loadState(DreamShaderProjectSettings.State())
        val configurable = DreamShaderSettingsConfigurable(project)
        val root = configurable.createComponent()

        val combo = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.PREFERRED_IMPORT_EXTENSION_COMBO_NAME,
            JComboBox::class.java
        )
        val autoUpdateBox = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.AUTO_UPDATE_PREFERRED_IMPORT_EXTENSION_CHECKBOX_NAME,
            JCheckBox::class.java
        )
        val previewLabel = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.IMPORT_EXTENSION_PREVIEW_LABEL_NAME,
            JLabel::class.java
        )

        assertTrue("Expected preview to show default extension", previewLabel.text.contains(".dsh"))
        assertTrue("Expected preview to show disabled toggle state", previewLabel.text.contains("Disabled"))

        combo.selectedItem = ".dsf"
        autoUpdateBox.doClick()

        assertTrue("Expected preview to reflect selected extension", previewLabel.text.contains(".dsf"))
        assertTrue("Expected preview to reflect enabled toggle state", previewLabel.text.contains("Enabled"))
    }

    fun testHoverOverridesStatusUpdatesAfterInsertSample() {
        val settingsService = project.getService(DreamShaderProjectSettings::class.java)
        settingsService.loadState(DreamShaderProjectSettings.State())
        val configurable = DreamShaderSettingsConfigurable(project)
        val root = configurable.createComponent()

        val insertSampleButton = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_INSERT_SAMPLE_BUTTON_NAME,
            JButton::class.java
        )
        val statusLabel = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_STATUS_LABEL_NAME,
            JLabel::class.java
        )

        assertTrue("Expected initial status to report no active entries", statusLabel.text.contains("0 active"))

        insertSampleButton.doClick()

        assertTrue("Expected inserted sample to produce one active override", statusLabel.text.contains("1 active"))
    }

    fun testHoverOverridesStatusTextReflectsFirstIssue() {
        val configurable = DreamShaderSettingsConfigurable(project)
        val status = configurable.testBuildHoverOverridesStatusText(
            """
            declaration.function.description=ok
            bad line
            """.trimIndent()
        )
        assertTrue("Expected status to report line number", status.contains("line 2"))
        assertTrue("Expected status to explain missing equals issue", status.contains("missing '=' separator"))
    }

    fun testHoverOverridesRowButtonsWorkWithSelectionState() {
        val configurable = DreamShaderSettingsConfigurable(project)
        val root = configurable.createComponent()

        val addRowButton = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_ADD_ROW_BUTTON_NAME,
            JButton::class.java
        )
        val removeRowButton = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_REMOVE_ROW_BUTTON_NAME,
            JButton::class.java
        )

        assertFalse("Expected remove button disabled when no row is selected", removeRowButton.isEnabled)

        addRowButton.doClick()

        assertTrue("Expected remove button enabled after adding/selecting a row", removeRowButton.isEnabled)

        removeRowButton.doClick()

        assertFalse("Expected remove button disabled after removing the selected row", removeRowButton.isEnabled)
    }

    fun testHoverOverridesStatusShowsIssueForIncompleteTableRow() {
        val configurable = DreamShaderSettingsConfigurable(project)
        val root = configurable.createComponent()

        val addRowButton = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_ADD_ROW_BUTTON_NAME,
            JButton::class.java
        )
        val table = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_TABLE_NAME,
            JTable::class.java
        )
        val statusLabel = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_STATUS_LABEL_NAME,
            JLabel::class.java
        )

        addRowButton.doClick()
        val customRow = table.model.rowCount - 1
        table.model.setValueAt("custom.path.description", customRow, 0)

        assertTrue("Expected status to report incomplete row issue", statusLabel.text.contains("empty value after '='"))
    }

    fun testHoverOverridesTableContainsBuiltinEntries() {
        val configurable = DreamShaderSettingsConfigurable(project)
        val root = configurable.createComponent()
        val table = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_TABLE_NAME,
            JTable::class.java
        )

        assertTrue("Expected builtin rows to be present", table.rowCount > 0)
    }

    fun testHoverOverridesBuiltinRowResetButtonRestoresDefaultAndApplyDropsOverride() {
        val settingsService = project.getService(DreamShaderProjectSettings::class.java)
        settingsService.loadState(
            DreamShaderProjectSettings.State(
                hoverDocumentationOverrides = "declaration.function.description=Custom override"
            )
        )

        val configurable = DreamShaderSettingsConfigurable(project)
        val root = configurable.createComponent()
        val table = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_TABLE_NAME,
            JTable::class.java
        )

        val rowIndex = findRowByPath(table.model, "declaration.function.description")
        assertTrue("Expected builtin declaration.function.description row", rowIndex >= 0)

        val resetColumn = 2
        val resetText = table.model.getValueAt(rowIndex, resetColumn)?.toString().orEmpty()
        assertTrue("Expected reset button visible for changed builtin row", resetText.isNotBlank())

        val editorComponent = table.columnModel.getColumn(resetColumn).cellEditor.getTableCellEditorComponent(
            table,
            table.model.getValueAt(rowIndex, resetColumn),
            true,
            rowIndex,
            resetColumn
        )
        (editorComponent as JButton).doClick()

        configurable.apply()
        assertEquals(
            "",
            settingsService.state.hoverDocumentationOverrides.trim()
        )
    }

    fun testHoverOverridesResetAllBuiltinsButtonClearsBuiltinOverrides() {
        val settingsService = project.getService(DreamShaderProjectSettings::class.java)
        settingsService.loadState(
            DreamShaderProjectSettings.State(
                hoverDocumentationOverrides = """
                    declaration.function.description=Custom function
                    settings.domain.description=Custom domain
                """.trimIndent()
            )
        )

        val configurable = DreamShaderSettingsConfigurable(project)
        val root = configurable.createComponent()
        val resetAllButton = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.HOVER_DOCS_RESET_BUILTINS_BUTTON_NAME,
            JButton::class.java
        )

        assertTrue("Expected reset-all button enabled when builtin overrides exist", resetAllButton.isEnabled)

        resetAllButton.doClick()
        configurable.apply()

        assertEquals("", settingsService.state.hoverDocumentationOverrides.trim())
    }

    fun testProjectRootAutoResolvedHintVisibility() {
        val settingsService = project.getService(DreamShaderProjectSettings::class.java)
        settingsService.loadState(DreamShaderProjectSettings.State(projectRoot = ""))

        val configurable = DreamShaderSettingsConfigurable(project)
        val root = configurable.createComponent()

        val projectRootField = findComponentByName(
            root,
            DreamShaderSettingsConfigurable.PROJECT_ROOT_FIELD_NAME,
            JBTextField::class.java
        )

        // 自动解析提示现在作为输入框的占位文本（emptyText）展示，留空时可见。
        assertTrue(
            "Expected auto-resolved hint shown as empty-text placeholder",
            projectRootField.emptyText.text.isNotBlank()
        )
    }

    private fun findRowByPath(model: TableModel, path: String): Int {
        for (i in 0 until model.rowCount) {
            if (model.getValueAt(i, 0)?.toString() == path) return i
        }
        return -1
    }

}
