package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertEquals

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

/**
 * A3.1 合并优先级回归：`dreamshader-builtin-catalog.json`（2.0 绑定器目录）高于
 * `material-expressions.json`（1.x Bridge 产物），但低于用户显式配置的 manifest；
 * B2 内嵌兜底 = V2 目录 + 旧 6 条 legacy manifest 合并。
 */
class DreamShaderBuiltinCatalogMergeTest : BasePlatformTestCase() {


    override fun tearDown() {
        // 沙盒目录在同一测试运行内跨类共享：清掉本类写入的 Bridge 工件，
        // 避免遮蔽其他测试类的 manifest 候选文件。
        val bridge = Paths.get(project.basePath!!, "Saved", "DreamShader", "Bridge")
        kotlin.runCatching { bridge.toFile().deleteRecursively() }
        super.tearDown()
    }

    private fun writeBridgeFile(name: String, content: String) {
        val projectBase = project.basePath ?: error("project base path is null")
        val path = Paths.get(projectBase, "Saved", "DreamShader", "Bridge", name)
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(path.parent.toString())
            val file = parent.findOrCreateChildData(this, path.fileName.toString())
            VfsUtil.saveText(file, content)
        }
    }

    fun testBridgeBuiltinCatalogWinsOverBridgeManifest() {
        writeBridgeFile(
            "dreamshader-builtin-catalog.json",
            """
            {
              "schema": "dreamshader-builtin-catalog",
              "version": 1,
              "expressions": [
                { "namespace": "UE", "shortName": "Panner", "className": "Panner",
                  "inputs": [ { "name": "Coordinate" } ],
                  "outputs": [ { "type": "Float2" } ] }
              ]
            }
            """.trimIndent()
        )
        writeBridgeFile(
            "material-expressions.json",
            """{ "expressions": [ { "namespace": "UE", "className": "Panner" } ] }"""
        )

        val settings = project.getService(DreamShaderProjectSettings::class.java)
        val entries = DreamShaderMaterialExpressionManifest.catalogEntries(
            project,
            settings.state.materialExpressionManifestPath
        )

        val panner = entries.firstOrNull { it.ueName == "Panner" }
        assertNotNull(panner)
        assertEquals(DreamShaderMaterialExpressionSource.BRIDGE_BUILTIN_CATALOG, panner!!.source)
        assertEquals(1, panner.parameters.size, "builtin-catalog 引脚参数应来自 2.0 目录而非 1.x manifest")
    }

    fun testConfiguredManifestStillBeatsBridgeBuiltinCatalog() {
        writeBridgeFile(
            "dreamshader-builtin-catalog.json",
            """
            {
              "schema": "dreamshader-builtin-catalog",
              "version": 1,
              "expressions": [
                { "namespace": "UE", "shortName": "Panner", "className": "Panner" }
              ]
            }
            """.trimIndent()
        )
        val projectBase = project.basePath ?: error("project base path is null")
        val configured = Paths.get(projectBase, "configured-manifest.json")
        WriteCommandAction.runWriteCommandAction(project) {
            val file = VfsUtil.createDirectories(configured.parent.toString())
                .findOrCreateChildData(this, configured.fileName.toString())
            VfsUtil.saveText(file, """{ "expressions": [ { "namespace": "UE", "className": "Panner", "signature": "UE.Panner(Configured)" } ] }""")
        }

        val entries = DreamShaderMaterialExpressionManifest.catalogEntries(project, configured.toString())
        val panner = entries.firstOrNull { it.ueName == "Panner" }
        assertNotNull(panner)
        assertEquals(DreamShaderMaterialExpressionSource.CONFIGURED_MANIFEST, panner!!.source)
    }

    fun testBundledFallbackMergesV2CatalogWithLegacyManifest() {
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        val entries = DreamShaderMaterialExpressionManifest.catalogEntries(
            project,
            settings.state.materialExpressionManifestPath
        )
        val ueEntries = entries.filter { it.namespace == "UE" }

        val bundledUe = ueEntries.filter { it.source == DreamShaderMaterialExpressionSource.BUNDLED_FALLBACK }
        assertTrue(
            bundledUe.size >= 27,
            "B2: offline install must still complete >= 27 registered UE calls, got ${bundledUe.size}"
        )
        // V2 注册调用与 legacy 泛型路径调用都要在离线兜底里。
        assertNotNull(bundledUe.firstOrNull { it.ueName == "TexCoord" })
        assertNotNull(bundledUe.firstOrNull { it.ueName == "Sine" })
    }
}
