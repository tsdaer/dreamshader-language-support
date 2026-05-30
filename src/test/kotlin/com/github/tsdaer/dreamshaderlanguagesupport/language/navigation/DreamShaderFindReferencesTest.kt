package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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

    fun testReferencesSearchForNamespaceMemberExcludesTopLevelSameName() {
        val file = myFixture.configureByText(
            "refs_namespace_member.dsh",
            """
            Namespace Tools {
                Function Util {
                }
            }

            Function Util {
            }

            Shader Main {
                Graph {
                    Tools::Util();
                    Util();
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("Function Util") + "Function ".length
        val nameElement = file.findElementAt(declarationOffset)
        assertNotNull("Expected namespace member declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(1, refs.size)
        assertEquals("Util", refs.first().element.text)

        val usageOffset = file.text.indexOf("Tools::Util();") + "Tools::".length + 1
        val usageElement = file.findElementAt(usageOffset)
        assertNotNull("Expected namespaced usage identifier", usageElement)
        assertEquals(usageElement, refs.first().element)
    }

    fun testReferencesSearchForTopLevelFunctionExcludesNamespaceQualifiedSameName() {
        val file = myFixture.configureByText(
            "refs_top_level_vs_namespace.dsh",
            """
            Namespace Tools {
                Function Util {
                }
            }

            Function Util {
            }

            Shader Main {
                Graph {
                    Tools::Util();
                    Util();
                    float3 c = Util();
                }
            }
            """.trimIndent()
        )

        val topLevelDeclarationOffset = file.text.lastIndexOf("Function Util") + "Function ".length
        val nameElement = file.findElementAt(topLevelDeclarationOffset)
        assertNotNull("Expected top-level declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.element.text == "Util" })
    }

    fun testReferencesSearchForNestedNamespaceMemberUsesNearestQualifier() {
        val file = myFixture.configureByText(
            "refs_nested_namespace_member.dsh",
            """
            Namespace A {
                Namespace B {
                    Function Blend {
                    }
                }
                Function Blend {
                }
            }

            Shader Main {
                Graph {
                    A::B::Blend();
                    A::Blend();
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("Function Blend") + "Function ".length
        val nameElement = file.findElementAt(declarationOffset)
        assertNotNull("Expected nested namespace member declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(1, refs.size)
        val usageOffset = file.text.indexOf("A::B::Blend();") + "A::B::".length + 1
        val usageElement = file.findElementAt(usageOffset)
        assertNotNull("Expected nested namespaced usage identifier", usageElement)
        assertEquals(usageElement, refs.first().element)
    }

    fun testReferencesSearchForNestedNamespaceMemberUsesFullQualifierPath() {
        val file = myFixture.configureByText(
            "refs_nested_namespace_full_path.dsh",
            """
            Namespace A {
                Namespace B {
                    Function Blend {
                    }
                }
            }

            Namespace C {
                Namespace B {
                    Function Blend {
                    }
                }
            }

            Shader Main {
                Graph {
                    A::B::Blend();
                    C::B::Blend();
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("Namespace A")
        val aBlockStart = file.text.indexOf('{', declarationOffset)
        val targetFunctionOffset = file.text.indexOf("Function Blend", aBlockStart) + "Function ".length
        val nameElement = file.findElementAt(targetFunctionOffset)
        assertNotNull("Expected nested namespace member declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(1, refs.size)

        val usageOffset = file.text.indexOf("A::B::Blend();") + "A::B::".length + 1
        val usageElement = file.findElementAt(usageOffset)
        assertNotNull("Expected full-path namespaced usage identifier", usageElement)
        assertEquals(usageElement, refs.first().element)
    }
}
