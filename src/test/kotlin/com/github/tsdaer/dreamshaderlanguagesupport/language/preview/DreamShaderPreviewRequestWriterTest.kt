package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.io.path.writeText

class DreamShaderPreviewRequestWriterTest : BasePlatformTestCase() {
    fun testWritePreviewMaterialRequestCreatesRequestJson() {
        val base = Path.of(project.basePath!!)
        Files.createDirectories(base)
        base.resolve("Demo.uproject").writeText("{}")
        val source = base.resolve("DShader").resolve("Materials").resolve("M_Test.dsm")
        Files.createDirectories(source.parent)
        source.writeText("Shader(Name=\"Materials/M_Test\") { Graph = { } }")

        val target = DreamShaderPreviewRequestWriter().writePreviewMaterialRequest(
            project = project,
            sourceFile = source.toString(),
            mesh = "cube",
            requestId = "request-1"
        )

        assertNotNull(target)
        val request = requireNotNull(target)
        assertTrue(request.isRegularFile())
        val text = request.readText()
        assertTrue(text.contains(""""action": "previewMaterial""""))
        assertTrue(text.contains(""""sourceFile": "${source.toString().replace('\\', '/')}""""))
        assertTrue(text.contains(""""mesh": "cube""""))
        assertTrue(text.contains(""""requestId": "request-1""""))
    }
}
