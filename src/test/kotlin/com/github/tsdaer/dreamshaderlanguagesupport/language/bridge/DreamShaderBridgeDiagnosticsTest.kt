package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderBridgeDiagnosticsTest : BasePlatformTestCase() {
    fun testBridgeDiagnosticNavigationToExactLocation() {
        val projectBase = project.basePath ?: error("project base path is null")
        val sourcePath = Paths.get(projectBase, "DShader", "Materials", "Main.dsm")
        var sourceFile = VfsUtil.findFile(sourcePath, false)
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(sourcePath.parent.toString())
            val file = parent.findOrCreateChildData(this, sourcePath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Shader Main {
                    Graph {
                        float x = 1.0;
                    }
                }
                """.trimIndent()
            )
            sourceFile = file
        }
        val createdSourceFile = sourceFile ?: error("source file not created")

        val diagnosticsPath = Paths.get(projectBase, "Saved", "DreamShader", "Bridge", "diagnostics.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(diagnosticsPath.parent.toString())
            val file = parent.findOrCreateChildData(this, diagnosticsPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                [
                  {
                    "sourcePath": "${createdSourceFile.path.replace("\\", "/")}",
                    "line": 3,
                    "column": 15,
                    "severity": "error",
                    "message": "Bridge test error"
                  }
                ]
                """.trimIndent()
            )
        }

        val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)
        val snapshot = repository.refresh(createdSourceFile)
        assertEquals(1, snapshot.diagnostics.size)
        val diagnostic = snapshot.diagnostics.first()
        assertEquals(3, diagnostic.line)
        assertEquals(15, diagnostic.column)
        assertEquals("Bridge test error", diagnostic.message)
    }

    fun testRefreshBridgeDiagnosticsSyncsPanelAndEditor() {
        val projectBase = project.basePath ?: error("project base path is null")
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.projectRoot = projectBase

        myFixture.configureByText(
            "Refresh.dsm",
            """
            Shader Refresh {
                Graph {
                    float x = 1.0;
                }
            }
            """.trimIndent()
        )
        val activeFile = myFixture.file.virtualFile ?: error("active source file not created")

        val diagnosticsPath = Paths.get(projectBase, "Saved", "DreamShader", "Bridge", "diagnostics.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(diagnosticsPath.parent.toString())
            val file = parent.findOrCreateChildData(this, diagnosticsPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                {
                  "diagnostics": [
                    {
                      "sourcePath": "${activeFile.path.replace("\\", "/")}",
                      "line": 3,
                      "column": 12,
                      "severity": "warning",
                      "message": "Bridge warning one"
                    }
                  ]
                }
                """.trimIndent()
            )
        }

        val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)
        repository.refresh(activeFile)
        val highlights = myFixture.doHighlighting(HighlightSeverity.WARNING)
        assertTrue(
            "Expected warning 'Bridge warning one', actual: ${highlights.map { it.description }}",
            highlights.any { it.description == "Bridge warning one" }
        )
        val mapped = repository.diagnosticsForFile(activeFile)
        assertEquals(1, mapped.size)
        assertEquals("Bridge warning one", mapped.first().message)
    }

    fun testParsesGroupedByFileStructure() {
        val repository = project.getService(DreamShaderBridgeDiagnosticsRepository::class.java)
        val json = """
            {
              "version": 1,
              "files": [
                {
                  "path": "J:/honkai_rts_5_6/DShader/Materials/M_Sample.dsm",
                  "diagnostics": [
                    {
                      "message": "Unknown Graph identifier 'Tint2'.",
                      "stage": "generate",
                      "code": "generate-error",
                      "line": 18,
                      "column": 9,
                      "severity": "error",
                      "source": "DreamShader Generate"
                    }
                  ]
                }
              ]
            }
        """.trimIndent()

        val parsed = repository.parseDiagnosticsJson(json)
        assertEquals(1, parsed.size)
        val diagnostic = parsed.first()
        assertEquals("J:/honkai_rts_5_6/DShader/Materials/M_Sample.dsm", diagnostic.sourcePath)
        assertEquals(18, diagnostic.line)
        assertEquals(9, diagnostic.column)
        assertEquals("error", diagnostic.severity)
        assertEquals("Unknown Graph identifier 'Tint2'.", diagnostic.message)
    }
}
