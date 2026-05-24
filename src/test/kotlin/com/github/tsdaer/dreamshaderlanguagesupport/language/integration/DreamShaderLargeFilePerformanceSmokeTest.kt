package com.github.tsdaer.dreamshaderlanguagesupport.language.integration
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
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
