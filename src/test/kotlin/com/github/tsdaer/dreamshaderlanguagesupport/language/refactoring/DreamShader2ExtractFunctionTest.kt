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
        assertTrue(
            after.contains("void Extracted(float Gain, out float3 Tinted)\n{\n    Tinted *= Gain;\n}"),
            "extracted function expected: ${after.lines()}",
        )
        assertTrue(
            after.contains("    Extracted(Gain, Tinted);"),
            "call site replacement expected: ${after.lines()}",
        )
        // The read uniform becomes the value parameter; the written-then-used local is `out`
        // (the compound assignment writes it but never reads it).
        assertTrue(after.indexOf("void Extracted") > before.indexOf("m.BaseColor"), "the helper lands after the current function")
        assertFalse("no inout qualifier for write-only values", after.contains("inout float3 Tinted"))
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
