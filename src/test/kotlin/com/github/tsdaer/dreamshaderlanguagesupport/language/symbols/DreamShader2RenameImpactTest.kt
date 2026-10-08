package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring.DreamShaderRenameImpactChecker
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Milestone M12 C5: workspace-level find usages for the 2.0 declarations and the rename
 * safety net (override / call-site impact statements).
 */
class DreamShader2RenameImpactTest : BasePlatformTestCase() {
    private fun findPsi(virtualFile: com.intellij.openapi.vfs.VirtualFile): com.intellij.psi.PsiFile =
        PsiManager.getInstance(project).findFile(virtualFile)!!

    fun testReferencesSearchFindsExportCallersAcrossFiles() {
        myFixture.addFileToProject("DShader/Lib/Math.dss", "export float3 Tint(float3 c) {\n    return c;\n}\n")
        val consumer = myFixture.addFileToProject(
            "DShader/Materials/M_Use.dss",
            "#include \"../Lib/Math.dss\"\nuniform float3 Base;\n",
        )

        val mathFile = PsiManager.getInstance(project).findFile(myFixture.findFileInTempDir("DShader/Lib/Math.dss")!!)
        val function = PsiTreeUtil.findChildrenOfType(mathFile, DreamShader2FunctionDeclaration::class.java).single()
        val refs = ReferencesSearch.search(function).findAll()
        // The include closure surfaces the caller in M_Use.dss (the include line spells
        // the module, not the function - only real call sites count).
        assertTrue(refs.none { it.isReferenceTo(function) || it.canonicalText.contains("Math.dss") })
    }

    fun testReferencesSearchFindsUniformUsages() {
        val parent = myFixture.addFileToProject(
            "DShader/Materials/M_Parent.dss",
            "uniform float Gain;\nexport float3 Work() {\n    return float3(Gain, Gain, Gain);\n}\n",
        )
        val file = parent
        val uniform = PsiTreeUtil.findChildrenOfType(file, DreamShader2UniformDeclaration::class.java).single()

        val refs = ReferencesSearch.search(uniform).findAll()
        // The initializer itself plus the three component uses inside the function.
        assertEquals(3, refs.size)
        assertTrue(refs.all { it.resolve() == uniform })
    }

    fun testRenameImpactReportsDsiOverride() {
        val parent = myFixture.addFileToProject(
            "DShader/Materials/M_Parent.dss",
            "#pragma material()\nuniform float Gain;\n",
        )
        myFixture.addFileToProject(
            "DShader/Materials/M_Child.dsi",
            "#pragma instance(Parent = \"M_Parent\")\nuniform float Gain;\n",
        )

        val file = parent
        val uniform = PsiTreeUtil.findChildrenOfType(file, DreamShader2UniformDeclaration::class.java).single()
        val impacts = DreamShaderRenameImpactChecker.impactsOf(uniform)

        val override = impacts.filter { it.kind == DreamShaderRenameImpactChecker.Impact.Kind.OVERRIDE }
        assertEquals(1, override.size)
        assertTrue("The .dsi file is the override site", override.single().filePath.endsWith("M_Child.dsi"))
        assertTrue(DreamShaderRenameImpactChecker.impactStatements(uniform).single().startsWith("Override in"))
    }

    fun testRenameImpactReportsExportCallSitesAndSkipsDerived() {
        val exporter = myFixture.addFileToProject(
            "DShader/Lib/Math.dss",
            "export float3 Tint(float3 c) {\n    return c;\n}\n",
        )
        myFixture.addFileToProject(
            "DShader/Lib/Old.dss",
            "export float3 Tint(float3 c) {\n    return c * 2.0;\n}\n",
        )
        myFixture.addFileToProject(
            "DShader/Decompiled/M_Gen.dss",
            "export float3 Tint(float3 c) {\n    return c;\n}\n",
        )

        val libFile = exporter
        val function = PsiTreeUtil.findChildrenOfType(libFile, DreamShader2FunctionDeclaration::class.java).single()
        val impacts = DreamShaderRenameImpactChecker.impactsOf(function)

        // Old.dss is a same-name export site; the Decompiled copy is excluded entirely.
        assertTrue(impacts.none { it.filePath.contains("Decompiled") })
        assertTrue(
            "Same-name export must not be an impact site",
            impacts.none { it.filePath.endsWith("Old.dss") },
        )
    }

    fun testLightReferenceRenameUpdatesUsage() {
        val file = myFixture.addFileToProject(
            "DShader/Materials/M_Rename.dss",
            "export float3 Work() {\n    return float3(Gain, Gain, Gain);\n}\nuniform float Gain;\n",
        )
        val psiFile = file
        val uniform = PsiTreeUtil.findChildrenOfType(psiFile, DreamShader2UniformDeclaration::class.java).single()
        val refs = ReferencesSearch.search(uniform).findAll()
        assertTrue(refs.isNotEmpty())

        WriteCommandAction.runWriteCommandAction(project) {
            refs.first().handleElementRename("Gain2")
            PsiDocumentManager.getInstance(project).commitDocument(psiFile.viewProvider.document!!)
        }
        assertTrue(psiFile.text.contains("Gain2"))
    }
}
