package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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

    fun testProducesHintsForUserDeclaredFunctionCall() {
        val file = myFixture.configureByText(
            "inlay_user_declared_call.dsm",
            """
            Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                result = color * tint;
            }

            Shader Main {
                Graph {
                    ApplyTint(float3(1,1,1), float3(1,0,0), finalColor);
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers
            .flatMap { provider.getParameterHints(it) }
            .sortedBy { it.offset }

        assertEquals(listOf("color:", "tint:", "result:"), hints.map { it.text })
        assertTrue(
            "Expected inlay at first argument",
            hints.any { it.text == "color:" && file.text.substring(it.offset).startsWith("float3(1,1,1)") }
        )
        assertTrue(
            "Expected inlay at second argument",
            hints.any { it.text == "tint:" && file.text.substring(it.offset).startsWith("float3(1,0,0)") }
        )
        assertTrue(
            "Expected inlay at third argument",
            hints.any { it.text == "result:" && file.text.substring(it.offset).startsWith("finalColor") }
        )
    }

    fun testProducesHintsForImportedUserDeclaredFunctionCall() {
        myFixture.addFileToProject(
            "Library/shared.dsh",
            """
            Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                result = color * tint;
            }
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "inlay_imported_user_declared_call.dsm",
            """
            import "Library/shared.dsh"

            Shader Main {
                Graph {
                    ApplyTint(float3(1,1,1), float3(1,0,0), finalColor);
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers
            .flatMap { provider.getParameterHints(it) }
            .sortedBy { it.offset }

        assertEquals(listOf("color:", "tint:", "result:"), hints.map { it.text })
        assertTrue(
            "Expected inlay at imported function first argument",
            hints.any { it.text == "color:" && file.text.substring(it.offset).startsWith("float3(1,1,1)") }
        )
    }
}
