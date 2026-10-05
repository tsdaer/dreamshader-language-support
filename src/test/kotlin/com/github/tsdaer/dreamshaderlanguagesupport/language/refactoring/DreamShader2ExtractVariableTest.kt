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
 * C20 验收：2.0 文件的 Extract Variable——调用/成员/字面量提取的文档 diff 断言（声明位置、
 * 类型拼写、替换正确）；类型不可知或上下文不支持时拒绝且文档不变；1.x 文件不响应。
 */
class DreamShader2ExtractVariableTest : BasePlatformTestCase() {

    private val body = """
        uniform float2 TintBase = float2(1, 1);

        float3 TintScale(float2 v)
        {
            return float3(v.x, v.x, v.x);
        }

        export void M_Entry(inout material m)
        {
            float2 UV = UE.TexCoord(Index = 0);
            float3 Tinted = TintScale(UV);
            m.BaseColor = Tinted;
        }
    """.trimIndent()

    private fun openFile(text: String = body, name: String = "M_Extract.dss") =
        myFixture.configureByText(name, text)

    private fun select(file: PsiFile, needle: String, occurrence: Int = 0) {
        var start = -1
        repeat(occurrence + 1) { start = file.text.indexOf(needle, start + 1) }
        check(start >= 0) { "needle not found: $needle" }
        myFixture.editor.selectionModel.setSelection(start, start + needle.length)
    }

    private fun invokeHandler(file: PsiFile) {
        DreamShader2ExtractVariableHandler().invoke(project, myFixture.editor, file, DataContext.EMPTY_CONTEXT)
    }

    fun testExtractsCallWithInferredTypeAndSuggestedName() {
        val file = openFile()
        select(file, "TintScale(UV)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(after.contains("float3 uv = TintScale(UV);"), "declaration inserted: ${after.lines()}")
        assertTrue(after.contains("float3 Tinted = uv;"), "use site replaced: ${after.lines()}")
        assertTrue(after.indexOf("float3 uv =") < after.indexOf("float3 Tinted"), "declaration goes before the statement")
    }

    fun testIntroduceVariableActionRoutesToTheHandler() {
        val file = openFile()
        select(file, "TintScale(UV)")

        myFixture.performEditorAction("IntroduceVariable")

        val after = myFixture.editor.document.text
        assertTrue(after.contains("float3 uv = TintScale(UV);"), "the platform action reaches the 2.0 handler: ${after.lines()}")
    }

    fun testExtractsMaterialMemberAsSubstrate() {
        val file = openFile(
            """
            export void M_Member(inout material m)
            {
                Substrate S = m.FrontMaterial;
            }
            """.trimIndent(),
            "M_ExtractMember.dss",
        )
        select(file, "m.FrontMaterial")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(after.contains("Substrate frontMaterial = m.FrontMaterial;"), "got: ${after.lines()}")
        assertTrue(after.contains("Substrate S = frontMaterial;"), "got: ${after.lines()}")
    }

    fun testExtractsLiteralWithScalarSpelling() {
        val file = openFile(
            """
            export void M_Literal(inout material m)
            {
                float Scaled = saturate(0.5);
            }
            """.trimIndent(),
            "M_ExtractLiteral.dss",
        )
        select(file, "0.5")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(after.contains("float value = 0.5;"), "got: ${after.lines()}")
        assertTrue(after.contains("float Scaled = saturate(value);"), "got: ${after.lines()}")
    }

    fun testExtractsFromCaretWithoutSelection() {
        val file = openFile()
        val text = file.text
        val needle = "TintScale(UV)"
        myFixture.editor.caretModel.moveToOffset(text.indexOf(needle) + needle.length)

        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(after.contains("float3 uv = TintScale(UV);"), "caret gesture extracts the trailing call: ${after.lines()}")
    }

    fun testRefusesWhenTypeIsUnknownAndKeepsDocumentUntouched() {
        val file = openFile(
            """
            export void M_Unknown(inout material m)
            {
                float3 Tinted = Mystery(UV);
            }
            """.trimIndent(),
            "M_ExtractUnknown.dss",
        )
        val before = myFixture.editor.document.text
        select(file, "Mystery(UV)")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals(
            before,
            myFixture.editor.document.text,
        )
    }

    fun testRefusesInsideLoopBody() {
        val file = openFile(
            """
            export void M_Loop(inout material m)
            {
                for (int i = 0; i < 4; i++)
                {
                    float Scaled = saturate(i);
                }
            }
            """.trimIndent(),
            "M_ExtractLoop.dss",
        )
        select(file, "saturate(i)")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertFalse(
            myFixture.editor.document.text.contains("float value = saturate"),
            "loop body stays untouched: ${myFixture.editor.document.text.lines()}",
        )
    }

    fun testIgnoresV1Files() {
        val file = openFile(
            """
            Shader(Name="DreamMaterials/Test") {
                Graph = {
                    Color = UE.TexCoord(Index=0);
                }
            }
            """.trimIndent(),
            "Legacy.dsm",
        )
        val text = file.text
        select(file, "UE.TexCoord(Index=0)")

        invokeHandler(file)

        assertTrue(
            myFixture.editor.document.text == text,
            "1.x files keep their content, got: ${myFixture.editor.document.text.lines()}",
        )
    }
}
