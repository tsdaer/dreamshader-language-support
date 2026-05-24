package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

class DreamShaderSymbolModelBuilderTest : BasePlatformTestCase() {
    fun testBuildsDeclarationAndSectionSymbols() {
        val text = """
            Shader SurfaceMain {
                Inputs {
                    float2 UV;
                }
                Outputs {
                    float3 Color;
                }
            }
        """.trimIndent()

        val model = DreamShaderSymbolModelBuilder.buildFromText(text)

        assertEquals(1, model.topLevelSymbols.size)
        val declaration = model.topLevelSymbols.first()
        assertEquals(DreamShaderSymbolKind.DECLARATION, declaration.kind)
        assertEquals("shader SurfaceMain", declaration.name)
        assertEquals(2, declaration.children.size)

        val sectionNames = declaration.children.map { it.name }.sorted()
        assertEquals(listOf("inputs", "outputs"), sectionNames)
        assertTrue(declaration.children.all { it.kind == DreamShaderSymbolKind.SECTION })
    }
}
