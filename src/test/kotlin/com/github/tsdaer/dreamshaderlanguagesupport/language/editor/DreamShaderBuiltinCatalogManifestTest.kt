package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DreamShaderBuiltinCatalogManifestTest {

    @Test
    fun `parses upstream catalog entries with inputs properties and positional order`() {
        val json = """
            {
              "schema": "dreamshader-builtin-catalog",
              "version": 1,
              "engine": "5.6.0",
              "expressions": [
                {
                  "namespace": "UE",
                  "shortName": "Panner",
                  "className": "Panner",
                  "classPathName": "/Script/Engine.MaterialExpressionPanner",
                  "inputs": [
                    { "name": "Coordinate", "type": "Numeric" },
                    { "name": "Time", "type": "Numeric", "aliases": ["T"] },
                    { "name": "Speed", "type": "Numeric" }
                  ],
                  "outputs": [ { "name": "", "type": "Float2" } ],
                  "properties": [
                    { "name": "SpeedX", "type": "Float1" },
                    { "name": "FractionalPart", "type": "Bool" }
                  ],
                  "positional": [ "SpeedX", "FractionalPart", "Coordinate" ]
                }
              ]
            }
        """.trimIndent()

        val entries = DreamShaderBuiltinCatalogManifest.parseCatalogEntries(
            json,
            DreamShaderMaterialExpressionSource.BRIDGE_BUILTIN_CATALOG
        )

        assertEquals(1, entries.size)
        val panner = entries.single()
        assertEquals("UE", panner.namespace)
        assertEquals("Panner", panner.className)
        assertEquals("Panner", panner.ueName)
        assertEquals("float2", panner.outputType)
        // 位置参数在 catalog `positional[]` 中出现的名字排最前，其余保持 inputs→properties 顺序。
        val parameterNames = panner.parameters.map { it.name }
        assertEquals(listOf("SpeedX", "FractionalPart", "Coordinate", "Time", "Speed"), parameterNames)
        // 图引脚无 qualifier，字面量属性标记为 property。
        assertNull(panner.parameters.first { it.name == "Coordinate" }.qualifier)
        assertEquals("property", panner.parameters.first { it.name == "SpeedX" }.qualifier)
        assertTrue(panner.parameters.first { it.name == "Coordinate" }.required.not())
    }

    @Test
    fun `normalizes numeric output types and keeps named types`() {
        val json = """
            {
              "schema": "dreamshader-builtin-catalog",
              "version": 1,
              "expressions": [
                { "namespace": "Substrate", "shortName": "Slab", "className": "SubstrateSlabBSDF",
                  "outputs": [ { "type": "Substrate" } ] },
                { "namespace": "UE", "shortName": "VertexColor", "className": "VertexColor",
                  "outputs": [ { "type": "Float4" } ] }
              ]
            }
        """.trimIndent()

        val entries = DreamShaderBuiltinCatalogManifest.parseCatalogEntries(
            json,
            DreamShaderMaterialExpressionSource.BRIDGE_BUILTIN_CATALOG
        )
        assertEquals("Substrate", entries.first { it.ueName == "Slab" }.outputType)
        assertEquals("float4", entries.first { it.ueName == "VertexColor" }.outputType)
    }

    @Test
    fun `keeps aliases and skips abstract entries`() {
        val json = """
            {
              "schema": "dreamshader-builtin-catalog",
              "version": 1,
              "expressions": [
                { "namespace": "UE", "shortName": "CollectionParam", "className": "CollectionParameter",
                  "aliases": [ "CollectionParameter" ],
                  "properties": [ { "name": "Collection", "type": "String" } ] },
                { "namespace": "UE", "shortName": "AbstractBase", "className": "AbstractBase",
                  "isAbstract": true }
              ]
            }
        """.trimIndent()

        val entries = DreamShaderBuiltinCatalogManifest.parseCatalogEntries(
            json,
            DreamShaderMaterialExpressionSource.BRIDGE_BUILTIN_CATALOG
        )
        assertEquals(1, entries.size)
        val entry = entries.single()
        assertEquals("CollectionParam", entry.ueName)
        assertEquals(listOf("CollectionParameter"), entry.aliases)
        assertTrue(entry.signature.orEmpty().startsWith("UE.CollectionParam("))
    }

    @Test
    fun `rejects incompatible schema and version by degrading to empty`() {
        val wrongSchema = """
            { "schema": "dreamshader-builtin-catalog-v2", "version": 1, "expressions": [] }
        """.trimIndent()
        val wrongVersion = """
            { "schema": "dreamshader-builtin-catalog", "version": 2, "expressions": [] }
        """.trimIndent()

        assertTrue(
            DreamShaderBuiltinCatalogManifest.parseCatalogEntries(
                wrongSchema,
                DreamShaderMaterialExpressionSource.BRIDGE_BUILTIN_CATALOG
            ).isEmpty()
        )
        assertTrue(
            DreamShaderBuiltinCatalogManifest.parseCatalogEntries(
                wrongVersion,
                DreamShaderMaterialExpressionSource.BRIDGE_BUILTIN_CATALOG
            ).isEmpty()
        )
        assertTrue(
            DreamShaderBuiltinCatalogManifest.parseCatalogEntries(
                "not json at all",
                DreamShaderMaterialExpressionSource.BRIDGE_BUILTIN_CATALOG
            ).isEmpty()
        )
    }

    @Test
    fun `bundled fallback exposes at least 27 registered UE calls`() {
        val entries = DreamShaderBuiltinCatalogManifest.parseBundledFallbackEntries()
        val ueEntries = entries.filter { it.namespace == "UE" }

        assertTrue(ueEntries.size >= 27, "expected >= 27 UE.* fallback entries, got ${ueEntries.size}")
        // 注册表中的代表条目。
        assertNotNull(ueEntries.firstOrNull { it.ueName == "TexCoord" })
        assertNotNull(ueEntries.firstOrNull { it.ueName == "TransformVector" })
        // 特殊处理条目。
        assertNotNull(ueEntries.firstOrNull { it.ueName == "StaticSwitchParameter" })
        assertNotNull(ueEntries.firstOrNull { it.ueName == "SceneTexture" })
        assertNotNull(ueEntries.firstOrNull { it.ueName == "Expression" })
    }

    @Test
    fun `bundled fallback TexCoord carries documented literal arguments`() {
        val texCoord = DreamShaderBuiltinCatalogManifest.parseBundledFallbackEntries()
            .first { it.ueName == "TexCoord" }
        assertEquals("TextureCoordinate", texCoord.className)
        assertEquals("float2", texCoord.outputType)
        assertEquals(
            listOf("Index", "UTiling", "VTiling", "UnMirrorU", "UnMirrorV"),
            texCoord.parameters.map { it.name }
        )
        assertEquals("property", texCoord.parameters.first().qualifier)
    }
}
