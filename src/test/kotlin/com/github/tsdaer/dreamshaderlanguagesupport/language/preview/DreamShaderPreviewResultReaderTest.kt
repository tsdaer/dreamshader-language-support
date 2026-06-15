package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.writeText

class DreamShaderPreviewResultReaderTest : BasePlatformTestCase() {
    fun testReadPreviewResultMatchesSourceFile() {
        val base = Path.of(project.basePath!!)
        Files.createDirectories(base)
        base.resolve("Demo.uproject").writeText("{}")
        val source = base.resolve("DShader").resolve("Materials").resolve("M_Test.dsm")
        Files.createDirectories(source.parent)
        source.writeText("Shader(Name=\"Materials/M_Test\") { Graph = { } }")
        val bridge = base.resolve("Saved").resolve("DreamShader").resolve("Bridge")
        Files.createDirectories(bridge)
        val image = bridge.resolve("Preview").resolve("test.png")
        Files.createDirectories(image.parent)
        image.writeText("png")
        bridge.resolve("preview.json").writeText(
            """
            {
              "sourceFile": "${source.toString().replace('\\', '/')}",
              "status": "ready",
              "message": "ok",
              "imagePath": "${image.toString().replace('\\', '/')}",
              "assetPath": "/Game/M_Test",
              "updatedAtUtc": "2026-06-15T00:00:00Z"
            }
            """.trimIndent()
        )

        val result = DreamShaderPreviewResultReader().readPreviewResult(project, source.toString())

        assertNotNull(result)
        assertEquals("ready", result?.status)
        assertEquals(image.toString().replace('\\', '/'), result?.imagePath)
    }

    fun testReadPreviewResultIgnoresDifferentSourceFileAndBadJson() {
        val base = Path.of(project.basePath!!)
        Files.createDirectories(base)
        base.resolve("Demo.uproject").writeText("{}")
        val source = base.resolve("DShader").resolve("Materials").resolve("M_Test.dsm")
        Files.createDirectories(source.parent)
        source.writeText("Shader(Name=\"Materials/M_Test\") { Graph = { } }")
        val bridge = base.resolve("Saved").resolve("DreamShader").resolve("Bridge")
        Files.createDirectories(bridge)
        bridge.resolve("preview.json").writeText("""{"sourceFile":"C:/Other/M.dsm","status":"ready"}""")

        assertNull(DreamShaderPreviewResultReader().readPreviewResult(project, source.toString()))

        bridge.resolve("preview.json").writeText("{ bad json")
        assertNull(DreamShaderPreviewResultReader().readPreviewResult(project, source.toString()))
    }
}
