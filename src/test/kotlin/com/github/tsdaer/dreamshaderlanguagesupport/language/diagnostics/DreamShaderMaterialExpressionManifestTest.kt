package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
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
