package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderGotoDeclarationHandler
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderDocumentationProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.*
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShader211IntelligenceTest : BasePlatformTestCase() {
    private val created = mutableListOf<com.intellij.openapi.vfs.VirtualFile>()
    override fun tearDown() {
        try {
            com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
                created.asReversed().filter { it.isValid }.forEach { it.delete(this) }
            }
            com.intellij.testFramework.PsiTestUtil.removeContentEntry(module, com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(project.basePath!!)!!)
        } finally { super.tearDown() }
    }
    override fun setUp() {
        super.setUp()
        val root = com.intellij.openapi.vfs.VfsUtil.createDirectories(project.basePath!!)
        com.intellij.testFramework.PsiTestUtil.addContentRoot(module, root)
    }
    private fun addSource(path: String, text: String): DreamShaderPsiFile {
        val rootPath = java.nio.file.Path.of(project.basePath!!).resolve(path)
        var result: com.intellij.openapi.vfs.VirtualFile? = null
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val directory = com.intellij.openapi.vfs.VfsUtil.createDirectories(rootPath.parent.toString())
            result = directory.findOrCreateChildData(this, rootPath.fileName.toString())
            com.intellij.openapi.vfs.VfsUtil.saveText(result!!, text)
        }
        created += result!!
        return com.intellij.psi.PsiManager.getInstance(project).findFile(result!!) as DreamShaderPsiFile
    }

    private fun file(text: String, name: String = "Editor.dss") = myFixture.configureByText(name, text) as DreamShaderPsiFile
    private fun leaf(file: DreamShaderPsiFile, spelling: String, last: Boolean = true) =
        file.findElementAt(if (last) file.text.lastIndexOf(spelling) else file.text.indexOf(spelling))!!
    private fun suggest(file: DreamShaderPsiFile, offset: Int = myFixture.caretOffset,
                        catalog: List<DreamShaderMaterialExpressionInfo> = emptyList()): List<DreamShaderCompletionItem> =
        DreamShader2CompletionSuggester.suggest(file, file.findElementAt((offset - 1).coerceAtLeast(0))!!, offset, catalog)
    private fun select(name: String) {
        val lookup = myFixture.lookup as? LookupImpl ?: return // A unique match may be inserted immediately.
        lookup.currentItem = lookup.items.first { it.lookupString == name }
        myFixture.finishLookup(Lookup.NORMAL_SELECT_CHAR)
    }

    fun testCompletionRespectsNearestBindingAndSiblingBlocks() {
        val f = file("uniform float Value; void Work(float Param) { { float Hidden; } { float2 Value; Val<caret> } float Later; }")
        val symbols = DreamShader2SemanticResolver.visibleSymbols(f, leaf(f, "Val", true))
        assertEquals("float2", symbols.first { it.name == "Value" }.type)
        assertFalse(symbols.any { it.name == "Hidden" || it.name == "Later" })
        assertTrue(symbols.any { it.name == "Param" })
        assertEquals(" : float2", suggest(f).first { it.label == "Value" }.tailText)
    }

    fun testForInitializerDoesNotEscapeLoop() {
        val f = file("void Work() { for(int i = 0; i < 2; ++i) { float inner = i; } float after = i; }")
        assertNull(DreamShader2SemanticResolver.resolve("i", leaf(f, "i;")))
        assertEquals("int", DreamShader2SemanticResolver.resolve("i", leaf(f, "i;", false))?.type)
    }

    fun testDeclarationCommasDoNotReadConstructorArgumentsAsNames() {
        val f = file("uniform float4 Color = float4(1, 2, 3, 4); void Work(){ float3 Value = float3(Color.x, Color.y, Color.z); <caret> }")
        val names = DreamShader2SemanticResolver.visibleSymbols(f, leaf(f, "}" )).map { it.name }
        assertEquals(1, names.count { it == "Color" })
        assertFalse(names.any { it in listOf("x", "y", "z") })
    }

    fun testReferencesSearchIgnoresShadowedAndUnrelatedUniforms() {
        val f = addSource("DShader/Original.dss", "uniform float Gain; void Work(){ float a = Gain; { float Gain; float b = Gain; } float c = Gain; }") as DreamShaderPsiFile
        addSource("DShader/Other.dss", "uniform float Gain; void Other(){ float v = Gain; }")
        val uniform = DreamShader2FileAnalysis.of(f).uniforms.single()
        val refs = ReferencesSearch.search(uniform).findAll()
        assertEquals(2, refs.size)
        assertTrue(refs.all { it.element.containingFile == f })
    }

    fun testGotoAndDocumentationShareLocalBinding() {
        val f = file("uniform float Value; void Work(){ float2 Value; float2 result = Value; }")
        val use = leaf(f, "Value;")
        val target = DreamShaderGotoDeclarationHandler().getGotoDeclarationTargets(use, use.textOffset, myFixture.editor)?.single()
        assertEquals(f.text.indexOf("Value;", f.text.indexOf("float2 Value")), target?.textOffset)
        assertTrue(DreamShaderDocumentationProvider().generateDoc(use, use).orEmpty().contains("float2 Value"))
    }

    fun testWriteBackInsideBranchDoesNotEscapeOrCrossShadow() {
        val f = file("void Fill(out float2 value){} void Work(bool flag){ float value; if(flag){ Fill(value); float2 inside = value; } float after = value; { float3 value; float3 inner = value; } }")
        val inference = DreamShader2InferenceContext.forFile(f, project.getService(DreamShader2WorkspaceSymbols::class.java))
        assertEquals(DreamShader2Type.fromSpelling("float2"), inference.typeOfName("value", f.findElementAt(f.text.indexOf("value;", f.text.indexOf("float2 inside")))!!))
        val after = f.findElementAt(f.text.indexOf("value;", f.text.indexOf("float after")))!!
        assertEquals(DreamShader2Type.fromSpelling("float"), inference.typeOfName("value", after))
        assertEquals(DreamShader2Type.fromSpelling("float3"), inference.typeOfName("value", leaf(f, "value;")))
    }

    fun testScalarRepeatsAndInvalidSwizzles() {
        val scalar = DreamShader2Type.fromSpelling("float")
        assertEquals(DreamShader2Type.fromSpelling("float3"), DreamShader2Swizzle.type(scalar, "RRr"))
        for (bad in listOf("y", "xg", "st", "uv", "xxxxx")) assertEquals(DreamShader2Type.Unknown, DreamShader2Swizzle.type(scalar, bad))
        assertEquals(DreamShader2Type.Unknown, DreamShader2Swizzle.type(DreamShader2Type.fromSpelling("float2"), "z"))
        val f = file("void Work(float gain) { gain.<caret> }")
        val names = suggest(f).map { it.label }
        assertTrue(names.containsAll(listOf("x", "xx", "xxx", "xxxx", "r", "rrr")))
        assertFalse(names.contains("y"))
    }

    fun testUserNamedArgumentsAndSignaturesIncludeHeaderHelpers() {
        addSource("DShader/Math.dsh", "/// @desc Apply a tint\nfloat3 Tint(float3 color, float gain) { return color * gain; }")
        val f = addSource("DShader/Use.dss", "#include \"Math.dsh\"\nvoid Work(){ Tint(color = float3(1,1,1), ); }") as DreamShaderPsiFile
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        val offset = f.text.indexOf(", );") + 2
        val names = suggest(f, offset).map { it.label }
        assertTrue("$names", "gain" in names)
        assertFalse("color" in names)
        val signature = DreamShaderCallSignatureResolver.resolveSignatures("Tint", f).single()
        assertEquals(listOf("color", "gain"), signature.parameterNames)
        assertEquals(listOf("float3", "float"), signature.parameterTypes)
        assertEquals("float3", signature.returnType)
        assertTrue(signature.documentation.orEmpty().contains("Apply a tint"))
    }

    fun testCallFinderIgnoresParenthesesInsideStringsAndComments() {
        val text = "UE.Example(Label = \"( nested )\", Other(1, 2), /* ) */ Final = \"abc(\")"
        val call = DreamShaderSignatureHelpAnalyzer.findEnclosingCall(text, text.lastIndexOf("abc(") + 4, true)
        assertEquals("UE.Example", call?.functionName)
        assertEquals(3, call?.arguments?.size)
    }

    private fun richCatalog(): List<DreamShaderMaterialExpressionInfo> = DreamShaderBuiltinCatalogManifest.parseCatalogEntries("""
        {"schema":"dreamshader-builtin-catalog","version":1,
        "materialAttributes":[{"name":"CustomAttribute","property":"MP_Custom","type":"float2","aliases":["Attr"]}],
        "expressions":[{"namespace":"UE","shortName":"Multi","className":"MaterialExpressionMulti",
          "hasInstanceDependentPins":true,"isParameter":true,"isCustomOutput":true,
          "inputs":[{"name":"Input","type":"Float3","required":true,"constProperty":"ConstInput","aliases":["OldInput"]}],
          "properties":[{"name":"Mode","type":"Enum","enumValues":["First","Second"],"default":"First"}],
          "outputs":[{"name":"Color","type":"Float3"},{"name":"InvSize","type":"Float2"}]}]}
    """.trimIndent(), DreamShaderMaterialExpressionSource.BUNDLED_FALLBACK)

    fun testCatalogPreservesExistingSchemaOptionalFieldsAndUnknownOutput() {
        val entry = richCatalog().single()
        assertNull(entry.outputType)
        assertEquals(listOf("Color", "InvSize"), entry.outputs.map { it.name })
        assertEquals(listOf("First", "Second"), entry.parameters.last().enumValues)
        assertEquals("First", entry.parameters.last().defaultValue)
        assertEquals("ConstInput", entry.parameters.first().constProperty)
        assertEquals(listOf("OldInput"), entry.parameters.first().aliases)
        assertTrue(entry.hasInstanceDependentPins && entry.isParameter && entry.isCustomOutput)
        assertEquals("CustomAttribute", entry.materialAttributes.single().name)
    }

    fun testNodeOutputsAndEnumsUseCatalogMetadata() {
        var f = file("void Work(){ UE.Multi().<caret> }")
        assertEquals(listOf("Color", "InvSize"), suggest(f, catalog = richCatalog()).map { it.label })
        f = file("void Work(){ UE.Multi(Mode = <caret>); }")
        val values = suggest(f, catalog = richCatalog())
        assertEquals(listOf("First", "Second"), values.map { it.insertText })
        f = file("void Work(inout material m){ m.<caret> }")
        assertTrue(suggest(f, catalog = richCatalog()).any { it.label == "CustomAttribute" })
    }

    fun testSubstrateReadsAndFutureWritesDoNotHidePins() {
        val f = file("void Work(){ Substrate s = Substrate.Slab(); float read = s.Roughness; s.<caret>; s.Roughness = 1; }")
        assertTrue(suggest(f).any { it.label == "Roughness" })
    }

    fun testSmartCompletionRanksCompatibleValues() {
        val f = file("void Work(float3 color, float gain){ float3 result = <caret>; }")
        val source = DreamShader2CompletionSuggester.bareIdentifierCompletions(f, f.findElementAt(myFixture.caretOffset - 1)!!)
        val ranked = DreamShader2CompletionContexts.rankByExpectedType(f, f.findElementAt(myFixture.caretOffset - 1)!!, myFixture.caretOffset, source)
        assertTrue(ranked.first { it.label == "color" }.priority > source.first { it.label == "color" }.priority)
        assertTrue(ranked.first { it.label == "gain" }.priority > source.first { it.label == "gain" }.priority)
        myFixture.complete(CompletionType.SMART)
        assertNotNull(myFixture.lookup)
    }

    fun testTypeSlotInsertsTypeWithoutConstructor() {
        val f = file("uniform flo<caret>")
        assertEquals("float", suggest(f).first { it.label == "float" }.insertText)
        myFixture.completeBasic(); select("float")
        assertEquals("uniform float", myFixture.editor.document.text)
    }

    fun testInsertionReusesExistingArguments() {
        file("float Helper(float x){ return x; } void Work(){ Hel<caret>(3.0); }")
        myFixture.completeBasic(); select("Helper")
        assertTrue(myFixture.editor.document.text.contains("Helper(3.0)"))
        assertFalse(myFixture.editor.document.text.contains("Helper()("))
        assertEquals('3', myFixture.editor.document.charsSequence[myFixture.caretOffset])
    }

    fun testSecondCompletionImportsHeaderAndSingleUndoRestoresBothEdits() {
        addSource("DShader/Shared/Utility.dsh", "float Helper(float x) { return x; }")
        val original = "#pragma material(ShadingModel = Unlit)\nvoid Work(){ Hel; }"
        val f = addSource("DShader/Materials/Use.dss", original) as DreamShaderPsiFile
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        myFixture.editor.caretModel.moveToOffset(original.indexOf("Hel;") + 3)
        myFixture.complete(CompletionType.BASIC, 2); select("Helper")
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("#include \"../Shared/Utility.dsh\""))
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("Helper()"))
        myFixture.performEditorAction(IdeActions.ACTION_UNDO)
        assertEquals(original, myFixture.editor.document.text)
    }

    fun testExpandedCompletionExcludesAssetsAndInstances() {
        addSource("DShader/Good.dsh", "float HelpGood(float x){return x;}")
        addSource("DShader/Asset.dss", "#pragma material()\nexport float HelpAsset(){return 1;}")
        addSource("DShader/Instance.dsi", "float HelpInstance(){return 1;}")
        val f = addSource("DShader/Use.dss", "void Work(){ Help; }") as DreamShaderPsiFile
        val candidates = DreamShader2AutoImport.candidates(f, "Help", leaf(f, "Help"))
        assertEquals(listOf("HelpGood"), candidates.map { it.label })
    }

    fun testExpandedCompletionIncludesHeaderTypesAndValuesOnlyInLegalContexts() {
        addSource("DShader/Types.dsh", "struct HeaderType { float x; }; uniform float HeaderGain; const float HeaderConstant = 1;")
        val f = addSource("DShader/Use.dss", "void Work(){ Head; }")
        assertEquals(setOf("HeaderType", "HeaderGain", "HeaderConstant"), DreamShader2AutoImport.candidates(f, "Head", leaf(f, "Head;")).map { it.label }.toSet())
        val types = addSource("DShader/Declaration.dss", "uniform Head;")
        assertEquals(listOf("HeaderType"), DreamShader2AutoImport.candidates(types, "Head", leaf(types, "Head;")).map { it.label })
        val doc = addSource("DShader/Doc.dss", "/// @name Head\nvoid Work(){}")
        assertTrue(DreamShader2AutoImport.candidates(doc, "Head", leaf(doc, "Head")).isEmpty())
    }

    fun testInstanceTypesTemplatesAndInvalidCandidates() {
        var f = file("<caret>", "Child.dsi")
        // Direct context supports an empty file where no PSI leaf exists.
        val items = DreamShader2CompletionContexts.suggest(f, f, 0).orEmpty()
        assertTrue(items.any { it.label == "StaticMask" && it.insertText.contains("bool4") })
        assertTrue(items.any { it.label == "Font" && it.insertText.contains("@page") })
        assertFalse(items.any { it.label in listOf("export", "extern", "struct", "#include", "lerp") })
        f = file("uniform Tex<caret>", "Child.dsi")
        assertTrue(suggest(f).any { it.label == "Texture2D" })
        f = file("uniform float2x2 Matrix = <caret>;", "Child.dsi")
        assertTrue(suggest(f).isEmpty())
    }

    fun testInstanceParentResolvesAcrossDirectoriesWithinOwnRootOnly() {
        addSource("DShader/Materials/Parent.dss", "#pragma material()\nuniform float Gain;")
        addSource("Plugins/Other/DShader/Parent.dss", "#pragma material()\nuniform float Other;")
        val f = addSource("DShader/Instances/Child.dsi", "#pragma instance(Parent = \"Parent\")\nuniform float G;") as DreamShaderPsiFile
        assertEquals(listOf("Gain"), DreamShader2InstanceModel.sourceParameters(f).map { it.first })
        assertTrue("Parent" in DreamShader2AutoImport.parentNames(f))
        assertFalse("Other" in DreamShader2AutoImport.parentNames(f))
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        assertTrue(myFixture.doHighlighting().none { it.description.orEmpty().contains("DSH7258") })
    }

    fun testInstanceNameMappingSurvivesCompletion() {
        addSource("DShader/Parent.dss", "#pragma material()\n/// @name Base Color Boost\nuniform float Boost;")
        val f = addSource("DShader/Child.dsi", "#pragma instance(Parent = \"Parent\")\nuniform float Base;")
        myFixture.configureFromExistingVirtualFile(f.virtualFile)
        myFixture.editor.caretModel.moveToOffset(f.text.lastIndexOf("Base;") + 4)
        myFixture.completeBasic(); select("Base Color Boost")
        assertTrue(myFixture.editor.document.text, myFixture.editor.document.text.contains("/// @name Base Color Boost\nuniform float BaseColorBoost;"))
    }

    fun testImportNavigationUsesTheConfiguredSourceRoot() {
        val settings = project.getService(com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java)
        val original = settings.state.sourceDirectory
        try {
            settings.state.sourceDirectory = "Shaders"
            val header = addSource("Shaders/Lib.dsh", "float Helper(){ return 1; }")
            val f = addSource("Shaders/Use.dss", "#include \"Lib.dsh\"\nvoid Work(){}")
            myFixture.configureFromExistingVirtualFile(f.virtualFile)
            val path = f.findElementAt(f.text.indexOf("Lib.dsh"))!!
            val targets = DreamShaderGotoDeclarationHandler().getGotoDeclarationTargets(path, path.textOffset, myFixture.editor)
            assertEquals(header.virtualFile, targets?.single()?.containingFile?.virtualFile)
        } finally { settings.state.sourceDirectory = original }
    }

    fun testIncludeExtensionAndScopedPathsHaveNoMagicFallback() {
        addSource("DShader/Thing.dss", "float Helper(){return 1;}")
        addSource("DShader/Packages/@scope/pkg/Library/Thing.dsh", "float Helper(){return 1;}")
        val f = addSource("DShader/Use.dss", "void Work(){}")
        assertNull(DreamShaderImportResolver.resolveImport(f, "Thing"))
        assertEquals("Thing.dss", DreamShaderImportResolver.resolveImport(f, "Thing.dss")?.name)
        assertNull(DreamShaderImportResolver.resolveImport(f, "@scope/pkg"))
        assertEquals("Thing.dsh", DreamShaderImportResolver.resolveImport(f, "@scope/pkg/Library/Thing")?.name)
    }
    fun testLocalRenamePreservesShadowedAndSiblingBindings() {
        val f = file("uniform float Value; void Work(float Value){ float a = Value; { float Value = 1; float b = Value; } float c = Value; } void Other(float Value){ float d = Value; }")
        val parameter = DreamShader2FileAnalysis.of(f).functions.first().parameterList()!!.parameters().single()
        myFixture.renameElement(parameter, "Gain")
        assertEquals("uniform float Value; void Work(float Gain){ float a = Gain; { float Value = 1; float b = Value; } float c = Gain; } void Other(float Value){ float d = Value; }".replace(Regex("\\s+"), ""), myFixture.editor.document.text.replace(Regex("\\s+"), ""))
        val local = psiTreeWalk(f).first { it.node.elementType == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT && it.text.startsWith("float Value") }
        myFixture.renameElement(local, "Inner")
        assertTrue(myFixture.editor.document.text.replace(Regex("\\s+"), "").contains("floatInner=1;floatb=Inner;"))
        assertTrue(myFixture.editor.document.text.contains("uniform float Value;"))
    }

    fun testUserStructSignaturesAndTopLevelRenameUseActualNameSlots() {
        val f = file("struct Payload { float gain; }; uniform float Scale; Payload Global; Payload Identity(Payload value){ return value; } void Work(){ Payload data; Payload copy = Identity(data); float result = Scale; }")
        val signatures = DreamShaderCallSignatureResolver.resolveSignatures("Identity", f)
        assertEquals("Payload", signatures.single().returnType)
        assertEquals(listOf("value"), signatures.single().parameterNames)
        assertEquals(listOf("Payload"), signatures.single().parameterTypes)
        val context = DreamShader2InferenceContext.forFile(f, project.getService(DreamShader2WorkspaceSymbols::class.java))
        assertEquals(DreamShader2Type.StructType("Payload"), context.typeOfName("Global", leaf(f, "Identity(data)")))
        myFixture.renameElement(DreamShader2FileAnalysis.of(f).uniforms.single(), "Gain")
        val renamedUniform = myFixture.editor.document.text.replace(Regex("\\s+"), "")
        assertTrue(renamedUniform, renamedUniform.contains("uniformfloatGain;"))
        assertTrue(renamedUniform, renamedUniform.contains("=Gain;"))
        myFixture.renameElement(DreamShader2FileAnalysis.of(f).structs.single(), "Data")
        val compact = myFixture.editor.document.text.replace(Regex("\\s+"), "")
        assertTrue(compact, compact.contains("structData{floatgain;}"))
        assertTrue(compact, compact.contains("DataIdentity(Datavalue)"))
        assertTrue(compact, compact.contains("Datadata;Datacopy=Identity(data);"))
        myFixture.renameElement(DreamShader2FileAnalysis.of(f).functions.first { it.declarationName() == "Identity" }, "Copy")
        val renamedFunction = myFixture.editor.document.text.replace(Regex("\\s+"), "")
        assertTrue(renamedFunction, renamedFunction.contains("Copy(data)"))
    }

    fun testRenameAtATypeReferenceTargetsTheStructRatherThanItsOwningDeclaration() {
        val f = file("struct Payload { float gain; }; Payload Identity(Payload value){ return value; } void Work(){ Payload data; Payload copy = Identity(data); }")
        val typeOffset = f.text.indexOf("Payload Identity")
        myFixture.editor.caretModel.moveToOffset(typeOffset + 2)
        assertEquals("Payload", (f.findReferenceAt(typeOffset + 2)?.resolve() as? com.intellij.psi.PsiNamedElement)?.name)
        myFixture.renameElementAtCaret("Data")
        val compact = myFixture.editor.document.text.replace(Regex("\\s+"), "")
        assertTrue(compact, compact.contains("structData{floatgain;}"))
        assertTrue(compact, compact.contains("DataIdentity(Datavalue)"))
        assertTrue(compact, compact.contains("Datadata;Datacopy=Identity(data);"))
    }

    fun testParameterSharingATypeNameBindsToItsOwnIdentifier() {
        val f = file("struct Payload { float gain; }; Payload Identity(Payload Payload){ return Payload; }")
        val parameter = DreamShader2FileAnalysis.of(f).functions.single().parameterList()!!.parameters().single()
        val owner = parameter as com.intellij.psi.PsiNameIdentifierOwner
        assertEquals(f.text.indexOf("Payload){"), owner.nameIdentifier!!.textOffset)
        myFixture.renameElement(parameter, "value")
        val compact = myFixture.editor.document.text.replace(Regex("\\s+"), "")
        assertTrue(compact, compact.contains("PayloadIdentity(Payloadvalue){returnvalue;}"))
        assertTrue(compact, compact.contains("structPayload{floatgain;}"))
    }

    fun testGroupedTopLevelNamesRenameWithoutTouchingOtherDeclarations() {
        val f = file("uniform float a = 1, b = 2; const float c = 3, d = 4; void Work(){ float total = a + b + c + d; }")
        val site = leaf(f, "a + b + c + d")
        myFixture.renameElement(DreamShader2SemanticResolver.resolve("b", site)!!.owner, "second")
        myFixture.renameElement(DreamShader2SemanticResolver.resolve("d", leaf(f, "a + second"))!!.owner, "fourth")
        val compact = myFixture.editor.document.text.replace(Regex("\\s+"), "")
        assertTrue(compact, compact.contains("uniformfloata=1,second=2;"))
        assertTrue(compact, compact.contains("constfloatc=3,fourth=4;"))
        assertTrue(compact, compact.contains("=a+second+c+fourth;"))
    }

    fun testUnknownEnvironmentDoesNotGuessConflictingDeclarationTypes() {
        val f = file("""
            #if DS_ENGINE_MINOR >= 8
            uniform float4 Choice;
            float4 Choose(){return float4(1,1,1,1);}
            #else
            uniform float Choice;
            float Choose(){return 1;}
            #endif
            void Work(){ float value = Choice.w; float other = Choose().w; }
        """.trimIndent())
        val context = DreamShader2InferenceContext.forFile(f, project.getService(DreamShader2WorkspaceSymbols::class.java))
        assertEquals(DreamShader2Type.Unknown, context.typeOfName("Choice", leaf(f, "Choice.w")))
        val call = psiTreeWalk(f).first { it.node.elementType == DreamShader2ElementTypes.CALL_EXPRESSION && it.text == "Choose()" }
        assertEquals(DreamShader2Type.Unknown, context.typeOf(call))
        assertTrue(myFixture.doHighlighting().none { it.description.orEmpty().contains("DSH4230") })
    }

    fun testConditionalAndDisabledCallsDoNotProduceCertainWriteBacks() {
        val f = file("""
            bool Fill(out float2 value){ return true; }
            void Work(bool flag){
                float value;
                flag && Fill(value);
                float afterShortCircuit = value;
                #if 0
                Fill(value);
                #endif
                float afterDisabled = value;
                flag ? Fill(value) : false;
                float afterTernary = value;
            }
        """.trimIndent())
        val context = DreamShader2InferenceContext.forFile(f, project.getService(DreamShader2WorkspaceSymbols::class.java))
        for (name in listOf("afterShortCircuit", "afterDisabled", "afterTernary")) {
            assertEquals(DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT), context.typeOfName("value", leaf(f, "$name = value")))
        }
    }

    fun testGroupedLocalDeclaratorsRenameTheirActualBindings() {
        val f = file("void Work(){ float a = 1, b = a; float total = a + b; { float b = 3; float inner = b; } }")
        val b = DreamShader2SemanticResolver.resolve("b", leaf(f, "a + b;").parent)!!
        myFixture.renameElement(b.owner, "second")
        val compact = myFixture.editor.document.text.replace(Regex("\\s+"), "")
        assertTrue(compact, compact.contains("floata=1,second=a;floattotal=a+second;"))
        assertTrue(compact, compact.contains("floatb=3;floatinner=b;"))
    }

    fun testGraphRestrictionsAreReportedOnlyWhenProven() {
        file("""
            /// @custom
            float Safe(float x) { return UE.TexCoord(Index = x).x; }
            /// @custom
            float Unsafe(float x) { float local = x + 1; return UE.TexCoord(Index = local).x; }
            void Work(float count) { while(count > 0) { count -= 1; } while(true) {} float value; float bad = value.y; float2 good = value.xx; }
        """.trimIndent())
        val diagnostics = myFixture.doHighlighting().mapNotNull { it.description }
        assertEquals(diagnostics.toString(), 1, diagnostics.count { it.contains("DSH6326") })
        assertEquals(diagnostics.toString(), 1, diagnostics.count { it.contains("DSH4360") })
        assertEquals(diagnostics.toString(), 1, diagnostics.count { it.contains("DSH4230") })
    }

    fun testIncludeDiamondCycleAndInactiveIncludesRemainBoundOnce() {
        addSource("DShader/Graph/A.dsh", "#include \"Shared.dsh\"\nfloat A(){return 0;}")
        addSource("DShader/Graph/B.dsh", "#include \"Shared.dsh\"\nfloat B(){return 0;}")
        addSource("DShader/Graph/Shared.dsh", "#define PRIVATE 1\n#include \"A.dsh\"\nfloat Shared(){return 0;}")
        addSource("DShader/Graph/Inactive.dsh", "float Hidden(){return 0;}")
        val f = addSource("DShader/Graph/Use.dss", "#include \"A.dsh\"\n#include \"B.dsh\"\n#if 0\n#include \"Inactive.dsh\"\n#endif\nvoid Work(){ Shared(); }")
        val workspace = project.getService(DreamShader2WorkspaceSymbols::class.java)
        val closure = workspace.includeClosurePsi(f)
        assertEquals(3, closure.size)
        assertSame(closure, workspace.includeClosurePsi(f))
        assertTrue(DreamShaderPreprocessorModel.of(f).definitionsAt(f.textLength).isEmpty())
        val names = DreamShader2SemanticResolver.visibleSymbols(f, leaf(f, "Shared();")).map { it.name }
        assertEquals(1, names.count { it == "Shared" })
        assertFalse("Hidden" in names)
        com.intellij.openapi.command.WriteCommandAction.runWriteCommandAction(project) {
            val doc = com.intellij.psi.PsiDocumentManager.getInstance(project).getDocument(closure.first())!!
            doc.insertString(0, "uniform float Added;\n")
            com.intellij.psi.PsiDocumentManager.getInstance(project).commitDocument(doc)
        }
        assertTrue(DreamShader2SemanticResolver.visibleSymbols(f, leaf(f, "Shared();")).any { it.name == "Added" })
    }

    fun testWarmCompletionPerformanceWithTenThousandLinesAndHundredIncludes() {
        for (index in 0 until 100) {
            val include = if (index == 99) "" else "#include \"Header${index + 1}.dsh\"\n"
            addSource("DShader/Perf/Header$index.dsh", include + "float Helper$index(float x) { return x; }")
        }
        val source = "#include \"Header0.dsh\"\nvoid Work(){\n" + (0 until 9996).joinToString("\n") { "float Value$it = 1;" } + "\nVal;\n}"
        assertEquals(10000, source.lines().size)
        val f = addSource("DShader/Perf/Use.dss", source)
        val offset = source.lastIndexOf("Val;") + 3
        val site = f.findElementAt(offset - 1)!!
        assertEquals(100, project.getService(DreamShader2WorkspaceSymbols::class.java).includeClosurePsi(f).size)
        repeat(10) { DreamShader2CompletionSuggester.suggest(f, site, offset, emptyList()) }
        val milliseconds = (0 until 30).map {
            val start = System.nanoTime()
            val items = DreamShader2CompletionSuggester.suggest(f, site, offset, emptyList())
            assertTrue(items.any { item -> item.label == "Helper99" })
            (System.nanoTime() - start) / 1_000_000.0
        }.sorted()
        val p95 = milliseconds[28]
        println("DreamShader 2.1.1 warm completion: 10000 lines, 100 includes, 30 samples, P95=$p95 ms")
        assertTrue("Warm completion P95=$p95 ms exceeds 150 ms", p95 <= 150.0)
        val indicator = com.intellij.openapi.progress.EmptyProgressIndicator()
        indicator.cancel()
        var canceled = false
        try { com.intellij.openapi.progress.ProgressManager.getInstance().runProcess(Runnable { indicator.cancel(); DreamShader2SemanticResolver.visibleSymbols(f, site) }, indicator) }
        catch (_: com.intellij.openapi.progress.ProcessCanceledException) { canceled = true }
        assertTrue("Completion must respond to cancellation", canceled)
    }

}
