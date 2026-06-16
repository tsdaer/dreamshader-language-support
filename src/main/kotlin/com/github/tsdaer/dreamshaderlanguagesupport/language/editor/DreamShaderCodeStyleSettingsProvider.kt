package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.application.options.*
import com.intellij.psi.codeStyle.*

class DreamShaderCodeStyleSettingsProvider : CodeStyleSettingsProvider() {
    override fun createCustomSettings(settings: CodeStyleSettings): CustomCodeStyleSettings {
        return DreamShaderCodeStyleSettings(settings)
    }

    override fun createConfigurable(settings: CodeStyleSettings, originalSettings: CodeStyleSettings): CodeStyleConfigurable {
        return object : CodeStyleAbstractConfigurable(settings, originalSettings, configurableDisplayName) {
            override fun createPanel(settings: CodeStyleSettings): CodeStyleAbstractPanel {
                return DreamShaderCodeStyleMainPanel(currentSettings, settings)
            }
        }
    }

    override fun getConfigurableId(): String = "preferences.sourceCode.DreamShader"

    override fun getConfigurableDisplayName(): String = DreamShaderBundle.message("settings.title")

    override fun getLanguage() = DreamShaderLanguage
}

private class DreamShaderCodeStyleMainPanel(
    currentSettings: CodeStyleSettings,
    settings: CodeStyleSettings
) : TabbedLanguageCodeStylePanel(DreamShaderLanguage, currentSettings, settings)

class DreamShaderLanguageCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {
    override fun getLanguage() = DreamShaderLanguage

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

    override fun customizeSettings(
        consumer: CodeStyleSettingsCustomizable,
        settingsType: SettingsType
    ) {
        val customizableOptions = CodeStyleSettingsCustomizableOptions.getInstance()

        when (settingsType) {
            SettingsType.SPACING_SETTINGS -> {
                consumer.showStandardOptions(
                    "SPACE_AROUND_ASSIGNMENT_OPERATORS",
                    "SPACE_AROUND_ADDITIVE_OPERATORS",
                    "SPACE_AROUND_MULTIPLICATIVE_OPERATORS",
                    "SPACE_AROUND_RELATIONAL_OPERATORS",
                    "SPACE_AROUND_EQUALITY_OPERATORS",
                    "SPACE_AROUND_LOGICAL_OPERATORS",
                    "SPACE_BEFORE_COMMA",
                    "SPACE_AFTER_COMMA",
                    "SPACE_BEFORE_COLON",
                    "SPACE_AFTER_COLON",
                    "SPACE_WITHIN_PARENTHESES",
                    "SPACE_BEFORE_METHOD_CALL_PARENTHESES",
                    "SPACE_BEFORE_IF_PARENTHESES"
                )
                consumer.showCustomOption(
                    DreamShaderCodeStyleSettings::class.java,
                    "SPACE_AROUND_DOUBLE_COLON",
                    DreamShaderBundle.message("codeStyle.spaceAroundDoubleColon"),
                    customizableOptions.SPACES_OTHER
                )
            }
            SettingsType.BLANK_LINES_SETTINGS -> {
                consumer.showCustomOption(
                    DreamShaderCodeStyleSettings::class.java,
                    "BLANK_LINES_BETWEEN_SECTIONS",
                    DreamShaderBundle.message("codeStyle.blankLinesBetweenSections"),
                    customizableOptions.BLANK_LINES
                )
            }
            SettingsType.WRAPPING_AND_BRACES_SETTINGS -> {
                consumer.showCustomOption(
                    DreamShaderCodeStyleSettings::class.java,
                    "ALIGN_SECTION_ASSIGNMENTS",
                    DreamShaderBundle.message("codeStyle.alignSectionAssignments"),
                    "DreamShader"
                )
            }
            else -> {
            }
        }
    }

    override fun getCodeSample(settingsType: SettingsType): String {
        return """
            Shader Main {
                Settings = {
                    Domain = Surface;
                    ShadingModel = DefaultLit;
                }

                Graph = {
                    float2 uv = UE.TexCoord(Index=0);
                    float3 color = Texture::Sample2DRGB(MainTex, uv);
                    Base.BaseColor = color;
                }
            }
        """.trimIndent()
    }
}
