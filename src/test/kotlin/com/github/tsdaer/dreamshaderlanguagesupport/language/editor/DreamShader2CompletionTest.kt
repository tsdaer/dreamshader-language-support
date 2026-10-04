package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText

/**
 * Milestone M12 C3: context-aware completion for the 2.0 dialect - named arguments from
 * the builtin catalog, Substrate builder members, `.dsi` parent parameters from the
 * Bridge symbol index, and `#pragma material(...)` keys/values.
 */
class DreamShader2CompletionTest : BasePlatformTestCase() {
    private fun catalogEntries(file: DreamShaderPsiFile): List<DreamShaderMaterialExpressionInfo> {
        val settings = file.project.getService(DreamShaderProjectSettings::class.java)
        return DreamShaderMaterialExpressionManifest.catalogEntries(
            project = file.project,
            explicitManifestPath = settings?.state?.materialExpressionManifestPath,
        )
    }

    private fun suggest(filePsi: com.intellij.psi.PsiFile, offset: Int): List<DreamShader2CompletionEntry> {
        val file = filePsi as DreamShaderPsiFile
        val leaf = file.findElementAt((offset - 1).coerceAtLeast(0))!!
        return DreamShader2CompletionSuggester.suggest(file, leaf, catalogEntries(file))
    }

    // ------------------------------------------------------------------
    // C3.1 named arguments
    // ------------------------------------------------------------------

    fun testNamedArgumentsSuggestCatalogInputs() {
        val file = myFixture.configureByText(
            "named_args.dss",
            "export void Work() {\n    UE.Panner(Coordinate = uv, \n}\n".replace("uv, \n", "uv, T"),
        )
        val offset = file.text.indexOf("T") + 1
        val entries = suggest(file, offset)
        assertTrue("Expected named-argument suggestions", entries.isNotEmpty())
        assertEquals("Time", entries.first().label)
        assertEquals("Time = ", entries.first().insertText)
    }

    fun testNamedArgumentsExcludeFilledNamesAndPositionalSlots() {
        val file = myFixture.configureByText(
            "named_args_filled.dss",
            "export void Work() {\n    UE.Panner(uv, Time = t, S\n}\n",
        )
        val offset = file.text.indexOf("S\n") + 1
        val entries = suggest(file, offset)
        val labels = entries.map { it.label }
        assertTrue("Speed should be suggested, got $labels", labels.contains("Speed"))
        assertFalse("Time is already named", labels.contains("Time"))
        assertFalse("Coordinate is consumed positionally", labels.contains("Coordinate"))
    }

    fun testNamedArgumentsStayEmptyForUnknownCalls() {
        val file = myFixture.configureByText(
            "named_args_unknown.dss",
            "export void Work() {\n    lerp(a, b, c)\n}\n",
        )
        val offset = file.text.indexOf("c)")
        val entries = suggest(file, offset + 1)
        assertTrue(entries.isEmpty())
    }

    // ------------------------------------------------------------------
    // C3.2 Substrate builder members
    // ------------------------------------------------------------------

    fun testSubstrateMembersSuggestPinsWithTypeInference() {
        val file = myFixture.configureByText(
            "substrate.dss",
            """
            export void Work(float3 albedo) {
                Substrate S = Substrate.Slab(BaseColor = albedo);
                S.Roughness = 0.4;
                S.M
            }
            """.trimIndent()
        )
        val offset = file.text.indexOf("S.M") + "S.M".length
        val entries = suggest(file, offset)
        val labels = entries.map { it.label }
        assertTrue("Metallic should be suggested, got $labels", labels.contains("Metallic"))
        assertFalse("Roughness was already written via a member assignment, got $labels", labels.contains("Roughness"))
        assertTrue("Constructor named arguments are not member writes, got $labels", labels.contains("BaseColor"))
    }

    fun testSubstrateMembersRequireSubstrateReceiver() {
        val file = myFixture.configureByText(
            "substrate_non_sub.dss",
            "export void Work() {\n    float3 V = float3(0.0, 0.0, 0.0);\n    V.x\n}\n",
        )
        val offset = file.text.indexOf("V.x") + "V.x".length
        val entries = suggest(file, offset)
        assertTrue(entries.isEmpty())
    }

    // ------------------------------------------------------------------
    // C3.3 .dsi parent parameters
    // ------------------------------------------------------------------

    fun testInstanceParametersSuggestParentSchema() {
        val virtualFile = writeDsiFixture()
        val repository = project.getService(
            com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexRepository::class.java,
        )
        val snapshot = repository.indexForSource(virtualFile)
        val data = snapshot.data
        assertTrue("index fixture must be schema-compatible", data != null && data.instance?.schema?.valid == true)

        val psiManager = com.intellij.psi.PsiManager.getInstance(project)
        val file = psiManager.findFile(virtualFile) as DreamShaderPsiFile
        // The declaration-name slot of the `uniform float Gain;` override.
        val offset = file.text.indexOf("Gain")
        val leaf = file.findElementAt(offset)!!
        val entries = DreamShader2CompletionSuggester.instanceParameterCompletions(file, leaf)
        val labels = entries.orEmpty().map { it.label }
        assertTrue("Expected parent-parameter suggestions, got $labels", "Roughness" in labels && "BaseColor" in labels)
        assertFalse("The already-declared name is not suggested, got $labels", "Gain" in labels)
    }

    fun testInstanceParametersDegradeWithoutSchema() {
        val file = myFixture.configureByText("M_Lone.dsi", "uniform float \n")
        val offset = file.text.indexOf("float ") + "float ".length
        val leaf = file.findElementAt((offset - 1).coerceAtLeast(0))!!
        val entries = DreamShader2CompletionSuggester.instanceParameterCompletions(file as DreamShaderPsiFile, leaf)
        assertTrue("No suggestions without a schema", entries.isNullOrEmpty())
    }

    // ------------------------------------------------------------------
    // C3.4 #pragma material keys and values
    // ------------------------------------------------------------------

    fun testPragmaMaterialSuggestsKeys() {
        val file = myFixture.configureByText(
            "pragma.dss",
            "#pragma material( \nuniform float Gain;\n",
        )
        val offset = file.text.indexOf("( ")
        val entries = suggest(file, offset + 2)
        assertTrue("Expected pragma key suggestions", entries.isNotEmpty())
        assertTrue(entries.all { it.typeText == "material setting" })
    }

    fun testPragmaMaterialSuggestsValuesForKnownKey() {
        val file = myFixture.configureByText(
            "pragma_value.dss",
            "#pragma material(BlendMode = O\nuniform float Gain;\n",
        )
        val offset = file.text.indexOf("O\n")
        val entries = suggest(file, offset + 1)
        assertTrue("Expected value suggestions for BlendMode", entries.isNotEmpty())
    }

    fun testPragmaNonMaterialIsIgnored() {
        val file = myFixture.configureByText(
            "pragma_instance.dsi",
            "#pragma instance(Parent = \"M_Skin\")\nuniform float Gain;\n",
        )
        val offset = file.text.indexOf("(Parent") + 1
        val leaf = file.findElementAt(offset - 1)!!
        val entries = DreamShader2CompletionSuggester.pragmaMaterialCompletions(file as DreamShaderPsiFile, leaf)
        assertNull(entries)
    }

    // ------------------------------------------------------------------

    /** Creates a `.dsi` source plus a schema-bearing symbol index under the Bridge layout. */
    private fun writeDsiFixture(): com.intellij.openapi.vfs.VirtualFile {
        val base = Paths.get(project.basePath!!)
        Files.createDirectories(base)
        base.resolve("Demo.uproject").writeText("{}")
        val source = base.resolve("DShader/Materials/M_Inst.dsi")
        Files.createDirectories(source.parent)
        source.writeText("#pragma instance(Parent = \"M_Parent\")\nuniform float Gain;\n")
        val index = base.resolve("Saved/DreamShader/Index/Project/DShader/Materials/M_Inst.dsi.index.json")
        Files.createDirectories(index.parent)
        index.writeText(
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "M_Inst.dsi",
              "declarations": [],
              "references": [],
              "includes": [],
              "parameters": [],
              "instance": {
                "parent": "Path(Game/Materials/M_Parent)",
                "overrides": [],
                "schema": {
                  "valid": true,
                  "origin": "asset",
                  "parameters": [
                    { "name": "Gain", "kind": "global", "type": "float1", "group": "Tuning" },
                    { "name": "Roughness", "kind": "global", "type": "float1", "group": "Surface" },
                    { "name": "BaseColor", "kind": "global", "type": "float3", "group": "Surface" }
                  ]
                }
              }
            }
            """.trimIndent()
        )
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(source.toString().replace('\\', '/'))!!
    }
}
