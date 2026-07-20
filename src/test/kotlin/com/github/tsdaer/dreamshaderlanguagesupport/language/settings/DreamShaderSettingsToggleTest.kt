package com.github.tsdaer.dreamshaderlanguagesupport.language.settings
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCodeVisionProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderInlayParameterHintsProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import javax.swing.JButton
import javax.swing.JLabel
import javax.swing.JTable

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
}
