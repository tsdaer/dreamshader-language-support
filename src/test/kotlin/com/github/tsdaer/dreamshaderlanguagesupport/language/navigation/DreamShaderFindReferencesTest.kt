package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderFindReferencesTest : BasePlatformTestCase() {
    fun testReferencesSearchFindsIdentifierUsages() {
        val file = myFixture.configureByText(
            "refs.dsf",
            """
            Function Util {
                Graph {
                    float3 v = float3(0.0, 1.0, 0.0);
                }
            }

            Shader Main {
                Graph {
                    Util();
                    float3 c = Util();
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("Function Util") + "Function ".length
        val nameElement = file.findElementAt(declarationOffset)
        assertNotNull("Expected declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(2, refs.size)
        val referenceTexts = refs.map { it.element.text }.toSet()
        assertTrue(referenceTexts.contains("Util"))
        assertTrue(refs.all { it.resolve() == declaration })
    }

    fun testReferencesSearchForNamespaceMemberExcludesTopLevelSameName() {
        val file = myFixture.configureByText(
            "refs_namespace_member.dsh",
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

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(1, refs.size)
        assertEquals("Util", refs.first().element.text)

        val usageOffset = file.text.indexOf("Tools::Util();") + "Tools::".length + 1
        val usageElement = file.findElementAt(usageOffset)
        assertNotNull("Expected namespaced usage identifier", usageElement)
        assertEquals(usageElement, refs.first().element)
    }

    fun testReferencesSearchForTopLevelFunctionExcludesNamespaceQualifiedSameName() {
        val file = myFixture.configureByText(
            "refs_top_level_vs_namespace.dsh",
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
                    float3 c = Util();
                }
            }
            """.trimIndent()
        )

        val topLevelDeclarationOffset = file.text.lastIndexOf("Function Util") + "Function ".length
        val nameElement = file.findElementAt(topLevelDeclarationOffset)
        assertNotNull("Expected top-level declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.element.text == "Util" })
    }

    fun testReferencesSearchForNestedNamespaceMemberUsesNearestQualifier() {
        val file = myFixture.configureByText(
            "refs_nested_namespace_member.dsh",
            """
            Namespace A {
                Namespace B {
                    Function Blend {
                    }
                }
                Function Blend {
                }
            }

            Shader Main {
                Graph {
                    A::B::Blend();
                    A::Blend();
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("Function Blend") + "Function ".length
        val nameElement = file.findElementAt(declarationOffset)
        assertNotNull("Expected nested namespace member declaration identifier", nameElement)
        val declaration = nameElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(1, refs.size)
        val usageOffset = file.text.indexOf("A::B::Blend();") + "A::B::".length + 1
        val usageElement = file.findElementAt(usageOffset)
        assertNotNull("Expected nested namespaced usage identifier", usageElement)
        assertEquals(usageElement, refs.first().element)
    }

    fun testReferencesSearchForNestedNamespaceMemberUsesFullQualifierPath() {
        val file = myFixture.configureByText(
            "refs_nested_namespace_full_path.dsh",
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

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(1, refs.size)

        val usageOffset = file.text.indexOf("A::B::Blend();") + "A::B::".length + 1
        val usageElement = file.findElementAt(usageOffset)
        assertNotNull("Expected full-path namespaced usage identifier", usageElement)
        assertEquals(usageElement, refs.first().element)
    }

    fun testReferencesSearchForNestedNameAttributeNamespaceMemberUsesFullQualifierPath() {
        val file = myFixture.configureByText(
            "refs_nested_name_attr_namespace_full_path.dsh",
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

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(1, refs.size)

        val usageOffset = file.text.indexOf("A::B::Blend();") + "A::B::".length + 1
        val usageElement = file.findElementAt(usageOffset)
        assertNotNull("Expected full-path namespaced usage identifier", usageElement)
        assertEquals(usageElement, refs.first().element)
    }

    fun testReferencesSearchFindsUsagesAcrossImportedFiles() {
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

        myFixture.configureByText(
            "cross_file_refs_caller.dsm",
            """
            import "Shared/Utils.dsh";

            Shader Main {
                Graph {
                    vec3 c = vec3(1.0, 1.0, 1.0);
                    vec3 t = vec3(0.5, 0.5, 0.5);
                    vec3 outA;
                    vec3 outB;
                    ApplyTint(c, t, outA);
                    ApplyTint(c, t, outB);
                }
            }
            """.trimIndent()
        )

        val importedVf = VfsUtil.findFile(importedPath, true)
        assertNotNull("Expected imported declaration file", importedVf)
        myFixture.configureFromExistingVirtualFile(importedVf!!)
        val importedPsi = PsiManager.getInstance(project).findFile(importedVf)
        assertNotNull("Expected imported declaration psi file", importedPsi)
        val importedPsiFile = importedPsi!!
        val declarationOffset = importedPsiFile.text.indexOf("Function ApplyTint") + "Function ".length
        val declarationElement = importedPsiFile.findElementAt(declarationOffset)
        assertNotNull("Expected imported declaration identifier", declarationElement)
        val declaration = declarationElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.element.text == "ApplyTint" })
        assertTrue(refs.all { it.element.containingFile.name == "cross_file_refs_caller.dsm" })
    }

    fun testReferencesSearchFindsUsagesThroughRecursiveImportChain() {
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

        myFixture.configureByText(
            "recursive_refs_caller.dsm",
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

        val implVf = VfsUtil.findFile(implPath, true)
        assertNotNull("Expected implementation declaration file", implVf)
        myFixture.configureFromExistingVirtualFile(implVf!!)
        val implPsi = PsiManager.getInstance(project).findFile(implVf)
        assertNotNull("Expected implementation declaration psi file", implPsi)
        val implPsiFile = implPsi!!
        val declarationOffset = implPsiFile.text.indexOf("Function IndirectFunc") + "Function ".length
        val declarationElement = implPsiFile.findElementAt(declarationOffset)
        assertNotNull("Expected recursive-chain declaration identifier", declarationElement)
        val declaration = declarationElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        val referenceFiles = refs.map { it.element.containingFile.name }.toSet()
        val referenceTexts = refs.map { it.element.text }.toSet()
        assertTrue(
            "Expected recursive import-chain references to include caller usage (files=$referenceFiles texts=$referenceTexts)",
            refs.size == 1
        )
        assertEquals("IndirectFunc", refs.first().element.text)
        assertEquals("recursive_refs_caller.dsm", refs.first().element.containingFile.name)
    }

    fun testReferencesSearchFindsVirtualFunctionUsagesByNameAttributeLeaf() {
        val file = myFixture.configureByText(
            "refs_virtual_function_name_leaf.dsh",
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
                    float3 writtenA = BufferWriter(Color, 1.0, Output="Result");
                    float3 writtenB = BufferWriter(Color, 0.5, Output="Result");
                }
            }
            """.trimIndent()
        )

        val declarationOffset = file.text.indexOf("VirtualFunction(Name=") + "VirtualFunction(".length
        val declarationElement = file.findElementAt(declarationOffset)
        assertNotNull("Expected virtual function declaration identifier", declarationElement)
        val declaration = declarationElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.element.text == "BufferWriter" })
    }

    fun testReferencesSearchFindsShaderFunctionUsagesByNameAttributePathLeafAcrossImportedFiles() {
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

        myFixture.configureByText(
            "refs_name_attr_cross_file_caller.dsm",
            """
            import "Shared/Library.dsf";

            Shader Main {
                Graph {
                    vec3 a = vec3(1.0, 1.0, 1.0);
                    vec3 b = F_PulseTint(a);
                    vec3 c = F_PulseTint(b);
                }
            }
            """.trimIndent()
        )

        val importedVf = VfsUtil.findFile(importedPath, true)
        assertNotNull("Expected imported shader function file", importedVf)
        myFixture.configureFromExistingVirtualFile(importedVf!!)
        val importedPsi = PsiManager.getInstance(project).findFile(importedVf)
        assertNotNull("Expected imported shader function psi file", importedPsi)
        val importedPsiFile = importedPsi!!
        val declarationOffset = importedPsiFile.text.indexOf("ShaderFunction(Name=") + "ShaderFunction(".length
        val declarationElement = importedPsiFile.findElementAt(declarationOffset)
        assertNotNull("Expected shader function declaration identifier", declarationElement)
        val declaration = declarationElement!!.parent as DreamShaderDeclaration

        val refs = ReferencesSearch.search(declaration).findAll()
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.element.text == "F_PulseTint" })
        assertTrue(refs.all { it.element.containingFile.name == "refs_name_attr_cross_file_caller.dsm" })
    }

    fun testReferencesSearchRespectsGlobalFileScope() {
        val projectBase = project.basePath ?: error("project base path is null")
        val importedPath = Paths.get(projectBase, "Shared", "ScopedUtils.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(importedPath.parent.toString())
            val file = parent.findOrCreateChildData(this, importedPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Function ScopedTint(in vec3 color, out vec3 result) {
                    result = color;
                }
                """.trimIndent()
            )
        }

        val callerFile = myFixture.configureByText(
            "scope_refs_caller.dsm",
            """
            import "Shared/ScopedUtils.dsh";

            Shader Main {
                Graph {
                    vec3 c = vec3(1.0, 1.0, 1.0);
                    vec3 outA;
                    vec3 outB;
                    ScopedTint(c, outA);
                    ScopedTint(c, outB);
                }
            }
            """.trimIndent()
        )

        val importedVf = VfsUtil.findFile(importedPath, true)
        assertNotNull("Expected imported declaration file", importedVf)
        myFixture.configureFromExistingVirtualFile(importedVf!!)
        val importedPsi = PsiManager.getInstance(project).findFile(importedVf)
        assertNotNull("Expected imported declaration psi file", importedPsi)
        val importedPsiFile = importedPsi!!
        val declarationOffset = importedPsiFile.text.indexOf("Function ScopedTint") + "Function ".length
        val declarationElement = importedPsiFile.findElementAt(declarationOffset)
        assertNotNull("Expected imported declaration identifier", declarationElement)
        val declaration = declarationElement!!.parent as DreamShaderDeclaration

        val importedFileScopeRefs = ReferencesSearch.search(
            declaration,
            GlobalSearchScope.fileScope(project, importedVf)
        ).findAll()
        assertEquals(0, importedFileScopeRefs.size)

        val callerVf = callerFile.virtualFile
        val callerFileScopeRefs = ReferencesSearch.search(
            declaration,
            GlobalSearchScope.fileScope(project, callerVf)
        ).findAll()
        assertEquals(2, callerFileScopeRefs.size)
        assertTrue(callerFileScopeRefs.all { it.element.text == "ScopedTint" })
        assertTrue(callerFileScopeRefs.all { it.element.containingFile.name == "scope_refs_caller.dsm" })
    }

    fun testReferencesSearchRespectsLocalSearchScopeFileBoundary() {
        val projectBase = project.basePath ?: error("project base path is null")
        val importedPath = Paths.get(projectBase, "Shared", "LocalScopeUtils.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(importedPath.parent.toString())
            val file = parent.findOrCreateChildData(this, importedPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Function LocalScopedTint(in vec3 color, out vec3 result) {
                    result = color;
                }
                """.trimIndent()
            )
        }

        val callerFile = myFixture.configureByText(
            "local_scope_refs_caller.dsm",
            """
            import "Shared/LocalScopeUtils.dsh";

            Shader Main {
                Graph {
                    vec3 c = vec3(1.0, 1.0, 1.0);
                    vec3 outA;
                    vec3 outB;
                    LocalScopedTint(c, outA);
                    LocalScopedTint(c, outB);
                }
            }
            """.trimIndent()
        )

        val importedVf = VfsUtil.findFile(importedPath, true)
        assertNotNull("Expected imported declaration file", importedVf)
        myFixture.configureFromExistingVirtualFile(importedVf!!)
        val importedPsi = PsiManager.getInstance(project).findFile(importedVf)
        assertNotNull("Expected imported declaration psi file", importedPsi)
        val importedPsiFile = importedPsi!!
        val declarationOffset = importedPsiFile.text.indexOf("Function LocalScopedTint") + "Function ".length
        val declarationElement = importedPsiFile.findElementAt(declarationOffset)
        assertNotNull("Expected imported declaration identifier", declarationElement)
        val declaration = declarationElement!!.parent as DreamShaderDeclaration

        val localImportedScopeRefs = ReferencesSearch.search(
            declaration,
            LocalSearchScope(importedPsiFile)
        ).findAll()
        assertEquals(0, localImportedScopeRefs.size)

        val localCallerScopeRefs = ReferencesSearch.search(
            declaration,
            LocalSearchScope(callerFile)
        ).findAll()
        assertEquals(2, localCallerScopeRefs.size)
        assertTrue(localCallerScopeRefs.all { it.element.text == "LocalScopedTint" })
        assertTrue(localCallerScopeRefs.all { it.element.containingFile.name == "local_scope_refs_caller.dsm" })
    }
}
