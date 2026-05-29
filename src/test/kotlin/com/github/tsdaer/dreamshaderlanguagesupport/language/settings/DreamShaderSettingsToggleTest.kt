package com.github.tsdaer.dreamshaderlanguagesupport.language.settings
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCodeVisionProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderInlayParameterHintsProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.JButton
import javax.swing.JCheckBox
import javax.swing.JComboBox
import javax.swing.JLabel
import javax.swing.JTable

class DreamShaderSettingsToggleTest : DreamShaderSettingsUiTestBase() {
    fun testEnableCodeLensToggleControlsInlayHintsProviderOutput() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        val provider = DreamShaderInlayParameterHintsProvider()
        val file = myFixture.configureByText(
            "code_lens_toggle.dsm",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )

        settings.enableCodeLens = true
        val enabledHints = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
            .flatMap { provider.getParameterHints(it) }
        assertTrue("Expected hints when enableCodeLens=true", enabledHints.isNotEmpty())

        settings.enableCodeLens = false
        val disabledHints = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
            .flatMap { provider.getParameterHints(it) }
        assertTrue("Expected no hints when enableCodeLens=false", disabledHints.isEmpty())
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
        table.model.setValueAt("declaration.function.description", 0, 0)

        assertTrue("Expected status to report incomplete row issue", statusLabel.text.contains("empty value after '='"))
    }
}
