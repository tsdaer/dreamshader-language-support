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
}
