package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderImportClosureResolverTest : BasePlatformTestCase() {
    fun testResolveDirectImportsReturnsImmediateImportedFilesOnly() {
        val projectBase = project.basePath ?: error("project base path is null")
        val aPath = Paths.get(projectBase, "Shared", "A.dsh")
        val bPath = Paths.get(projectBase, "Shared", "B.dsh")
        val cPath = Paths.get(projectBase, "Shared", "C.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(aPath.parent.toString())
            VfsUtil.saveText(parent.findOrCreateChildData(this, aPath.fileName.toString()), "Function A { }")
            VfsUtil.saveText(parent.findOrCreateChildData(this, bPath.fileName.toString()), "import \"C.dsh\";\nFunction B { }")
            VfsUtil.saveText(parent.findOrCreateChildData(this, cPath.fileName.toString()), "Function C { }")
        }

        val seed = myFixture.configureByText(
            "seed_direct_imports.dsm",
            """
            import "Shared/A.dsh";
            import "Shared/B.dsh";
            Shader Main { Graph { } }
            """.trimIndent()
        )

        val direct = DreamShaderImportClosureResolver.resolveDirectImports(seed)
        val names = direct.mapNotNull { it.virtualFile?.name }.toSet()
        assertEquals(setOf("A.dsh", "B.dsh"), names)
        assertFalse(names.contains("C.dsh"))
    }

    fun testResolveImportClosureIncludesRecursiveImportsInBfsOrder() {
        val projectBase = project.basePath ?: error("project base path is null")
        val apiPath = Paths.get(projectBase, "Shared", "Api.dsh")
        val implPath = Paths.get(projectBase, "Shared", "Impl.dsh")
        val utilPath = Paths.get(projectBase, "Shared", "Util.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(apiPath.parent.toString())
            VfsUtil.saveText(parent.findOrCreateChildData(this, apiPath.fileName.toString()), "import \"Impl.dsh\";\nFunction Api { }")
            VfsUtil.saveText(parent.findOrCreateChildData(this, implPath.fileName.toString()), "import \"Util.dsh\";\nFunction Impl { }")
            VfsUtil.saveText(parent.findOrCreateChildData(this, utilPath.fileName.toString()), "Function Util { }")
        }

        val seed = myFixture.configureByText(
            "seed_recursive_imports.dsm",
            """
            import "Shared/Api.dsh";
            Shader Main { Graph { } }
            """.trimIndent()
        )

        val closure = DreamShaderImportClosureResolver.resolveImportClosure(seed)
        val names = closure.mapNotNull { it.virtualFile?.name }
        assertTrue(names.size >= 4)
        assertEquals("seed_recursive_imports.dsm", names.first())
        assertTrue(names.contains("Api.dsh"))
        assertTrue(names.contains("Impl.dsh"))
        assertTrue(names.contains("Util.dsh"))
        assertTrue(names.indexOf("Api.dsh") < names.indexOf("Impl.dsh"))
        assertTrue(names.indexOf("Impl.dsh") < names.indexOf("Util.dsh"))
    }

    fun testResolveImportClosureHandlesImportCycleWithoutDuplicates() {
        val projectBase = project.basePath ?: error("project base path is null")
        val aPath = Paths.get(projectBase, "Shared", "CycleA.dsh")
        val bPath = Paths.get(projectBase, "Shared", "CycleB.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(aPath.parent.toString())
            VfsUtil.saveText(parent.findOrCreateChildData(this, aPath.fileName.toString()), "import \"CycleB.dsh\";\nFunction A { }")
            VfsUtil.saveText(parent.findOrCreateChildData(this, bPath.fileName.toString()), "import \"CycleA.dsh\";\nFunction B { }")
        }

        val seed = myFixture.configureByText(
            "seed_cycle_imports.dsm",
            """
            import "Shared/CycleA.dsh";
            Shader Main { Graph { } }
            """.trimIndent()
        )

        val closure = DreamShaderImportClosureResolver.resolveImportClosure(seed)
        val names = closure.mapNotNull { it.virtualFile?.name }
        assertEquals(3, names.size)
        assertEquals(setOf("seed_cycle_imports.dsm", "CycleA.dsh", "CycleB.dsh"), names.toSet())
    }

    fun testResolveDirectImportsCacheInvalidatesAfterImportEdit() {
        val projectBase = project.basePath ?: error("project base path is null")
        val aPath = Paths.get(projectBase, "Shared", "CachedA.dsh")
        val bPath = Paths.get(projectBase, "Shared", "CachedB.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(aPath.parent.toString())
            VfsUtil.saveText(parent.findOrCreateChildData(this, aPath.fileName.toString()), "Function CachedA { }")
            VfsUtil.saveText(parent.findOrCreateChildData(this, bPath.fileName.toString()), "Function CachedB { }")
        }

        val seed = myFixture.configureByText(
            "seed_cached_imports.dsm",
            """
            import "Shared/CachedA.dsh";
            Shader Main { Graph { } }
            """.trimIndent()
        )

        val initialNames = DreamShaderImportClosureResolver.resolveDirectImports(seed)
            .mapNotNull { it.virtualFile?.name }
            .toSet()
        assertEquals(setOf("CachedA.dsh"), initialNames)

        val importOffset = seed.text.indexOf("CachedA")
        WriteCommandAction.runWriteCommandAction(project) {
            myFixture.editor.document.replaceString(importOffset, importOffset + "CachedA".length, "CachedB")
            PsiDocumentManager.getInstance(project).commitDocument(myFixture.editor.document)
        }

        val updatedNames = DreamShaderImportClosureResolver.resolveDirectImports(seed)
            .mapNotNull { it.virtualFile?.name }
            .toSet()
        assertEquals(setOf("CachedB.dsh"), updatedNames)
    }

    fun testIsImportStringLiteralTokenRecognizesOnlyImportStrings() {
        val file = myFixture.configureByText(
            "import_literal_predicate.dsm",
            """
            import "Shared/RealImport.dsh";

            Shader Main {
                Settings = {
                    Description = "Shared/RealImport.dsh";
                }
            }
            """.trimIndent()
        )

        val firstStringOffset = file.text.indexOf("\"Shared/RealImport.dsh\"") + 1
        val secondStringOffset = file.text.lastIndexOf("\"Shared/RealImport.dsh\"") + 1
        val importString = file.findElementAt(firstStringOffset)
        val nonImportString = file.findElementAt(secondStringOffset)
        assertNotNull(importString)
        assertNotNull(nonImportString)

        assertTrue(DreamShaderImportClosureResolver.isImportStringLiteralToken(importString!!))
        assertFalse(DreamShaderImportClosureResolver.isImportStringLiteralToken(nonImportString!!))
    }
}
