package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Paths

/**
 * Milestone M12 C7: the reverse half of the navigation loop - the source span navigator
 * behind the reveal-node "Open at line" affordance, and the revealed-line gutter marker
 * rendering (mark, tooltip content, equality).
 */
class DreamShaderSourceSpanNavigatorTest : BasePlatformTestCase() {
    fun testOpenAtLineNavigatesThroughHook() {
        val vf = writeFixture()
        var captured: Triple<com.intellij.openapi.project.Project, com.intellij.openapi.vfs.VirtualFile, Pair<Int, Int>>? = null
        val previous = DreamShaderBridgeActionTestHooks.locationNavigator
        try {
            DreamShaderBridgeActionTestHooks.locationNavigator = { project, file, line, column ->
                captured = Triple(project, file, line to column)
            }
            val navigated = DreamShaderSourceSpanNavigator.openAtLine(
                project, vf.path, 3, 5,
            )
            assertTrue(navigated)
            assertEquals(vf, captured?.second)
            assertEquals(3 to 5, captured?.third)
        } finally {
            DreamShaderBridgeActionTestHooks.locationNavigator = previous
        }
    }

    fun testOpenAtLineReportsMissingFiles() {
        assertFalse(
            DreamShaderSourceSpanNavigator.openAtLine(
                project, "C:/definitely/not/there.dss", 1, 1,
            )
        )
    }

    fun testRevealMarkerAttachesToRequestedLine() {
        val vf = writeFixture()
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vf)!!
        val editor = com.intellij.openapi.editor.EditorFactory.getInstance().createEditor(document, project)
        try {
            val response = DreamShaderRevealNodeResponseDto(
                ok = true,
                version = 1,
                assetPath = "/Game/Materials/M_Skin",
                instanceAssetPath = "/Game/Materials/M_Skin_Inst",
                expressions = listOf("A1B2C3D4-E5F6-4789-9ABC-DEF012345678"),
            )
            val highlighter = DreamShaderRevealNodeMarker.mark(editor, 3, response)
            assertNotNull("Expected a range highlighter on the revealed line", highlighter)
            assertEquals(
                document.getLineStartOffset(2),
                highlighter!!.startOffset,
            )
            assertEquals(
                document.getLineEndOffset(2),
                highlighter.endOffset,
            )
            val renderer = highlighter.gutterIconRenderer
            assertNotNull(renderer)
            assertTrue(renderer!!.tooltipText.orEmpty().contains("M_Skin"))
            assertTrue(renderer.tooltipText.orEmpty().contains("A1B2C3D4"))
            assertEquals(renderer, DreamShaderRevealNodeGutterMark(
                "/Game/Materials/M_Skin", "/Game/Materials/M_Skin_Inst",
                listOf("A1B2C3D4-E5F6-4789-9ABC-DEF012345678"),
            ))
        } finally {
            com.intellij.openapi.editor.EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    fun testRevealMarkerRejectsOutOfRangeLines() {
        val vf = writeFixture()
        val document = com.intellij.openapi.fileEditor.FileDocumentManager.getInstance().getDocument(vf)!!
        val editor = com.intellij.openapi.editor.EditorFactory.getInstance().createEditor(document, project)
        try {
            val response = DreamShaderRevealNodeResponseDto(ok = true, version = 1)
            assertNull(DreamShaderRevealNodeMarker.mark(editor, 0, response))
            assertNull(DreamShaderRevealNodeMarker.mark(editor, document.lineCount + 1, response))
        } finally {
            com.intellij.openapi.editor.EditorFactory.getInstance().releaseEditor(editor)
        }
    }

    private fun writeFixture(): com.intellij.openapi.vfs.VirtualFile {
        val path = Paths.get(project.basePath!!, "DShader", "Materials", "M_Span.dss")
        Files.createDirectories(path.parent)
        Files.write(path, listOf("uniform float A;", "uniform float B;", "uniform float C;", ""))
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(path.toString().replace('\\', '/'))!!
    }
}
