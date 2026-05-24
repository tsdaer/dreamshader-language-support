package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertTrue

class DreamShaderCodeVisionProviderTest : BasePlatformTestCase() {
    fun testCodeVisionHintForDeclarationContainsPackageSummary() {
        val file = myFixture.configureByText(
            "code_vision.dsm",
            """
            Shader Main {
                Graph = {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )
        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)
        assertNotNull("Expected declaration PSI", declaration)

        val provider = DreamShaderCodeVisionProvider()
        val hint = provider.getHint(declaration!!, file)

        assertTrue(hint.contains("Pkg:"))
        assertTrue(hint.contains("Main"))
    }
}
