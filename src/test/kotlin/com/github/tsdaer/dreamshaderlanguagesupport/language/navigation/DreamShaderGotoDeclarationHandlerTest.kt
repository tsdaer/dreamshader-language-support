package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
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
}
