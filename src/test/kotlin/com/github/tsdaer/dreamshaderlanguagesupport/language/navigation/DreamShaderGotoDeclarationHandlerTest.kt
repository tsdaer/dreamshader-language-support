package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderGotoDeclarationHandlerTest : BasePlatformTestCase() {
    fun testGotoDeclarationResolvesImportPathToFile() {
        val projectBase = project.basePath ?: error("project base path is null")
        val corePath = Paths.get(projectBase, "Common", "Core.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(
                VfsUtil.createDirectories(corePath.parent.toString()).createChildData(this, corePath.fileName.toString()),
                """
                Shader CoreShader {
                    Outputs {
                        float3 Color;
                    }
                }
                """.trimIndent()
            )
        }

        val file = myFixture.configureByText(
            "main.dsf",
            """
            import "Common/Core.dsh"
            Shader Main {
                Outputs {
                    float3 Color = float3(1.0, 1.0, 1.0);
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Common/Core.dsh") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected source element inside import string", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto declaration target for import path", targets)
        assertTrue(targets!!.isNotEmpty())
        assertEquals("Core.dsh", targets.first().containingFile.name)
    }

    fun testGotoDeclarationResolvesIdentifierToTopLevelDeclaration() {
        val file = myFixture.configureByText(
            "symbols.dsf",
            """
            Function Util {
                Graph {
                    float3 v = float3(0.0, 1.0, 0.0);
                }
            }

            Shader Main {
                Graph {
                    Util();
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Util();") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected identifier source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto declaration target for identifier", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("Util", declaration.declarationName())
    }

    fun testGotoDeclarationDoesNotResolveBuiltinLibraryFallbackImportPath() {
        val projectBase = project.basePath ?: error("project base path is null")
        val builtinPath = Paths.get(projectBase, "Plugins", "DreamShader", "Library", "Texture.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            VfsUtil.saveText(
                VfsUtil.createDirectories(builtinPath.parent.toString()).createChildData(this, builtinPath.fileName.toString()),
                """
                Namespace BuiltinTexture {
                    Function Sample {
                    }
                }
                """.trimIndent()
            )
        }

        val file = myFixture.configureByText(
            "main_builtin_import.dsf",
            """
            import "Texture.dsh"
            Shader Main {
                Outputs {
                    float3 Color = float3(1.0, 1.0, 1.0);
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Texture.dsh") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected source element inside import string", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertTrue("Expected no goto declaration target for removed builtin-library fallback", targets.isNullOrEmpty())
    }

    fun testGotoDeclarationResolvesNamespaceQualifiedFunctionMember() {
        val file = myFixture.configureByText(
            "namespace_call.dsh",
            """
            Namespace Tools {
                Function ApplyTint(in float3 InColor, out float3 OutColor) {
                    OutColor = InColor;
                }
            }

            Shader Main {
                Graph {
                    Tools::<caret>ApplyTint(float3(1,1,1), ColorOut);
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Tools::ApplyTint(") + "Tools::".length + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected member identifier source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto declaration target for namespaced member", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("ApplyTint", declaration.declarationName())
        assertEquals("function", declaration.keywordText())
    }

    fun testGotoDeclarationResolvesNamespaceQualifierToNamespaceDeclaration() {
        val file = myFixture.configureByText(
            "namespace_qualifier.dsh",
            """
            Namespace Tools {
                GraphFunction Blend(in float A, out float B) {
                    B = A;
                }
            }

            Shader Main {
                Graph {
                    <caret>Tools::Blend(1.0, ValueOut);
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Tools::") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected namespace qualifier source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto declaration target for namespace qualifier", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("Tools", declaration.declarationName())
        assertEquals("namespace", declaration.keywordText())
    }

    fun testGotoDeclarationResolvesNestedNamespaceQualifiedFunctionMember() {
        val file = myFixture.configureByText(
            "nested_namespace_call.dsh",
            """
            Namespace A {
                Namespace B {
                    Function Blend(in float X, out float Y) {
                        Y = X;
                    }
                }
            }

            Shader Main {
                Graph {
                    A::B::Blend(1.0, ValueOut);
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("A::B::Blend(") + "A::B::".length + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected nested namespaced member identifier source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto declaration target for nested namespaced member", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("Blend", declaration.declarationName())
        assertEquals("function", declaration.keywordText())
    }

    fun testGotoDeclarationResolvesNestedNamespaceQualifierToNamespaceDeclaration() {
        val file = myFixture.configureByText(
            "nested_namespace_qualifier.dsh",
            """
            Namespace A {
                Namespace B {
                    GraphFunction Make(in float X, out float Y) {
                        Y = X;
                    }
                }
            }

            Shader Main {
                Graph {
                    A::<caret>B::Make(1.0, ValueOut);
                }
            }
            """.trimIndent()
        )

        val usageOffset = myFixture.editor.caretModel.offset
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected nested namespace qualifier source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto declaration target for nested namespace qualifier", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("B", declaration.declarationName())
        assertEquals("namespace", declaration.keywordText())
    }

    fun testGotoDeclarationResolvesNestedNameAttributeNamespaceQualifier() {
        val file = myFixture.configureByText(
            "nested_name_attr_namespace_qualifier.dsh",
            """
            Namespace(Name="A") {
                Namespace(Name="B") {
                    GraphFunction Make(in float X, out float Y) {
                        Y = X;
                    }
                }
            }

            Shader Main {
                Graph {
                    A::<caret>B::Make(1.0, ValueOut);
                }
            }
            """.trimIndent()
        )

        val usageOffset = myFixture.editor.caretModel.offset
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected nested namespace qualifier source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto declaration target for nested namespace qualifier", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("B", declaration.declarationName())
        assertEquals("namespace", declaration.keywordText())
    }

    fun testGotoDeclarationDoesNotFallbackToTopLevelForUnresolvedQualifiedMember() {
        val file = myFixture.configureByText(
            "namespace_unresolved_qualified_member.dsh",
            """
            Namespace Tools {
            }

            Function ApplyTint {
            }

            Shader Main {
                Graph {
                    Tools::ApplyTint();
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Tools::ApplyTint(") + "Tools::".length + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected qualified member source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertTrue("Expected no goto target when qualified namespace member is unresolved", targets.isNullOrEmpty())
    }

    fun testGotoDeclarationUnqualifiedCallPrefersNearestNamespaceMember() {
        val file = myFixture.configureByText(
            "namespace_unqualified_prefer_nearest.dsh",
            """
            Namespace Tools {
                Function ApplyTint {
                }

                Shader Main {
                    Graph {
                        ApplyTint();
                    }
                }
            }

            Function ApplyTint {
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("ApplyTint();") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected unqualified call source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto target for unqualified namespaced member call", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("ApplyTint", declaration.declarationName())
        assertEquals("function", declaration.keywordText())
        val namespaceOwner = PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true)
        assertNotNull("Expected resolved declaration to be owned by namespace", namespaceOwner)
        assertTrue(
            "Expected resolved declaration to be inside Namespace Tools",
            namespaceOwner!!.keywordText() == "namespace" && namespaceOwner.declarationName() == "Tools"
        )
    }

    fun testGotoDeclarationUnqualifiedCallFallsBackToParentNamespaceMember() {
        val file = myFixture.configureByText(
            "namespace_unqualified_parent_fallback.dsh",
            """
            Namespace A {
                Function Blend {
                }

                Namespace B {
                    Shader Main {
                        Graph {
                            Blend();
                        }
                    }
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Blend();") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected unqualified nested call source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto target for parent namespace member call", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("Blend", declaration.declarationName())
        assertEquals("function", declaration.keywordText())
    }

    fun testGotoDeclarationResolvesGraphIdentifierToPropertiesVariableDeclaration() {
        val file = myFixture.configureByText(
            "graph_to_properties_variable.dsm",
            """
            Shader(Name="DreamMaterials/M_Minimal")
            {
                Properties = {
                    vec3 Tint = vec3(1.0, 0.2, 0.2);
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

        val usageOffset = file.text.lastIndexOf("Tint;") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull("Expected Tint usage source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto declaration target for Tint usage", targets)
        assertTrue(targets!!.isNotEmpty())

        val target = targets.first()
        assertEquals("Tint", target.text)
        val prev = PsiTreeUtil.prevVisibleLeaf(target)
        val next = PsiTreeUtil.nextVisibleLeaf(target)
        assertNotNull("Expected variable declaration type token before Tint target", prev)
        assertNotNull("Expected assignment token after Tint target", next)
        assertEquals(DreamShaderTokenTypes.TYPE, prev!!.node?.elementType)
        assertEquals("=", next!!.text)
    }

    fun testGotoDeclarationResolvesQualifiedMemberFromImportedFile() {
        val projectBase = project.basePath ?: error("project base path is null")
        val importedPath = Paths.get(projectBase, "Shared", "Common.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(importedPath.parent.toString())
            val file = parent.findOrCreateChildData(this, importedPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Namespace Common {
                    Function BuildPulse(in float t, in vec2 uv, out vec3 result) {
                        result = vec3(1.0, 1.0, 1.0);
                    }
                }
                """.trimIndent()
            )
        }

        val caller = myFixture.configureByText(
            "cross_file_qualified_member.dsm",
            """
            import "Shared/Common.dsh";

            Shader Main {
                Graph {
                    Common::BuildPulse(time, uv, outColor);
                }
            }
            """.trimIndent()
        )

        val usageOffset = caller.text.indexOf("BuildPulse(") + 1
        val sourceElement = caller.findElementAt(usageOffset)
        assertNotNull("Expected qualified member source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto target for imported qualified member", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("BuildPulse", declaration.declarationName())
        assertEquals("Common.dsh", declaration.containingFile.name)
    }

    fun testGotoDeclarationResolvesUnqualifiedFunctionCallFromImportedFile() {
        val projectBase = project.basePath ?: error("project base path is null")
        val importedPath = Paths.get(projectBase, "Shared", "Utils.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(importedPath.parent.toString())
            val file = parent.findOrCreateChildData(this, importedPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                    result = color * tint;
                }
                """.trimIndent()
            )
        }

        val caller = myFixture.configureByText(
            "cross_file_unqualified_member.dsm",
            """
            import "Shared/Utils.dsh";

            Shader Main {
                Graph {
                    vec3 c = vec3(1.0, 1.0, 1.0);
                    vec3 t = vec3(0.5, 0.5, 0.5);
                    vec3 outColor;
                    ApplyTint(c, t, outColor);
                }
            }
            """.trimIndent()
        )

        val usageOffset = caller.text.indexOf("ApplyTint(") + 1
        val sourceElement = caller.findElementAt(usageOffset)
        assertNotNull("Expected imported function call source element", sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull("Expected goto target for imported unqualified function call", targets)
        assertTrue(targets!!.isNotEmpty())
        val declaration = targets.first() as DreamShaderDeclaration
        assertEquals("ApplyTint", declaration.declarationName())
        assertEquals("Utils.dsh", declaration.containingFile.name)
    }

    fun testGotoDeclarationResolvesVirtualFunctionCallByNameAttributeLeaf() {
        val file = myFixture.configureByText(
            "virtual_function_name_leaf.dsh",
            """
            VirtualFunction(Name="BufferWriter")
            {
                Options = {
                    Asset = Path(Plugins.MoonToon, "MaterialFunctions/Buffer/Writer");
                }
                Inputs = {
                    float3 Color;
                    float Alpha;
                }
                Outputs = {
                    float3 Result;
                }
            }

            Shader Main {
                Graph {
                    float3 written = BufferWriter(Color, 1.0, Output="Result");
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("BufferWriter(") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull(sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull(targets)
        val declaration = targets!!.first() as DreamShaderDeclaration
        assertEquals("virtualfunction", declaration.keywordText())
    }

    fun testGotoDeclarationResolvesShaderFunctionCallByNameAttributePathLeaf() {
        val file = myFixture.configureByText(
            "shader_function_name_path_leaf.dsf",
            """
            ShaderFunction(Name="Functions/F_PulseTint")
            {
                Inputs = {
                    vec3 InColor;
                }
                Outputs = {
                    vec3 OutColor;
                }
                Graph = {
                    OutColor = InColor;
                }
            }

            Shader Main {
                Graph {
                    vec3 a = vec3(1.0, 1.0, 1.0);
                    vec3 b = F_PulseTint(a);
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("F_PulseTint(") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull(sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull(targets)
        val declaration = targets!!.first() as DreamShaderDeclaration
        assertEquals("shaderfunction", declaration.keywordText())
    }

    fun testGotoDeclarationResolvesImportedFunctionThroughRecursiveImportChain() {
        val projectBase = project.basePath ?: error("project base path is null")
        val apiPath = Paths.get(projectBase, "Shared", "Api.dsh")
        val implPath = Paths.get(projectBase, "Shared", "Impl.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val apiParent = VfsUtil.createDirectories(apiPath.parent.toString())
            val apiFile = apiParent.findOrCreateChildData(this, apiPath.fileName.toString())
            VfsUtil.saveText(apiFile, "import \"Impl.dsh\";")

            val implParent = VfsUtil.createDirectories(implPath.parent.toString())
            val implFile = implParent.findOrCreateChildData(this, implPath.fileName.toString())
            VfsUtil.saveText(
                implFile,
                """
                Function IndirectFunc(in float x, out float y) {
                    y = x;
                }
                """.trimIndent()
            )
        }

        val caller = myFixture.configureByText(
            "recursive_import_caller.dsm",
            """
            import "Shared/Api.dsh";

            Shader Main {
                Graph {
                    float x = 1.0;
                    float y = 0.0;
                    IndirectFunc(x, y);
                }
            }
            """.trimIndent()
        )

        val usageOffset = caller.text.indexOf("IndirectFunc(") + 1
        val sourceElement = caller.findElementAt(usageOffset)
        assertNotNull(sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull(targets)
        val declaration = targets!!.first() as DreamShaderDeclaration
        assertEquals("IndirectFunc", declaration.declarationName())
        assertEquals("Impl.dsh", declaration.containingFile.name)
    }

    fun testGotoDeclarationResolvesUeMemberToUnrealSourceWhenScanningEnabled() {
        val projectBase = project.basePath ?: error("project base path is null")
        val sourceRoot = Paths.get(projectBase, "Engine", "Source", "Runtime", "Engine", "Classes", "Materials")
        val headerPath = sourceRoot.resolve("MaterialExpressionSine.h")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(sourceRoot.toString())
            val file = parent.findOrCreateChildData(this, headerPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                UCLASS()
                class ENGINE_API UMaterialExpressionSine : public UMaterialExpression
                {
                };
                """.trimIndent()
            )
        }

        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.materialExpressionScanEnabled = true
        settings.state.unrealEngineSourceRoot = sourceRoot.toString()

        val file = myFixture.configureByText(
            "ue_source_goto.dsm",
            """
            Shader Main {
                Graph {
                    float value = UE.Sine(Input=0.5);
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Sine(") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull(sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertNotNull(targets)
        assertEquals("MaterialExpressionSine.h", targets!!.first().containingFile.name)
        assertEquals("UMaterialExpressionSine", targets.first().text)
    }

    fun testGotoDeclarationDoesNotResolveUeMemberToSourceWhenScanningDisabled() {
        val projectBase = project.basePath ?: error("project base path is null")
        val sourceRoot = Paths.get(projectBase, "Engine", "Source", "Runtime", "Engine", "Classes", "Materials")
        val headerPath = sourceRoot.resolve("MaterialExpressionSine.h")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(sourceRoot.toString())
            val file = parent.findOrCreateChildData(this, headerPath.fileName.toString())
            VfsUtil.saveText(file, "class ENGINE_API UMaterialExpressionSine : public UMaterialExpression {};")
        }

        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.materialExpressionScanEnabled = false
        settings.state.unrealEngineSourceRoot = sourceRoot.toString()

        val file = myFixture.configureByText(
            "ue_source_goto_disabled.dsm",
            """
            Shader Main {
                Graph {
                    float value = UE.Sine(Input=0.5);
                }
            }
            """.trimIndent()
        )

        val usageOffset = file.text.indexOf("Sine(") + 1
        val sourceElement = file.findElementAt(usageOffset)
        assertNotNull(sourceElement)

        val handler = DreamShaderGotoDeclarationHandler()
        val targets = handler.getGotoDeclarationTargets(sourceElement, usageOffset, myFixture.editor)
        assertTrue("Expected catalog-only UE member to keep existing no-target fallback", targets.isNullOrEmpty())
    }
}
