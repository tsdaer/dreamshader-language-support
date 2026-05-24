package com.github.tsdaer.dreamshaderlanguagesupport.language.settings
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.intellij.codeInsight.hints.ParameterHintsPassFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.JBUI
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

/**
 * $name 设置面板实现。
 */
class DreamShaderSettingsConfigurable(
    private val project: Project
) : Configurable {
    private var panel: JPanel? = null
    private var projectRootField: JBTextField? = null
    private var manifestPathField: JBTextField? = null
    private var showStatusBarBox: JBCheckBox? = null
    private var enableCodeLensBox: JBCheckBox? = null
    private var outArgumentPlaceholderSuffixField: JBTextField? = null
    private var packageSearchGitHubTokenField: JBTextField? = null
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

        projectRootField = JBTextField()
        manifestPathField = JBTextField()
        showStatusBarBox = JBCheckBox(DreamShaderBundle.message("settings.showStatusBar.checkbox"))
        enableCodeLensBox = JBCheckBox(DreamShaderBundle.message("settings.enableCodeLens.checkbox"))
        outArgumentPlaceholderSuffixField = JBTextField()
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
        addLabelAndField(
            DreamShaderBundle.message("settings.packageSearchGitHubToken.label"),
            packageSearchGitHubTokenField as JBTextField,
            DreamShaderBundle.message("settings.packageSearchGitHubToken.tooltip")
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
            packageSearchGitHubTokenField?.text.orEmpty() != state.packageStoreGitHubToken ||
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
        state.packageStoreGitHubToken = packageSearchGitHubTokenField?.text.orEmpty().trim()
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
        packageSearchGitHubTokenField?.text = state.packageStoreGitHubToken
        recompileCurrentCommandField?.text = state.bridgeRecompileCurrentCommand
        recompileAllCommandField?.text = state.bridgeRecompileAllCommand
        cleanGeneratedCommandField?.text = state.bridgeCleanGeneratedShadersCommand
    }

    override fun disposeUIResources() {
        panel = null
        projectRootField = null
        manifestPathField = null
        showStatusBarBox = null
        enableCodeLensBox = null
        outArgumentPlaceholderSuffixField = null
        packageSearchGitHubTokenField = null
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

    internal fun testNormalizeOutPlaceholderSuffix(raw: String): String = normalizeOutPlaceholderSuffix(raw)
}
