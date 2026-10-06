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
 * C31 验收：2.0 文件的 Inline Function——语句位/赋值位/初始化器位的展开 diff、out/inout 写回
 * 朴素实参、include 闭包被调函数；拒绝矩阵（递归、嵌套/多 return、循环外 break、值形参被写、
 * out 实参非朴素、实参副作用/用户调用、别名写回、绑定不匹配、名字冲突、@custom、更大表达式、
 * 复合赋值、裸 return 用于值位）；1.x 文件静默；action 接线。
 */
class DreamShader2InlineFunctionTest : BasePlatformTestCase() {

    private fun openFile(text: String, name: String) =
        myFixture.configureByText(name, text)

    private fun moveTo(file: PsiFile, needle: String, occurrence: Int = 0) {
        var start = -1
        repeat(occurrence + 1) { start = file.text.indexOf(needle, start + 1) }
        check(start >= 0) { "needle not found: $needle" }
        myFixture.editor.caretModel.moveToOffset(start)
    }

    private fun invokeHandler(file: PsiFile) {
        DreamShader2InlineFunctionHandler().invoke(project, myFixture.editor, file, DataContext.EMPTY_CONTEXT)
    }

    private fun expectRefusal(file: PsiFile, before: String, needle: String, occurrence: Int = 0) {
        moveTo(file, needle, occurrence)
        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals("the document is untouched", before, myFixture.editor.document.text)
    }

    // ------------------------------------------------------------------
    // the three call-site shapes
    // ------------------------------------------------------------------

    fun testInlinesStatementPositionAndDropsTrailingReturn() {
        val file = openFile(
            """
            float TintScale(float s)
            {
                float Adjusted = s * 2.0;
                return Adjusted;
            }

            export void M_Entry(inout material m)
            {
                TintScale(0.25);
                m.BaseColor = float3(1.0, 1.0, 1.0);
            }
            """.trimIndent(),
            "M_InlineFn.dss",
        )
        moveTo(file, "TintScale(0.25)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue("the body is spliced with the argument substituted: $after", after.contains("float Adjusted = (0.25) * 2.0;"))
        assertTrue("the call is gone: $after", !after.contains("TintScale(0.25)"))
        assertTrue("the callee declaration stays: $after", after.contains("float TintScale(float s)"))
        assertFalse("no blank hole left: $after", after.contains("\n\n\n\n"))
    }

    fun testInlinesAssignmentRhsAndRewritesReturn() {
        val file = openFile(
            """
            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                float a = 1.0;
                a = Fade(a);
                m.BaseColor = float3(a, a, a);
            }
            """.trimIndent(),
            "M_InlineAssign.dss",
        )
        moveTo(file, "Fade(a)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue("the return is rewritten onto the LHS: $after", after.contains("a = (a) * 0.5;"))
        assertFalse("the call is gone: $after", after.contains("Fade(a)"))
    }

    fun testInlinesInitializerAndInsertsBareDeclaration() {
        val file = openFile(
            """
            float3 Tint(float t)
            {
                return float3(t, t, t);
            }

            export void M_Entry(inout material m)
            {
                float3 Tinted = Tint(0.5);
                m.BaseColor = Tinted;
            }
            """.trimIndent(),
            "M_InlineInit.dss",
        )
        moveTo(file, "Tint(0.5)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue("the declaration is split: $after", after.contains("float3 Tinted;"))
        assertTrue("the return assigns the declared name: $after", after.contains("Tinted = float3((0.5), (0.5), (0.5));"))
    }

    fun testInlinesAtTheCallStatementsIndentation() {
        val file = openFile(
            """
            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                if (m.Roughness > 0.5)
                {
                    m.Emissive = Fade(0.5);
                }
            }
            """.trimIndent(),
            "M_InlineIndent.dss",
        )
        moveTo(file, "Fade(0.5)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue(
            "the expanded assignment keeps the call's indentation: $after",
            after.contains("        m.Emissive = (0.5) * 0.5;"),
        )
    }

    // ------------------------------------------------------------------
    // parameter binding
    // ------------------------------------------------------------------

    fun testBindsOutParameterWritebackToPlainArguments() {
        val file = openFile(
            """
            void Split(float v, out float lo, out float hi)
            {
                lo = v * 0.5;
                hi = v * 2.0;
            }

            export void M_Entry(inout material m)
            {
                float a = 0.0;
                float b = 0.0;
                Split(1.0, a, b);
                m.BaseColor = float3(a, b, 1.0);
            }
            """.trimIndent(),
            "M_InlineOut.dss",
        )
        moveTo(file, "Split(1.0, a, b)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue("the first write lands on the caller variable: $after", after.contains("a = (1.0) * 0.5;"))
        assertTrue("the second write lands on the other variable: $after", after.contains("b = (1.0) * 2.0;"))
    }

    fun testBindsNamedArgumentsByParameterName() {
        val file = openFile(
            """
            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                float a = 1.0;
                a = Fade(t = a);
                m.BaseColor = float3(a, a, a);
            }
            """.trimIndent(),
            "M_InlineNamed.dss",
        )
        moveTo(file, "Fade(t = a)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue("the named argument binds by name: $after", after.contains("a = (a) * 0.5;"))
    }

    fun testResolvesCalleeThroughIncludeClosure() {
        myFixture.addFileToProject(
            "Includes/helpers.dss",
            """
            export float Fade(float t)
            {
                return t * 0.5;
            }
            """.trimIndent(),
        )
        val file = openFile(
            """
            #include "Includes/helpers.dss"

            export void M_Entry(inout material m)
            {
                m.Emissive = Fade(0.5);
            }
            """.trimIndent(),
            "M_InlineInclude.dss",
        )
        moveTo(file, "Fade(0.5)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue("the included callee is inlined: $after", after.contains("m.Emissive = (0.5) * 0.5;"))
    }

    // ------------------------------------------------------------------
    // refusal matrix
    // ------------------------------------------------------------------

    fun testRefusesRecursiveBody() {
        val file = openFile(
            """
            float Walk(float depth)
            {
                return Walk(depth - 1.0);
            }

            export void M_Entry(inout material m)
            {
                m.Emissive = Walk(4.0);
            }
            """.trimIndent(),
            "M_InlineRec.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Walk(4.0)")
    }

    fun testRefusesReturnInsideNestedBlock() {
        val file = openFile(
            """
            float Pick(float v)
            {
                if (v > 0.5)
                {
                    return v;
                }
                return 0.0;
            }

            export void M_Entry(inout material m)
            {
                m.Emissive = Pick(0.4);
            }
            """.trimIndent(),
            "M_InlineNestedReturn.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Pick(0.4)")
    }

    fun testRefusesBreakOutsideLoop() {
        val file = openFile(
            """
            float Escaped(float v)
            {
                break;
                return v;
            }

            export void M_Entry(inout material m)
            {
                m.Emissive = Escaped(0.4);
            }
            """.trimIndent(),
            "M_InlineBreak.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Escaped(0.4)")
    }

    fun testRefusesBodyWritingValueParameter() {
        val file = openFile(
            """
            float Grown(float t)
            {
                t = t * 2.0;
                return t;
            }

            export void M_Entry(inout material m)
            {
                m.Emissive = Grown(0.5);
            }
            """.trimIndent(),
            "M_InlineWriteParam.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Grown(0.5)")
    }

    fun testRefusesOutArgumentThatIsNotAPlainReference() {
        val file = openFile(
            """
            void Split(float v, out float lo, out float hi)
            {
                lo = v;
                hi = v;
            }

            export void M_Entry(inout material m)
            {
                float a = 0.0;
                Split(1.0, a, 0.5);
                m.BaseColor = float3(a, a, a);
            }
            """.trimIndent(),
            "M_InlineOutLiteral.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Split(1.0, a, 0.5)")
    }

    fun testRefusesByValueArgumentCallingUserFunction() {
        val file = openFile(
            """
            float Noise()
            {
                return 0.5;
            }

            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                m.Emissive = Fade(Noise());
            }
            """.trimIndent(),
            "M_InlineImpure.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Fade(Noise())")
    }

    fun testRefusesAliasedOutArguments() {
        val file = openFile(
            """
            void Split(float v, out float lo, out float hi)
            {
                lo = v;
                hi = v;
            }

            export void M_Entry(inout material m)
            {
                float a = 0.0;
                Split(1.0, a, a);
                m.BaseColor = float3(a, a, a);
            }
            """.trimIndent(),
            "M_InlineAlias.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Split(1.0, a, a)")
    }

    fun testRefusesValueArgumentReadingAnOutTarget() {
        val file = openFile(
            """
            void Split(float v, out float lo, out float hi)
            {
                lo = v;
                hi = v;
            }

            export void M_Entry(inout material m)
            {
                float a = 0.0;
                float b = 0.0;
                Split(a, a, b);
                m.BaseColor = float3(a, b, 1.0);
            }
            """.trimIndent(),
            "M_InlineAliasRead.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Split(a, a, b)")
    }

    fun testRefusesArgumentCountMismatch() {
        val file = openFile(
            """
            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                m.Emissive = Fade(0.5, 1.0);
            }
            """.trimIndent(),
            "M_InlineCount.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Fade(0.5, 1.0)")
    }

    fun testRefusesUnknownNamedArgument() {
        val file = openFile(
            """
            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                m.Emissive = Fade(x = 0.5);
            }
            """.trimIndent(),
            "M_InlineUnknownNamed.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Fade(x = 0.5)")
    }

    fun testRefusesNameCollisionWithCallerLocal() {
        val file = openFile(
            """
            float TintScale(float s)
            {
                float a = s * 2.0;
                return a;
            }

            export void M_Entry(inout material m)
            {
                float a = 1.0;
                m.Emissive = TintScale(0.25);
                m.BaseColor = float3(a, a, a);
            }
            """.trimIndent(),
            "M_InlineCollision.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "TintScale(0.25)")
    }

    fun testRefusesCustomBody() {
        val file = openFile(
            """
            /// @custom
            float3 Body(float3 c)
            {
                return c * 0.5;
            }

            export void M_Entry(inout material m)
            {
                m.BaseColor = Body(float3(1.0, 1.0, 1.0));
            }
            """.trimIndent(),
            "M_InlineCustom.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Body(float3(1.0, 1.0, 1.0))")
    }

    fun testRefusesBuiltinTarget() {
        val file = openFile(
            """
            export void M_Entry(inout material m)
            {
                m.Emissive = saturate(0.5);
            }
            """.trimIndent(),
            "M_InlineBuiltin.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "saturate(0.5)")
    }

    fun testRefusesCallInsideLargerExpression() {
        val file = openFile(
            """
            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                float x = 2.0 * Fade(1.0);
                m.BaseColor = float3(x, x, x);
            }
            """.trimIndent(),
            "M_InlineLarger.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Fade(1.0)")
    }

    fun testRefusesCompoundAssignmentRhs() {
        val file = openFile(
            """
            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                float a = 1.0;
                a += Fade(1.0);
                m.BaseColor = float3(a, a, a);
            }
            """.trimIndent(),
            "M_InlineCompound.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Fade(1.0)")
    }

    fun testRefusesBareReturnValueSite() {
        val file = openFile(
            """
            void Touch(float t)
            {
                return;
            }

            export void M_Entry(inout material m)
            {
                float a = Touch(1.0);
                m.BaseColor = float3(a, a, a);
            }
            """.trimIndent(),
            "M_InlineBareReturn.dss",
        )
        expectRefusal(file, myFixture.editor.document.text, "Touch(1.0)")
    }

    fun testInlinesBareReturnStatementPosition() {
        val file = openFile(
            """
            void Touch(inout float t)
            {
                t = t * 2.0;
                return;
            }

            export void M_Entry(inout material m)
            {
                float a = 1.0;
                Touch(a);
                m.BaseColor = float3(a, a, a);
            }
            """.trimIndent(),
            "M_InlineBareReturnStmt.dss",
        )
        moveTo(file, "Touch(a)")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertTrue("the body runs and the bare return is dropped from the expansion: $after", after.contains("a = a * 2.0;"))
        assertTrue("the call is gone: $after", !after.contains("Touch(a)"))
    }

    fun testLegacyFileStaysSilent() {
        val file = openFile(
            """
            Function void Helper(float x)
            {
                return;
            }
            """.trimIndent(),
            "M_LegacyFn.dsm",
        )
        val before = myFixture.editor.document.text
        moveTo(file, "Function void Helper")

        invokeHandler(file)
        assertEquals("the 1.x dialect keeps the platform default handler", before, myFixture.editor.document.text)
    }

    fun testInlineFunctionActionIsRegistered() {
        val file = openFile(
            """
            float Fade(float t)
            {
                return t * 0.5;
            }

            export void M_Entry(inout material m)
            {
                m.Emissive = Fade(0.5);
            }
            """.trimIndent(),
            "M_InlineAction.dss",
        )
        moveTo(file, "Fade(0.5)")

        myFixture.performEditorAction("DreamShader.InlineFunction")

        val after = myFixture.editor.document.text
        assertTrue(
            "the Refactoring-menu action reaches the 2.0 handler: $after",
            after.contains("m.Emissive = (0.5) * 0.5;"),
        )
    }
}
