package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Milestone M12 C4: the 2.0 semantic token classifier - three-level identifier colors
 * (uniform / function parameter / locals), Substrate/material variable values, and the
 * 1.x zero-regression rule (V1 files never take the V2 branches).
 */
class DreamShader2SemanticClassifierTest : BasePlatformTestCase() {
    private fun classifyAt(file: com.intellij.psi.PsiFile, offset: Int): com.intellij.openapi.editor.colors.TextAttributesKey? {
        val leaf = file.findElementAt((offset - 1).coerceAtLeast(0))!!
        return DreamShaderSemanticTokenClassifier.classify(leaf)
    }

    fun testUniformParameterNameColored() {
        val file = myFixture.configureByText(
            "uniform.dss",
            "uniform float3 Tint = float3(1.0, 1.0, 1.0);\n",
        )
        val key = classifyAt(file, file.text.indexOf("Tint") + 1)
        assertEquals(DreamShaderTextAttributes.UNIFORM_PARAMETER, key)
    }

    fun testUniformInitializerUseNotColoredAsParameter() {
        val file = myFixture.configureByText(
            "uniform_init.dss",
            "uniform float3 Tint = Other;\n",
        )
        val key = classifyAt(file, file.text.indexOf("Other") + 1)
        assertNotSame(DreamShaderTextAttributes.UNIFORM_PARAMETER, key)
    }

    fun testFunctionParameterNameColored() {
        val file = myFixture.configureByText(
            "params.dss",
            "export float3 Blend(float a, float b) {\n    return a;\n}\n",
        )
        val aKey = classifyAt(file, file.text.indexOf("float a") + "float a".length)
        assertEquals(DreamShaderTextAttributes.FUNCTION_PARAMETER, aKey)
    }

    fun testSubstrateVariableUsagesColored() {
        val file = myFixture.configureByText(
            "substrate.dss",
            """
            export void Work() {
                Substrate S = Substrate.Slab();
                float3 Other = float3(0.0, 0.0, 0.0);
                S.Roughness = 0.2;
                Other.x = S.Roughness;
            }
            """.trimIndent()
        )
        val sUse = classifyAt(file, file.text.indexOf("S.Roughness") + 1)
        assertEquals("Substrate variable usage should be colored", DreamShaderTextAttributes.SUBSTRATE_VALUE, sUse)

        val otherUse = classifyAt(file, file.text.indexOf("Other.x") + 1)
        assertNotSame("Plain locals keep the shared color", DreamShaderTextAttributes.SUBSTRATE_VALUE, otherUse)
    }

    fun testV1FilesSkipV2Classification() {
        val file = myFixture.configureByText(
            "v1.dsf",
            "Shader Main {\n    Inputs {\n        float2 UV;\n    }\n}\n",
        )
        val declaration = com.intellij.psi.util.PsiTreeUtil.findChildrenOfType(file, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration::class.java).first()
        val leaf = file.findElementAt(declaration.textRange.startOffset + "Shader ".length)!!
        // A V1 identifier is never classified by the V2 rules: exercise the guard directly.
        assertFalse(DreamShader2SemanticClassifier.isV2File(leaf))
    }

    fun testV2GuardAcceptsDssAndDsi() {
        val dss = myFixture.configureByText("guard.dss", "uniform float A;\n")
        val leafDss = dss.findElementAt(1)!!
        assertTrue(DreamShader2SemanticClassifier.isV2File(leafDss))

        val dsi = myFixture.configureByText("guard.dsi", "uniform float A;\n")
        val leafDsi = dsi.findElementAt(1)!!
        assertTrue(DreamShader2SemanticClassifier.isV2File(leafDsi))
    }
}
