package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.application.options.CodeStyle
import com.intellij.application.options.CodeStyleAbstractConfigurable
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderCodeStyleSettingsProviderTest : BasePlatformTestCase() {
    fun testCustomCodeStyleSettingsDefaults() {
        val custom = CodeStyle.getSettings(project).getCustomSettings(DreamShaderCodeStyleSettings::class.java)

        assertFalse(custom.ALIGN_SECTION_ASSIGNMENTS)
        assertEquals(1, custom.BLANK_LINES_BETWEEN_SECTIONS)
        assertFalse(custom.SPACE_AROUND_DOUBLE_COLON)
    }

    fun testLanguageProviderUsesDreamShaderLanguageAndSample() {
        val provider = DreamShaderLanguageCodeStyleSettingsProvider()

        assertSame(DreamShaderLanguage, provider.language)
        assertTrue(provider.getCodeSample(com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider.SettingsType.SPACING_SETTINGS).contains("Shader Main"))
    }

    fun testCodeStyleProviderCreatesSettingsPage() {
        val provider = DreamShaderCodeStyleSettingsProvider()
        val settings = CodeStyle.createTestSettings()
        val page = provider.createConfigurable(settings, settings) as CodeStyleAbstractConfigurable

        assertNotNull(page)
        assertEquals("DreamShader", page.displayName)
    }
}
