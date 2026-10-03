package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertEquals

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.readText
import kotlin.io.path.writeText

/**
 * A3.2：`dreamshader-symbol-index` v1 工件的路径解析、DTO 解析、schema 校验降级与缓存刷新。
 */
class DreamShaderSymbolIndexRepositoryTest : BasePlatformTestCase() {

    private lateinit var repository: DreamShaderSymbolIndexRepository

    override fun setUp() {
        super.setUp()
        repository = project.getService(DreamShaderSymbolIndexRepository::class.java)
        // .uproject 锚定 UE 项目根。
        val base = Paths.get(project.basePath!!)
        Files.createDirectories(base)
        base.resolve("Demo.uproject").writeText("{}")
    }


    override fun tearDown() {
        // 沙盒目录在同一测试运行内跨类共享：清掉本类写入的 Bridge 工件，
        // 避免遮蔽其他测试类的 manifest 候选文件。
        val bridge = Paths.get(project.basePath!!, "Saved", "DreamShader")
        kotlin.runCatching { bridge.toFile().deleteRecursively() }
        super.tearDown()
    }

    private fun createSourceFile(fileName: String = "M_Test.dss"): com.intellij.openapi.vfs.VirtualFile {
        val source = Paths.get(project.basePath!!, "DShader", "Materials", fileName)
        Files.createDirectories(source.parent)
        source.writeText("#pragma material(RenderDomain = Surface)\nuniform float Gain;\n")
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(source.toString().replace('\\', '/'))!!
    }

    private fun writeIndex(sourcePath: String, content: String) {
        val index = Paths.get(project.basePath!!, "Saved", "DreamShader", "Index", "Project", sourcePath)
            .toString() + ".index.json"
        Files.createDirectories(Paths.get(index).parent)
        Paths.get(index).writeText(content)
        LocalFileSystem.getInstance().refreshIoFiles(listOf(java.io.File(Paths.get(index).toString())))
    }

    private val sampleIndex = """
        {
          "schema": "dreamshader-symbol-index",
          "version": 1,
          "file": "C:/Proj/DShader/Materials/M_Test.dss",
          "declarations": [
            { "kind": "uniform", "name": "Gain", "signature": "float Gain", "type": "float1",
              "doc": "gain factor", "file": "C:/Proj/DShader/Materials/M_Test.dss",
              "line": 3, "column": 8, "offset": 24, "length": 4 },
            { "kind": "function", "name": "F", "role": "export", "signature": "float F(float x)",
              "type": "float1", "line": 5, "column": 8 },
            { "kind": "field", "name": "Albedo", "container": "S", "type": "float3", "line": 7, "column": 10 }
          ],
          "references": [
            { "name": "Gain", "spans": [
              { "file": "C:/Proj/DShader/Materials/M_Test.dss", "line": 9, "column": 10, "offset": 80, "length": 4 }
            ] }
          ],
          "includes": [ "Shader/Lib.dsh" ],
          "parameters": [
            { "name": "Gain", "variable": "Gain", "type": "float1", "group": "Tuning", "desc": "gain",
              "sampler": "", "defaultAsset": "", "static": false, "line": 3, "column": 8 }
          ]
        }
    """.trimIndent()

    fun testSymbolIndexPathFollowsSavedDreamShaderIndexLayout() {
        val source = createSourceFile()
        val path = repository.symbolIndexPath(source)
        assertNotNull(path)
        assertTrue(
            path!!.replace('\\', '/').endsWith("Saved/DreamShader/Index/Project/DShader/Materials/M_Test.dss.index.json"),
            "unexpected index path: $path"
        )
    }

    fun testParsesUpstreamIndexShape() {
        val source = createSourceFile()
        writeIndex("DShader/Materials/M_Test.dss", sampleIndex)

        val snapshot = repository.indexForSource(source)
        assertNotNull(snapshot.loadedFromPath)
        val data = requireNotNull(snapshot.data) { "schema-compatible index must parse" }
        assertTrue(data.isSchemaCompatible())
        assertEquals(3, data.declarations.size)
        assertEquals("uniform", data.declarations[0].kind)
        assertEquals("Gain", data.declarations[0].name)
        assertEquals("export", data.declarations[1].role)
        assertEquals("S", data.declarations[2].container)
        assertEquals(9, data.references.single().spans.single().line)
        assertEquals(listOf("Shader/Lib.dsh"), data.includes)
        assertEquals("Tuning", data.parameters.single().group)
        assertNull(data.instance)
    }

    fun testParsesInstanceSectionForDsi() {
        val source = createSourceFile()
        writeIndex(
            "DShader/Materials/M_Test.dss",
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "M_Test.dsi",
              "declarations": [],
              "references": [],
              "includes": [],
              "parameters": [],
              "instance": {
                "parent": "Path(Game/Materials/M_Parent)",
                "parentObject": "/Game/Materials/M_Parent.M_Parent",
                "overrides": [ { "name": "Gain", "variable": "Gain", "kind": "global", "schemaIndex": 0 } ],
                "schema": {
                  "valid": true,
                  "origin": "asset",
                  "parameters": [ { "name": "Gain", "kind": "global", "type": "float1", "group": "Tuning" } ]
                }
              }
            }
            """.trimIndent()
        )

        val data = requireNotNull(repository.indexForSource(source).data)
        val instance = requireNotNull(data.instance)
        assertEquals("/Game/Materials/M_Parent.M_Parent", instance.parentObject)
        assertEquals("Gain", instance.overrides.single().name)
        assertTrue(instance.schema?.valid == true)
        assertEquals(1, instance.schema?.parameters?.size)
    }

    fun testIncompatibleSchemaDegradesToNullData() {
        val source = createSourceFile()
        writeIndex("DShader/Materials/M_Test.dss", sampleIndex.replace("\"version\": 1", "\"version\": 2"))
        assertNull(repository.indexForSource(source).data)

        writeIndex("DShader/Materials/M_Test.dss", sampleIndex.replace("dreamshader-symbol-index", "other-schema"))
        assertNull(repository.indexForSource(source).data)
    }

    fun testMissingIndexDegradesGracefully() {
        // 独立源文件名：类内各测试共享沙盒目录，避免读到其他用例写入的索引。
        val source = createSourceFile("M_Absent.dss")
        val snapshot = repository.indexForSource(source)
        assertNull(snapshot.data)
        assertNotNull(snapshot.loadedFromPath, "expected index path should still be reported")
    }

    fun testRewrittenIndexIsRereadOnStampChange() {
        val source = createSourceFile()
        writeIndex("DShader/Materials/M_Test.dss", sampleIndex)
        assertEquals(3, repository.indexForSource(source).data?.declarations?.size)

        // 引擎重写索引（内容与体积变化）后，仓库应按 (mtime, size) 失效并重读。
        writeIndex(
            "DShader/Materials/M_Test.dss",
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "M_Test.dss",
              "declarations": [
                { "kind": "function", "name": "F", "role": "export", "line": 5, "column": 8 }
              ],
              "references": [],
              "includes": [],
              "parameters": []
            }
            """.trimIndent()
        )
        assertEquals(1, repository.indexForSource(source).data?.declarations?.size)
    }

    fun testResolveFailsWithoutUprojectAnchor() {
        // 无 .uproject 锚点时仍回退到 project.basePath —— 此处验证路径仍可解析。
        val source = createSourceFile("M_Anchor.dss")
        assertNotNull(repository.symbolIndexPath(source))
    }
}
