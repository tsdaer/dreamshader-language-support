package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderFileIconProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderGotoDeclarationHandler
import com.intellij.ide.FileIconProvider
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.LightVirtualFile

class DreamShader220CompatibilityTest : BasePlatformTestCase() {
    private val headerPath = "DShader/Packages/@typedreammoon/dreamshader-blender/Library/Noise.dsh"

    fun testMixedPackageHeaderQualifiedCallInDss() {
        val header = myFixture.addFileToProject(headerPath, """
            #pragma generate(Functions = "MF_{Name}")
            float OtherHelper(float p) { return p; }
            Namespace(Name = "BL") {
                Function(Generate = "MF_BL_TexNoise3D") TexNoise3D(in float3 co, out float Fac) {}
            }
        """.trimIndent())
        myFixture.addFileToProject("DShader/PackageEntry.dsh", "import \"@typedreammoon/dreamshader-blender/Library/Noise.dsh\";")
        val source = myFixture.addFileToProject("DShader/Material.dss", """
            #include "PackageEntry.dsh"
            export void M_Noise(inout material m) {
                float Fac;
                BL :: TexNoise3D(float3(0, 0, 0), Fac);
            }
        """.trimIndent())
        myFixture.configureFromExistingVirtualFile(source.virtualFile)
        val file = myFixture.file as DreamShaderPsiFile
        assertTrue(PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).isEmpty())
        val offset = file.text.indexOf("TexNoise3D")
        val use = file.findElementAt(offset)!!
        val target = DreamShaderGotoDeclarationHandler().getGotoDeclarationTargets(use, offset, myFixture.editor)?.single()
        assertEquals(header.virtualFile, target?.containingFile?.virtualFile)
        assertEquals(header.text.indexOf("TexNoise3D(in"), target?.textOffset)

        val signature = DreamShaderCallSignatureResolver.resolveSignatures("BL :: TexNoise3D", file).single()
        assertEquals(listOf("co", "Fac"), signature.parameterNames)
        assertEquals(listOf("float3", "float"), signature.parameterTypes)
        assertEquals(listOf("in", "out"), signature.parameterQualifiers)
        assertTrue(DreamShaderCallSignatureResolver.resolveSignatures("Missing::TexNoise3D", file).isEmpty())
        assertEquals("BL::TexNoise3D", DreamShaderSignatureHelpAnalyzer.findEnclosingCall(file.text, offset + "TexNoise3D(".length)?.functionName)
        assertEquals(listOf("co:", "Fac:"), DreamShaderInlayParameterHintsProvider().getParameterHints(use).map { it.text })

        val endOfPrefix = offset + "Tex".length
        assertTrue(suggest(file, endOfPrefix).any { it.label == "TexNoise3D" })
    }

    fun testCompletionOffersSharedPragmasAndSeveritySpecificCodes() {
        var file = myFixture.configureByText("Legacy.dsm", "#pragma <caret>\nShader(Name = \"M\") {}") as DreamShaderPsiFile
        var offset = myFixture.caretOffset
        val items = DreamShader2CompletionContexts.preprocessorSuggestions(file, file.findElementAt(offset - 1)!!, offset)
        assertEquals(setOf("nogeneration", "disable_warning", "disable_error"), items?.map { it.label }?.toSet())

        file = myFixture.configureByText("Material.dss", "#pragma disable_warning(DSH827<caret>)\nfloat F() { return 0; }") as DreamShaderPsiFile
        offset = myFixture.caretOffset
        val codes = suggest(file, offset).map { it.label }
        assertTrue(codes.contains("DSH8278"))
        assertFalse(codes.contains("DSH8277"))
        assertFalse(codes.contains("DSH8279"))

        file = myFixture.configureByText("Material.dss", "float F() { return 0; } // disable_error DSH626<caret>") as DreamShaderPsiFile
        assertTrue(suggest(file, myFixture.caretOffset).any { it.label == "DSH6265" })
    }

    fun testGenerateCompletionOnlyInPackageHeaders() {
        val text = "#pragma gen\nfloat F() { return 0; }"
        val packageFile = myFixture.addFileToProject(headerPath, text) as DreamShaderPsiFile
        val localFile = myFixture.addFileToProject("DShader/Noise.dsh", text) as DreamShaderPsiFile
        val offset = text.indexOf('\n')
        assertTrue(suggest(packageFile, offset).any { it.label == "generate" })
        assertFalse(suggest(localFile, offset).any { it.label == "generate" })
    }

    fun testFileIconExtensionLoadsAsFileIconProvider() {
        val provider = ExtensionPointName.create<FileIconProvider>("com.intellij.fileIconProvider").extensionList
            .filterIsInstance<DreamShaderFileIconProvider>().single()
        assertSame(DreamShaderIcons.FILE_DSS, provider.getIcon(LightVirtualFile("Material.dss"), 0, project))
        assertSame(DreamShaderIcons.FILE_DSI, provider.getIcon(LightVirtualFile("Instance.dsi"), 0, project))
        assertNull(provider.getIcon(LightVirtualFile("Legacy.dsm"), 0, project))
    }

    private fun suggest(file: DreamShaderPsiFile, offset: Int): List<DreamShaderCompletionItem> =
        DreamShader2CompletionSuggester.suggest(file, file.findElementAt(offset - 1)!!, offset, emptyList())
}
