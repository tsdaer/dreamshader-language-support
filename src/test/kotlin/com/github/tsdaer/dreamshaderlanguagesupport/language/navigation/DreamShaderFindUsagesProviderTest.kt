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
        assertEquals("dreamshader shaderfunction declaration", typeText)
    }

    fun testGetTypeFallsBackWhenKeywordMissing() {
        val provider = DreamShaderFindUsagesProvider()
        val plain = myFixture.configureByText("plain.dsh", "float3 c = float3(1,1,1);")
        val token = plain.findElementAt(0)
        assertNotNull(token)
        assertEquals("dreamshader symbol", provider.getType(token!!))
    }
}

