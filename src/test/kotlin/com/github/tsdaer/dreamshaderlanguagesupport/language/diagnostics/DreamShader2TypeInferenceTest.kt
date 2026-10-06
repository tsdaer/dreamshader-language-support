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

    // ------------------------------------------------------------------
    // C17 — ternary folding, compound assignments, include-closure helpers
    // ------------------------------------------------------------------

    fun testTernaryFoldsToBranchTypeNotConditionType() {
        val file = openFile(
            """
            export void M_Ternary(inout material m)
            {
                float3 A = float3(1, 0, 0);
                float3 B = float3(0, 1, 0);
                float3 R = true ? A : B;
            }
            """.trimIndent(),
            "M_Ternary.dss",
        )
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            typedValueOf(file, "float3 R ="),
        )
    }

    fun testTernaryPromotesScalarAndVectorArms() {
        val file = openFile(
            """
            export void M_TernaryPromote(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                float2 P = true ? 1.0 : UV;
            }
            """.trimIndent(),
            "M_TernaryPromote.dss",
        )
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 2),
            typedValueOf(file, "float2 P ="),
        )
    }

    fun testTernaryRightNestingMergesInnerFirst() {
        val file = openFile(
            """
            export void M_TernaryNested(inout material m)
            {
                float3 A = float3(1, 0, 0);
                float3 B = float3(0, 1, 0);
                float3 C = float3(0, 0, 1);
                float3 R = false ? A : true ? B : C;
            }
            """.trimIndent(),
            "M_TernaryNested.dss",
        )
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            typedValueOf(file, "float3 R ="),
        )
    }

    fun testCompoundAssignmentKeepsLeftHandShape() {
        val file = openFile(
            """
            export void M_Compound(inout material m)
            {
                float x = 0.5;
                float2 UV = UE.TexCoord(Index = 0);
                x += UV;
                UV *= 0.5;
            }
            """.trimIndent(),
            "M_Compound.dss",
        )
        val context = DreamShader2InferenceContext.forFile(file, null)
        val compoundStatements = psiTreeWalk(file).filter {
            it.node.elementType == DreamShader2ElementTypes.EXPRESSION_STATEMENT
        }.toList()
        val scalarStatement = compoundStatements.first { it.text.contains("+=") }
        val vectorStatement = compoundStatements.first { it.text.contains("*=") }
        assertEquals(
            DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT),
            context.typeOf(scalarStatement.node.findChildByType(DreamShader2ElementTypes.EXPRESSION)!!.psi),
        )
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 2),
            context.typeOf(vectorStatement.node.findChildByType(DreamShader2ElementTypes.EXPRESSION)!!.psi),
        )
    }

    fun testIncludeClosureHelperCallResolvesReturnType() {
        myFixture.addFileToProject(
            "DShader/Materials/TintHelpers.dsh",
            """
            uniform float HelperTint = 0.5;

            float3 TintScale(float x)
            {
                return float3(x, x, x);
            }
            """.trimIndent(),
        )
        val file = myFixture.configureByText(
            "M_Closure.dss",
            """
            #include "DShader/Materials/TintHelpers.dsh"

            export void M_Entry(inout material m)
            {
                float3 Tinted = TintScale(0.5);
            }
            """.trimIndent(),
        ) as DreamShaderPsiFile

        // The closure tier runs through the workspace service's include resolution.
        val workspace = file.project.getService(com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols::class.java)
        val context = DreamShader2InferenceContext.forFile(file, workspace)
        val call = psiTreeWalk(file).firstOrNull { it.node.elementType == DreamShader2ElementTypes.CALL_EXPRESSION }
            ?: error("call expression not found")
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            context.typeOf(call),
        )
    }

    fun testLocalResolutionRespectsLexicalScope() {
        val file = openFile(
            """
            export void M_Scope(inout material m)
            {
                if (true)
                {
                    float3 Shadowed = float3(0.0, 0.0, 0.0);
                }
                float x = Shadowed;
                float3 Visible = float3(0.0, 0.0, 0.0);
                float ok = Visible;
                for (int i = 0; i < 4; i++)
                {
                    float y = i;
                }
                float z = i;
                float v = 1.0;
                if (true)
                {
                    float2 v = float2(0.0, 0.0);
                    float a = v;
                }
            }
            """.trimIndent(),
            "M_Scope.dss",
        )
        // An earlier if-arm's declaration is out of scope: no confident type leaks across.
        assertEquals(DreamShader2Type.Unknown, typedValueOf(file, "float x ="))
        assertEquals(
            "same-block declarations stay visible",
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            typedValueOf(file, "float ok ="),
        )
        // A for-init declaration scopes to the loop: visible inside, gone after it.
        assertEquals(
            DreamShader2Type.Scalar(DreamShader2Type.Family.INT),
            typedValueOf(file, "float y ="),
        )
        assertEquals(DreamShader2Type.Unknown, typedValueOf(file, "float z ="))
        // Shadowing resolves innermost-first.
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 2),
            typedValueOf(file, "float a ="),
        )
    }

    fun testOperandTypedIntrinsicsExtendTheConfidentSet() {
        val file = openFile(
            """
            export void M_Intrinsics(inout material m)
            {
                float3 Base = float3(1.0, 2.0, 3.0);
                float3 Other = float3(1.0, 1.0, 1.0);
                float4 Wide = float4(1.0, 1.0, 1.0, 1.0);
                float3 N = normalize(Base);
                float D = dot(Base, Other);
                float B = dot(Base, Wide);
                float3 C = cross(Base, Other);
                float F = floor(Base.x);
                float U = reflect(Base, Other);
            }
            """.trimIndent(),
            "M_Intrinsics.dss",
        )
        assertEquals(
            "normalize carries the operand shape",
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            typedValueOf(file, "float3 N ="),
        )
        assertEquals("dot of equal shapes widens to a scalar", DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT), typedValueOf(file, "float D ="))
        assertEquals("dot of different shapes stays Unknown", DreamShader2Type.Unknown, typedValueOf(file, "float B ="))
        assertEquals(DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3), typedValueOf(file, "float3 C ="))
        assertEquals(DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT), typedValueOf(file, "float F ="))
        assertEquals("unlisted intrinsics stay Unknown", DreamShader2Type.Unknown, typedValueOf(file, "float U ="))
    }

    // ------------------------------------------------------------------
    // C30 out/inout write-back narrowing
    // ------------------------------------------------------------------

    private val writeBackBody = """
        void Fill(out float3 v, float s)
        {
            v = float3(s, s, s);
        }

        export void M_Entry(inout material m)
        {
            float x = 0.5;
            Fill(x, 1.0);
            float3 After = x;
            float2 Mixed = x.xy;
        }
    """.trimIndent()

    /** The `x` references of [file] in document order (call argument first, uses after). */
    private fun xReferencesOf(file: DreamShaderPsiFile): List<PsiElement> =
        psiTreeWalk(file).filter {
            it.node.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION && it.text == "x"
        }.toList()

    fun testOutWriteBackNarrowsAfterTheCall() {
        val file = openFile(writeBackBody, "M_WriteBack.dss")
        val context = DreamShader2InferenceContext.forFile(file, null)
        val references = xReferencesOf(file)
        // references: Fill's argument, the `float3 After = x` initializer, the `x.xy` swizzle base
        assertEquals(3, references.size)
        assertEquals(
            "the call argument keeps its declared type (pre-call)",
            DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT),
            context.typeOfName("x", references[0]),
        )
        assertEquals(
            "after the call the out formal's type holds",
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            context.typeOfName("x", references[1]),
        )
        assertEquals(
            "the swizzle base is narrowed too",
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            context.typeOfName("x", references[2]),
        )
    }

    fun testInoutWriteBackNarrowsAfterTheCall() {
        val file = openFile(
            """
            void MixInto(inout float2 v)
            {
                v = v * 2.0;
            }

            export void M_Entry(inout material m)
            {
                float x = 0.5;
                MixInto(x);
                float2 After = x;
            }
            """.trimIndent(),
            "M_InoutBack.dss",
        )
        val context = DreamShader2InferenceContext.forFile(file, null)
        val references = xReferencesOf(file)
        assertEquals(
            "an inout formal re-types the variable after the call",
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 2),
            context.typeOfName("x", references[1]),
        )
    }

    fun testNamedArgumentWriteBackHitsTheFormalByName() {
        val file = openFile(
            """
            void Fill(out float3 v, float s)
            {
                v = float3(s, s, s);
            }

            export void M_Entry(inout material m)
            {
                float x = 0.5;
                Fill(s = 1.0, v = x);
                float3 After = x;
            }
            """.trimIndent(),
            "M_NamedBack.dss",
        )
        val context = DreamShader2InferenceContext.forFile(file, null)
        val references = xReferencesOf(file)
        assertEquals(
            "the named out argument re-types x after the call",
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            context.typeOfName("x", references[1]),
        )
    }

    fun testUnknownFormalTypeStaysSilent() {
        val file = openFile(
            """
            void FillUnknown(out float5 v)
            {
                v = v;
            }

            export void M_Entry(inout material m)
            {
                float x = 0.5;
                FillUnknown(x);
                float After = x;
            }
            """.trimIndent(),
            "M_UnknownBack.dss",
        )
        val context = DreamShader2InferenceContext.forFile(file, null)
        val references = xReferencesOf(file)
        assertEquals(
            "an unparsable formal spelling never narrows",
            DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT),
            context.typeOfName("x", references[1]),
        )
    }

    fun testNearerRedeclarationBeatsTheWriteBack() {
        val file = openFile(
            """
            void Fill(out float3 v, float s)
            {
                v = float3(s, s, s);
            }

            export void M_Entry(inout material m)
            {
                float x = 0.5;
                Fill(x, 1.0);
                float x = 2.0;
                float After = x;
            }
            """.trimIndent(),
            "M_ShadowBack.dss",
        )
        val context = DreamShader2InferenceContext.forFile(file, null)
        val references = xReferencesOf(file)
        // references: Fill's argument, then the `float After = x` initializer (declaration
        // names are identifier leaves, not references).
        assertEquals(
            "the re-declared x answers with its own declared type",
            DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT),
            context.typeOfName("x", references[1]),
        )
    }

    fun testWriteBackFeedsChainedInference() {
        val file = openFile(
            """
            void Fill(out float3 v, float s)
            {
                v = float3(s, s, s);
            }

            export void M_Entry(inout material m)
            {
                float x = 0.5;
                Fill(x, 1.0);
                float3 Norm = normalize(x);
            }
            """.trimIndent(),
            "M_ChainBack.dss",
        )
        // The normalize argument carries the write-back type, so the result is a float3.
        assertEquals(
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3),
            typedValueOf(file, "float3 Norm ="),
        )
    }
}
