package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.intellij.testFramework.fixtures.BasePlatformTestCase

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

    fun testBuildsNamespaceAsTopLevelDeclarationSymbol() {
        val text = """
            Namespace Tools {
                Function ApplyTint(in float3 InColor, out float3 OutColor) {
                    OutColor = InColor;
                }
            }
        """.trimIndent()

        val model = DreamShaderSymbolModelBuilder.buildFromText(text)

        assertEquals(1, model.topLevelSymbols.size)
        val declaration = model.topLevelSymbols.first()
        assertEquals(DreamShaderSymbolKind.DECLARATION, declaration.kind)
        assertEquals("namespace Tools", declaration.name)
        assertEquals(1, declaration.children.size)
        val member = declaration.children.first()
        assertEquals(DreamShaderSymbolKind.NAMESPACE_MEMBER, member.kind)
        assertEquals("function ApplyTint", member.name)
        assertTrue(member.children.isEmpty())
    }

    fun testBuildsNamespaceMemberWithSectionChildren() {
        val text = """
            Namespace Tools {
                ShaderFunction BuildNoise {
                    Outputs {
                        float Out = 0.0;
                    }
                    Graph {
                        Out = 1.0;
                    }
                }
            }
        """.trimIndent()

        val model = DreamShaderSymbolModelBuilder.buildFromText(text)

        assertEquals(1, model.topLevelSymbols.size)
        val namespace = model.topLevelSymbols.first()
        assertEquals("namespace Tools", namespace.name)
        assertEquals(1, namespace.children.size)
        val member = namespace.children.first()
        assertEquals(DreamShaderSymbolKind.NAMESPACE_MEMBER, member.kind)
        assertEquals("shaderfunction BuildNoise", member.name)
        val sectionNames = member.children.map { it.name }.sorted()
        assertEquals(listOf("graph", "outputs"), sectionNames)
    }
}
