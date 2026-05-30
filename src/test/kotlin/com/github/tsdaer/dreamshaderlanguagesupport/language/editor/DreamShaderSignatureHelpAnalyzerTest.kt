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
        assertEquals("Blend", call!!.functionName)

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
}
