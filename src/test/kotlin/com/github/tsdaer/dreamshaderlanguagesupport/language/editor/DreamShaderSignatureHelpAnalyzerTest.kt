package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import org.junit.Assert.*
import org.junit.Test

class DreamShaderSignatureHelpAnalyzerTest {
    @Test
    fun `resolves call context for UE builtin`() {
        val text = """
            Shader Main {
                Graph {
                    float2 uv = UE.TexCoord(Index=0);
                }
            }
        """.trimIndent()
        val offset = text.indexOf("Index=0")

        val call = DreamShaderSignatureHelpAnalyzer.findCallContext(text, offset)
        assertNotNull(call)
        assertEquals("UE.TexCoord", call!!.functionName)

        val signatures = DreamShaderSignatureHelpAnalyzer.resolveSignatures(call.functionName)
        assertTrue(signatures.isNotEmpty())
        assertEquals("UE.TexCoord(Index=0)", signatures.first().presentableText)
    }

    @Test
    fun `computes parameter index for nested call arguments`() {
        val text = """
            Shader Main {
                Graph {
                    float2 uv = UE.Panner(Coordinate=UV, Time=UE.Time(Period=4.0), Speed=float2(0.1, 0.0));
                }
            }
        """.trimIndent()
        val speedOffset = text.indexOf("Speed=") + "Speed=".length
        val call = DreamShaderSignatureHelpAnalyzer.findCallContext(text, speedOffset)
        assertNotNull(call)

        val parameterIndex = DreamShaderSignatureHelpAnalyzer.parameterIndex(text, call!!, speedOffset)
        assertEquals(2, parameterIndex)
    }

    @Test
    fun `returns minus one when caret leaves call`() {
        val text = "float x = lerp(0, 1, 0.5);"
        val offset = text.indexOf(';')
        val call = DreamShaderSignatureHelpAnalyzer.findCallContext(text, offset)
        assertEquals(null, call)
    }

    @Test
    fun `resolves signatures from user function declarations`() {
        val text = """
            Function ApplyTint(in vec3 color, in vec3 tint, out vec3 result) {
                result = color * tint;
            }

            Shader Main {
                Graph {
                    ApplyTint(float3(1,1,1), float3(1,0,0), finalColor);
                }
            }
        """.trimIndent()

        val callOffset = text.indexOf("float3(1,1,1)")
        val call = DreamShaderSignatureHelpAnalyzer.findCallContext(text, callOffset)
        assertNotNull(call)
        assertEquals("ApplyTint", call!!.functionName)

        val signatures = DreamShaderSignatureHelpAnalyzer.resolveSignatures(call.functionName, text)
        assertEquals(1, signatures.size)
        assertEquals("ApplyTint(color, tint, result)", signatures.first().presentableText)
    }

    @Test
    fun `resolves signatures from namespaced calls using declared function`() {
        val text = """
            Function Blend(in float a, in float b, out float result) {
                result = lerp(a, b, 0.5);
            }

            Shader Main {
                Graph {
                    float outValue;
                    Utils::Blend(0.2, 0.6, outValue);
                }
            }
        """.trimIndent()

        val callOffset = text.indexOf("0.2")
        val call = DreamShaderSignatureHelpAnalyzer.findCallContext(text, callOffset)
        assertNotNull(call)
        assertEquals("Utils::Blend", call!!.functionName)

        val signatures = DreamShaderSignatureHelpAnalyzer.resolveSignatures(call.functionName, text)
        assertEquals(1, signatures.size)
        assertEquals("Blend(a, b, result)", signatures.first().presentableText)
    }

    @Test
    fun `prefers source text declarations before additional imported sources`() {
        val localText = """
            Function Blend(in float localA, in float localB, out float localResult) {
                localResult = lerp(localA, localB, 0.5);
            }
        """.trimIndent()
        val importedText = """
            Function Blend(in float importedA, in float importedB, out float importedResult) {
                importedResult = lerp(importedA, importedB, 0.5);
            }
        """.trimIndent()

        val signatures = DreamShaderSignatureHelpAnalyzer.resolveSignatures(
            functionName = "Blend",
            sourceText = localText,
            additionalSourceTexts = listOf(importedText)
        )
        assertEquals(1, signatures.size)
        assertEquals("Blend(localA, localB, localResult)", signatures.first().presentableText)
    }

    @Test
    fun `resolves catalog signature with explicit signature and parameters`() {
        val entries = listOf(
            DreamShaderMaterialExpressionInfo(
                namespace = "UE",
                className = "UMaterialExpressionDreamOnly",
                ueName = "DreamOnly",
                signature = "UE.DreamOnly(Input=Value, Scale=1.0)",
                parameters = listOf(
                    DreamShaderMaterialExpressionParameter("Input"),
                    DreamShaderMaterialExpressionParameter("Scale")
                ),
                source = DreamShaderMaterialExpressionSource.CONFIGURED_MANIFEST
            )
        )

        val signatures = DreamShaderSignatureHelpAnalyzer.resolveCatalogSignatures(
            functionName = "UE.DreamOnly",
            catalogEntries = entries,
            requireComplete = true
        )
        assertEquals(1, signatures.size)
        assertEquals("UE.DreamOnly(Input=Value, Scale=1.0)", signatures.first().presentableText)
        assertEquals(2, signatures.first().parameterRanges.size)
    }

    @Test
    fun `resolves catalog signature for non UE namespace`() {
        val entries = listOf(
            DreamShaderMaterialExpressionInfo(
                namespace = "Substrate",
                className = "UMaterialExpressionSubstrateSlabBSDF",
                ueName = "Slab",
                signature = "Substrate.Slab(BaseColor=Color)",
                parameters = listOf(DreamShaderMaterialExpressionParameter("BaseColor")),
                outputType = "Substrate",
                source = DreamShaderMaterialExpressionSource.CONFIGURED_MANIFEST
            )
        )

        val signatures = DreamShaderSignatureHelpAnalyzer.resolveCatalogSignatures(
            functionName = "Substrate.Slab",
            catalogEntries = entries,
            requireComplete = true
        )
        assertEquals(1, signatures.size)
        assertEquals("Substrate.Slab(BaseColor=Color)", signatures.first().presentableText)
    }

    @Test
    fun `skips incomplete catalog entry when complete signature required`() {
        val entries = listOf(
            DreamShaderMaterialExpressionInfo(
                namespace = "UE",
                className = "UMaterialExpressionSparse",
                ueName = "Sparse",
                signature = "UE.Sparse(...)",
                source = DreamShaderMaterialExpressionSource.BUNDLED_FALLBACK
            )
        )

        val requireComplete = DreamShaderSignatureHelpAnalyzer.resolveCatalogSignatures(
            functionName = "UE.Sparse",
            catalogEntries = entries,
            requireComplete = true
        )
        assertTrue(requireComplete.isEmpty())

        val allowIncomplete = DreamShaderSignatureHelpAnalyzer.resolveCatalogSignatures(
            functionName = "UE.Sparse",
            catalogEntries = entries,
            requireComplete = false
        )
        assertEquals(1, allowIncomplete.size)
        assertEquals("UE.Sparse(...)", allowIncomplete.first().presentableText)
    }

    @Test
    fun `signature stores structured parameter names`() {
        val signatures = DreamShaderSignatureHelpAnalyzer.resolveSignatures("UE.Panner")

        assertEquals(listOf("Coordinate", "Time", "Speed"), signatures.first().parameterNames)
    }

    @Test
    fun `finds namespace qualified call at identifier name`() {
        val text = """
            Shader Main {
                Graph {
                    Tools::ApplyTint(float3(1,1,1), tint, finalColor);
                }
            }
        """.trimIndent()
        val start = text.indexOf("ApplyTint")
        val call = DreamShaderSignatureHelpAnalyzer.findCallAtName(text, start, start + "ApplyTint".length)

        assertNotNull(call)
        assertEquals("Tools::ApplyTint", call!!.functionName)
        assertEquals(3, call.arguments.size)
        assertEquals("float3(1,1,1)", call.arguments.first().text)
    }

    @Test
    fun `does not resolve call context from comments or strings`() {
        val commentText = "// UE.TexCoord(0"
        val stringText = "float label = \"UE.TexCoord(0\";"

        assertNull(DreamShaderSignatureHelpAnalyzer.findEnclosingCall(commentText, commentText.length))
        assertNull(DreamShaderSignatureHelpAnalyzer.findEnclosingCall(stringText, stringText.indexOf("0") + 1))
    }
}
