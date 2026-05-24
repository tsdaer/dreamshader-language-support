package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertTrue

class DreamShaderFoldingBuilderTest : BasePlatformTestCase() {
    fun testBuildFoldRegionsIncludesBraceAndRegionBlocks() {
        val text = """
            Shader MySurface {
                // region Main Graph
                Graph {
                    float3 v = float3(0.0, 1.0, 0.0);
                }
                // endregion
            }
        """.trimIndent()

        val file = myFixture.configureByText("folding.dsf", text)
        val document = myFixture.editor.document
        val builder = DreamShaderFoldingBuilder()

        val descriptors = builder.buildFoldRegions(file, document, false)

        assertTrue("Expected at least one brace fold region", descriptors.any { it.placeholderText == "{...}" })
        assertTrue(
            "Expected // region fold placeholder",
            descriptors.any { it.placeholderText?.startsWith("// region") == true }
        )
    }

    fun testRegionFoldingUsesRegionTitleAsPlaceholder() {
        val text = """
            // region Lighting
            float3 Shade() {
                return float3(1.0, 1.0, 1.0);
            }
            // endregion
        """.trimIndent()

        val file = myFixture.configureByText("region.dsf", text)
        val document = myFixture.editor.document
        val builder = DreamShaderFoldingBuilder()

        val descriptors = builder.buildFoldRegions(file, document, false)
        val regionDescriptor = descriptors.firstOrNull { it.placeholderText?.startsWith("// region") == true }

        assertTrue("Expected named region descriptor", regionDescriptor != null)
        assertTrue("Expected region title in placeholder", regionDescriptor!!.placeholderText == "// region Lighting")
    }
}
