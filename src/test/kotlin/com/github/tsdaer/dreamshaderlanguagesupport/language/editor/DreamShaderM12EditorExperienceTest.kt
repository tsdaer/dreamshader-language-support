package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring.DreamShaderSuggestedRefactoringSupport
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Milestone M12 D-line editor experience: copy path (D6), in-file usage highlight (D1),
 * code block support (D5), Surround With (D2), editor action handlers (D4), conditional
 * HLSL injection ranges (D7) and suggested rename refactoring (D3).
 */
class DreamShaderM12EditorExperienceTest : BasePlatformTestCase() {
    // ------------------------------------------------------------------
    // D6 copy path
    // ------------------------------------------------------------------

    fun testCopyPathAssetFormInsideSourceRoot() {
        assertEquals(
            "/Game/Materials/M_Grass",
            DreamShaderCopyPathProvider.copyPathFor(
                projectBasePath = "C:/Project",
                sourceDirectorySetting = "DShader",
                fileUrl = "C:/Project/DShader/Materials/M_Grass.dss",
                extension = "dss",
            ),
        )
    }

    fun testCopyPathProjectRelativeOutsideSourceRoot() {
        assertEquals(
            "Docs/Notes.dsm",
            DreamShaderCopyPathProvider.copyPathFor(
                projectBasePath = "C:/Project",
                sourceDirectorySetting = "DShader",
                fileUrl = "C:/Project/Docs/Notes.dsm",
                extension = "dsm",
            ),
        )
        assertEquals(
            "C:/Elsewhere/Extras.dsf",
            DreamShaderCopyPathProvider.copyPathFor(
                projectBasePath = "C:/Project",
                sourceDirectorySetting = "DShader",
                fileUrl = "C:/Elsewhere/Extras.dsf",
                extension = "dsf",
            ),
        )
    }

    fun testCopyPathIgnoresForeignExtensions() {
        assertNull(
            DreamShaderCopyPathProvider.copyPathFor(
                projectBasePath = "C:/Project",
                sourceDirectorySetting = "DShader",
                fileUrl = "C:/Project/DShader/Materials/readme.txt",
                extension = "txt",
            ),
        )
    }

    // ------------------------------------------------------------------
    // D1 in-file usage highlighting
    // ------------------------------------------------------------------

    fun testHighlightUsagesFromDeclarationName() {
        val file = myFixture.configureByText(
            "usages.dsf",
            """
            Function Pulse {
            }

            Shader Main {
                Graph {
                    Pulse();
                    float3 c = Pulse();
                }
            }
            """.trimIndent()
        )
        myFixture.editor.caretModel.moveToOffset(file.text.indexOf("Function Pulse") + "Function ".length)
        val handler = DreamShaderHighlightUsagesHandlerFactory().createHighlightUsagesHandler(
            myFixture.editor, file,
        )
        assertNotNull("Expected a usage handler on the declaration name", handler)
        val typed = handler as com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase<PsiElement>
        typed.computeUsages(typed.targets)
        val usages = typed.readUsages.map { it.startOffset }.sorted()
        assertEquals(3, usages.size)
    }

    fun testHighlightUsagesFromUsageResolvesDeclaration() {
        val file = myFixture.configureByText(
            "usages_call.dsf",
            """
            Function Glow {
            }

            Shader Main {
                Graph {
                    Glow();
                }
            }
            """.trimIndent()
        )
        val callOffset = file.text.indexOf("Glow();") + 1
        myFixture.editor.caretModel.moveToOffset(callOffset)
        val handler = DreamShaderHighlightUsagesHandlerFactory().createHighlightUsagesHandler(
            myFixture.editor, file,
        )
        assertNotNull("Expected a usage handler on the call-site identifier", handler)
        val typed = handler as com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase<PsiElement>
        assertEquals(1, typed.targets.size)
        typed.computeUsages(typed.targets)
        assertEquals(2, typed.readUsages.size)
    }

    fun testHighlightUsagesIgnoresNonIdentifierCaret() {
        val file = myFixture.configureByText(
            "usages_whitespace.dsf",
            "Function Glow {\n}\n",
        )
        myFixture.editor.caretModel.moveToOffset(0)
        val handler = DreamShaderHighlightUsagesHandlerFactory().createHighlightUsagesHandler(
            myFixture.editor, file,
        )
        assertNull(handler)
    }

    // ------------------------------------------------------------------
    // D5 code block support
    // ------------------------------------------------------------------

    fun testCodeBlockRangeCoversV1Declaration() {
        val file = myFixture.configureByText(
            "blocks.dsf",
            "Function Body {\n    Graph {\n    }\n}\n",
        )
        val declaration = PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java).first()
        val handler = DreamShaderCodeBlockSupportHandler()

        val inner = file.findElementAt(declaration.textRange.startOffset + 4)!!
        assertEquals(declaration.textRange, handler.getCodeBlockRange(inner))

        val markers = handler.getCodeBlockMarkerRanges(inner)
        assertEquals(2, markers.size)
    }

    fun testCodeBlockRangeForV2FunctionBody() {
        val file = myFixture.configureByText(
            "blocks_v2.dss",
            """
            export float3 Tint(float3 c) {
                return c;
            }
            """.trimIndent()
        )
        val function = PsiTreeUtil.findChildrenOfType(file, PsiElement::class.java)
            .first { PsiUtilCore.getElementType(it) == DreamShader2ElementTypes.FUNCTION_DECLARATION }
        val handler = DreamShaderCodeBlockSupportHandler()
        val keyword = file.findElementAt(function.textRange.startOffset)!!
        assertEquals(function.textRange, handler.getCodeBlockRange(keyword))
    }

    // ------------------------------------------------------------------
    // D2 Surround With
    // ------------------------------------------------------------------

    fun testSurroundElementsToSurroundWidenToStatementLevel() {
        val file = myFixture.configureByText(
            "surround.dss",
            """
            export void Work(float x) {
                x = x + 1.0;
                x = x * 2.0;
            }
            """.trimIndent()
        )
        val firstStatementStart = file.text.indexOf("x = x + 1.0;") + 3
        val lastStatementEnd = file.text.indexOf("x = x * 2.0;") + "x = x * 2.0;".length - 1
        val elements = DreamShaderSurroundDescriptor().getElementsToSurround(file, firstStatementStart, lastStatementEnd)
        assertEquals(2, elements.size)
    }

    fun testSurroundWithBlockReplacesSelection() {
        val file = myFixture.configureByText(
            "surround_block.dss",
            "export void Work(float x) {\n    x = x + 1.0;\n}\n",
        )
        val editor = myFixture.editor
        val element = PsiTreeUtil.collectElements(file) { true }
            .single { PsiUtilCore.getElementType(it) == DreamShader2ElementTypes.EXPRESSION_STATEMENT }
        val range = WriteCommandAction.runWriteCommandAction<com.intellij.openapi.util.TextRange?>(project) {
            DreamShaderWithBracesSurrounder().surroundElements(project, editor, arrayOf(element))
        }
        assertNotNull(range)
        val text = editor.document.text
        assertTrue("Expected braces around the statement, got: $text", text.contains("{\n    x = x + 1.0;\n}"))
    }

    fun testSurroundWithIfOnlyApplicableInV2Files() {
        val v2File = myFixture.configureByText("surround_if.dss", "export void W(float x) {\n    x = 1.0;\n}\n")
        val v2Element = PsiTreeUtil.collectElements(v2File) {
            PsiUtilCore.getElementType(it) == DreamShader2ElementTypes.EXPRESSION_STATEMENT
        }.single()
        val ifSurrounder = DreamShaderWithIfSurrounder()
        assertTrue(ifSurrounder.isApplicable(arrayOf(v2Element)))

        val v1File = myFixture.configureByText("surround_if.dsf", "Function W {\n}\n")
        val v1Declaration = PsiTreeUtil.findChildrenOfType(v1File, DreamShaderDeclaration::class.java).first()
        assertFalse(ifSurrounder.isApplicable(arrayOf(v1Declaration)))
    }

    // ------------------------------------------------------------------
    // D4 editor actions
    // ------------------------------------------------------------------

    fun testJoinLinesMergesCommentLines() {
        val file = myFixture.configureByText(
            "join.dsf",
            "// first part\n// second part\nShader Main {\n}\n",
        )
        val document = myFixture.editor.document
        val start = document.text.indexOf("// second part")
        val result = WriteCommandAction.runWriteCommandAction<Int>(project) {
            DreamShaderJoinLinesHandler().tryJoinLines(document, file, start - 2, start)
        }
        assertEquals("// first part second part", document.text.lines().first())
        assertTrue(result!! >= 0)
    }

    fun testJoinLinesFallsThroughForCode() {
        val file = myFixture.configureByText(
            "join_code.dsf",
            "Function A {\n}\n\nFunction B {\n}\n",
        )
        val document = myFixture.editor.document
        val offset = document.text.indexOf("Function B")
        val result = DreamShaderJoinLinesHandler().tryJoinLines(document, file, offset, offset)
        assertEquals(com.intellij.codeInsight.editorActions.JoinLinesHandlerDelegate.CANNOT_JOIN, result)
    }

    fun testMoveElementLeftRightExposesArgumentListItems() {
        val file = myFixture.configureByText(
            "move.dss",
            "export void Work() {\n    float3 c = lerp(a, b, t);\n}\n",
        )
        val argumentList = PsiTreeUtil.collectElements(file) { true }
            .single { PsiUtilCore.getElementType(it) == DreamShader2ElementTypes.ARGUMENT_LIST }
        val movable = DreamShaderMoveElementLeftRightHandler().getMovableSubElements(argumentList)
        assertEquals(3, movable.size)
    }

    fun testStatementUpDownMovesV1Declarations() {
        val file = myFixture.configureByText(
            "moveupdown.dsf",
            "Function First {\n}\n\nFunction Second {\n}\n",
        )
        val second = PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java)
            .first { it.declarationName() == "Second" }
        myFixture.editor.caretModel.moveToOffset(second.textRange.startOffset + 2)

        val mover = DreamShaderStatementUpDownMover()
        val info = com.intellij.codeInsight.editorActions.moveUpDown.StatementUpDownMover.MoveInfo()
        assertTrue(mover.checkAvailable(myFixture.editor, file, info, false))
        assertEquals(0, info.toMove2.startLine)
    }

    fun testListSplitJoinContextExtractsParameterList() {
        val file = myFixture.configureByText(
            "splitjoin.dss",
            "export float3 Blend(float a, float b, float t) {\n    return float3(0.0, 0.0, 0.0);\n}\n",
        )
        val parameterList = PsiTreeUtil.collectElements(file) { true }
            .single { PsiUtilCore.getElementType(it) == DreamShader2ElementTypes.PARAMETER_LIST }
        val context = DreamShaderListSplitJoinContext()
        val data = context.extractData(parameterList)
        assertNotNull(data)
        assertEquals(3, data!!.elements.size)
        assertTrue(context.isSplitAvailable(data))
    }

    // ------------------------------------------------------------------
    // D7 HLSL injection ranges
    // ------------------------------------------------------------------

    fun testV1FunctionBodyHostRange() {
        val file = myFixture.configureByText(
            "inject.dsf",
            "Function Body {\n    return 1.0;\n}\n",
        )
        val declaration = PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java).first()
        val host = declaration as? DreamShaderHlslBodyHost
        assertNotNull("1.x function declarations should be injection hosts", host)
        val range = host!!.dreamShaderHlslBodyRange()!!
        assertEquals("\n    return 1.0;\n", declaration.text.substring(range.startOffset, range.endOffset))
    }

    fun testV2CustomBodyHostRange() {
        val file = myFixture.configureByText(
            "inject_v2.dss",
            """
            /// @custom
            export float3 Body(float3 c) {
                return c * 2.0;
            }
            """.trimIndent()
        )
        val function = PsiTreeUtil.findChildrenOfType(file, DreamShader2FunctionDeclaration::class.java).first()
        val host = function as? DreamShaderHlslBodyHost
        assertNotNull("V2 custom bodies should be injection hosts", host)
        val range = host!!.dreamShaderHlslBodyRange()!!
        assertEquals("\n    return c * 2.0;\n", function.text.substring(range.startOffset, range.endOffset))
    }

    fun testInjectorIsRegisteredForHostElementsAndSkipsWithoutHlsl() {
        val injector = DreamShaderHlslInjector()
        assertTrue(injector.elementsToInjectIn().isNotEmpty())
        // The sandbox has no HLSL language: the injector must stay silent (no exception).
        val file = myFixture.configureByText("inject_silent.dsf", "Function Body {\n    return 1.0;\n}\n")
        val declaration = PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java).first()
        val registrar = RecordingRegistrar()
        injector.getLanguagesToInject(registrar, declaration)
        assertFalse(registrar.started)
    }

    // ------------------------------------------------------------------
    // D3 suggested refactoring (rename-only)
    // ------------------------------------------------------------------

    fun testSuggestedRefactoringExtractsSignature() {
        val file = myFixture.configureByText(
            "suggested.dss",
            "export float3 Blend(float a, float b) {\n    return a;\n}\n",
        )
        val function = PsiTreeUtil.findChildrenOfType(file, DreamShader2FunctionDeclaration::class.java).first()
        val support = DreamShaderSuggestedRefactoringSupport()
        assertTrue(support.isAnchor(function))

        val signature = support.stateChanges.signature(function, null)!!
        assertEquals("Blend", signature.name)
        assertEquals("float3", signature.type)
        assertEquals(listOf("a", "b"), signature.parameters.map { it.name })
    }

    fun testSuggestedRenameTriggersAfterNameEdit() {
        val file = myFixture.configureByText(
            "suggested_rename.dss",
            "export void Work(float x) {\n}\n",
        )
        val function = PsiTreeUtil.findChildrenOfType(file, DreamShader2FunctionDeclaration::class.java).first()
        val support = DreamShaderSuggestedRefactoringSupport()
        val stateChanges = support.stateChanges
        val state = stateChanges.createInitialState(function)

        WriteCommandAction.runWriteCommandAction(project) {
            val document = file.viewProvider.document!!
            val nameOffset = file.text.indexOf("Work")
            document.replaceString(nameOffset, nameOffset + "Work".length, "Work2")
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }
        val updated = stateChanges.updateState(state!!, function)
        assertNotNull("State should survive the name edit", updated)
        assertTrue(
            "Name change should be detected (the suggestion trigger condition)",
            updated!!.oldSignature.name != updated.newSignature.name,
        )
        val data = support.availability.detectAvailableRefactoring(updated)
        assertTrue(data is com.intellij.refactoring.suggested.SuggestedRenameData)
        assertEquals("Work", (data as com.intellij.refactoring.suggested.SuggestedRenameData).oldName)
    }

    fun testSuggestedRenameDoesNotTriggerWithoutEdit() {
        val file = myFixture.configureByText(
            "suggested_stable.dss",
            "export void Work(float x) {\n}\n",
        )
        val function = PsiTreeUtil.findChildrenOfType(file, DreamShader2FunctionDeclaration::class.java).first()
        val support = DreamShaderSuggestedRefactoringSupport()
        val state = support.stateChanges.createInitialState(function)
        val updated = support.stateChanges.updateState(state!!, function)
        val triggered = updated != null && updated.oldSignature.name != updated.newSignature.name
        assertFalse("No suggestion expected when nothing changed", triggered)
    }

    // ------------------------------------------------------------------

    private class RecordingRegistrar : com.intellij.lang.injection.MultiHostRegistrar {
        var started = false
            private set

        override fun startInjecting(language: com.intellij.lang.Language): com.intellij.lang.injection.MultiHostRegistrar {
            started = true
            return this
        }

        override fun addPlace(
            prefix: String?,
            suffix: String?,
            host: com.intellij.psi.PsiLanguageInjectionHost,
            rangeInsideHost: com.intellij.openapi.util.TextRange,
        ): com.intellij.lang.injection.MultiHostRegistrar = this

        override fun doneInjecting() {}
    }

}
