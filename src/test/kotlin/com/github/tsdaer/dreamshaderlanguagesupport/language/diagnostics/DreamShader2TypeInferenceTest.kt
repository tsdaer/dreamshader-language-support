package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * C2 验收：类型推断覆盖上游文档示例（构造器/类型转换/swizzle 收缩/材质与 Substrate/
 * 纹理采样/目录驱动 UE.* 返回值），且推断失败必须静默为 Unknown、绝不产生 error 级诊断。
 */
class DreamShader2TypeInferenceTest : BasePlatformTestCase() {

    private val fileBody = """
        #pragma material(ShadingModel = Unlit)

        uniform float4 Tint = float4(1, 1, 1, 1);
        uniform float Intensity = 0.7;
        uniform Texture2D Albedo;

        float2 GlowMask(float2 UV)
        {
            float2 P = UV * 2.0 - 1.0;
            return P;
        }

        export void M_Entry(inout material m)
        {
            float2 UV = UE.TexCoord(Index = 0);
            float4 Tex = Albedo.Sample(UE.TexCoord(Index = 0));
            float2 Mask = GlowMask(UV) * Intensity;
            float3 Swizzled = Tint.rgb;
            float Scaled = (float)Mask.x + 1;
            bool Flag = Intensity > 0.5;
            m.FrontMaterial = Tint.rgb;
        }
    """.trimIndent()

    private fun openFile(text: String = fileBody, name: String = "M_Types.dss"): DreamShaderPsiFile =
        myFixture.configureByText(name, text) as DreamShaderPsiFile

    /** The right-hand side of the one statement whose text matches [statementText], typed. */
    private fun typedValueOf(file: DreamShaderPsiFile, statementText: String): DreamShader2Type {
        val context = DreamShader2InferenceContext.forFile(file, null)
        val statement = psiTreeWalk(file).firstOrNull {
            (it.node.elementType == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT ||
                it.node.elementType == DreamShader2ElementTypes.EXPRESSION_STATEMENT) &&
                it.text.replace(Regex("\\s+"), " ").contains(statementText)
        } ?: error("statement not found: $statementText")
        var scanRoot = statement
        if (statement.node.elementType == DreamShader2ElementTypes.EXPRESSION_STATEMENT) {
            scanRoot = statement.node.findChildByType(DreamShader2ElementTypes.EXPRESSION)?.psi
                ?: error("no expression in: $statementText")
        }
        val children = com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren(scanRoot)
        val assignIndex = children.indexOfFirst {
            it.elementType == com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.OPERATOR &&
                it.text == "="
        }
        require(assignIndex in 0 until children.lastIndex) { "no initializer in: $statementText" }
        return context.typeOf(children[assignIndex + 1].psi)
    }

    fun testLiteralAndSpellingParsing() {
        assertEquals(
            DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT),
            DreamShader2Type.fromSpelling("float"),
        )
        assertEquals(
            // 索引与目录里的上游写法 float1
            DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT),
            DreamShader2Type.fromSpelling("float1"),
        )
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 4),
            DreamShader2Type.fromSpelling("float4"),
        )
        assertEquals(
            DreamShader2Type.Matrix(DreamShader2Type.Family.FLOAT, 4, 4),
            DreamShader2Type.fromSpelling("float4x4"),
        )
        assertEquals(DreamShader2Type.Texture("Texture2D"), DreamShader2Type.fromSpelling("Texture2D"))
        assertEquals(DreamShader2Type.Substrate, DreamShader2Type.fromSpelling("Substrate"))
        assertEquals(DreamShader2Type.Material, DreamShader2Type.fromSpelling("material"))
        assertEquals(DreamShader2Type.Void, DreamShader2Type.fromSpelling("void"))
        assertTrue(DreamShader2Type.fromSpelling("MyStruct") is DreamShader2Type.StructType)
    }

    fun testFileScopeAndParameterTypes() {
        val file = openFile()
        val context = DreamShader2InferenceContext.forFile(file, null)
        val anyUseSite = DreamShader2FileAnalysis.of(file).functionByName("GlowMask")!!
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 4),
            context.typeOfName("Tint", anyUseSite),
        )
        assertEquals(
            DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT),
            context.typeOfName("Intensity", anyUseSite),
        )
        assertEquals(
            DreamShader2Type.Texture("Texture2D"),
            context.typeOfName("Albedo", anyUseSite),
        )
        val parameter = anyUseSite.parameterList()!!.parameters().single()
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 2),
            parameter.typeName()?.let(DreamShader2Type::fromSpelling),
        )
    }

    fun testExpressionFormsInferUpstreamExamples() {
        val file = openFile()
        // 目录驱动：UE.TexCoord → float2（bundled fallback catalog）
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 2),
            typedValueOf(file, "float2 UV ="),
        )
        // 纹理采样：Texture2D.Sample → float4
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 4),
            typedValueOf(file, "float4 Tex ="),
        )
        // 向量 ⊕ 标量 → 向量；用户函数返回值
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 2),
            typedValueOf(file, "float2 Mask ="),
        )
        // swizzle 收缩：float4.rgb → float3
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            typedValueOf(file, "float3 Swizzled ="),
        )
        // 类型转换 + 标量提升
        assertEquals(
            DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT),
            typedValueOf(file, "float Scaled ="),
        )
        // 比较运算 → bool
        assertEquals(
            DreamShader2Type.Scalar(DreamShader2Type.Family.BOOL),
            typedValueOf(file, "bool Flag ="),
        )
        // material 属性表：赋值语句取 RHS 侧类型（Tint.rgb → float3）
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            typedValueOf(file, "m.FrontMaterial ="),
        )
    }

    fun testInferenceFailureIsSilent() {
        val file = openFile()
        val context = DreamShader2InferenceContext.forFile(file, null)
        assertEquals(DreamShader2Type.Unknown, context.typeOfName("NoSuchName", file))
        val errors = myFixture.doHighlighting(HighlightSeverity.ERROR).mapNotNull { it.description }
        assertTrue(errors.isEmpty(), "inference must never produce errors, got: $errors")
    }

    fun testConstructorCastAndSubstrateTyping() {
        val file = openFile(
            """
            export void M_Sub(inout material m)
            {
                float4 Base = float4(1, 1, 1, 1);
                float3 Narrowed = float3(Base);
                Substrate Slab = Substrate.Slab();
                Substrate Mixed = lerp(Slab, Slab, 0.5);
            }
            """.trimIndent(),
            "M_Substrate.dss",
        )
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 4),
            typedValueOf(file, "float4 Base ="),
        )
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            typedValueOf(file, "float3 Narrowed ="),
        )
        assertEquals(DreamShader2Type.Substrate, typedValueOf(file, "Substrate Slab ="))
        assertEquals(DreamShader2Type.Substrate, typedValueOf(file, "Substrate Mixed ="))
    }
}
