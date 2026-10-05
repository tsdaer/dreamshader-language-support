package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * M13 C15: the 1.x completion hot path reuses the committed originalFile PSI for context
 * analysis (lexer fallback only), merges the callable-candidate collectors into one cached
 * pass, and collects import path candidates lazily with a cached FilenameIndex scan.
 */
class DreamShaderCompletionHotPathTest : BasePlatformTestCase() {

    // ------------------------------------------------------------------
    // C15.1 PSI-backed context analysis agrees with the text-based entry
    // ------------------------------------------------------------------

    fun testPsiBackedSuggestMatchesTextBackedSuggestAcrossContexts() {
        assertPsiAndTextAgree("import \"Common/Core.dsh\";\n\n", offsetMarker = null, label = "top level")
        assertPsiAndTextAgree(
            """
            Shader MySurface {
                Settings {

                }
            }
            """.trimIndent(),
            offsetMarker = "Settings {\n",
            label = "settings section",
        )
        assertPsiAndTextAgree(
            """
            Shader MySurface {
                Settings {
                    Domain =
                }
            }
            """.trimIndent(),
            offsetMarker = "Domain =",
            label = "setting value slot",
        )
        assertPsiAndTextAgree("Shader(Na", offsetMarker = null, label = "declaration head")
        assertPsiAndTextAgree(
            """
            Shader MySurface {
                Graph {
                    float3 c = float3(1.0, 0.0, 0.0);
                    c.
                }
            }
            """.trimIndent(),
            offsetMarker = "c.",
            label = "graph member access",
        )
        assertPsiAndTextAgree(
            """
            Shader MySurface {
                Outputs {
                    Base.
                }
            }
            """.trimIndent(),
            offsetMarker = "Base.",
            label = "outputs base member",
        )
        assertPsiAndTextAgree(
            """
            Shader MySurface {
                // comment
            }
            """.trimIndent(),
            offsetMarker = "// comment",
            label = "comment stays empty",
        )
    }

    /** [offsetMarker] selects the caret position (after its last occurrence); null = end of file. */
    private fun assertPsiAndTextAgree(text: String, offsetMarker: String?, label: String) {
        val offset = when (offsetMarker) {
            null -> text.length
            else -> {
                val index = text.lastIndexOf(offsetMarker)
                require(index >= 0) { "marker not found: $offsetMarker" }
                index + offsetMarker.length
            }
        }
        val file = myFixture.configureByText("parity.dsm", text)
        val candidates = collectCallableCandidates(file)

        val fromPsi = DreamShaderCompletionSuggester.suggest(
            file,
            offset,
            importCandidates = { listOf("Common/Core.dsh") },
            callableCandidates = candidates,
        ).map { it.label }
        val fromText = DreamShaderCompletionSuggester.suggest(
            file.text,
            offset,
            importCandidates = listOf("Common/Core.dsh"),
            callableCandidates = candidates.plain,
            namespaceCallableCandidates = candidates.namespaced,
        ).map { it.label }

        assertEquals("context mismatch in $label", fromText, fromPsi)
    }

    // ------------------------------------------------------------------
    // C15.3 import candidates are evaluated lazily
    // ------------------------------------------------------------------

    fun testImportCandidatesAreNotCollectedOutsideImportContext() {
        val file = myFixture.configureByText(
            "lazy.dsm",
            """
            Shader MySurface {
                Graph {
                    float3 c = float3(1.0, 0.0, 0.0);
                    c.
                }
            }
            """.trimIndent(),
        )
        val offset = file.text.lastIndexOf("c.") + "c.".length

        val items = DreamShaderCompletionSuggester.suggest(
            file,
            offset,
            importCandidates = { error("import candidates must not be collected outside import contexts") },
            callableCandidates = collectCallableCandidates(file),
        )

        assertTrue("unknown receiver stays within callables/locals", items.isNotEmpty())
        assertTrue(items.none { it.label == "Common/Core.dsh" })
    }

    fun testImportCandidatesAreCollectedInImportContext() {
        val file = myFixture.configureByText("eager.dsm", "import \"Co")
        var evaluated = false

        val items = DreamShaderCompletionSuggester.suggest(
            file,
            file.text.length,
            importCandidates = {
                evaluated = true
                listOf("Common/Core.dsh", "Common/Common.dsh")
            },
        )

        assertTrue("supplier must run in import context", evaluated)
        assertTrue(items.map { it.label }.contains("Common/Core.dsh"))
    }

    // ------------------------------------------------------------------
    // C15.2 callable candidates are computed once per revision
    // ------------------------------------------------------------------

    fun testCallableCandidatesAreCachedUntilPsiChanges() {
        val file = myFixture.configureByText(
            "callables.dsm",
            "function float3 Helper(float a) {\n    return float3(a);\n}\n",
        )

        val first = collectCallableCandidates(file)
        assertSame(first, collectCallableCandidates(file))
        assertTrue(first.plain.any { it.label == "Helper" })
        assertTrue(first.namespaced.isEmpty())

        val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
        WriteCommandAction.runWriteCommandAction(project) {
            document.insertString(document.textLength, "function float3 Extra() {\n}\n")
        }
        PsiDocumentManager.getInstance(project).commitDocument(document)

        val second = collectCallableCandidates(file)
        assertNotSame(first, second)
        assertTrue(second.plain.any { it.label == "Extra" })
    }

    // ------------------------------------------------------------------
    // C15.3 FilenameIndex scan is cached per project revision
    // ------------------------------------------------------------------

    fun testProjectShaderFileScanIsCachedAndSeesNewFiles() {
        myFixture.addFileToProject("Common/Core.dsm", "Shader Core {\n}\n")

        val first = collectProjectShaderFiles(project, listOf("dsm"))
        assertSame(first, collectProjectShaderFiles(project, listOf("dsm")))
        assertTrue(first.any { it.name == "Core.dsm" })

        myFixture.addFileToProject("Common/Lib.dsm", "Shader Lib {\n}\n")

        val second = collectProjectShaderFiles(project, listOf("dsm"))
        assertNotSame(first, second)
        assertTrue(second.any { it.name == "Lib.dsm" })
    }

    fun testImportCompletionUsesCachedProjectFiles() {
        myFixture.addFileToProject("Common/Core.dsm", "Shader Core {\n}\n")
        val file = myFixture.configureByText("importer.dsm", "import \"")

        // Test-project content roots (/src/...) do not sit under project.basePath, so the
        // normalizer's name-fallback branch provides the candidate - the wiring under test
        // is the lazy supplier feeding from the cached FilenameIndex scan.
        val candidates = collectProjectImportCandidates(file)
        assertTrue("candidates: $candidates", candidates.contains("Core.dsm"))

        val items = DreamShaderCompletionSuggester.suggest(
            file,
            file.text.length,
            importCandidates = { collectProjectImportCandidates(file) },
        )

        assertTrue("items: ${items.map { it.label }}", items.map { it.label }.contains("Core.dsm"))
    }
}
