package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.psi.PsiFile
import com.intellij.refactoring.util.CommonRefactoringUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * C21 验收：2.0 文件的 Extract Function——单语句/多语句提取的文档 diff 断言（新函数签名、
 * 入参/出参映射、调用点替换、插入位置）；控制流、部分选区、成员写入、作用域改变等场景拒绝
 * 且文档不变；1.x 文件静默。
 */
class DreamShader2ExtractFunctionTest : BasePlatformTestCase() {

    private val body = """
        uniform float Gain = 0.5;

        float3 TintScale(float2 v)
        {
            return float3(v.x, v.x, v.x);
        }

        export void M_Entry(inout material m)
        {
            float2 UV = UE.TexCoord(Index = 0);
            float3 Tinted = TintScale(UV);
            Tinted *= Gain;
            m.BaseColor = Tinted;
        }
    """.trimIndent()

    private fun openFile(text: String = body, name: String = "M_ExtractFn.dss") =
        myFixture.configureByText(name, text)

    private fun select(file: PsiFile, needle: String, occurrence: Int = 0) {
        var start = -1
        repeat(occurrence + 1) { start = file.text.indexOf(needle, start + 1) }
        check(start >= 0) { "needle not found: $needle" }
        myFixture.editor.selectionModel.setSelection(start, start + needle.length)
    }

    private fun invokeHandler(file: PsiFile) {
        DreamShader2ExtractFunctionHandler().invoke(project, myFixture.editor, file, DataContext.EMPTY_CONTEXT)
    }

    fun testExtractsSingleStatementWithInParameterAndOutParameter() {
        val file = openFile()
        val before = myFixture.editor.document.text
        select(file, "Tinted *= Gain;")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        // A compound assignment reads its own old value, so the written-then-used local
        // comes back `inout` (M16 C26 correction; the old `out` read an undefined value).
        assertTrue(
            after.contains("void Extracted(float Gain, inout float3 Tinted)\n{\n    Tinted *= Gain;\n}"),
            "extracted function expected: ${after.lines()}",
        )
        assertTrue(
            after.contains("    Extracted(Gain, Tinted);"),
            "call site replacement expected: ${after.lines()}",
        )
        assertTrue(after.indexOf("void Extracted") > before.indexOf("m.BaseColor"), "the helper lands after the current function")
    }

    fun testIncrementTargetBecomesInout() {
        val file = openFile(
            """
            export void M_Inc(inout material m)
            {
                float Weight = 0.5;
                Weight++;
                m.BaseColor = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_ExtractInc.dss",
        )
        select(file, "Weight++;")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("void Extracted(inout float Weight)"),
            "`++` target reads its old value and must be inout: ${after.lines()}",
        )
    }

    fun testSingleOutValueBecomesReturnValue() {
        val file = openFile(
            """
            export void M_ReturnValue(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float3 Tinted;
                Tinted = TintScale(UV);
                m.BaseColor = Tinted;
            }
            """.trimIndent(),
            "M_ExtractReturn.dss",
        )
        select(file, "Tinted = TintScale(UV);")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("float3 Extracted(float2 UV)\n{\n    float3 Tinted = TintScale(UV);\n    return Tinted;\n}"),
            "return-value signature with `return` line expected: ${after.lines()}",
        )
        assertTrue(
            after.contains("    float3 Tinted = Extracted(UV);"),
            "call site becomes the receiving declaration: ${after.lines()}",
        )
        assertFalse("the bare declaration line is removed", after.contains("float3 Tinted;"))
        assertTrue(after.contains("m.BaseColor = Tinted;"), "later code is untouched: ${after.lines()}")
    }

    fun testMultipleWritesOfReturnValueKeepTheirStatements() {
        val file = openFile(
            """
            export void M_MultiWrite(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float3 Tinted;
                Tinted = TintScale(UV);
                Tinted = TintScale(UV * 2.0);
                m.BaseColor = Tinted;
            }
            """.trimIndent(),
            "M_ExtractMultiWrite.dss",
        )
        select(file, "Tinted = TintScale(UV);\n    Tinted = TintScale(UV * 2.0);")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("float3 Extracted(float2 UV)\n{\n    float3 Tinted;\n    Tinted = TintScale(UV);\n    Tinted = TintScale(UV * 2.0);\n    return Tinted;\n}"),
            "multi-write body keeps its statements under a bare declaration: ${after.lines()}",
        )
        assertTrue(after.contains("float3 Tinted = Extracted(UV);"), "call site declaration expected: ${after.lines()}")
        assertFalse("the bare declaration line is removed", after.contains("\n    float3 Tinted;\n    Tinted = TintScale(UV);\n    Tinted = TintScale(UV * 2.0);\n    m.BaseColor"))
    }

    fun testUpgradeFallsBackToOutParameterForInitializedDeclaration() {
        val file = openFile(
            """
            export void M_InitDecl(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float3 Tinted = TintScale(UV);
                Tinted = TintScale(UV * 2.0);
                m.BaseColor = Tinted;
            }
            """.trimIndent(),
            "M_ExtractInitDecl.dss",
        )
        select(file, "Tinted = TintScale(UV * 2.0);")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        // Replacing the assignment with a same-name declaration would redeclare the
        // initialized local, so the out matrix holds.
        assertTrue(
            after.contains("void Extracted(float2 UV, out float3 Tinted)"),
            "initialized declaration keeps the out matrix: ${after.lines()}",
        )
        assertTrue(after.contains("Extracted(UV, Tinted);"))
    }

    fun testUpgradeFallsBackToOutParameterForParameterTarget() {
        val file = openFile(
            """
            export void M_ParamTarget(float3 Tinted, inout material m)
            {
                Tinted = TintScale(UE.TexCoord(Index = 0));
                m.BaseColor = Tinted;
            }
            """.trimIndent(),
            "M_ExtractParamTarget.dss",
        )
        select(file, "Tinted = TintScale(UE.TexCoord(Index = 0));")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("void Extracted(out float3 Tinted)"),
            "a parameter target keeps the out matrix: ${after.lines()}",
        )
        assertTrue(after.contains("Extracted(Tinted);"))
    }

    fun testUpgradeFallsBackWhenDeclarationHasIntermediateUses() {
        val file = openFile(
            """
            export void M_GapUse(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float3 Tinted;
                Tinted = TintScale(UV);
                Tinted = TintScale(UV * 2.0);
                m.BaseColor = Tinted;
            }
            """.trimIndent(),
            "M_ExtractGapUse.dss",
        )
        select(file, "Tinted = TintScale(UV * 2.0);")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        // The first assignment between the bare declaration and the selection reads/writes
        // the declared name — removing the declaration would break it, so the out matrix holds.
        assertTrue(
            after.contains("void Extracted(float2 UV, out float3 Tinted)"),
            "intermediate uses keep the out matrix: ${after.lines()}",
        )
        assertTrue(after.contains("    float3 Tinted;"), "the bare declaration survives: ${after.lines()}")
    }

    fun testMultipleOutValuesKeepOutMatrix() {
        val file = openFile(
            """
            export void M_TwoOuts(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float3 A;
                float B;
                A = TintScale(UV);
                B = 2.0;
                m.BaseColor = A * B;
            }
            """.trimIndent(),
            "M_ExtractTwoOuts.dss",
        )
        select(file, "A = TintScale(UV);\n    B = 2.0;")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("void Extracted(float2 UV, out float3 A, out float B)"),
            "two written values keep the out matrix: ${after.lines()}",
        )
        assertFalse(after.contains("return A;") || after.contains("return B;"), "no return-value upgrade: ${after.lines()}")
    }

    fun testConditionalLastStatementKeepsOutMatrix() {
        val file = openFile(
            """
            export void M_CondLast(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float3 Tinted;
                if (Gain > 0.0)
                {
                    Tinted = TintScale(UV);
                }
                m.BaseColor = Tinted;
            }
            """.trimIndent(),
            "M_ExtractCondLast.dss",
        )
        select(file, "if (Gain > 0.0)\n    {\n        Tinted = TintScale(UV);\n    }")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("void Extracted(float2 UV, out float3 Tinted)"),
            "a conditional final write keeps the out matrix: ${after.lines()}",
        )
        assertFalse(after.contains("return Tinted;"), "no return-value upgrade: ${after.lines()}")
    }

    fun testExtractsMultipleStatementsWithoutFreeVariables() {
        val file = openFile(
            """
            export void M_Multi(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float Scaled = UV.x * 2.0;
                float3 Color = float3(Scaled, Scaled, Scaled);
            }
            """.trimIndent(),
            "M_ExtractMulti.dss",
        )
        select(
            file,
            "float2 UV = UE.TexCoord(Index = 0);\n    float Scaled = UV.x * 2.0;\n    float3 Color = float3(Scaled, Scaled, Scaled);",
        )
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("void Extracted()") && after.contains("float2 UV = UE.TexCoord(Index = 0);"),
            "zero-parameter extraction expected: ${after.lines()}",
        )
        assertTrue(after.contains("    Extracted();"), "bare call expected: ${after.lines()}")
    }

    fun testReadAndWriteBecomesInout() {
        val file = openFile(
            """
            export void M_Inout(inout material m)
            {
                float Weight = 0.5;
                Weight = Weight * 2.0;
                m.BaseColor = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_ExtractInout.dss",
        )
        select(file, "Weight = Weight * 2.0;")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("void Extracted(inout float Weight)") || after.contains("void Extracted(float Weight, inout float Weight)"),
            "inout qualifier expected for read-then-write: ${after.lines()}",
        )
    }

    fun testExtractMethodActionReachesTheHandler() {
        val file = openFile()
        select(file, "Tinted *= Gain;")

        myFixture.performEditorAction("ExtractMethod")

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("void Extracted("),
            "the platform Extract Method action reaches the 2.0 handler: ${after.lines()}",
        )
    }

    fun testRefusesReturnStatement() {
        val file = openFile()
        val before = myFixture.editor.document.text
        select(file, "return float3(v.x, v.x, v.x);")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals(before, myFixture.editor.document.text)
    }

    fun testRefusesPartialStatementSelection() {
        val file = openFile()
        val before = myFixture.editor.document.text
        select(file, "float3 Tinted = TintScale(UV)")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals(before, myFixture.editor.document.text)
    }

    fun testRefusesMemberWrite() {
        val file = openFile()
        val before = myFixture.editor.document.text
        select(file, "m.BaseColor = Tinted;")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals(before, myFixture.editor.document.text)
    }

    fun testRefusesWhenDeclaredInsideIsUsedAfter() {
        val file = openFile(
            """
            export void M_Scope(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float Scaled = UV.x * 2.0;
                m.BaseColor = float3(Scaled, Scaled, Scaled);
            }
            """.trimIndent(),
            "M_ExtractScope.dss",
        )
        val before = myFixture.editor.document.text
        select(file, "float2 UV = UE.TexCoord(Index = 0);\n    float Scaled = UV.x * 2.0;")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals(before, myFixture.editor.document.text)
    }

    fun testLegacyFileStaysSilent() {
        val file = openFile(
            """
            Function void Helper()
            {
                float x = 1.0;
            }
            """.trimIndent(),
            "M_Legacy.dsm",
        )
        val before = myFixture.editor.document.text
        select(file, "float x = 1.0;")

        invokeHandler(file)
        assertEquals("the 1.x dialect keeps the platform default handler", before, myFixture.editor.document.text)
    }
}
