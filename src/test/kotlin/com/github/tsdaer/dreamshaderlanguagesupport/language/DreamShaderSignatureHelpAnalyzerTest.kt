package com.github.tsdaer.dreamshaderlanguagesupport.language

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
}
