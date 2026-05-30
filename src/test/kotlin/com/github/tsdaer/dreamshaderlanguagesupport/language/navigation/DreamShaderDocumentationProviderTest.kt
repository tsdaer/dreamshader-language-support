package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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

    fun testDeclarationKeywordProvidesHoverDoc() {
        val file = myFixture.configureByText(
            "hover_declaration_keyword.dsf",
            """
            Function Util {
                Graph {
                    float3 v = float3(0.0, 1.0, 0.0);
                }
            }
            """.trimIndent()
        )

        val offset = file.text.indexOf("Function")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Function Util"))
        assertTrue(doc.contains("local function declaration"))
    }

    fun testShaderNameAttributeUsedAsDeclarationDisplayName() {
        val file = myFixture.configureByText(
            "hover_shader_name_attribute.dsm",
            """
            Shader(Name="DreamMaterials/M_Minimal") {
                Graph {
                    float3 color = float3(1.0, 1.0, 1.0);
                }
            }
            """.trimIndent()
        )

        val offset = file.text.indexOf("Shader")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Shader DreamMaterials/M_Minimal"))
        assertFalse(doc.contains("Shader Name"))
    }

    fun testSectionKeywordsProvideHoverDoc() {
        val file = myFixture.configureByText(
            "hover_sections.dsm",
            """
            Shader(Name="DreamMaterials/M_Minimal") {
                Properties = {
                    vec3 Tint = vec3(1.0, 0.2, 0.2);
                }
                Settings = {
                    Domain = "UI";
                    ShadingModel = "Unlit";
                }
                Outputs = {
                    vec3 Color;
                    Base.EmissiveColor = Color;
                }
                Graph = {
                    Color = Tint;
                }
            }
            """.trimIndent()
        )

        val properties = provider.generateDoc(file.findElementAt(file.text.indexOf("Properties")), file.findElementAt(file.text.indexOf("Properties")))
        val settings = provider.generateDoc(file.findElementAt(file.text.indexOf("Settings")), file.findElementAt(file.text.indexOf("Settings")))
        val outputs = provider.generateDoc(file.findElementAt(file.text.indexOf("Outputs")), file.findElementAt(file.text.indexOf("Outputs")))
        val graph = provider.generateDoc(file.findElementAt(file.text.indexOf("Graph")), file.findElementAt(file.text.indexOf("Graph")))

        assertNotNull(properties)
        assertNotNull(settings)
        assertNotNull(outputs)
        assertNotNull(graph)

        assertTrue(properties!!.contains("Properties"))
        assertTrue(settings!!.contains("Settings"))
        assertTrue(outputs!!.contains("Outputs"))
        assertTrue(graph!!.contains("Graph"))
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

    fun testFunctionCallProvidesHoverDoc() {
        val file = myFixture.configureByText(
            "hover_function_call.dsf",
            """
            Function Blend(in float a, in float b, out float result) {
                result = lerp(a, b, 0.5);
            }

            Shader Main {
                Graph {
                    float x = 0.2;
                    float y = 0.8;
                    float outValue = 0.0;
                    Blend(x, y, outValue);
                }
            }
            """.trimIndent()
        )

        val offset = file.text.lastIndexOf("Blend(")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Function Call: Blend"))
        assertTrue(doc.contains("Blend(a, b, result)"))
    }

    fun testFunctionCallHoverUsesOriginalElementWhenResolveTargetDiffers() {
        val file = myFixture.configureByText(
            "hover_function_call_original_element.dsf",
            """
            Function Blend(in float a, in float b, out float result) {
                result = lerp(a, b, 0.5);
            }

            Shader Main {
                Graph {
                    float x = 0.2;
                    float y = 0.8;
                    float outValue = 0.0;
                    Blend(x, y, outValue);
                }
            }
            """.trimIndent()
        )

        val callOffset = file.text.lastIndexOf("Blend(")
        val declarationOffset = file.text.indexOf("Function Blend") + "Function ".length
        val callElement = file.findElementAt(callOffset)
        val declarationElement = file.findElementAt(declarationOffset)
        val doc = provider.generateDoc(declarationElement, callElement)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Function Call: Blend"))
        assertTrue(doc.contains("Blend(a, b, result)"))
    }

    fun testImportedFunctionCallProvidesHoverDoc() {
        myFixture.addFileToProject(
            "Library/shared_blend.dsh",
            """
            Function Blend(in float a, in float b, out float result) {
                result = lerp(a, b, 0.5);
            }
            """.trimIndent()
        )
        val file = myFixture.configureByText(
            "hover_imported_function_call.dsf",
            """
            import "Library/shared_blend.dsh"

            Shader Main {
                Graph {
                    float x = 0.2;
                    float y = 0.8;
                    float outValue = 0.0;
                    Blend(x, y, outValue);
                }
            }
            """.trimIndent()
        )

        val offset = file.text.lastIndexOf("Blend(")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Function Call: Blend"))
        assertTrue(doc.contains("Blend(a, b, result)"))
    }

    fun testLocalVariableProvidesHoverDoc() {
        val file = myFixture.configureByText(
            "hover_local_variable.dsf",
            """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(Index=0);
                    float n = saturate(uv.x);
                    float finalValue = n;
                }
            }
            """.trimIndent()
        )

        val offset = file.text.lastIndexOf("n;")
        val element = file.findElementAt(offset)
        val doc = provider.generateDoc(element, element)

        assertNotNull(doc)
        assertTrue(doc!!.contains("Local Variable: n"))
        assertTrue(doc.contains("Type: float"))
        assertTrue(doc.contains("Scope: Graph"))
    }

    fun testSettingsKeyHoverCanBeOverriddenFromProjectSettings() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.hoverDocumentationOverrides = """
            settings.domain.description=Custom Domain Description
        """.trimIndent()
        try {
            val file = myFixture.configureByText(
                "hover_settings_key_override.dsf",
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
            assertTrue(doc.contains("Custom Domain Description"))
        } finally {
            settings.hoverDocumentationOverrides = ""
        }
    }
}
