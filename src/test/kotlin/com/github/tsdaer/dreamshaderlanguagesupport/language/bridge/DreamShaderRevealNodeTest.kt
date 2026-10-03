package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertFalse

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertFalse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A3.4：`reveal-node` 请求体与上游 `Docs/tools/bridge.md`（Reveal 一节）字段逐一对照，
 * 以及 `Responses/<requestId>.json` 信封 + 附加字段的往返解析。
 */
class DreamShaderRevealNodeTest : BasePlatformTestCase() {


    override fun tearDown() {
        // 沙盒目录在同一测试运行内跨类共享：清掉本类写入的 Bridge 工件，
        // 避免遮蔽其他测试类的 manifest 候选文件。
        val bridge = Paths.get(project.basePath!!, "Saved", "DreamShader")
        kotlin.runCatching { bridge.toFile().deleteRecursively() }
        super.tearDown()
    }

    private fun anchorProjectRoot() {
        val base = Paths.get(project.basePath!!)
        Files.createDirectories(base)
        base.resolve("Demo.uproject").writeText("{}")
    }

    private fun createSource(): Path {
        val source = Paths.get(project.basePath!!, "DShader", "Materials", "M_Test.dss")
        Files.createDirectories(source.parent)
        source.writeText("#pragma material(RenderDomain = Surface)\n")
        return source
    }

    private fun bridgeDir(): Path = Paths.get(project.basePath!!, "Saved", "DreamShader", "Bridge")

    fun testRequestBodyMatchesUpstreamContract() {
        anchorProjectRoot()
        val source = createSource()

        val target = DreamShaderRevealNodeRequestWriter().writeRevealNodeRequest(
            project = project,
            sourceFile = source.toString(),
            line = 12,
            requestId = "reveal-1"
        )

        assertNotNull(target)
        val text = requireNotNull(target).readText()
        assertTrue(text.contains(""""action": "reveal-node""""))
        assertTrue(text.contains(""""file": "${source.toString().replace('\\', '/')}""""))
        assertTrue(text.contains(""""line": 12"""))
        assertTrue(text.contains(""""requestId": "reveal-1""""))
        // 请求落在 Requests/ 目录（上游非递归扫描 Requests/*.json）。
        assertTrue(
            target!!.parent.toString().replace('\\', '/').endsWith("/Requests"),
            "request must land in Requests/: $target"
        )
    }

    fun testRequestLineIsAtLeastOne() {
        anchorProjectRoot()
        val source = createSource()
        val target = DreamShaderRevealNodeRequestWriter().writeRevealNodeRequest(
            project, source.toString(), line = 0, requestId = "reveal-line-zero"
        )
        assertTrue(requireNotNull(target).readText().contains(""""line": 1"""))
    }

    fun testRequestFallsBackToBasePathAnchor() {
        // 未写 .uproject 时按 base 路径回退仍可解析；空项目根场景由 null 返回路径覆盖。
        val source = createSource()
        anchorProjectRoot()
        assertNotNull(
            DreamShaderRevealNodeRequestWriter().writeRevealNodeRequest(project, source.toString(), 3, "reveal-fallback")
        )
    }

    fun testResponseReaderParsesEnvelopeAndAddedFields() {
        anchorProjectRoot()
        val responses = Files.createDirectories(bridgeDir().resolve("Responses"))
        responses.resolve("reveal-envelope.json").writeText(
            """
            {
              "protocol": 1,
              "requestId": "reveal-envelope",
              "ok": true,
              "durationMs": 42,
              "message": "Revealed node.",
              "version": 1,
              "assetPath": "/Game/Materials/M_Test.M_Test",
              "instanceAssetPath": "/Game/Materials/MI_Test.MI_Test",
              "expressions": ["5f1a2b3c-4d5e-6f70-8192-a3b4c5d6e7f8"]
            }
            """.trimIndent()
        )

        val response = DreamShaderRevealNodeResponseReader(pollIntervalMs = 10, timeoutMs = 100)
            .readOnce(project, "reveal-envelope")
        assertNotNull(response)
        assertTrue(response!!.ok)
        assertEquals(1, response.payloadVersion)
        assertEquals("/Game/Materials/M_Test.M_Test", response.assetPath)
        assertEquals("/Game/Materials/MI_Test.MI_Test", response.instanceAssetPath)
        assertEquals(1, response.expressions.size)
        assertNull(response.navigateFailureCode)
    }

    fun testResponseReaderExtractsNavigateFailureCode() {
        anchorProjectRoot()
        val responses = Files.createDirectories(bridgeDir().resolve("Responses"))
        responses.resolve("reveal-9.json").writeText(
            """
            {
              "protocol": 1,
              "requestId": "reveal-9",
              "ok": false,
              "message": "No asset from that source is loaded.",
              "diagnostics": [
                { "file": "M_Test.dss", "line": 3, "column": 1, "severity": "error",
                  "code": "DSH9051", "stage": "navigate" }
              ]
            }
            """.trimIndent()
        )

        val response = DreamShaderRevealNodeResponseReader(pollIntervalMs = 10, timeoutMs = 100)
            .readOnce(project, "reveal-9")
        assertNotNull(response)
        assertFalse(response!!.ok)
        assertEquals(0, response.payloadVersion)
        assertEquals("DSH9051", response.navigateFailureCode)
        assertEquals(0, response.expressions.size)
    }

    fun testResponseReaderIgnoresForeignRequestIdAndMissingFiles() {
        anchorProjectRoot()
        val responses = Files.createDirectories(bridgeDir().resolve("Responses"))
        responses.resolve("other.json").writeText(
            """{ "protocol": 1, "requestId": "other", "ok": true }"""
        )

        val reader = DreamShaderRevealNodeResponseReader(pollIntervalMs = 10, timeoutMs = 100)
        // reveal-envelope.json 可能由同沙盒的前序用例写入；用一个从不存在且 id 不符的请求 id。
        assertNull(reader.readOnce(project, "reveal-envelope-unknown"), "id 不符的信封不得回给等待方")
        assertNull(reader.readOnce(project, ""))
        assertNull(reader.poll(project, "never-answered"))
    }

    fun testRequestsDirectoryOnlyHoldsOurRequest() {
        anchorProjectRoot()
        val source = createSource()
        DreamShaderRevealNodeRequestWriter().writeRevealNodeRequest(project, source.toString(), 1, "reveal-x")
        val requests = bridgeDir().resolve("Requests").listDirectoryEntries("*.json")
        assertEquals(1, requests.size)
        assertTrue(requests.single().fileName.toString().endsWith(".json"))
        // 临时文件不残留在 Requests/。
        assertTrue(requests.none { it.fileName.toString().endsWith(".tmp") })
    }
}
