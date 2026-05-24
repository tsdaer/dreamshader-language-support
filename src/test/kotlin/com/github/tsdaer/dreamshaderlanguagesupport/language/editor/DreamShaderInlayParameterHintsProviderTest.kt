package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DreamShaderInlayParameterHintsProviderTest : BasePlatformTestCase() {
    private val provider = DreamShaderInlayParameterHintsProvider()

    fun testProducesHintsForUeAndIntrinsicCalls() {
        val file = myFixture.configureByText(
            "inlay_calls.dsm",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(0);
                    float s = saturate(roughness);
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers
            .flatMap { provider.getParameterHints(it) }
            .sortedBy { it.offset }

        assertEquals(listOf("Index:", "x:"), hints.map { hint: InlayInfo -> hint.text })
        assertTrue(
            "Expected inlay at literal argument '0'",
            hints.any { it.text == "Index:" && file.text.substring(it.offset).startsWith("0") }
        )
        assertTrue(
            "Expected inlay at identifier argument 'roughness'",
            hints.any { it.text == "x:" && file.text.substring(it.offset).startsWith("roughness") }
        )
    }

    fun testSkipsNamedArguments() {
        val file = myFixture.configureByText(
            "inlay_named_args.dsm",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(Index=0);
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers.flatMap { provider.getParameterHints(it) }

        assertTrue("Named argument call should not emit redundant hint", hints.isEmpty())
    }
}
