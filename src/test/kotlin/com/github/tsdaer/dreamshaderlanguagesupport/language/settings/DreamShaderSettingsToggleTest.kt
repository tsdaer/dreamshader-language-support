package com.github.tsdaer.dreamshaderlanguagesupport.language.settings
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderInlayParameterHintsProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderSettingsToggleTest : BasePlatformTestCase() {
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
}
