package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionSource
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamShaderMaterialExpressionManifestTest {
    @Test
    fun `parses manifest class fields`() {
        val objectShape = """
            {
              "classes": [
                { "className": "Sine" },
                { "name": "Multiply" }
              ]
            }
        """.trimIndent()
        val objectNames = DreamShaderMaterialExpressionManifest.parseExpressionClassNames(objectShape)

        assertTrue(objectNames.contains("Sine"))
        assertTrue(objectNames.contains("Multiply"))
    }

    @Test
    fun `parses rich expression catalog entries`() {
        val richShape = """
            {
              "expressions": [
                {
                  "namespace": "UE",
                  "className": "UMaterialExpressionDreamOnly",
                  "ueName": "DreamOnly",
                  "signature": "UE.DreamOnly(Input=Value)",
                  "outputType": "float1",
                  "description": "A catalog-only node.",
                  "parameters": [
                    { "name": "Input", "type": "float1", "required": true }
                  ]
                }
              ]
            }
        """.trimIndent()

        val entries = DreamShaderMaterialExpressionManifest.parseCatalogEntries(
            richShape,
            DreamShaderMaterialExpressionSource.CONFIGURED_MANIFEST
        )
        val entry = entries.single()

        assertEquals("UE", entry.namespace)
        assertEquals("UMaterialExpressionDreamOnly", entry.className)
        assertEquals("DreamOnly", entry.ueName)
        assertEquals("UE.DreamOnly(Input=Value)", entry.signature)
        assertEquals("float1", entry.outputType)
        assertEquals("Input", entry.parameters.single().name)
        assertTrue(entry.parameters.single().required)
    }

    @Test
    fun `derives ue name and preserves Substrate output type`() {
        val richShape = """
            {
              "expressions": [
                {
                  "namespace": "Substrate",
                  "className": "UMaterialExpressionSubstrateSlabBSDF",
                  "outputType": "Substrate"
                }
              ]
            }
        """.trimIndent()

        val entry = DreamShaderMaterialExpressionManifest.parseCatalogEntries(richShape).single()
        val classNames = DreamShaderMaterialExpressionManifest.expressionClassNames(listOf(entry))

        assertEquals("Substrate", entry.namespace)
        assertEquals("SubstrateSlabBSDF", entry.ueName)
        assertEquals("Substrate", entry.outputType)
        assertTrue(classNames.contains("UMaterialExpressionSubstrateSlabBSDF"))
        assertTrue(classNames.contains("MaterialExpressionSubstrateSlabBSDF"))
        assertTrue(classNames.contains("SubstrateSlabBSDF"))
    }

    @Test
    fun `parses substrate builtins into Substrate namespace entries`() {
        val json = """
            {
              "schema": "DreamShader.SubstrateBuiltins",
              "version": 1,
              "supported": true,
              "builtins": [
                {
                  "name": "Slab",
                  "qualifiedName": "Substrate.Slab",
                  "className": "MaterialExpressionSubstrateSlabBSDF",
                  "outputType": "Substrate",
                  "detail": "Creates a Substrate slab BSDF.",
                  "example": "Substrate.Slab(DiffuseAlbedo=Color, Roughness=0.45)",
                  "parameters": [
                    { "name": "DiffuseAlbedo", "type": "value" },
                    { "name": "Roughness", "type": "value" }
                  ]
                },
                {
                  "name": "TransmittanceToMFP",
                  "outputType": "auto",
                  "parameters": []
                }
              ]
            }
        """.trimIndent()

        val entries = DreamShaderMaterialExpressionManifest.parseSubstrateBuiltins(json)
        assertEquals(2, entries.size)

        val slab = entries.first { it.ueName == "Slab" }
        assertEquals("Substrate", slab.namespace)
        assertEquals("MaterialExpressionSubstrateSlabBSDF", slab.className)
        assertEquals("Substrate", slab.outputType)
        assertEquals("Substrate.Slab(DiffuseAlbedo=Color, Roughness=0.45)", slab.signature)
        assertEquals("Creates a Substrate slab BSDF.", slab.description)
        assertEquals(listOf("DiffuseAlbedo", "Roughness"), slab.parameters.map { it.name })
        assertEquals("Substrate.Slab", slab.qualifiedName)

        val helper = entries.first { it.ueName == "TransmittanceToMFP" }
        assertEquals("Substrate", helper.namespace)
        assertEquals("MaterialExpressionSubstrateTransmittanceToMFP", helper.className)
    }

    @Test
    fun `builds parameters and signature from bridge inputs and outputs`() {
        // Bridge material-expressions.json 的实际形状：无 parameters/signature，
        // 仅有 inputs[]/outputs[]/defaultOutputType。
        val json = """
            {
              "expressions": [
                {
                  "name": "Add",
                  "className": "MaterialExpressionAdd",
                  "properties": [
                    { "name": "A", "type": "input", "isInput": true },
                    { "name": "B", "type": "input", "isInput": true },
                    { "name": "ConstA", "type": "float", "isInput": false },
                    { "name": "Desc", "type": "string", "isInput": false }
                  ],
                  "inputs": [
                    { "name": "A", "type": "input", "isInput": true },
                    { "name": "B", "type": "input", "isInput": true }
                  ],
                  "outputs": [
                    { "index": 0, "name": "None", "componentCount": 1, "outputType": "float1" }
                  ],
                  "defaultOutputType": "float1"
                }
              ]
            }
        """.trimIndent()

        val entry = DreamShaderMaterialExpressionManifest.parseCatalogEntries(json).single()
        assertEquals("UE", entry.namespace)
        assertEquals("Add", entry.ueName)
        assertEquals("float1", entry.outputType)
        // 参数来自 inputs（仅 A、B，不含 ConstA/Desc 这类非输入属性）。
        assertEquals(listOf("A", "B"), entry.parameters.map { it.name })
        // 合成的具名调用签名带 OutputType 与各输入实参占位，不再是 (...)。
        assertEquals("UE.Add(OutputType=\"float1\", A=Value, B=Value)", entry.signature)
    }
}
