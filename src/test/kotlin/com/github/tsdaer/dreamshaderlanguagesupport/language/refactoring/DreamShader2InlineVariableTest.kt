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
 * C25 验收：2.0 文件的 Inline Variable——单/多使用处替换为带括号初始化器、声明与 doc 块随删的
 * diff 断言；拒绝矩阵（无初始化器、多声明器、初始化器引用形参/局部/全局、初始化器含副作用、
 * 初始化器调用用户函数、使用处为写目标、`@custom` 体、出作用域使用不串扰）；1.x 文件静默；
 * action 接线。
 */
class DreamShader2InlineVariableTest : BasePlatformTestCase() {

    private val body = """
        export void M_Entry(inout material m)
        {
            /// The tint scale for this entry.
            float Weight = 0.5;
            float3 Tinted = TintScale(Weight);
            m.BaseColor = Tinted * Weight;
        }
    """.trimIndent()

    private fun openFile(text: String = body, name: String = "M_InlineVar.dss") =
        myFixture.configureByText(name, text)

    private fun moveTo(file: PsiFile, needle: String, occurrence: Int = 0) {
        var start = -1
        repeat(occurrence + 1) { start = file.text.indexOf(needle, start + 1) }
        check(start >= 0) { "needle not found: $needle" }
        myFixture.editor.caretModel.moveToOffset(start)
    }

    private fun invokeHandler(file: PsiFile) {
        DreamShader2InlineVariableHandler().invoke(project, myFixture.editor, file, DataContext.EMPTY_CONTEXT)
    }

    private fun expectRefusal(file: PsiFile, before: String, needle: String, occurrence: Int = 0) {
        moveTo(file, needle, occurrence)
        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals("the document is untouched", before, myFixture.editor.document.text)
    }

    fun testInlinesIntoEveryUsageAndRemovesDeclarationWithDocBlock() {
        val file = openFile()
        moveTo(file, "Weight = 0.5")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertFalse("declaration removed: $after", after.contains("float Weight"))
        assertFalse("doc block removed with the declaration: $after", after.contains("tint scale"))
        assertTrue("usage replaced parenthesized: $after", after.contains("TintScale((0.5))"))
        assertTrue(after.contains("Tinted * (0.5);"))
        assertFalse("no blank hole left: $after", after.contains("\n\n\n"))
    }

    fun testInlineActionIsRegistered() {
        val file = openFile()
        moveTo(file, "Weight = 0.5")

        myFixture.performEditorAction("DreamShader.InlineVariable")

        val after = myFixture.editor.document.text
        assertTrue(
            after.contains("TintScale((0.5))") && !after.contains("float Weight"),
            "the Refactoring-menu action reaches the 2.0 handler: $after",
        )
    }

    fun testRefusesDeclarationWithoutInitializer() {
        val file = openFile(
            """
            export void M_NoInit(inout material m)
            {
                float Weight;
                m.BaseColor = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_InlineNoInit.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "float Weight")
    }

    fun testRefusesMultiDeclaratorDeclaration() {
        val file = openFile(
            """
            export void M_MultiDecl(inout material m)
            {
                float Weight = 0.5, Other = 1.0;
                m.BaseColor = float3(Weight, Weight, Weight) * Other;
            }
            """.trimIndent(),
            "M_InlineMulti.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Weight = 0.5")
    }

    fun testRefusesInitializerReadingParameter() {
        val file = openFile(
            """
            export void M_ParamInit(float Tint, inout material m)
            {
                float Weight = Tint * 0.5;
                m.BaseColor = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_InlineParam.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Weight = Tint")
    }

    fun testRefusesInitializerReadingLocal() {
        val file = openFile(
            """
            export void M_LocalInit(inout material m)
            {
                float Base = 2.0;
                float Weight = Base * 0.5;
                m.BaseColor = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_InlineLocal.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Weight = Base")
    }

    fun testRefusesInitializerReadingGlobal() {
        val file = openFile(
            """
            float Counter;

            export void M_GlobalInit(inout material m)
            {
                float Weight = Counter * 0.5;
                m.BaseColor = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_InlineGlobal.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Weight = Counter")
    }

    fun testRefusesInitializerWithAssignmentSideEffect() {
        val file = openFile(
            """
            export void M_SideEffect(inout material m)
            {
                float Weight = 0.5;
                Weight += 1.0;
                m.BaseColor = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_InlineSide.dss",
        )
        // The caret sits on the second statement: the inlined initializer would be `0.5`,
        // and the compound assignment would become `0.5 += 1.0` — refuse.
        expectRefusal(file, myFixture.editor.document.text, "Weight += 1.0")
    }

    fun testRefusesInitializerCallingUserFunction() {
        val file = openFile(
            """
            float3 TintScale(float2 v)
            {
                return float3(v.x, v.x, v.x);
            }

            export void M_CallInit(inout material m)
            {
                float3 Weight = TintScale(float2(1.0, 2.0));
                m.BaseColor = Weight;
            }
            """.trimIndent(),
            "M_InlineCall.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Weight = TintScale")
    }

    fun testRefusesUsageInWritePosition() {
        val file = openFile(
            """
            export void M_WriteUse(inout material m)
            {
                float Weight = 0.5;
                Weight = 2.0;
                m.BaseColor = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_InlineWrite.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Weight = 0.5")
    }

    fun testRefusesInsideCustomBody() {
        val file = openFile(
            """
            /// @custom
            export float3 Body(float3 c)
            {
                float Weight = 0.5;
                return c * Weight;
            }
            """.trimIndent(),
            "M_InlineCustom.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "float Weight")
    }

    fun testOutOfScopeUsageIsLeftAlone() {
        val file = openFile(
            """
            export void M_Scope(inout material m)
            {
                if (m.Roughness > 0.5)
                {
                    /// Only visible inside the arm.
                    float Weight = 0.5;
                    m.BaseColor = float3(Weight, Weight, Weight);
                }
                m.Emissive = float3(Weight, Weight, Weight);
            }
            """.trimIndent(),
            "M_InlineScope.dss",
        )
        moveTo(file, "Weight = 0.5")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue("the visible usage is inlined: $after", after.contains("m.BaseColor = float3((0.5), (0.5), (0.5));"))
        assertFalse("the declaration is removed: $after", after.contains("float Weight = 0.5"))
        assertTrue(
            "the out-of-scope usage does not belong to this declaration: $after",
            after.contains("m.Emissive = float3(Weight, Weight, Weight);"),
        )
    }

    fun testLegacyFileStaysSilent() {
        val file = openFile(
            """
            Function void Helper()
            {
                float x = 1.0;
                float y = x;
            }
            """.trimIndent(),
            "M_Legacy.dsm",
        )
        val before = myFixture.editor.document.text
        moveTo(file, "float x = 1.0")

        invokeHandler(file)
        assertEquals("the 1.x dialect keeps the platform default handler", before, myFixture.editor.document.text)
    }
}
