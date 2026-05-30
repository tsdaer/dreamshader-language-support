package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderDeclarationRenameTest : BasePlatformTestCase() {
    fun testDeclarationSetNameUpdatesNameIdentifier() {
        val file = myFixture.configureByText(
            "rename.dsf",
            """
            Function Util {
                Graph {
                    float3 v = float3(0.0, 1.0, 0.0);
                }
            }
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)
        assertNotNull("Expected declaration PSI", declaration)

        WriteCommandAction.runWriteCommandAction(project) {
            declaration!!.setName("Utility")
        }

        assertEquals("Utility", declaration!!.declarationName())
        assertEquals("Function Utility {", file.text.lineSequence().first())
    }

    fun testRenameNamespaceMemberDoesNotRenameTopLevelSameName() {
        val file = myFixture.configureByText(
            "rename_namespace_member.dsh",
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
        runRenameAndCommit(declaration, "Utility")
        val updated = file.text

        assertMatches(updated, """Namespace\s+Tools\s*\{\s*Function\s+Utility\s*\{""")
        assertMatches(updated, """Function\s+Util\s*\{\s*\}""")
        assertMatches(updated, """Tools\s*::\s*Utility""")
        assertMatches(updated, """\bUtil\s*\(""")
    }

    fun testRenameTopLevelFunctionDoesNotRenameNamespaceMemberSameName() {
        val file = myFixture.configureByText(
            "rename_top_level_function.dsh",
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

        val topLevelDeclarationOffset = file.text.lastIndexOf("Function Util") + "Function ".length
        val nameElement = file.findElementAt(topLevelDeclarationOffset)
        assertNotNull("Expected top-level declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration
        runRenameAndCommit(declaration, "Utility")
        val updated = file.text

        assertMatches(updated, """Namespace\s+Tools\s*\{\s*Function\s+Util\s*\{""")
        assertMatches(updated, """Function\s+Utility\s*\{\s*\}""")
        assertMatches(updated, """Tools\s*::\s*Util""")
        assertMatches(updated, """\bUtility\s*\(""")
    }

    fun testRenameNestedNamespaceMemberDoesNotRenameSameNameUnderOtherNamespacePath() {
        val file = myFixture.configureByText(
            "rename_nested_namespace_full_path.dsh",
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

        runRenameAndCommit(declaration, "Compose")
        val updated = file.text

        assertMatches(updated, """Namespace\s+A\s*\{\s*Namespace\s+B\s*\{\s*Function\s+Compose\s*\{""")
        assertMatches(updated, """Namespace\s+C\s*\{\s*Namespace\s+B\s*\{\s*Function\s+Blend\s*\{""")
        assertMatches(updated, """A\s*::\s*B\s*::\s*Compose""")
        assertMatches(updated, """C\s*::\s*B\s*::\s*Blend""")
    }

    fun testRenameNestedNameAttributeNamespaceMemberDoesNotRenameSameNameUnderOtherNamespacePath() {
        val file = myFixture.configureByText(
            "rename_nested_name_attr_namespace_full_path.dsh",
            """
            Namespace(Name="A") {
                Namespace(Name="B") {
                    Function Blend {
                    }
                }
            }

            Namespace(Name="C") {
                Namespace(Name="B") {
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

        val declarationOffset = file.text.indexOf("Namespace(Name=\"A\")")
        val aBlockStart = file.text.indexOf('{', declarationOffset)
        val targetFunctionOffset = file.text.indexOf("Function Blend", aBlockStart) + "Function ".length
        val nameElement = file.findElementAt(targetFunctionOffset)
        assertNotNull("Expected nested namespace member declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        runRenameAndCommit(declaration, "Compose")
        val updated = file.text

        assertMatches(updated, """Namespace\(Name="A"\)\s*\{\s*Namespace\(Name="B"\)\s*\{\s*Function\s+Compose\s*\{""")
        assertMatches(updated, """Namespace\(Name="C"\)\s*\{\s*Namespace\(Name="B"\)\s*\{\s*Function\s+Blend\s*\{""")
        assertMatches(updated, """A\s*::\s*B\s*::\s*Compose""")
        assertMatches(updated, """C\s*::\s*B\s*::\s*Blend""")
    }

    fun testRenameVirtualFunctionNameAttributeUpdatesStringAndUsages() {
        val file = myFixture.configureByText(
            "rename_virtual_function_name_attr.dsh",
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

        val declarationOffset = file.text.indexOf("\"BufferWriter\"") + 1
        val nameElement = file.findElementAt(declarationOffset)
        assertNotNull("Expected virtual function declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        runRenameAndCommit(declaration, "BufferOutput")
        val updated = file.text
        assertTrue("Updated text:\n$updated", updated.contains("BufferOutput"))

        assertTrue(
            "Updated text:\n$updated",
            Regex("""VirtualFunction\(\s*Name\s*=\s*"BufferOutput"\s*\)""").containsMatchIn(updated)
        )
        assertTrue(
            "Updated text:\n$updated",
            Regex("""BufferOutput\s*\(\s*Color\s*,\s*1\.0\s*,\s*Output\s*=\s*"Result"\s*\)""").containsMatchIn(updated)
        )
        assertFalse(updated.contains("BufferWriter("))
    }

    fun testRenameShaderFunctionNameAttributePathLeafKeepsPrefixAndUpdatesCrossFileUsages() {
        val projectBase = project.basePath ?: error("project base path is null")
        val importedPath = Paths.get(projectBase, "Shared", "Library.dsf")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(importedPath.parent.toString())
            val file = parent.findOrCreateChildData(this, importedPath.fileName.toString())
            VfsUtil.saveText(
                file,
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
                """.trimIndent()
            )
        }

        val caller = myFixture.configureByText(
            "rename_name_attr_cross_file_caller.dsm",
            """
            import "Shared/Library.dsf";

            Shader Main {
                Graph {
                    vec3 a = vec3(1.0, 1.0, 1.0);
                    vec3 b = F_PulseTint(a);
                }
            }
            """.trimIndent()
        )

        val importedVf = VfsUtil.findFile(importedPath, true)
        assertNotNull("Expected imported shader function file", importedVf)
        myFixture.configureFromExistingVirtualFile(importedVf!!)
        val importedPsi = com.intellij.psi.PsiManager.getInstance(project).findFile(importedVf)
        assertNotNull("Expected imported shader function psi", importedPsi)
        val declarationOffset = importedPsi!!.text.indexOf("\"Functions/F_PulseTint\"") + 1
        val nameElement = importedPsi.findElementAt(declarationOffset)
        assertNotNull("Expected shader function declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        runRenameAndCommit(declaration, "F_OutputTint")

        val importedUpdated = importedPsi.text
        assertTrue("Updated imported text:\n$importedUpdated", importedUpdated.contains("F_OutputTint"))
        assertTrue(
            "Updated imported text:\n$importedUpdated",
            Regex("""ShaderFunction\(\s*Name\s*=\s*"Functions/F_OutputTint"\s*\)""").containsMatchIn(importedUpdated)
        )
        assertFalse(importedUpdated.contains("Functions/F_PulseTint"))

        val callerUpdated = caller.text
        assertTrue(
            "Updated caller text:\n$callerUpdated",
            Regex("""\bF_OutputTint\s*\(\s*a\s*\)""").containsMatchIn(callerUpdated)
        )
        assertFalse(
            "Updated caller text:\n$callerUpdated",
            Regex("""\bF_PulseTint\s*\(""").containsMatchIn(callerUpdated)
        )
    }

    fun testDeclarationNameUsesVirtualFunctionNameAttributeLeaf() {
        val file = myFixture.configureByText(
            "name_attr_virtual_decl_name.dsh",
            """
            VirtualFunction(Name="BufferWriter")
            {
                Options = {
                    Asset = Path(Plugins.MoonToon, "MaterialFunctions/Buffer/Writer");
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("\"BufferWriter\"") + 1
        val nameElement = file.findElementAt(declarationOffset)
        assertNotNull(nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration
        assertEquals("BufferWriter", declaration.declarationName())
        assertEquals("BufferWriter", declaration.name)
    }

    fun testDeclarationNameUsesShaderFunctionNameAttributePathLeaf() {
        val file = myFixture.configureByText(
            "name_attr_shader_decl_name.dsf",
            """
            ShaderFunction(Name="Functions/F_PulseTint")
            {
                Inputs = {
                    vec3 InColor;
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("\"Functions/F_PulseTint\"") + 1
        val nameElement = file.findElementAt(declarationOffset)
        assertNotNull(nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration
        assertEquals("F_PulseTint", declaration.declarationName())
        assertEquals("F_PulseTint", declaration.name)
    }

    private fun runRenameAndCommit(declaration: DreamShaderDeclaration, newName: String) {
        RenameProcessor(project, declaration, newName, false, false).run()
        PsiDocumentManager.getInstance(project).commitAllDocuments()
    }

    private fun assertMatches(text: String, pattern: String) {
        assertTrue("Expected pattern `$pattern` in text:\n$text", Regex(pattern).containsMatchIn(text))
    }
}
