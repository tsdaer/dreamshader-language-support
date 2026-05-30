package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.rename.RenameProcessor
import com.intellij.testFramework.fixtures.BasePlatformTestCase

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
        RenameProcessor(project, declaration, "Utility", false, false).run()
        val updated = file.text

        assertTrue(updated.contains("Namespace Tools {\n    Function Utility {"))
        assertTrue(updated.contains("Function Util {\n}"))
        assertTrue(updated.contains("Tools::Utility"))
        assertTrue(Regex("""\bUtil\s*\(""").containsMatchIn(updated))
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
        RenameProcessor(project, declaration, "Utility", false, false).run()
        val updated = file.text

        assertTrue(updated.contains("Namespace Tools {\n    Function Util {"))
        assertTrue(updated.contains("Function Utility {\n}"))
        assertTrue(updated.contains("Tools::Util"))
        assertTrue(Regex("""\bUtility\s*\(""").containsMatchIn(updated))
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

        RenameProcessor(project, declaration, "Compose", false, false).run()
        val updated = file.text

        assertTrue(updated.contains("Namespace A {\n    Namespace B {\n        Function Compose {"))
        assertTrue(updated.contains("Namespace C {\n    Namespace B {\n        Function Blend {"))
        assertTrue(updated.contains("A::B::Compose"))
        assertTrue(updated.contains("C::B::Blend"))
    }
}
