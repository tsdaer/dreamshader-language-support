package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * D8 first batch: the unknown-type rule moved from the semantic annotator to
 * `DreamShaderUnknownTypeInspection`. The annotator must not report the rule any more
 * (no duplicate reporting), while the inspection drives both editor squiggles and
 * Inspect Code over cross-file samples.
 */
class DreamShaderUnknownTypeInspectionTest : BasePlatformTestCase() {

    private val shaderWithBadType = """
        Shader Main {
            Inputs {
                float9 Tint;
            }
            Graph {
                Base.BaseColor = float3(1.0, 1.0, 1.0);
            }
        }
    """.trimIndent()

    fun testCollectorFindsUnknownTypes() {
        myFixture.configureByText("collector.dsm", shaderWithBadType)
        val problems = DreamShaderSemanticAnnotationPipeline()
            .collectUnknownTypeProblems(myFixture.file as com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile)
        assertEquals(1, problems.size)
        assertEquals("float9", problems[0].type)
        assertEquals("float", problems[0].suggestion)
        assertTrue(problems[0].message.contains("Unknown type 'float9'"))
    }

    fun testInspectionHighlightsAndAnnotatorDoesNotDuplicate() {
        myFixture.enableInspections(DreamShaderUnknownTypeInspection())
        myFixture.configureByText("dedup.dsm", shaderWithBadType)

        val infos = myFixture.doHighlighting(HighlightSeverity.ERROR)
            .mapNotNull(HighlightInfo::getDescription)
        assertEquals(
            1,
            infos.count { it.contains("Unknown type 'float9'") },
            "expected exactly one reporting source for the unknown type, got: $infos"
        )
    }

    fun testInspectionScanCoversCrossFileSample() {
        myFixture.enableInspections(DreamShaderUnknownTypeInspection())
        val header = myFixture.addFileToProject(
            "DShader/Headers/Shared.dsh",
            """
            Function Tint(in float9 color, out float3 outColor) {
                outColor = float3(color, color, color);
            }
            """.trimIndent()
        ).virtualFile
        val material = myFixture.addFileToProject(
            "DShader/Materials/M_Bad.dsm",
            """
            Shader Main {
                Properties {
                    float9 SurfaceTint = float9(1.0, 1.0, 1.0);
                }
                Graph {
                    Base.BaseColor = float3(1.0, 1.0, 1.0);
                }
            }
            """.trimIndent()
        ).virtualFile

        val headerDescriptions = descriptionsFor(header)
        val materialDescriptions = descriptionsFor(material)
        assertTrue(
            headerDescriptions.any { it.contains("Unknown type 'float9'") },
            "expected the inspection to scan the header, got: $headerDescriptions"
        )
        assertTrue(
            materialDescriptions.any { it.contains("Unknown type 'float9'") },
            "expected the inspection to scan the material, got: $materialDescriptions"
        )
    }

    private fun descriptionsFor(vararg files: com.intellij.openapi.vfs.VirtualFile): List<String> =
        files.flatMap { file ->
            myFixture.openFileInEditor(file)
            myFixture.doHighlighting(HighlightSeverity.ERROR).mapNotNull(HighlightInfo::getDescription)
        }
}
