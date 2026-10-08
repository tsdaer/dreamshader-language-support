package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialect
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.io.path.writeText

/**
 * M13 completion intelligence: receiver-typed member completion for the 2.0 dialect
 * (swizzles, struct fields, texture/material members, catalog namespaces), slot-checked
 * named arguments, `#include` path candidates, bare-identifier completion, pragma value
 * quoting, and the C8 dialect gating that keeps 1.x suggestions out of 2.0 files.
 */
class DreamShader2CompletionTest : BasePlatformTestCase() {
    private fun catalogEntries(file: DreamShaderPsiFile): List<DreamShaderMaterialExpressionInfo> {
        val settings = file.project.getService(DreamShaderProjectSettings::class.java)
        return DreamShaderMaterialExpressionManifest.catalogEntries(
            project = file.project,
            explicitManifestPath = settings?.state?.materialExpressionManifestPath,
        )
    }

    private fun suggest(filePsi: com.intellij.psi.PsiFile, offset: Int): List<DreamShaderCompletionItem> {
        val file = filePsi as DreamShaderPsiFile
        val leaf = file.findElementAt((offset - 1).coerceAtLeast(0))!!
        return DreamShader2CompletionSuggester.suggest(file, leaf, offset, catalogEntries(file))
    }

    // ------------------------------------------------------------------
    // C8 dialect gating
    // ------------------------------------------------------------------

    fun testDialectGatingSplitsTheContributors() {
        val dss = myFixture.configureByText("gating.dss", "uniform float Gain;\n") as DreamShaderPsiFile
        val dsm = myFixture.configureByText("gating.dsm", "Shader(\"M\") {\n}\n") as DreamShaderPsiFile
        val dshV2 = myFixture.configureByText("gating_v2.dsh", "#pragma material(MaterialDomain = \"Surface\")\n") as DreamShaderPsiFile
        val dshMixed = myFixture.configureByText(
            "gating_mixed.dsh",
            "Shader(\"M\") {\n}\n#pragma material(MaterialDomain = \"Surface\")\n",
        ) as DreamShaderPsiFile

        assertEquals(DreamShaderDialect.V2, DreamShaderCompletionDialects.dialectOf(dss))
        assertEquals(DreamShaderDialect.V1, DreamShaderCompletionDialects.dialectOf(dsm))
        assertEquals(DreamShaderDialect.V2, DreamShaderCompletionDialects.dialectOf(dshV2))
        assertEquals(DreamShaderDialect.HEADER_MIXED, DreamShaderCompletionDialects.dialectOf(dshMixed))

        assertFalse("1.x contributor must stay silent in .dss", DreamShaderCompletionDialects.servesV1(dss))
        assertTrue(DreamShaderCompletionDialects.servesV2(dss))
        assertTrue("1.x contributor serves .dsm", DreamShaderCompletionDialects.servesV1(dsm))
        assertFalse(DreamShaderCompletionDialects.servesV2(dsm))
        assertTrue("pure-V2 .dsh routes to the 2.0 contributor", DreamShaderCompletionDialects.servesV2(dshV2))
        assertFalse(DreamShaderCompletionDialects.servesV1(dshV2))
        assertTrue("mixed .dsh keeps 1.x service for its legacy blocks", DreamShaderCompletionDialects.servesV1(dshMixed))
        assertFalse(DreamShaderCompletionDialects.servesV2(dshMixed))
    }

    fun testMixedDshRegionsSplitCompletionByDialect() {
        val mixed = myFixture.configureByText(
            "regions_mixed.dsh",
            """
            Shader("M") {
                Graph = { }
            }
            #pragma material(MaterialDomain = "Surface")
            uniform float Gain;
            export void Work() {
                float3 v = float3(0.0, 0.0, 0.0);
                v.
            }
            """.trimIndent(),
        ) as DreamShaderPsiFile
        val legacyBody = mixed.text.indexOf("Graph")
        val topLevelGap = mixed.text.indexOf("\n}") + 2
        val pragmaLine = mixed.text.indexOf("MaterialDomain")
        val uniformLine = mixed.text.indexOf("Gain")
        val v2Member = mixed.text.indexOf("v.") + 2

        assertEquals(
            "legacy block body is a 1.x region",
            DreamShaderCompletionDialects.Region.V1,
            DreamShaderCompletionDialects.regionAt(mixed, legacyBody),
        )
        assertEquals(
            "the gap between top-level nodes hosts both dialects",
            DreamShaderCompletionDialects.Region.BOTH,
            DreamShaderCompletionDialects.regionAt(mixed, topLevelGap),
        )
        assertEquals(
            "a pragma directive is a V2 region",
            DreamShaderCompletionDialects.Region.V2,
            DreamShaderCompletionDialects.regionAt(mixed, pragmaLine),
        )
        assertEquals("a uniform declaration is a V2 region",
            DreamShaderCompletionDialects.Region.V2,
            DreamShaderCompletionDialects.regionAt(mixed, uniformLine),
        )
        assertEquals(
            "a V2 function body is a V2 region",
            DreamShaderCompletionDialects.Region.V2,
            DreamShaderCompletionDialects.regionAt(mixed, v2Member),
        )

        assertFalse(DreamShaderCompletionDialects.servesV1At(mixed, uniformLine))
        assertTrue(DreamShaderCompletionDialects.servesV2At(mixed, uniformLine))
        assertTrue(DreamShaderCompletionDialects.servesV1At(mixed, legacyBody))
        assertFalse(DreamShaderCompletionDialects.servesV2At(mixed, legacyBody))
        assertTrue(DreamShaderCompletionDialects.servesV1At(mixed, topLevelGap))
        assertTrue(DreamShaderCompletionDialects.servesV2At(mixed, topLevelGap))

        // The 2.0 suggester answers member contexts inside the V2 regions of a mixed file.
        val entries = suggest(mixed, v2Member)
        val labels = entries.map { it.label }
        assertTrue("swizzles expected inside the mixed file's V2 region, got $labels", labels.contains("xyz"))

        // Pure files keep their file-level answer at every offset.
        val dss = myFixture.configureByText("regions_pure.dss", "uniform float Gain;\n") as DreamShaderPsiFile
        val dsm = myFixture.configureByText("regions_pure.dsm", "Shader(\"M\") {\n}\n") as DreamShaderPsiFile
        assertEquals(DreamShaderCompletionDialects.Region.V2, DreamShaderCompletionDialects.regionAt(dss, 0))
        assertEquals(DreamShaderCompletionDialects.Region.V2, DreamShaderCompletionDialects.regionAt(dss, dss.text.length))
        assertEquals(DreamShaderCompletionDialects.Region.V1, DreamShaderCompletionDialects.regionAt(dsm, 0))
        assertEquals(DreamShaderCompletionDialects.Region.V1, DreamShaderCompletionDialects.regionAt(dsm, dsm.text.length))
    }

    // ------------------------------------------------------------------
    // C9 receiver-typed member completion
    // ------------------------------------------------------------------

    fun testVectorReceiverSuggestsSwizzles() {
        val file = myFixture.configureByText(
            "swizzle.dss",
            "export void Work() {\n    float3 v = float3(0.0, 0.0, 0.0);\n    v.\n}\n",
        )
        val offset = file.text.indexOf("v.") + 2
        val entries = suggest(file, offset)
        val labels = entries.map { it.label }
        assertTrue("single components expected, got $labels", labels.containsAll(listOf("x", "y", "z", "r", "g", "b")))
        assertTrue(labels.contains("xy"))
        assertTrue(labels.contains("xyz"))
        assertTrue(labels.contains("rgb"))
        assertFalse("float3 has no fourth channel", labels.contains("w"))
        val xyz = entries.first { it.label == "xyz" }
        assertEquals(" : float3", xyz.tailText)
        assertEquals(DreamShaderCompletionPriorities.TYPED_MEMBER, xyz.priority)
    }

    fun testFloat4ReceiverSuggestsFullSwizzleSets() {
        val file = myFixture.configureByText(
            "swizzle4.dss",
            "export void Work() {\n    float4 v = float4(0.0, 0.0, 0.0, 0.0);\n    v.\n}\n",
        )
        val offset = file.text.indexOf("v.") + 2
        val labels = suggest(file, offset).map { it.label }
        assertTrue(labels.contains("xyzw"))
        assertTrue(labels.contains("rgba"))
        assertTrue("splatting swizzles stay available", labels.contains("xxx"))
    }

    fun testMatrixReceiverSuggestsCells() {
        val file = myFixture.configureByText(
            "matrix.dss",
            "export void Work() {\n    float4x4 m = float4x4(1.0);\n    m.\n}\n",
        )
        val offset = file.text.indexOf("m.") + 2
        val labels = suggest(file, offset).map { it.label }
        assertTrue(labels.contains("_m00"))
        assertTrue(labels.contains("_m33"))
        assertEquals(16, labels.size)
    }

    fun testScalarReceiverSuggestsSingleComponents() {
        val file = myFixture.configureByText(
            "scalar.dss",
            "export void Work() {\n    float s = 1.0;\n    s.\n}\n",
        )
        val offset = file.text.indexOf("s.") + 2
        val labels = suggest(file, offset).map { it.label }
        assertTrue(labels.contains("x"))
        assertTrue(labels.contains("r"))
    }

    fun testStructReceiverSuggestsFields() {
        val file = myFixture.configureByText(
            "struct_member.dss",
            """
            struct SurfaceInput
            {
                float3 Albedo;
                float Roughness;
            };
            export void Work() {
                SurfaceInput input;
                input.
            }
            """.trimIndent()
        )
        val offset = file.text.indexOf("input.") + 6
        val entries = suggest(file, offset)
        val labels = entries.map { it.label }
        assertTrue("struct fields expected, got $labels", labels.containsAll(listOf("Albedo", "Roughness")))
        val albedo = entries.first { it.label == "Albedo" }
        assertEquals(" : float3", albedo.tailText)
    }

    fun testTextureReceiverSuggestsSample() {
        val file = myFixture.configureByText(
            "texture_member.dss",
            "uniform Texture2D Tex;\nexport void Work() {\n    Tex.\n}\n",
        )
        val offset = file.text.indexOf("Tex.") + 4
        val entries = suggest(file, offset)
        val labels = entries.map { it.label }
        assertTrue("texture members expected, got $labels", labels.contains("sample"))
    }

    fun testMaterialReceiverSuggestsAttributes() {
        val file = myFixture.configureByText(
            "material_member.dss",
            "export void Work() {\n    material M;\n    M.\n}\n",
        )
        val offset = file.text.indexOf("M.") + 2
        val entries = suggest(file, offset)
        val labels = entries.map { it.label }
        assertTrue("material attributes expected, got $labels", labels.contains("FrontMaterial"))
        assertTrue(labels.contains("BaseColor"))
    }

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

    fun testVectorReceiverNoLongerDropsMemberSuggestions() {
        val file = myFixture.configureByText(
            "substrate_non_sub.dss",
            "export void Work() {\n    float3 V = float3(0.0, 0.0, 0.0);\n    V.x\n}\n",
        )
        val offset = file.text.indexOf("V.x") + "V.x".length
        val labels = suggest(file, offset).map { it.label }
        assertTrue("swizzles come from the receiver type now", labels.contains("x"))
        assertTrue(labels.contains("xyz"))
    }

    fun testNamespaceReceiverSuggestsCatalogMembers() {
        val file = myFixture.configureByText(
            "namespace_member.dss",
            "export void Work() {\n    UE.\n}\n",
        )
        val offset = file.text.indexOf("UE.") + 3
        val labels = suggest(file, offset).map { it.label }
        assertTrue("UE builtins expected, got $labels", labels.contains("TexCoord"))
    }

    fun testSubstrateNamespaceSuggestsFactories() {
        val file = myFixture.configureByText(
            "substrate_namespace.dss",
            "export void Work() {\n    Substrate S = Substrate.Slab(BaseColor = float3(1.0, 1.0, 1.0));\n    S = Substrate.\n}\n",
        )
        val offset = file.text.lastIndexOf("Substrate.") + 10
        val labels = suggest(file, offset).map { it.label }
        assertTrue("Substrate.Slab factory expected, got $labels", labels.contains("Slab"))
    }

    fun testUnknownReceiverStaysQuiet() {
        val file = myFixture.configureByText(
            "degraded.dss",
            "export void Work() {\n    Q.\n}\n",
        )
        val offset = file.text.indexOf("Q.") + 2
        val entries = suggest(file, offset)
        val labels = entries.map { it.label }
        assertTrue("Unknown receivers must not insert unrelated members: $labels", labels.isEmpty())
    }

    // ------------------------------------------------------------------
    // C11 named arguments, only at the parameter-name slot
    // ------------------------------------------------------------------

    fun testNamedArgumentsSuggestCatalogInputs() {
        val file = myFixture.configureByText(
            "named_args.dss",
            "export void Work() {\n    UE.Panner(Coordinate = uv, T\n}\n",
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

    fun testNamedArgumentsStayAwayFromValuePositions() {
        val file = myFixture.configureByText(
            "named_args_value.dss",
            "export void Work() {\n    UE.Panner(Coordinate = uv\n}\n",
        )
        val offset = file.text.indexOf("uv") + 2
        val labels = suggest(file, offset).map { it.label }
        assertFalse("value positions get identifiers, not pin names, got $labels", labels.contains("Time"))
        assertFalse(labels.contains("Speed"))
    }

    fun testNamedArgumentsStayEmptyForUnknownCalls() {
        val file = myFixture.configureByText(
            "named_args_unknown.dss",
            "export void Work() {\n    lerp(a, b, c)\n}\n",
        )
        val offset = file.text.indexOf("c)")
        val entries = suggest(file, offset + 1)
        assertTrue(
            "unknown call at a name slot yields no pins, got ${entries.map { it.label }}",
            entries.none { it.typeText == "named argument" },
        )
    }

    // ------------------------------------------------------------------
    // C11.2 enum values at named-argument value slots
    // ------------------------------------------------------------------

    fun testEnumValuesSuggestAtValueSlot() {
        val file = myFixture.configureByText(
            "enum_values.dss",
            "export void Work() {\n    UE.TransformVector(NormalTS, Source = )\n}\n",
        )
        val offset = file.text.indexOf("Source = ") + "Source = ".length
        val entries = suggest(file, offset)
        val labels = entries.map { it.label }
        assertEquals(
            "vector transform bases, got $labels",
            listOf("Tangent", "Local", "World", "View", "Camera", "Instance"),
            labels,
        )
        assertTrue(
            "a bare slot inserts the canonical enum token, got ${entries.firstOrNull()?.insertText}",
            entries.all { it.insertText == it.label },
        )
        assertTrue(entries.all { it.typeText == "enum value" })
    }

    fun testEnumValuesDifferBetweenVectorAndPositionBases() {
        val file = myFixture.configureByText(
            "enum_values_position.dss",
            "export void Work() {\n    UE.TransformPosition(WorldPos, Source = )\n}\n",
        )
        val offset = file.text.indexOf("Source = ") + "Source = ".length
        val labels = suggest(file, offset).map { it.label }
        assertTrue("position bases include PeriodicWorld, got $labels", labels.contains("PeriodicWorld"))
        assertFalse("Tangent is not a position basis", labels.contains("Tangent"))
    }

    fun testEnumValuesInsideOpenQuoteInsertBare() {
        val file = myFixture.configureByText(
            "enum_values_quoted.dss",
            "export void Work() {\n    UE.TransformVector(NormalTS, Source = \"Wo)\n}\n",
        )
        val offset = file.text.indexOf("Source = \"Wo") + "Source = \"Wo".length
        val entries = suggest(file, offset)
        assertEquals(
            "the full basis set rides the popup; the platform filters by the typed prefix, got ${entries.map { it.label }}",
            listOf("Tangent", "Local", "World", "View", "Camera", "Instance"),
            entries.map { it.label },
        )
        assertTrue(
            "an open quote takes a bare value (D9), got ${entries.firstOrNull()?.insertText}",
            entries.all { it.insertText == it.label },
        )
    }

    fun testNonEnumParameterValueFallsThroughToIdentifiers() {
        val file = myFixture.configureByText(
            "enum_values_nonenum.dss",
            "export void Work() {\n    UE.TransformPosition(WorldPos, PeriodicWorldTileSize = )\n}\n",
        )
        val offset = file.text.indexOf("PeriodicWorldTileSize = ") + "PeriodicWorldTileSize = ".length
        val entries = suggest(file, offset)
        assertTrue(
            "non-enum parameters get no enum values, got ${entries.map { it.label }}",
            entries.none { it.typeText == "enum value" },
        )
        assertTrue(
            "the value slot gets no pin names either",
            entries.none { it.typeText == "named argument" },
        )
        assertTrue("bare identifiers still serve the slot", entries.isNotEmpty())
    }

    fun testCompletedEnumValueStopsEnumSuggestions() {
        val file = myFixture.configureByText(
            "enum_values_done.dss",
            "export void Work() {\n    UE.TransformVector(NormalTS, Source = \"World\")\n}\n",
        )
        val offset = file.text.indexOf("\")") + 1
        val entries = suggest(file, offset)
        assertTrue(
            "a closed value slot yields identifiers, not enum values, got ${entries.map { it.label }}",
            entries.none { it.typeText == "enum value" },
        )
    }

    // ------------------------------------------------------------------
    // C13 bare-identifier completion
    // ------------------------------------------------------------------

    fun testBareIdentifiersSuggestLocalsUniformsAndFunctions() {
        val file = myFixture.configureByText(
            "bare.dss",
            """
            uniform float Gain;
            struct Params { float Strength; };
            export float Boost(float amount) { return amount; }
            export void Work() {
                float3 offset = float3(0.0, 0.0, 0.0);
                Gai
            }
            """.trimIndent()
        )
        val offset = file.text.indexOf("Gai") + 3
        val labels = suggest(file, offset).map { it.label }
        assertTrue("uniform suggested, got $labels", labels.contains("Gain"))
        assertTrue("file function suggested", labels.contains("Boost"))
        assertTrue("struct type suggested", labels.contains("Params"))
        assertTrue("HLSL intrinsics stay available", labels.contains("lerp"))
    }

    // ------------------------------------------------------------------
    // C14 #include path candidates
    // ------------------------------------------------------------------

    fun testIncludePathsSuggestProjectSources() {
        myFixture.addFileToProject("DShader/Materials/Lib.dss", "export float One() { return 1.0; }\n")
        val file = myFixture.configureByText(
            "include.dss",
            "#pragma material(MaterialDomain = \"Surface\")\n#include \"",
        )
        val offset = file.text.length
        val entries = DreamShader2CompletionSuggester.includePathCompletions(file as DreamShaderPsiFile, offset)
        val labels = entries.orEmpty().map { it.label }
        assertTrue("expected project include candidates, got $labels", labels.any { it.endsWith("Lib.dss") })
    }

    // ------------------------------------------------------------------
    // D9 quote-aware pragma value insertion
    // ------------------------------------------------------------------

    fun testPragmaValueInsertsBareInsideOpenQuote() {
        myFixture.configureByText("pragma_quote.dss", "#pragma material(MaterialDomain = \"<caret>\")\n")
        myFixture.completeBasic()
        selectLookupItem("Surface")
        assertEquals(
            "no double-quote stacking inside a pre-typed pair",
            "#pragma material(MaterialDomain = \"Surface\")\n",
            myFixture.editor.document.text,
        )
    }

    fun testPragmaValueInsertsQuotedOnBareSlot() {
        myFixture.configureByText("pragma_bare.dss", "#pragma material(MaterialDomain = <caret>)\n")
        myFixture.completeBasic()
        selectLookupItem("Surface")
        assertEquals(
            "a bare value slot receives the closing quote with the value",
            "#pragma material(MaterialDomain = \"Surface\")\n",
            myFixture.editor.document.text,
        )
    }

    private fun selectLookupItem(lookupString: String) {
        val lookup = myFixture.lookup
        assertNotNull("popup must open", lookup)
        val impl = lookup as com.intellij.codeInsight.lookup.impl.LookupImpl
        impl.currentItem = impl.items.firstOrNull { it.lookupString == lookupString }
            ?: error("lookup item '$lookupString' not in ${impl.items.map { it.lookupString }}")
        myFixture.finishLookup(com.intellij.codeInsight.lookup.Lookup.NORMAL_SELECT_CHAR)
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
    // C3.4 / D9 #pragma material keys and quote-aware values
    // ------------------------------------------------------------------

    fun testPragmaMaterialSuggestsKeys() {
        val file = myFixture.configureByText(
            "pragma.dss",
            "#pragma material( \nuniform float Gain;\n",
        )
        val offset = file.text.indexOf("( ") + 2
        val entries = suggest(file, offset)
        assertTrue("Expected pragma key suggestions", entries.isNotEmpty())
        assertTrue(entries.all { it.typeText == "material setting" })
    }

    fun testPragmaMaterialSuggestsValuesForKnownKey() {
        val file = myFixture.configureByText(
            "pragma_value.dss",
            "#pragma material(BlendMode = O\nuniform float Gain;\n",
        )
        val offset = file.text.indexOf("O\n") + 1
        val entries = suggest(file, offset)
        assertTrue("Expected value suggestions for BlendMode", entries.isNotEmpty())
        val opaque = entries.first { it.label == "Opaque" }
        assertEquals("bare value slots insert quoted", "\"Opaque\"", opaque.insertText)
    }

    fun testPragmaValuesInsertBareInsideAnOpenedString() {
        val file = myFixture.configureByText(
            "pragma_quoted.dss",
            "#pragma material(BlendMode = \"O\nuniform float Gain;\n",
        )
        val offset = file.text.indexOf("O\n") + 1
        val entries = suggest(file, offset)
        val opaque = entries.firstOrNull { it.label == "Opaque" }
        assertTrue("expected Opaque, got ${entries.map { it.label }}", opaque != null)
        assertEquals("opened strings must not double-quote", "Opaque", opaque!!.insertText)
    }

    fun testPragmaNonMaterialIsIgnored() {
        val file = myFixture.configureByText(
            "pragma_instance.dsi",
            "#pragma instance(Parent = \"M_Skin\")\nuniform float Gain;\n",
        )
        val offset = file.text.indexOf("(Parent") + 1
        val entries = DreamShader2CompletionSuggester.pragmaMaterialCompletions(file as DreamShaderPsiFile, offset)
        assertNull(entries)
    }

    // ------------------------------------------------------------------
    // A10 .dsi override values
    // ------------------------------------------------------------------

    fun testInstanceOverrideValuesFollowDeclaredType() {
        val file = myFixture.configureByText(
            "M_Value.dsi",
            "#pragma instance(Parent = \"M_P\")\n/// @static\nuniform bool StaticUse = ",
        )
        val offset = file.text.length
        val entries = DreamShader2CompletionSuggester.instanceOverrideValueCompletions(file as DreamShaderPsiFile, offset)
        val labels = entries.orEmpty().map { it.label }
        assertTrue("bool override values expected, got $labels", "true" in labels && "false" in labels)
    }

    fun testInstanceOverrideVectorValuesUseConstructorTemplates() {
        val file = myFixture.configureByText(
            "M_Vector.dsi",
            "#pragma instance(Parent = \"M_P\")\nuniform float3 BaseColor = ",
        )
        val offset = file.text.length
        val entries = DreamShader2CompletionSuggester.instanceOverrideValueCompletions(file as DreamShaderPsiFile, offset)
        val labels = entries.orEmpty().map { it.label }
        assertTrue("float3 template expected, got $labels", "float3(0.0, 0.0, 0.0)" in labels)
    }

    fun testInstanceOverrideValuesStaySilentAtNameSlot() {
        val file = myFixture.configureByText(
            "M_NameSlot.dsi",
            "#pragma instance(Parent = \"M_P\")\nuniform float \n",
        )
        val offset = file.text.length
        val entries = DreamShader2CompletionSuggester.instanceOverrideValueCompletions(file as DreamShaderPsiFile, offset)
        assertNull("the declaration-name slot is not a value slot", entries)
    }

    // ------------------------------------------------------------------
    // C29 .dsi override values driven by the parent schema
    // ------------------------------------------------------------------

    fun testSchemaTypeAgreesWithDeclarationKeepsSuggestions() {
        val virtualFile = writeDsiFixture(
            name = "M_SchemaAgree",
            sourceText = "#pragma instance(Parent = \"M_P\")\nuniform float3 BaseColor = ",
            schemaParameters = """{ "name": "BaseColor", "kind": "global", "type": "float3", "group": "Surface" }""",
        )
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as DreamShaderPsiFile
        val entries = DreamShader2CompletionSuggester.instanceOverrideValueCompletions(file, file.text.length)
        val labels = entries.orEmpty().map { it.label }
        assertTrue("schema float3 keeps the constructor template, got $labels", "float3(0.0, 0.0, 0.0)" in labels)
    }

    fun testSchemaStaticSwitchForcesTrueFalseOverDeclaredInt() {
        val virtualFile = writeDsiFixture(
            name = "M_SchemaSwitch",
            sourceText = "#pragma instance(Parent = \"M_P\")\nuniform int Count = ",
            schemaParameters = """{ "name": "Count", "kind": "StaticSwitch", "type": "int", "group": "Options" }""",
        )
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as DreamShaderPsiFile
        val entries = DreamShader2CompletionSuggester.instanceOverrideValueCompletions(file, file.text.length)
        val labels = entries.orEmpty().map { it.label }
        assertTrue("StaticSwitch kind forces true/false, got $labels", "true" in labels && "false" in labels)
        assertFalse("the declared int placeholder must not appear", "0" in labels)
    }

    fun testSchemaTextureKindStaysSilent() {
        val virtualFile = writeDsiFixture(
            name = "M_SchemaTexture",
            sourceText = "#pragma instance(Parent = \"M_P\")\nuniform int Mask = ",
            schemaParameters = """{ "name": "Mask", "kind": "Texture", "type": "texture2d", "group": "Resources" }""",
        )
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as DreamShaderPsiFile
        val entries = DreamShader2CompletionSuggester.instanceOverrideValueCompletions(file, file.text.length)
        assertTrue(
            "the value slot is recognized but resource values stay empty, got ${entries.orEmpty().map { it.label }}",
            entries != null && entries.isEmpty(),
        )
    }

    fun testSchemaWithoutTheOverrideNameKeepsDeclaredSpelling() {
        val virtualFile = writeDsiFixture(
            name = "M_SchemaMiss",
            sourceText = "#pragma instance(Parent = \"M_P\")\nuniform int Count = ",
            schemaParameters = """{ "name": "Other", "kind": "StaticSwitch", "type": "int", "group": "Options" }""",
        )
        val file = com.intellij.psi.PsiManager.getInstance(project).findFile(virtualFile) as DreamShaderPsiFile
        val entries = DreamShader2CompletionSuggester.instanceOverrideValueCompletions(file, file.text.length)
        val labels = entries.orEmpty().map { it.label }
        assertTrue("a name miss falls back to the declared spelling, got $labels", "0" in labels && "true" !in labels)
    }

    // ------------------------------------------------------------------

    /** Creates a `.dsi` source plus a schema-bearing symbol index under the Bridge layout. */
    private fun writeDsiFixture(
        name: String = "M_Inst",
        sourceText: String = "#pragma instance(Parent = \"M_Parent\")\nuniform float Gain;\n",
        schemaParameters: String = (
            "{ \"name\": \"Gain\", \"kind\": \"global\", \"type\": \"float1\", \"group\": \"Tuning\" },\n" +
                "                    { \"name\": \"Roughness\", \"kind\": \"global\", \"type\": \"float1\", \"group\": \"Surface\" },\n" +
                "                    { \"name\": \"BaseColor\", \"kind\": \"global\", \"type\": \"float3\", \"group\": \"Surface\" }"
            ),
    ): com.intellij.openapi.vfs.VirtualFile {
        val base = Paths.get(project.basePath!!)
        Files.createDirectories(base)
        base.resolve("Demo.uproject").writeText("{}")
        val source = base.resolve("DShader/Materials/$name.dsi")
        Files.createDirectories(source.parent)
        source.writeText(sourceText)
        val index = base.resolve("Saved/DreamShader/Index/Project/DShader/Materials/$name.dsi.index.json")
        Files.createDirectories(index.parent)
        index.writeText(
            """
            {
              "schema": "dreamshader-symbol-index",
              "version": 1,
              "file": "$name.dsi",
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
                    $schemaParameters
                  ]
                }
              }
            }
            """.trimIndent()
        )
        return LocalFileSystem.getInstance().refreshAndFindFileByPath(source.toString().replace('\\', '/'))!!
    }
}
