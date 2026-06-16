package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderFindUsagesProviderTest : BasePlatformTestCase() {
    fun testGetTypeUsesDeclarationKeyword() {
        val file = myFixture.configureByText(
            "find_usages_type_keyword.dsh",
            """
            ShaderFunction(Name="Functions/F_PulseTint")
            {
                Inputs = {
                    vec3 InColor;
                }
            }
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)
        assertNotNull("Expected declaration PSI", declaration)

        val provider = DreamShaderFindUsagesProvider()
        val typeText = provider.getType(declaration!!)
        assertEquals("dreamshader ShaderFunction declaration", typeText)
    }

    fun testGetTypeFallsBackWhenKeywordMissing() {
        val provider = DreamShaderFindUsagesProvider()
        val plain = myFixture.configureByText("plain.dsh", "float3 c = float3(1,1,1);")
        val token = plain.findElementAt(0)
        assertNotNull(token)
        assertEquals("dreamshader symbol", provider.getType(token!!))
    }

    fun testCanFindUsagesForNameAttributeValueToken() {
        val file = myFixture.configureByText(
            "find_usages_name_attribute_token.dsf",
            """
            ShaderFunction(Name="Functions/F_PulseTint")
            {
                Inputs = {
                    vec3 InColor;
                }
            }
            """.trimIndent()
        )

        val provider = DreamShaderFindUsagesProvider()
        val nameAttributeValue = file.findElementAt(file.text.indexOf("\"Functions/F_PulseTint\"") + 1)
        assertNotNull("Expected Name attribute string token", nameAttributeValue)
        assertTrue(provider.canFindUsagesFor(nameAttributeValue!!))
        assertEquals("F_PulseTint", provider.getDescriptiveName(nameAttributeValue))
        assertEquals("ShaderFunction F_PulseTint", provider.getNodeText(nameAttributeValue, false))
    }

    fun testGetNodeTextUsesQualifiedNamespaceNameWhenRequested() {
        val file = myFixture.configureByText(
            "find_usages_qualified_node_text.dsh",
            """
            Namespace Tools {
                Function Blend {
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("Function Blend") + "Function ".length
        val declaration = file.findElementAt(declarationOffset)!!.parent as DreamShaderDeclaration
        val provider = DreamShaderFindUsagesProvider()

        assertEquals("Tools::Blend", provider.getDescriptiveName(declaration))
        assertEquals("Function Blend", provider.getNodeText(declaration, false))
        assertEquals("Function Tools::Blend", provider.getNodeText(declaration, true))
    }
}
