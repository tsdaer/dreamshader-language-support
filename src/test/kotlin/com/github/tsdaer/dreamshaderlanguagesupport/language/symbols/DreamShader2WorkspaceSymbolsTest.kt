package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexRepository
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.io.path.writeText
import java.nio.file.Files
import java.nio.file.Paths

/**
 * C1 验收：Bridge symbol-index（权威）与 stub index（离线兜底）的合并优先级，
 * 以及 `.dsi` 父参数 schema 的静默降级路径。
 */
class DreamShader2WorkspaceSymbolsTest : BasePlatformTestCase() {

    private lateinit var workspace: DreamShader2WorkspaceSymbols

    override fun setUp() {
        super.setUp()
        workspace = project.getService(DreamShader2WorkspaceSymbols::class.java)
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

    /**
     * A test source lives twice, like the deployment does: a light-VFS copy (so the stub
     * index covers it) and a real-disk copy at the same relative path (so the Bridge
     * artifact lookup anchors on the project root). Returns the real-disk handle for the
     * Bridge-merging calls; stub queries are project-wide and need no file argument.
     */
    private fun addSourceFile(relativePath: String, text: String): VirtualFile {
        myFixture.addFileToProject(relativePath, text)
        val path = Paths.get(project.basePath!!, relativePath)
        Files.createDirectories(path.parent)
        path.writeText(text)
        return LocalFileSystem.getInstance()
            .refreshAndFindFileByPath(path.toString().replace('\\', '/'))
            ?: error("fixture file missing: $path")
    }

    /**
     * Full-suite runs keep the shared project dumb for a while, and [DreamShader2WorkspaceSymbols.query]
     * swallows [com.intellij.openapi.project.IndexNotReadyException] by contract (Bridge-only
     * degradation), so the stub half of a merged answer arrives as *missing data*, not as a
     * thrown exception. Index-backed asserts therefore retry until the stub indexes answer:
     * both the swallowed window (no exception to catch) and the dumb window (the exception
     * the caller would have seen) resolve once indexing completes.
     */
    private fun <T> withStubIndexesReady(action: () -> T): T {
        var last: Throwable? = null
        repeat(60) {
            try {
                return action()
            } catch (e: com.intellij.openapi.project.IndexNotReadyException) {
                last = e
            } catch (e: AssertionError) {
                last = e
            }
            com.intellij.util.ui.UIUtil.dispatchAllInvocationEvents()
            Thread.sleep(100)
        }
        throw last ?: IllegalStateException("stub indexes never became ready")
    }

    private fun writeIndex(sourceRelative: String, json: String) {
        val index = Paths.get(
            project.basePath!!, "Saved", "DreamShader", "Index", "Project", sourceRelative,
        ).toString() + ".index.json"
        Files.createDirectories(Paths.get(index).parent)
        Paths.get(index).writeText(json)
        LocalFileSystem.getInstance().refreshIoFiles(listOf(java.io.File(index)))
    }

    fun testBridgeEntryShadowsSameNameStubEntry() {
        val source = addSourceFile(
            "DShader/Materials/M_P.dss",
            """
            export float3 MF_Both(float x) { return float3(x); }
            export float3 MF_StubOnly(float x) { return float3(x); }
            """.trimIndent(),
        )

        writeIndex(
            "DShader/Materials/M_P.dss",
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "M_P.dss",
              "declarations": [
                { "kind": "function", "name": "MF_Both", "role": "export", "type": "float1" },
                { "kind": "function", "name": "MF_BridgeOnly", "role": "extern", "type": "float4" }
              ],
              "references": [], "includes": [], "parameters": []
            }
            """.trimIndent(),
        )

        withStubIndexesReady {
            val functions = workspace.exportedFunctions(source)
            val both = functions.filter { it.name == "MF_Both" }
            assertEquals(1, both.size, "same-name entries must collapse: $functions")
            assertEquals(DreamShader2WorkspaceSymbols.SymbolSource.BRIDGE, both.single().source)
            assertEquals("float1", both.single().returnType)
            assertTrue(functions.any { it.name == "MF_StubOnly" && it.source == DreamShader2WorkspaceSymbols.SymbolSource.STUB })
            assertTrue(functions.any { it.name == "MF_BridgeOnly" && it.source == DreamShader2WorkspaceSymbols.SymbolSource.BRIDGE })
            assertTrue(functions.any { it.name == "MF_BridgeOnly" && it.isExtern })
            functions
        }
    }

    fun testMissingBridgeDataDegradesToStubIndex() {
        val source = addSourceFile(
            "DShader/Functions/MF_Q.dss",
            "export float MF_Q(float k) { return k; }\n",
        )

        withStubIndexesReady {
            val merged = workspace.exportedFunctions(source)
            assertTrue(merged.any { it.name == "MF_Q" && it.source == DreamShader2WorkspaceSymbols.SymbolSource.STUB })
            assertEquals("float", merged.first { it.name == "MF_Q" }.returnType)
        }
    }

    fun testStructsAndUniformsMergeAcrossSources() {
        val source = addSourceFile(
            "DShader/Materials/M_U.dss",
            """
            struct InputShape { float3 P; };
            uniform float Gain = 1.0;
            """.trimIndent(),
        )
        writeIndex(
            "DShader/Materials/M_U.dss",
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "M_U.dss",
              "declarations": [
                { "kind": "struct", "name": "BridgeShape", "type": "" },
                { "kind": "uniform", "name": "Gain", "type": "float1" }
              ],
              "references": [], "includes": [], "parameters": []
            }
            """.trimIndent(),
        )

        // The sandbox is shared across test classes: assert on the names this test owns.
        withStubIndexesReady {
            val structs = workspace.structs(source)
            assertTrue(structs.any { it.name == "BridgeShape" && it.source == DreamShader2WorkspaceSymbols.SymbolSource.BRIDGE })
            assertTrue(structs.any { it.name == "InputShape" && it.source == DreamShader2WorkspaceSymbols.SymbolSource.STUB })

            val uniforms = workspace.uniforms(source)
            val gains = uniforms.filter { it.name == "Gain" }
            assertTrue(gains.isNotEmpty())
            assertTrue(gains.all { it.source == DreamShader2WorkspaceSymbols.SymbolSource.BRIDGE && it.type == "float1" })
        }
    }

    fun testInstanceSchemaReadsBridgeIndexAndDegradesSilently() {
        val source = addSourceFile(
            "DShader/Materials/MI_A.dsi",
            "#pragma instance(Parent = \"M_Parent\")\nuniform float Gain = 2.0;\n",
        )

        assertNull(workspace.instanceSchema(source), "no Bridge data → no schema (callers degrade silently)")

        writeIndex(
            "DShader/Materials/MI_A.dsi",
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "MI_A.dsi",
              "declarations": [], "references": [], "includes": [], "parameters": [],
              "instance": {
                "parent": "M_Parent",
                "overrides": [],
                "schema": {
                  "valid": true,
                  "origin": "asset",
                  "parameters": [ { "name": "Gain", "kind": "global", "type": "float1" } ]
                }
              }
            }
            """.trimIndent(),
        )

        val schema = workspace.instanceSchema(source)
        assertNotNull(schema)
        assertEquals(1, schema!!.parameters.size)
        assertEquals("Gain", schema.parameters.single().name)

        writeIndex(
            "DShader/Materials/MI_A.dsi",
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "MI_A.dsi",
              "declarations": [], "references": [], "includes": [], "parameters": [],
              "instance": {
                "parent": "M_Parent",
                "overrides": [],
                "schema": { "valid": false, "origin": "asset", "parameters": [] }
              }
            }
            """.trimIndent(),
        )
        assertNull(workspace.instanceSchema(source), "an invalid schema must read as absent")
    }

    fun testIncludeClosureResolvesRelativeImports() {
        addSourceFile(
            "DShader/Shared/Math.dsh",
            "export float2 MF_Lib(float2 v) { return v; }\n",
        )
        val source = addSourceFile(
            "DShader/Materials/M_I.dss",
            "import \"../Shared/Math.dsh\";\nexport float3 MF_Top(float3 c) { return c; }\n",
        )

        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(source)!!
        val closure = workspace.includeClosurePsi(psiFile)
        assertEquals(1, closure.size)
        assertTrue(closure.single().virtualFile!!.path.endsWith("Math.dsh"))
    }
}
