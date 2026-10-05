package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * A6 验收：Substrate 语法糖规则（DSH5293/5295/5296/5297/5298）与命名实参顺序（DSH2158）
 * 的正反例；规则不依赖 Bridge 数据，负例必须完全静默。
 */
class DreamShader2SubstrateDiagnosticsTest : BasePlatformTestCase() {

    private fun highlightErrors(vararg files: com.intellij.openapi.vfs.VirtualFile): List<String> =
        files.flatMap { file ->
            myFixture.openFileInEditor(file)
            myFixture.doHighlighting().mapNotNull(HighlightInfo::getDescription)
        }

    private fun addV2File(name: String, text: String) = myFixture.addFileToProject(name, text).virtualFile!!

    // ------------------------------------------------------------------
    // DSH5293 — operators outside the sugar
    // ------------------------------------------------------------------

    fun testDsh5293FlagsSubtractDivideAndCompoundAssignment() {
        val file = addV2File(
            "DShader/Materials/M_5293.dss",
            """
            export void M(inout material m)
            {
                Substrate A = Substrate.Slab();
                Substrate B = Substrate.Slab();
                Substrate X = A - B;
                Substrate Y = A / B;
                A += B;
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertTrue(descriptions.any { it.contains("DSH5293") }, "expected DSH5293, got: $descriptions")
        assertEquals(3, descriptions.count { it.contains("DSH5293") }, "subtract, divide and compound each report once: $descriptions")
    }

    fun testDsh5293FlagsSubstrateTimesSubstrateAndLerpMixing() {
        val file = addV2File(
            "DShader/Materials/M_5293b.dss",
            """
            export void M(inout material m)
            {
                Substrate A = Substrate.Slab();
                Substrate B = Substrate.Slab();
                Substrate X = A * B;
                Substrate Y = lerp(A, 0.5, 0.5);
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertEquals(2, descriptions.count { it.contains("DSH5293") }, "A*B and the mixed lerp each report: $descriptions")
    }

    fun testDsh5293AcceptsTheSugarForms() {
        val file = addV2File(
            "DShader/Materials/M_5293ok.dss",
            """
            export void M(inout material m)
            {
                Substrate A = Substrate.Slab(BaseColor = float3(1, 0, 0), Roughness = 0.4, Haziness = 0.2);
                Substrate B = Substrate.Slab();
                Substrate Sum = A + B;
                Substrate Weighted = A * 0.5;
                Substrate Mixed = lerp(A, B, 0.5);
                m.FrontMaterial = Sum;
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertTrue(
            descriptions.none { it.contains("DSH5293") || it.contains("DSH5295") || it.contains("DSH5296") },
            "legal sugar must stay silent, got: $descriptions",
        )
    }

    // ------------------------------------------------------------------
    // DSH5295 / DSH5296 — Slab parameter families
    // ------------------------------------------------------------------

    fun testDsh5295FlagsConflictingSlabParameters() {
        val file = addV2File(
            "DShader/Materials/M_5295.dss",
            """
            export void M(inout material m)
            {
                Substrate S = Substrate.Slab(BaseColor = float3(1, 1, 1), DiffuseAlbedo = float3(0, 0, 0));
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertEquals(2, descriptions.count { it.contains("DSH5295") }, "both conflicting arguments are marked: $descriptions")
    }

    fun testDsh5296FlagsCompanionlessVirtualArguments() {
        val haziness = addV2File(
            "DShader/Materials/M_5296a.dss",
            """
            export void M(inout material m)
            {
                Substrate S = Substrate.Slab(Haziness = 0.5);
            }
            """.trimIndent(),
        )
        val thickness = addV2File(
            "DShader/Materials/M_5296b.dss",
            """
            export void M(inout material m)
            {
                Substrate S = Substrate.Slab(Thickness = 1.0);
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(haziness, thickness)
        assertEquals(2, descriptions.count { it.contains("DSH5296") }, "Haziness and Thickness each report: $descriptions")
    }

    // ------------------------------------------------------------------
    // DSH5297 / DSH5298 — builder values
    // ------------------------------------------------------------------

    fun testDsh5297FlagsMemberWriteAfterFirstUse() {
        val file = addV2File(
            "DShader/Materials/M_5297.dss",
            """
            export void M(inout material m)
            {
                Substrate S = Substrate.Slab();
                S.Roughness = 0.2;
                m.FrontMaterial = S;
                S.BaseColor = float3(1, 0, 0);
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertEquals(1, descriptions.count { it.contains("DSH5297") }, "the write after first use reports: $descriptions")
    }

    fun testDsh5297AcceptsWritesBeforeFirstUse() {
        val file = addV2File(
            "DShader/Materials/M_5297ok.dss",
            """
            export void M(inout material m)
            {
                Substrate S = Substrate.Slab();
                S.Roughness = 0.2;
                S.BaseColor = float3(1, 0, 0);
                m.FrontMaterial = S;
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertTrue(descriptions.none { it.contains("DSH529") }, "writes before first use are legal, got: $descriptions")
    }

    fun testDsh5298FlagsMemberWriteUnderForeignIf() {
        val file = addV2File(
            "DShader/Materials/M_5298.dss",
            """
            export void M(inout material m, float wetness)
            {
                Substrate S = Substrate.Slab();
                if (wetness > 0.5)
                {
                    S.Roughness = 0.9;
                }
                m.FrontMaterial = S;
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertEquals(1, descriptions.count { it.contains("DSH5298") }, "the foreign-if write reports: $descriptions")
    }

    fun testBuilderDeclaredInsideArmIsItsOwn() {
        val file = addV2File(
            "DShader/Materials/M_5298ok.dss",
            """
            export void M(inout material m, float wetness)
            {
                Substrate Dry = Substrate.Slab();
                if (wetness > 0.5)
                {
                    Substrate Wet = Substrate.Slab();
                    Wet.Roughness = 0.9;
                    m.FrontMaterial = Wet;
                }
                m.FrontMaterial = Dry;
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertTrue(descriptions.none { it.contains("DSH5298") }, "a builder declared inside the arm is that arm's own, got: $descriptions")
    }

    // ------------------------------------------------------------------
    // DSH2158 — positional after named
    // ------------------------------------------------------------------

    fun testDsh2158FlagsPositionalAfterNamed() {
        val file = addV2File(
            "DShader/Materials/M_2158.dss",
            """
            export void M(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0, 1);
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertEquals(1, descriptions.count { it.contains("DSH2158") }, "got: $descriptions")
    }

    fun testDsh2158AcceptsConsistentlyNamedArguments() {
        val file = addV2File(
            "DShader/Materials/M_2158ok.dss",
            """
            export void M(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float3 C = float3(1, 2, 3);
                float Scaled = saturate(C.x);
            }
            """.trimIndent(),
        )

        val descriptions = highlightErrors(file)
        assertTrue(descriptions.none { it.contains("DSH2158") }, "got: $descriptions")
    }

    // ------------------------------------------------------------------
    // C2 mismatch — warning level, never error
    // ------------------------------------------------------------------

    fun testTypeMismatchReportsWeakWarningOnly() {
        val file = addV2File(
            "DShader/Materials/M_Mismatch.dss",
            """
            export void M(inout material m)
            {
                float Amount = 1.0;
                Substrate S = Substrate.Slab();
                Amount = S;
            }
            """.trimIndent(),
        )

        myFixture.openFileInEditor(file)
        val weak = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING).mapNotNull(HighlightInfo::getDescription)
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).mapNotNull(HighlightInfo::getDescription)
        assertTrue(weak.any { it.contains("inferred type") }, "expected the C2 mismatch warning, got: $weak")
        assertTrue(errors.none { it.contains("inferred type") }, "the mismatch must never be an error, got: $errors")
    }

    fun testCompoundAssignmentMismatchWarnsAndStaysSilentOnSubstrate() {
        val file = addV2File(
            "DShader/Materials/M_MismatchCompound.dss",
            """
            export void M(inout material m)
            {
                float3 Color = float3(1, 0, 0);
                Texture2D Map;
                Color += Map;
            }
            """.trimIndent(),
        )

        myFixture.openFileInEditor(file)
        val weak = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING).mapNotNull(HighlightInfo::getDescription)
        assertTrue(
            weak.any { it.contains("inferred type") },
            "compound assignment into a texture must warn, got: $weak",
        )
    }

    fun testCompoundAssignmentWithSubstrateDoesNotDoubleReport() {
        val file = addV2File(
            "DShader/Materials/M_MismatchSubstrate.dss",
            """
            export void M(inout material m)
            {
                Substrate A = Substrate.Slab();
                Substrate B = Substrate.Slab();
                A += B;
            }
            """.trimIndent(),
        )

        myFixture.openFileInEditor(file)
        val weak = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING).mapNotNull(HighlightInfo::getDescription)
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).mapNotNull(HighlightInfo::getDescription)
        assertTrue(errors.any { it.contains("DSH5293") }, "DSH5293 keeps the compound-assignment rule: $errors")
        assertTrue(
            weak.none { it.contains("inferred type") },
            "the mismatch warning must not stack on the DSH5293 error, got: $weak",
        )
    }

    fun testCompoundAssignmentBetweenNumericsStaysSilent() {
        val file = addV2File(
            "DShader/Materials/M_MismatchCompoundOk.dss",
            """
            export void M(inout material m)
            {
                float3 Color = float3(1, 0, 0);
                float3 Other = float3(0, 1, 0);
                Color += Other;
                Color *= 0.5;
            }
            """.trimIndent(),
        )

        myFixture.openFileInEditor(file)
        val weak = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING).mapNotNull(HighlightInfo::getDescription)
        assertTrue(
            weak.none { it.contains("inferred type") },
            "numeric compound assignments are legal, got: $weak",
        )
    }

    fun testCallArgumentMismatchWarnsAgainstDeclaredParameter() {
        val file = addV2File(
            "DShader/Materials/M_CallArg.dss",
            """
            void Tint(float3 Color)
            {
            }
            export void M(inout material m)
            {
                Texture2D Map;
                Tint(Map);
                Tint(Color = Map);
                float3 Ok = float3(1, 0, 0);
                Tint(Ok);
                Tint(UnknownThing);
            }
            """.trimIndent(),
        )

        myFixture.openFileInEditor(file)
        val weak = myFixture.doHighlighting(HighlightSeverity.WEAK_WARNING).mapNotNull(HighlightInfo::getDescription)
        assertEquals(
            "the positional and named texture arguments warn, the numeric and unknown ones stay silent: $weak",
            2,
            weak.count { it.contains("argument's inferred type") },
        )
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).mapNotNull(HighlightInfo::getDescription)
        assertTrue(
            errors.none { it.contains("argument's inferred type") },
            "the argument mismatch must never be an error, got: $errors",
        )
    }
}
