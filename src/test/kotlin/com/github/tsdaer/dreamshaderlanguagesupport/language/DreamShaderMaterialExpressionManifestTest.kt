package com.github.tsdaer.dreamshaderlanguagesupport.language

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
}
