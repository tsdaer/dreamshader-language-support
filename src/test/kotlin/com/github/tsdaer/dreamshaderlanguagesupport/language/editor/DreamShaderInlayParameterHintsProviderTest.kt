package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
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

    fun testInlayHintsUseDedicatedSettingIndependentFromCodeLens() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.enableCodeLens = false
        settings.enableInlayParameterHints = true
        val file = myFixture.configureByText(
            "inlay_setting_independent.dsm",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers.flatMap { provider.getParameterHints(it) }

        assertEquals(listOf("Index:"), hints.map { it.text })
    }

    fun testInlayHintsCanBeDisabledWithDedicatedSetting() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.enableCodeLens = true
        settings.enableInlayParameterHints = false
        val file = myFixture.configureByText(
            "inlay_setting_disabled.dsm",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers.flatMap { provider.getParameterHints(it) }

        assertTrue(hints.isEmpty())
    }

    fun testPrefersImportedDeclarationSignatureOverBuiltinSignature() {
        myFixture.addFileToProject(
            "Library/custom_texcoord.dsh",
            """
            Function TexCoord(in float slot, in float scale, out float2 uv) {
                uv = float2(slot, scale);
            }
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "inlay_imported_over_builtin.dsm",
            """
            import "Library/custom_texcoord.dsh"

            Shader Main {
                Graph {
                    TexCoord(2, 4.0, uvOut);
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers
            .flatMap { provider.getParameterHints(it) }
            .sortedBy { it.offset }

        assertEquals(listOf("slot:", "scale:", "uv:"), hints.map { it.text })
    }

    fun testProducesHintsForNamespaceQualifiedFunctionCall() {
        val file = myFixture.configureByText(
            "inlay_namespace_call.dsm",
            """
            Namespace Tools {
                Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                    result = color * tint;
                }
            }

            Shader Main {
                Graph {
                    Tools::ApplyTint(float3(1,1,1), float3(1,0,0), finalColor);
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers
            .flatMap { provider.getParameterHints(it) }
            .sortedBy { it.offset }

        assertEquals(listOf("color:", "tint:", "result:"), hints.map { it.text })
    }

    fun testSuppressesHintsForIncompleteCalls() {
        val file = myFixture.configureByText(
            "inlay_incomplete_call.dsm",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(0;
                }
            }
            """.trimIndent()
        )

        val identifiers = PsiTreeUtil.collectElements(file) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
        val hints = identifiers.flatMap { provider.getParameterHints(it) }

        assertTrue(hints.isEmpty())
    }
}
