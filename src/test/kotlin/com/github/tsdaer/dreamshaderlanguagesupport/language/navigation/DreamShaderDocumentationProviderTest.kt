package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

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

    fun testUeBuiltinResolvesThroughCatalogNotHardcodedTable() {
        // UE.* 内置节点文档不再硬编码，改由 catalog 提供（此处用临时 manifest 模拟 Bridge 产物）。
        val manifest = Files.createTempFile("ds-hover-texcoord", ".json")
        Files.writeString(
            manifest,
            """
            {
              "expressions": [
                {
                  "namespace": "UE",
                  "className": "UMaterialExpressionTextureCoordinate",
                  "ueName": "TexCoord",
                  "signature": "UE.TexCoord(Index=0)",
                  "outputType": "float2",
                  "description": "Catalog-sourced TexCoord doc."
                }
              ]
            }
            """.trimIndent()
        )
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.materialExpressionManifestPath = manifest.toString()
        try {
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
            assertTrue(doc.contains("Catalog-sourced TexCoord doc."))
        } finally {
            settings.materialExpressionManifestPath = ""
            Files.deleteIfExists(manifest)
        }
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

    fun testCatalogProvidesUeMemberHoverDoc() {
        val manifest = Files.createTempFile("ds-hover-catalog", ".json")
        Files.writeString(
            manifest,
            """
            {
              "expressions": [
                {
                  "namespace": "UE",
                  "className": "UMaterialExpressionDreamOnly",
                  "ueName": "DreamOnly",
                  "signature": "UE.DreamOnly(Input=Value)",
                  "outputType": "float1",
                  "description": "A catalog-only material expression."
                }
              ]
            }
            """.trimIndent()
        )
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.materialExpressionManifestPath = manifest.toString()
        try {
            val file = myFixture.configureByText(
                "hover_catalog_ue.dsf",
                """
                Shader Main {
                    Graph {
                        float x = UE.DreamOnly(Input=0.0);
                    }
                }
                """.trimIndent()
            )

            val offset = file.text.indexOf("DreamOnly")
            val element = file.findElementAt(offset)
            val doc = provider.generateDoc(element, element)

            assertNotNull(doc)
            assertTrue(doc!!.contains("UE.DreamOnly(Input=Value)"))
            assertTrue(doc.contains("A catalog-only material expression."))
            assertTrue(doc.contains("float1"))
        } finally {
            settings.materialExpressionManifestPath = ""
        }
    }

    fun testCatalogProvidesSubstrateMemberHoverDoc() {
        val manifest = Files.createTempFile("ds-hover-substrate", ".json")
        Files.writeString(
            manifest,
            """
            {
              "expressions": [
                {
                  "namespace": "Substrate",
                  "className": "UMaterialExpressionSubstrateSlabBSDF",
                  "ueName": "Slab",
                  "signature": "Substrate.Slab(BaseColor=Color)",
                  "outputType": "Substrate",
                  "description": "Substrate slab BSDF node."
                }
              ]
            }
            """.trimIndent()
        )
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.materialExpressionManifestPath = manifest.toString()
        try {
            val file = myFixture.configureByText(
                "hover_catalog_substrate.dsf",
                """
                Shader Main {
                    Graph {
                        Substrate x = Substrate.Slab(BaseColor=0.0);
                    }
                }
                """.trimIndent()
            )

            val offset = file.text.indexOf("Slab")
            val element = file.findElementAt(offset)
            val doc = provider.generateDoc(element, element)

            assertNotNull(doc)
            assertTrue(doc!!.contains("Substrate.Slab(BaseColor=Color)"))
            assertTrue(doc.contains("Substrate slab BSDF node."))
        } finally {
            settings.materialExpressionManifestPath = ""
        }
    }

    fun testCatalogHoverRendersParameterList() {
        val manifest = Files.createTempFile("ds-hover-params", ".json")
        Files.writeString(
            manifest,
            """
            {
              "expressions": [
                {
                  "namespace": "Substrate",
                  "className": "UMaterialExpressionSubstrateSlabBSDF",
                  "ueName": "Slab",
                  "signature": "Substrate.Slab(DiffuseAlbedo=Color)",
                  "outputType": "Substrate",
                  "description": "Substrate slab BSDF node.",
                  "parameters": [
                    { "qualifier": "in", "type": "value", "name": "DiffuseAlbedo", "placeholder": "Color" }
                  ]
                }
              ]
            }
            """.trimIndent()
        )
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.materialExpressionManifestPath = manifest.toString()
        try {
            val file = myFixture.configureByText(
                "hover_catalog_params.dsf",
                """
                Shader Main {
                    Graph {
                        Substrate x = Substrate.Slab(DiffuseAlbedo=0.0);
                    }
                }
                """.trimIndent()
            )

            val offset = file.text.indexOf("Slab")
            val element = file.findElementAt(offset)
            val doc = provider.generateDoc(element, element)

            assertNotNull(doc)
            assertTrue(doc!!.contains("DiffuseAlbedo"))
            assertTrue(doc.contains("Color"))
        } finally {
            settings.materialExpressionManifestPath = ""
        }
    }

    fun testCatalogHoverRespectsUserOverridePriority() {
        val manifest = Files.createTempFile("ds-hover-override", ".json")
        Files.writeString(
            manifest,
            """
            {
              "expressions": [
                {
                  "namespace": "UE",
                  "className": "UMaterialExpressionDreamOnly",
                  "ueName": "DreamOnly",
                  "signature": "UE.DreamOnly(Input=Value)",
                  "description": "Default catalog description."
                }
              ]
            }
            """.trimIndent()
        )
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.materialExpressionManifestPath = manifest.toString()
        settings.hoverDocumentationOverrides = "ueBuiltins.dreamonly.description=Overridden hover text"
        try {
            val file = myFixture.configureByText(
                "hover_catalog_override.dsf",
                """
                Shader Main {
                    Graph {
                        float x = UE.DreamOnly(Input=0.0);
                    }
                }
                """.trimIndent()
            )

            val offset = file.text.indexOf("DreamOnly")
            val element = file.findElementAt(offset)
            val doc = provider.generateDoc(element, element)

            assertNotNull(doc)
            assertTrue(doc!!.contains("Overridden hover text"))
            assertFalse(doc.contains("Default catalog description."))
        } finally {
            settings.materialExpressionManifestPath = ""
            settings.hoverDocumentationOverrides = ""
        }
    }
}
