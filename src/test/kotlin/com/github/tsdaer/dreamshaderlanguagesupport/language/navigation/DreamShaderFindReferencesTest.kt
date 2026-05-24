package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class DreamShaderFindReferencesTest : BasePlatformTestCase() {
    fun testReferencesSearchFindsIdentifierUsages() {
        val file = myFixture.configureByText(
            "refs.dsf",
            """
            Function Util {
                Graph {
                    float3 v = float3(0.0, 1.0, 0.0);
                }
            }

            Shader Main {
                Graph {
                    Util();
                    float3 c = Util();
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("Function Util") + "Function ".length
        val nameElement = file.findElementAt(declarationOffset)
        assertNotNull("Expected declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(2, refs.size)
        val referenceTexts = refs.map { it.element.text }.toSet()
        assertTrue(referenceTexts.contains("Util"))
        assertTrue(refs.all { it.resolve() == declaration })
    }
}
