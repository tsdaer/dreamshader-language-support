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
 * C22 验收：2.0 文件的 Inline Uniform——单/多使用处替换为带括号初始化器、声明与 doc 块随删的
 * diff 断言；无初始化器、`.dsi` 覆盖、跨文件使用、被其他初始化器引用时拒绝且文档不变；1.x 静默。
 */
class DreamShader2InlineUniformTest : BasePlatformTestCase() {

    private val body = """
        /// Gain multiplier for the tint curve.
        uniform float Gain = 0.5;

        export void M_Entry(inout material m)
        {
            float3 Tinted = TintScale(Gain);
            m.BaseColor = Tinted * Gain;
        }
    """.trimIndent()

    private fun openFile(text: String = body, name: String = "M_Inline.dss") =
        myFixture.configureByText(name, text)

    private fun moveTo(file: PsiFile, needle: String, occurrence: Int = 0) {
        var start = -1
        repeat(occurrence + 1) { start = file.text.indexOf(needle, start + 1) }
        check(start >= 0) { "needle not found: $needle" }
        myFixture.editor.caretModel.moveToOffset(start)
    }

    private fun invokeHandler(file: PsiFile) {
        DreamShader2InlineUniformHandler().invoke(project, myFixture.editor, file, DataContext.EMPTY_CONTEXT)
    }

    fun testInlinesInitializerIntoUsagesAndRemovesDeclarationWithDocBlock() {
        val file = openFile()
        moveTo(file, "Gain = 0.5")
        invokeHandler(file)

        val after = myFixture.editor.document.text
        assertFalse("declaration removed: $after", after.contains("uniform float Gain"))
        assertFalse("doc block removed with the declaration: $after", after.contains("Gain multiplier"))
        assertTrue("usages replaced parenthesized: $after", after.contains("TintScale((0.5))"))
        assertTrue(after.contains("Tinted * (0.5);"))
        assertFalse("no blank hole left: $after", after.contains("\n\n\n"))
    }

    fun testInlineActionIsRegistered() {
        val file = openFile()
        moveTo(file, "Gain", occurrence = 1)

        myFixture.performEditorAction("DreamShader.InlineUniform")

        val after = myFixture.editor.document.text
        assertFalse("the menu action reaches the handler: $after", after.contains("uniform float Gain"))
        assertTrue(after.contains("TintScale((0.5))"))
    }

    fun testRefusesWithoutInitializer() {
        val file = openFile(
            """
            uniform float Gain;

            export void M_Entry(inout material m)
            {
                m.BaseColor = float3(Gain, Gain, Gain);
            }
            """.trimIndent(),
            "M_InlineNoInit.dss",
        )
        val before = myFixture.editor.document.text
        moveTo(file, "uniform float Gain")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals(before, myFixture.editor.document.text)
    }

    fun testRefusesWhenInstanceOverridesTheUniform() {
        myFixture.addFileToProject(
            "DShader/Instances/M_Entry.dsi",
            """
            #pragma instance(Parent = "DShader/Materials/M_Inline.dss")
            uniform float Gain = 2.0;
            """.trimIndent(),
        )
        val file = openFile()
        val before = myFixture.editor.document.text
        moveTo(file, "Gain = 0.5")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals(before, myFixture.editor.document.text)
    }

    fun testRefusesWhenAnotherInitializerReadsTheUniform() {
        val file = openFile(
            """
            uniform float Gain = 0.5;
            uniform float DoubleGain = Gain * 2.0;

            export void M_Entry(inout material m)
            {
                m.BaseColor = float3(Gain, Gain, Gain);
            }
            """.trimIndent(),
            "M_InlineChain.dss",
        )
        val before = myFixture.editor.document.text
        moveTo(file, "Gain = 0.5")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(file) }
        assertEquals(before, myFixture.editor.document.text)
    }

    fun testRefusesWhenOtherFilesUseTheUniform() {
        val shared = myFixture.addFileToProject(
            "DShader/Materials/Shared.dss",
            """
            uniform float Gain = 0.5;
            """.trimIndent(),
        )
        val importer = myFixture.addFileToProject(
            "DShader/Materials/M_Importer.dss",
            """
            #include "DShader/Materials/Shared.dss"

            export void M_Import()
            {
                m.BaseColor = float3(Gain, Gain, Gain);
            }
            """.trimIndent(),
        )
        myFixture.openFileInEditor(shared.virtualFile!!)
        val before = myFixture.editor.document.text
        moveTo(shared, "Gain = 0.5")

        assertThrows(CommonRefactoringUtil.RefactoringErrorHintException::class.java) { invokeHandler(shared) }
        assertEquals(before, myFixture.editor.document.text)
        assertTrue(myFixture.editor.document.text.contains("uniform float Gain"))
    }

    fun testLegacyFileStaysSilent() {
        val file = openFile(
            """
            Shader("M")
            {
                Graph = { }
            }
            """.trimIndent(),
            "M_LegacyInline.dsm",
        )
        val before = myFixture.editor.document.text
        myFixture.editor.caretModel.moveToOffset(5)

        invokeHandler(file)
        assertEquals("the 1.x dialect has no uniform inline", before, myFixture.editor.document.text)
    }
}
