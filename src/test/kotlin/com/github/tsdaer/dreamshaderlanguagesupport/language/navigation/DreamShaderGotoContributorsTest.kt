package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.navigation.NavigationItem
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderGotoContributorsTest : BasePlatformTestCase() {
    fun testGotoClassContributorIndexesAssetDeclarations() {
        myFixture.configureByText(
            "MainMaterial.dsm",
            """
            Shader MainMaterial {
                Graph {
                }
            }
            Function Helper {
            }
            Namespace Tools {
                ShaderFunction BuildNoise {
                    Graph {
                    }
                }
            }
            """.trimIndent()
        )

        val contributor = DreamShaderGotoClassContributor()
        val names = contributor.getNames(project, false).toSet()

        assertTrue(names.contains("MainMaterial"))
        assertTrue(names.contains("BuildNoise"))
        assertFalse(names.contains("Helper"))

        val items = contributor.getItemsByName("BuildNoise", "BuildNoise", project, false)
        assertEquals(1, items.size)
        val navigationItem = items.single() as DreamShaderDeclarationNavigationItem
        assertEquals("Tools::BuildNoise", navigationItem.presentation.presentableText)
        assertEquals("BuildNoise", navigationItem.declaration.declarationName())
    }

    fun testGotoSymbolContributorIndexesFunctionsNamespacesAndNamespaceMembers() {
        myFixture.configureByText(
            "Tools.dsh",
            """
            Namespace Tools {
                Namespace Color {
                    Function ApplyTint {
                    }
                }
                ShaderFunction BuildNoise {
                    Graph {
                    }
                }
            }
            GraphFunction Blend {
            }
            Shader MainMaterial {
                Graph {
                }
            }
            """.trimIndent()
        )
        myFixture.configureByText(
            "Materials.dsf",
            """
            VirtualFunction(Name="/Game/Functions/VF_Pulse") {
                Options {
                }
            }
            """.trimIndent()
        )

        val contributor = DreamShaderGotoSymbolContributor()
        val names = contributor.getNames(project, false).toSet()

        assertTrue(names.contains("Tools"))
        assertTrue(names.contains("Color"))
        assertTrue(names.contains("ApplyTint"))
        assertTrue(names.contains("BuildNoise"))
        assertTrue(names.contains("Blend"))
        assertTrue(names.contains("VF_Pulse"))
        assertFalse(names.contains("MainMaterial"))

        val applyTintItems = contributor.getItemsByName("ApplyTint", "ApplyTint", project, false)
        assertEquals(1, applyTintItems.size)
        assertNavigationTarget(applyTintItems.single(), "Tools::Color::ApplyTint", "ApplyTint")

        val buildNoiseItems = contributor.getItemsByName("BuildNoise", "BuildNoise", project, false)
        assertEquals(1, buildNoiseItems.size)
        assertNavigationTarget(buildNoiseItems.single(), "Tools::BuildNoise", "BuildNoise")

        val vfItems = contributor.getItemsByName("VF_Pulse", "VF_Pulse", project, false)
        assertEquals(1, vfItems.size)
        assertNavigationTarget(vfItems.single(), "VF_Pulse", "VF_Pulse")
    }

    private fun assertNavigationTarget(item: NavigationItem, expectedPresentation: String, expectedName: String) {
        val navigationItem = item as DreamShaderDeclarationNavigationItem
        assertEquals(expectedPresentation, navigationItem.presentation.presentableText)
        val declaration = navigationItem.declaration
        assertInstanceOf(declaration, DreamShaderDeclaration::class.java)
        assertEquals(expectedName, declaration.declarationName())
    }
}
