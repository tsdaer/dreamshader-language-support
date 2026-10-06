package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialect
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * C32 验收：混合 `.dsh` legacy 区段的逐声明补全——块内 caret 的 1.x 上下文来自块的缓存重解析
 * （函数体内 isInDeclarationBody+isFunctionLike、Settings 内 isInSettingsOrOptionsSection），
 * 建议器据此产出内建内建函数/设置键；顶层缝隙与 V2 区段的既有行为零变化；纯 1.x / 纯 V2 文件
 * 不走该分支；callable 候选含 legacy 声明。
 */
class DreamShaderLegacyCompletionSegmentationTest : BasePlatformTestCase() {

    private val mixed = """
        Function LegacyTint(in vec3 c, out vec3 o)
        {
            o = saturate(c);
        }

        Shader Main
        {
            Settings
            {
                Domain = "Surface"
            }
        }

        #pragma material(Domain = "Surface")

        uniform float3 Tint = float3(1.0, 0.0, 0.0);

        export void M_Entry(inout material m)
        {
            m.BaseColor = Tint;
        }
    """.trimIndent()

    private fun openMixed(): Pair<DreamShaderPsiFile, String> {
        val file = myFixture.configureByText("M_Mixed.dsh", mixed) as DreamShaderPsiFile
        return file to file.text
    }

    private fun offsetOf(text: String, needle: String, occurrence: Int = 0): Int {
        var start = -1
        repeat(occurrence + 1) { start = text.indexOf(needle, start + 1) }
        check(start >= 0) { "needle not found: $needle" }
        return start + needle.length
    }

    fun testLegacyFunctionBodyGetsFunctionContext() {
        val (file, text) = openMixed()
        val context = DreamShaderCompletionContextAnalyzer.analyze(file, offsetOf(text, "saturate(c)"))

        assertFalse("the caret is not top level inside the legacy body: $context", context.isTopLevel)
        assertTrue(context.isInDeclarationBody)
        assertTrue(context.isFunctionLikeDeclaration)
        assertEquals("function", context.declarationKeyword)
    }

    fun testLegacySettingsSectionGetsSettingsContext() {
        val (file, text) = openMixed()
        val context = DreamShaderCompletionContextAnalyzer.analyze(file, offsetOf(text, "Domain = \"Surface\""))

        assertTrue("the caret sits in the legacy Settings section: $context", context.isInSectionBody)
        assertTrue(context.isInSettingsOrOptionsSection)
        assertEquals("settings", context.currentSectionName)
        assertEquals("shader", context.declarationKeyword)
    }

    fun testSuggesterServesIntrinsicsInsideLegacyFunctionBody() {
        val (file, text) = openMixed()
        val suggestions = DreamShaderCompletionSuggester.suggest(file, offsetOf(text, "saturate(c)"))

        assertTrue(
            "the legacy function body context serves HLSL intrinsics: ${suggestions.map { it.label }}",
            suggestions.any { it.label == "saturate" },
        )
        assertTrue(suggestions.any { it.label == "UE" })
    }

    fun testSuggesterServesSettingsKeysInsideLegacySettingsSection() {
        val (file, text) = openMixed()
        // The caret right after a partially typed key name inside the legacy Settings
        // section — the fresh-key slot context.
        val suggestions = DreamShaderCompletionSuggester.suggest(file, text.indexOf("Domain") + "Domain".length)

        assertTrue(
            "the legacy Settings section serves setting keys: ${suggestions.map { it.label }}",
            suggestions.any { it.label == "Domain" },
        )
    }

    fun testTopLevelGapKeepsTopLevelKeywords() {
        val (file, text) = openMixed()
        val context = DreamShaderCompletionContextAnalyzer.analyze(file, offsetOf(text, "#pragma material(Domain"))
        assertTrue("the gap between declarations is top level: $context", context.isTopLevel)

        val suggestions = DreamShaderCompletionSuggester.suggest(file, offsetOf(text, "#pragma material(Domain"))
        assertTrue(suggestions.any { it.label == "Shader" })
    }

    fun testRegionDispatchStaysThreeValued() {
        val (file, text) = openMixed()
        assertEquals(
            DreamShaderCompletionDialects.Region.V1,
            DreamShaderCompletionDialects.regionAt(file, offsetOf(text, "saturate(c)")),
        )
        assertEquals(
            DreamShaderCompletionDialects.Region.V2,
            DreamShaderCompletionDialects.regionAt(file, offsetOf(text, "m.BaseColor = Tint")),
        )
        assertEquals(
            DreamShaderCompletionDialects.Region.BOTH,
            DreamShaderCompletionDialects.regionAt(file, text.indexOf("#pragma") - 1),
        )
        assertEquals(DreamShaderDialect.HEADER_MIXED, DreamShaderCompletionDialects.dialectOf(file))
    }

    fun testCallableCandidatesIncludeLegacyDeclarations() {
        val (file, _) = openMixed()
        val candidates = collectCallableCandidates(file)

        assertTrue(
            "the text-level signature scan still sees legacy declarations: ${candidates.plain.map { it.label }}",
            candidates.plain.any { it.label == "LegacyTint" },
        )
    }

    fun testPureLegacyFileKeepsPsiContext() {
        val file = myFixture.configureByText(
            "Pure.dsf",
            """
            Function LegacyTint(in vec3 c, out vec3 o)
            {
                o = saturate(c);
            }
            """.trimIndent(),
        )
        val context = DreamShaderCompletionContextAnalyzer.analyze(file, file.text.indexOf("saturate(c)"))

        assertTrue("the frozen 1.x PSI branch keeps answering: $context", context.isFunctionLikeDeclaration)
        assertFalse(context.isTopLevel)
    }

    fun testPureV2FileHasNoLegacySubtrees() {
        val file = myFixture.configureByText(
            "Pure.dss",
            """
            export void M_Entry(inout material m)
            {
                m.BaseColor = float3(1.0, 1.0, 1.0);
            }
            """.trimIndent(),
        ) as DreamShaderPsiFile

        assertTrue(DreamShaderLegacyCompletionSubtrees.of(file).isEmpty())
        assertEquals(DreamShaderDialect.V2, DreamShaderCompletionDialects.dialectOf(file))
    }
}
