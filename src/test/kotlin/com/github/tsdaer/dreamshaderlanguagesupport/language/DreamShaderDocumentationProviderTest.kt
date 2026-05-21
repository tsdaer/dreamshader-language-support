package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue

class DreamShaderDocumentationProviderTest : BasePlatformTestCase() {
    private val provider = DreamShaderDocumentationProvider()

    fun testDeclarationNameProvidesHoverDoc() {
        val file = myFixture.configureByText(
            "hover_declaration.dsf",
            """
            Function Util {
                Graph {
                    float3 v = float3(0.0, 1.0, 0.0);
                }
            }
            """.trimIndent()
        )

        val offset = file.text.indexOf("Util")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Function Util"))
        assertTrue(doc.contains("local function declaration"))
    }

    fun testSettingsKeyProvidesHoverDoc() {
        val file = myFixture.configureByText(
            "hover_settings_key.dsf",
            """
            Shader Main {
                Settings {
                    Domain = "Surface";
                }
            }
            """.trimIndent()
        )

        val offset = file.text.indexOf("Domain")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Settings Key: Domain"))
        assertTrue(doc.contains("material domain"))
    }

    fun testSettingsValueProvidesHoverDoc() {
        val file = myFixture.configureByText(
            "hover_settings_value.dsf",
            """
            Shader Main {
                Settings {
                    Domain = "Surface";
                }
            }
            """.trimIndent()
        )

        val offset = file.text.indexOf("Surface")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Setting Value: Surface"))
        assertTrue(doc.contains("Domain"))
    }

    fun testUeBuiltinProvidesHoverDoc() {
        val file = myFixture.configureByText(
            "hover_ue_builtin.dsf",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(Index=0);
                }
            }
            """.trimIndent()
        )

        val offset = file.text.indexOf("TexCoord")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("UE.TexCoord"))
        assertTrue(doc.contains("UV"))
    }
}
