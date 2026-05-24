package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderStructureViewModelTest : BasePlatformTestCase() {
    fun testStructureViewContainsDeclarationsAndSections() {
        val file = myFixture.configureByText(
            "structure.dsf",
            """
            Shader SurfaceMain {
                Inputs {
                    float2 UV;
                }
                Outputs {
                    float3 Color;
                }
            }
            Function Util {
                Graph {
                    float3 a = float3(0.0, 1.0, 0.0);
                }
            }
            """.trimIndent()
        )

        val factory = DreamShaderStructureViewFactory()
        val builder = factory.getStructureViewBuilder(file)
        assertTrue("Expected structure view builder for DreamShader file", builder != null)

        val treeBuilder = builder as TreeBasedStructureViewBuilder
        val model = treeBuilder.createStructureViewModel(myFixture.editor) as DreamShaderStructureViewModel
        val root = model.root as DreamShaderStructureViewElement
        val topChildren = root.children.map { it as DreamShaderStructureViewElement }

        assertEquals(2, topChildren.size)
        val topNames = topChildren.mapNotNull { it.presentation.presentableText }.sorted()
        assertEquals(listOf("function Util", "shader SurfaceMain"), topNames)

        val shaderNode = topChildren.first { it.presentation.presentableText == "shader SurfaceMain" }
        val shaderSectionNames = shaderNode.children
            .map { it as DreamShaderStructureViewElement }
            .mapNotNull { it.presentation.presentableText }
            .sorted()
        assertEquals(listOf("inputs", "outputs"), shaderSectionNames)
    }
}
