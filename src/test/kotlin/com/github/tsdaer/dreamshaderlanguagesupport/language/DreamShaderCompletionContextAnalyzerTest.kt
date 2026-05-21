package com.github.tsdaer.dreamshaderlanguagesupport.language

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamShaderCompletionContextAnalyzerTest {
    @Test
    fun `top level context detected`() {
        val text = """
            import "Common/Core.dsh";
            
            
        """.trimIndent()

        val context = DreamShaderCompletionContextAnalyzer.analyze(text, text.length)
        assertTrue(context.isTopLevel)
        assertFalse(context.isInDeclarationBody)
        assertFalse(context.isInCommentOrString)
        assertFalse(context.isTypeCompletionContext)
    }

    @Test
    fun `declaration body context detected`() {
        val text = """
            Shader MySurface {
                
            }
        """.trimIndent()
        val offset = text.indexOf('\n') + 1

        val context = DreamShaderCompletionContextAnalyzer.analyze(text, offset)
        assertFalse(context.isTopLevel)
        assertTrue(context.isInDeclarationBody)
        assertFalse(context.isInCommentOrString)
        assertFalse(context.isTypeCompletionContext)
    }

    @Test
    fun `section body is not declaration body context`() {
        val text = """
            Shader MySurface {
                Settings {
                    
                }
            }
        """.trimIndent()
        val marker = "Settings {\n"
        val offset = text.indexOf(marker) + marker.length

        val context = DreamShaderCompletionContextAnalyzer.analyze(text, offset)
        assertFalse(context.isTopLevel)
        assertFalse(context.isInDeclarationBody)
        assertFalse(context.isInCommentOrString)
        assertFalse(context.isTypeCompletionContext)
    }

    @Test
    fun `comment and string contexts are ignored`() {
        val commentText = """
            Shader MySurface {
                // Graph
            }
        """.trimIndent()
        val commentOffset = commentText.indexOf("Graph") + "Graph".length
        val commentContext = DreamShaderCompletionContextAnalyzer.analyze(commentText, commentOffset)
        assertTrue(commentContext.isInCommentOrString)
        assertFalse(commentContext.isTypeCompletionContext)

        val stringText = """
            import "Graph";
        """.trimIndent()
        val stringOffset = stringText.indexOf("Graph") + "Graph".length
        val stringContext = DreamShaderCompletionContextAnalyzer.analyze(stringText, stringOffset)
        assertTrue(stringContext.isInCommentOrString)
        assertFalse(stringContext.isTypeCompletionContext)
    }

    @Test
    fun `type completion enabled in inputs and outputs sections`() {
        val inputsText = """
            Shader MySurface {
                Inputs {
                    
                }
            }
        """.trimIndent()
        val inputsMarker = "Inputs {\n"
        val inputsOffset = inputsText.indexOf(inputsMarker) + inputsMarker.length
        val inputsContext = DreamShaderCompletionContextAnalyzer.analyze(inputsText, inputsOffset)
        assertTrue(inputsContext.isTypeCompletionContext)

        val outputsText = """
            Shader MySurface {
                Outputs {
                    
                }
            }
        """.trimIndent()
        val outputsMarker = "Outputs {\n"
        val outputsOffset = outputsText.indexOf(outputsMarker) + outputsMarker.length
        val outputsContext = DreamShaderCompletionContextAnalyzer.analyze(outputsText, outputsOffset)
        assertTrue(outputsContext.isTypeCompletionContext)
    }

    @Test
    fun `type completion disabled in settings section`() {
        val text = """
            Shader MySurface {
                Settings {
                    
                }
            }
        """.trimIndent()
        val marker = "Settings {\n"
        val offset = text.indexOf(marker) + marker.length
        val context = DreamShaderCompletionContextAnalyzer.analyze(text, offset)
        assertFalse(context.isTypeCompletionContext)
    }

    @Test
    fun `type completion enabled in function declaration body`() {
        val text = """
            Function Util {
                
            }
        """.trimIndent()
        val offset = text.indexOf('\n') + 1
        val context = DreamShaderCompletionContextAnalyzer.analyze(text, offset)
        assertTrue(context.isInDeclarationBody)
        assertTrue(context.isTypeCompletionContext)
    }
}
