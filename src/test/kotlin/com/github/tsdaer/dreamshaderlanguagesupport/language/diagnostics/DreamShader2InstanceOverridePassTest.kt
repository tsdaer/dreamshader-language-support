package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.io.path.writeText
import java.nio.file.Files
import java.nio.file.Paths

/**
 * A6.2 验收：`.dsi` 覆盖检查。schema 有效时按上游码报错（DSH7258/7264/7259/7268），
 * Bridge 数据缺席或 schema 无效时必须零诊断（静默降级，绝不误报）。
 */
class DreamShader2InstanceOverridePassTest : BasePlatformTestCase() {

    override fun setUp() {
        super.setUp()
        val base = Paths.get(project.basePath!!)
        Files.createDirectories(base)
        base.resolve("Demo.uproject").writeText("{}")
    }

    override fun tearDown() {
        kotlin.runCatching {
            Paths.get(project.basePath!!, "Saved").toFile().deleteRecursively()
        }
        super.tearDown()
    }

    /** The pass resolves Bridge artifacts from the file's real path, so write to disk. */
    private fun addProjectFile(relativePath: String, text: String): VirtualFile {
        val path = Paths.get(project.basePath!!, relativePath)
        Files.createDirectories(path.parent)
        path.writeText(text)
        return LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(path.toString().replace('\\', '/'))
            ?: error("fixture file missing: $path")
    }

    private fun writeIndex(sourceRelative: String, json: String) {
        val index = Paths.get(
            project.basePath!!, "Saved", "DreamShader", "Index", "Project", sourceRelative,
        ).toString() + ".index.json"
        Files.createDirectories(Paths.get(index).parent)
        Paths.get(index).writeText(json)
        LocalFileSystem.getInstance().refreshIoFiles(listOf(java.io.File(index)))
    }

    private fun descriptionsFor(file: VirtualFile): List<String> {
        myFixture.openFileInEditor(file)
        return myFixture.doHighlighting().mapNotNull(HighlightInfo::getDescription)
    }

    private fun schemaJson(parameters: String, parent: String = "M_Parent"): String = """
        {
          "schema": "dreamshader-symbol-index",
          "version": 1,
          "file": "MI_X.dsi",
          "declarations": [], "references": [], "includes": [], "parameters": [],
          "instance": {
            "parent": "$parent",
            "overrides": [],
            "schema": { "valid": true, "origin": "asset", "parameters": $parameters }
          }
        }
    """.trimIndent()

    fun testTypeMismatchReportsDsh7259() {
        val file = addProjectFile(
            "DShader/Materials/MI_7259.dsi",
            """
            #pragma instance(Parent = "M_Skin")
            uniform float Gain = 2.0;
            uniform float3 Tint = float3(1, 0, 0);
            uniform float2 Wrong = float2(1, 0);
            """.trimIndent(),
        )
        writeIndex(
            "DShader/Materials/MI_7259.dsi",
            schemaJson(
                """
                [
                  { "name": "Gain", "kind": "global", "type": "float1" },
                  { "name": "Tint", "kind": "global", "type": "float4" }
                ]
                """.trimIndent(),
            ),
        )

        val descriptions = descriptionsFor(file)
        // Gain: schema float1 ≡ float —— 合法；Tint: float3 覆盖 float4 参数（保留 alpha）—— 合法；
        // Wrong: 父材质无此参数，属 DSH7258。
        assertEquals(1, descriptions.count { it.contains("DSH7258") }, "got: $descriptions")
        assertTrue(descriptions.none { it.contains("DSH7259") }, "float/float1 and float3-over-float4 must pass, got: $descriptions")
    }

    fun testTrueTypeMismatchIsReported() {
        val file = addProjectFile(
            "DShader/Materials/MI_Type.dsi",
            """
            #pragma instance(Parent = "M_Skin")
            uniform float3 Tint = float3(1, 0, 0);
            """.trimIndent(),
        )
        writeIndex(
            "DShader/Materials/MI_Type.dsi",
            schemaJson("""[ { "name": "Tint", "kind": "global", "type": "float2" } ]"""),
        )

        val descriptions = descriptionsFor(file)
        assertEquals(1, descriptions.count { it.contains("DSH7259") }, "float3 over a float2 parameter reports: $descriptions")
    }

    fun testCaseOnlyMatchReportsDsh7264() {
        val file = addProjectFile(
            "DShader/Materials/MI_Case.dsi",
            """
            #pragma instance(Parent = "M_Skin")
            uniform float gain = 2.0;
            """.trimIndent(),
        )
        writeIndex(
            "DShader/Materials/MI_Case.dsi",
            schemaJson("""[ { "name": "Gain", "kind": "global", "type": "float1" } ]"""),
        )

        val descriptions = descriptionsFor(file)
        assertEquals(1, descriptions.count { it.contains("DSH7264") }, "got: $descriptions")
        assertTrue(descriptions.none { it.contains("DSH7258") })
    }

    fun testStaticSwitchOverrideNeedsStaticDoc() {
        val file = addProjectFile(
            "DShader/Materials/MI_Static.dsi",
            """
            #pragma instance(Parent = "M_Skin")
            uniform bool UseDetail = true;
            """.trimIndent(),
        )
        writeIndex(
            "DShader/Materials/MI_Static.dsi",
            schemaJson("""[ { "name": "UseDetail", "kind": "staticswitch", "type": "bool" } ]"""),
        )

        val descriptions = descriptionsFor(file)
        assertEquals(1, descriptions.count { it.contains("DSH7259") }, "bool without @static over a static switch reports: $descriptions")
    }

    fun testLayerOnlyParameterReportsDsh7268() {
        addProjectFile(
            "DShader/Materials/M_Skin.dss",
            "export void M_Skin(inout material m) { }\n",
        )
        writeIndex(
            "DShader/Materials/M_Skin.dss",
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "M_Skin.dss",
              "declarations": [
                { "kind": "uniform", "name": "LayerTint", "container": "L_Fur", "type": "float3" }
              ],
              "references": [], "includes": [], "parameters": []
            }
            """.trimIndent(),
        )
        val file = addProjectFile(
            "DShader/Materials/MI_Layer.dsi",
            """
            #pragma instance(Parent = "M_Skin")
            uniform float3 LayerTint = float3(1, 1, 1);
            """.trimIndent(),
        )
        writeIndex(
            "DShader/Materials/MI_Layer.dsi",
            schemaJson("""[ { "name": "Other", "kind": "global", "type": "float1" } ]"""),
        )

        val descriptions = descriptionsFor(file)
        assertEquals(1, descriptions.count { it.contains("DSH7268") }, "got: $descriptions")
        assertTrue(descriptions.none { it.contains("DSH7258") }, "the layer-only name must not double-report, got: $descriptions")
    }

    fun testMissingSchemaDegradesToSilence() {
        val file = addProjectFile(
            "DShader/Materials/MI_None.dsi",
            """
            #pragma instance(Parent = "M_Skin")
            uniform float Whatever = 2.0;
            """.trimIndent(),
        )

        val descriptions = descriptionsFor(file)
        assertTrue(
            descriptions.none { it.contains("DSH72") },
            "no Bridge schema → no override diagnostics at all, got: $descriptions",
        )
    }

    fun testInvalidSchemaDegradesToSilence() {
        val file = addProjectFile(
            "DShader/Materials/MI_Bad.dsi",
            """
            #pragma instance(Parent = "M_Skin")
            uniform float Whatever = 2.0;
            """.trimIndent(),
        )
        writeIndex(
            "DShader/Materials/MI_Bad.dsi",
            schemaJson("[]").replace("\"valid\": true", "\"valid\": false"),
        )

        val descriptions = descriptionsFor(file)
        assertTrue(descriptions.none { it.contains("DSH72") }, "got: $descriptions")
    }
}
