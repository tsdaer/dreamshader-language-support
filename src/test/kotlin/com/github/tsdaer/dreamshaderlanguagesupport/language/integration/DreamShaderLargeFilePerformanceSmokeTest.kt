package com.github.tsdaer.dreamshaderlanguagesupport.language.integration
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCompletionSuggester
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionManifest
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.V1_IMPORT_EXTENSIONS
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.collectCallableCandidates
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.collectProjectImportCandidates
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderParserDefinition
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderPsiParser
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlin.system.measureTimeMillis

class DreamShaderLargeFilePerformanceSmokeTest : BasePlatformTestCase() {
    fun testLargeFileHighlightingSmoke() {
        val text = buildLargeMaterialFile(declarationCount = 220, graphStatements = 10)
        myFixture.configureByText("LargeHighlightingSmoke.dsm", text)

        val elapsed = measureTimeMillis {
            myFixture.doHighlighting(HighlightSeverity.ERROR)
        }

        assertTrue(
            "Highlighting smoke test exceeded budget: ${elapsed}ms",
            elapsed < 30_000
        )
    }

    fun testLargeFileParserSmoke() {
        val text = buildLargeMaterialFile(declarationCount = 1_000, graphStatements = 6)
        val parserDefinition = DreamShaderParserDefinition()
        val builder = PsiBuilderFactoryImpl().createBuilder(parserDefinition, DreamShaderLexer(), text)

        val elapsed = measureTimeMillis {
            DreamShaderPsiParser().parse(DreamShaderElementTypes.FILE, builder)
        }

        assertTrue(
            "Parser smoke test exceeded budget: ${elapsed}ms",
            elapsed < 15_000
        )
    }

    fun testLargeFileCompletionSmoke() {
        val text = buildLargeMaterialFile(declarationCount = 320, graphStatements = 8) + """
            Shader(Name="Materials/CompletionTail") {
                Graph = {
                    UE.
                }
            }
        """.trimIndent()
        val offset = text.lastIndexOf("UE.") + "UE.".length

        var labels = emptySet<String>()
        val elapsed = measureTimeMillis {
            labels = DreamShaderCompletionSuggester
                .suggest(text, offset)
                .map { it.label }
                .toSet()
        }

        assertTrue(labels.contains("Expression"))
        assertTrue(
            "Completion smoke test exceeded budget: ${elapsed}ms",
            elapsed < 10_000
        )
    }

    /**
     * C15: the production hot path - file-backed context analysis, the merged callable
     * candidates, and the (lazily collected, cached) import candidates - over a large file.
     */
    fun testLargeFileCompletionViaPsiHotPathSmoke() {
        myFixture.addFileToProject("Common/Lib.dsm", "Shader Lib {\n}\n")
        val text = buildLargeMaterialFile(declarationCount = 320, graphStatements = 8) + """
            Shader(Name="Materials/CompletionTail") {
                Graph = {
                    UE.
                }
            }
        """.trimIndent()
        val file = myFixture.configureByText("LargeCompletionPsiSmoke.dsm", text)
        val offset = text.lastIndexOf("UE.") + "UE.".length

        var labels = emptySet<String>()
        val elapsed = measureTimeMillis {
            repeat(2) {
                labels = DreamShaderCompletionSuggester
                    .suggest(
                        file,
                        offset,
                        importCandidates = { collectProjectImportCandidates(file, V1_IMPORT_EXTENSIONS) },
                        callableCandidates = collectCallableCandidates(file),
                        materialExpressionCatalogEntries = DreamShaderMaterialExpressionManifest.catalogEntries(project, null),
                    )
                    .map { it.label }
                    .toSet()
            }
        }

        assertTrue(labels.contains("Expression"))
        assertTrue(labels.isNotEmpty())
        assertTrue(
            "Completion hot-path smoke test exceeded budget: ${elapsed}ms",
            elapsed < 10_000
        )
    }

    private fun buildLargeMaterialFile(
        declarationCount: Int,
        graphStatements: Int
    ): String {
        val sb = StringBuilder(declarationCount * 420)
        repeat(declarationCount) { index ->
            sb.append("Shader(Name=\"Materials/M_").append(index).append("\") {\n")
            sb.append("    Inputs = {\n")
            sb.append("        float2 UV").append(index).append(";\n")
            sb.append("        float Strength").append(index).append(" = 1.0;\n")
            sb.append("    }\n")
            sb.append("    Outputs = {\n")
            sb.append("        float3 Color").append(index).append(";\n")
            sb.append("    }\n")
            sb.append("    Settings = {\n")
            sb.append("        Domain = Surface;\n")
            sb.append("        ShadingModel = DefaultLit;\n")
            sb.append("        BlendMode = Opaque;\n")
            sb.append("    }\n")
            sb.append("    Graph = {\n")
            repeat(graphStatements) { line ->
                sb.append("        float3 T").append(line).append(" = float3(1.0, 0.5, 0.25);\n")
            }
            sb.append("        Color").append(index).append(" = float3(1.0, 1.0, 1.0);\n")
            sb.append("        Base.BaseColor = Color").append(index).append(";\n")
            sb.append("    }\n")
            sb.append("}\n\n")
        }
        return sb.toString()
    }
}
