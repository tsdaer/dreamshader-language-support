package com.github.tsdaer.dreamshaderlanguagesupport.language.parser

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShader2Lexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2FunctionDeclarationImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2IncludeDirectiveImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2PragmaDirectiveImpl
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.DreamShader2UniformDeclarationImpl
import com.intellij.lang.ASTNode
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * Structure tests for the 2.0 parser. The parser is permissive by design and emits no error
 * elements (red line: the plugin is not the compiler); these tests pin the node shapes the editor
 * features consume and the verbatim `/// @custom` capture.
 */
class DreamShader2PsiParserTest : BasePlatformTestCase() {

    private fun parseV2(text: String): ASTNode =
        DreamShader2PsiParser().parse(
            DreamShaderElementTypes.FILE,
            PsiBuilderFactoryImpl().createBuilder(DreamShaderParserDefinition(), DreamShader2Lexer(), text),
        )

    private fun collectNodes(root: ASTNode, type: IElementType): List<ASTNode> = buildList {
        fun walk(node: ASTNode) {
            if (node.elementType == type) add(node)
            var child = node.firstChildNode
            while (child != null) {
                walk(child)
                child = child.treeNext
            }
        }
        walk(root)
    }

    private fun countNodes(root: ASTNode, type: IElementType): Int = collectNodes(root, type).size

    private fun createElement(node: ASTNode): PsiElement = DreamShaderParserDefinition().createElement(node)

    private fun assertNoErrors(file: PsiFile) {
        val errors = PsiTreeUtil.collectElementsOfType(file, PsiErrorElement::class.java)
        assertTrue(errors.isEmpty(), "Expected no parser errors, got: ${errors.map { it.errorDescription }}")
    }

    // ------------------------------------------------------------------
    // declarations
    // ------------------------------------------------------------------

    fun testUniformDeclarationCarriesDocBlockAndTypedPsi() {
        val text = """
            /// @group Glow|Look   @desc Multiplied on top
            uniform float4 Tint = float4(1, 1, 1, 1);

            /// @static
            uniform bool UseDetail = true;
        """.trimIndent()
        val root = parseV2(text)

        assertEquals(2, countNodes(root, DreamShader2ElementTypes.UNIFORM_DECLARATION))
        assertEquals(2, countNodes(root, DreamShader2ElementTypes.DOC_BLOCK))

        val uniforms = PsiTreeUtil.collectElementsOfType(root.psi, DreamShader2UniformDeclarationImpl::class.java)
        assertEquals(2, uniforms.size)
        val tint = uniforms.first { it.declarationName() == "Tint" }
        assertEquals("float4", tint.typeName())
        assertFalse(tint.isStaticSwitch())
        val useDetail = uniforms.first { it.declarationName() == "UseDetail" }
        assertTrue(useDetail.isStaticSwitch())
    }

    fun testGroupedDeclaratorsStayOneNode() {
        val root = parseV2("uniform float a, b = 2, c;\n")
        val uniforms = PsiTreeUtil.collectElementsOfType(root.psi, DreamShader2UniformDeclarationImpl::class.java)
        assertEquals(1, uniforms.size)
        assertEquals("a", uniforms.single().declarationName())
    }

    fun testExportFunctionWithNamedArguments() {
        val text = """
            export void M_TeleportGlow(inout material m)
            {
                float2 UV = UE.TexCoord(Index = 0);
                m.EmissiveColor = Tint.rgb * GlowMask(UV) * Intensity;
            }
        """.trimIndent()
        val root = parseV2(text)

        val functions = PsiTreeUtil.collectElementsOfType(root.psi, DreamShader2FunctionDeclarationImpl::class.java)
        assertEquals(1, functions.size)
        assertTrue(functions.single().isExport())
        assertFalse(functions.single().isExtern())
        assertEquals("M_TeleportGlow", functions.single().declarationName())

        assertTrue(countNodes(root, DreamShader2ElementTypes.CALL_EXPRESSION) >= 1)
        assertEquals(1, countNodes(root, DreamShader2ElementTypes.NAMED_ARGUMENT))
        assertTrue(countNodes(root, DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION) >= 1)
    }

    fun testExternPrototypeHasNoBody() {
        val text = """
            /// @asset /MoonToon/MaterialFunctions/Utils/MF_UVChannelSwitch
            extern float2 MF_UVChannelSwitch(float UVChannelIndex);
        """.trimIndent()
        val root = parseV2(text)
        val function = PsiTreeUtil.collectElementsOfType(root.psi, DreamShader2FunctionDeclarationImpl::class.java)
            .single()
        assertTrue(function.isExtern())
        assertEquals("MF_UVChannelSwitch", function.declarationName())
        assertEquals(0, countNodes(root, DreamShader2ElementTypes.BLOCK))
    }

    fun testCustomBodyIsCapturedVerbatim() {
        val text = """
            /// @custom selfcontained
            /// @desc Braces inside strings must not end the raw body.
            float4 RawBody(float2 UV)
            {
                #include "/Engine/Private/Common.ush"
                // a closing brace } and an opening one {
                float4 Result = ${'$'}Globals.Tint @ UV.x # 2 `;
                printf("a closing brace } inside a string");
                return Result;
            }
        """.trimIndent()
        val root = parseV2(text)

        val body = requireNotNull(collectNodes(root, DreamShader2ElementTypes.CUSTOM_BODY).singleOrNull())
        val bodyText = body.text
        assertTrue("\$Globals.Tint" in bodyText, "Register name must survive verbatim: $bodyText")
        assertTrue("} inside a string" in bodyText, "String brace must survive: $bodyText")
        assertTrue(bodyText.trimEnd().endsWith("}"), "Capture must end at the final brace: $bodyText")
    }

    fun testStructDeclarationWithFieldsAndArrayDimensions() {
        val text = """
            struct ToonInputs
            {
                /// @desc Albedo in linear space.
                float3 Albedo;
                float  NdotL;
                float2 RampUV[4];
                int    Flags[2][3];
            };
        """.trimIndent()
        val root = parseV2(text)
        assertEquals(1, countNodes(root, DreamShader2ElementTypes.STRUCT_DECLARATION))
        assertEquals(4, countNodes(root, DreamShader2ElementTypes.STRUCT_FIELD))
    }

    fun testDirectivesBecomeNodes() {
        val text = """
            #pragma material(ShadingModel = Unlit, BlendMode = Additive)
            #pragma region Sampling
            import "Shared/Common.dsh";
        """.trimIndent()
        val root = parseV2(text)

        val pragma = PsiTreeUtil.collectElementsOfType(root.psi, DreamShader2PragmaDirectiveImpl::class.java)
            .firstOrNull { it.pragmaName() == "material" }
        assertTrue(pragma != null, "pragma material node expected")

        val include = PsiTreeUtil.collectElementsOfType(root.psi, DreamShader2IncludeDirectiveImpl::class.java)
            .firstOrNull()
        assertTrue(include != null, "import include node expected")
        assertEquals("Shared/Common.dsh", include?.pathText())
    }

    fun testMixedHeaderCapturesLegacyBlockAndKeepsV2Declarations() {
        val text = """
            uniform float SharedGain = 1.0;

            Shader(Name="Materials/M_Legacy")
            {
                Outputs {
                    vec3 Color;
                }
            }

            export void M_New(inout material m)
            {
                m.BaseColor = SharedGain;
            }
        """.trimIndent()
        val file = myFixture.configureByText("Mixed.dsh", text)
        assertNoErrors(file)

        assertEquals(1, countNodes(file.node, DreamShader2ElementTypes.LEGACY_BLOCK))
        assertEquals(1, countNodes(file.node, DreamShader2ElementTypes.UNIFORM_DECLARATION))
        assertEquals(1, countNodes(file.node, DreamShader2ElementTypes.FUNCTION_DECLARATION))
    }

    // ------------------------------------------------------------------
    // statements
    // ------------------------------------------------------------------

    fun testForVariantsAndControlFlow() {
        val text = """
            export float ForVariants(float A)
            {
                float Sum = 0.0;
                for (int i = 0; i < 4; ++i) { Sum += A; }
                for (;;) { break; }
                for (int k = 0; ; ++k) { if (k > 2) { break; } }
                int n = 3;
                while (n > 0) n = n - 1;
                do { n++; } while (n < 3);
                if (A > 0.0) Sum += 1.0; else Sum -= 1.0;
                return Sum;
            }
        """.trimIndent()
        val file = myFixture.configureByText("ForVariants.dss", text)
        assertNoErrors(file)
        assertEquals(3, countNodes(file.node, DreamShader2ElementTypes.FOR_STATEMENT))
        assertEquals(1, countNodes(file.node, DreamShader2ElementTypes.WHILE_STATEMENT))
        assertEquals(1, countNodes(file.node, DreamShader2ElementTypes.DO_WHILE_STATEMENT))
        // `if (k > 2)` inside the third for plus the top-level if/else
        assertEquals(2, countNodes(file.node, DreamShader2ElementTypes.IF_STATEMENT))
        assertEquals(1, countNodes(file.node, DreamShader2ElementTypes.RETURN_STATEMENT))
    }

    fun testCastsConstructorsAndPinIndexArguments() {
        val text = """
            export float Casts(float A, int I, float3 V)
            {
                int ToInt = (int)A;
                float3 ToVec = (float3)A;
                float Chained = (float)(int)A;
                m.EmissiveColor = UE.Expression(Class = "LinearInterpolate", Pin[0] = float3(1, 0, 0));
                return ToInt + ToVec.x + Chained;
            }
        """.trimIndent()
        val file = myFixture.configureByText("Casts.dss", text)
        assertNoErrors(file)
        // (int)A + (float3)A + (float)(int)A - the chained cast contributes two
        assertEquals(4, countNodes(file.node, DreamShader2ElementTypes.CAST_EXPRESSION))
        assertEquals(1, countNodes(file.node, DreamShader2ElementTypes.CONSTRUCTOR_EXPRESSION))
        assertEquals(2, countNodes(file.node, DreamShader2ElementTypes.NAMED_ARGUMENT))
    }

    // ------------------------------------------------------------------
    // full pipeline via extension routing
    // ------------------------------------------------------------------

    fun testDsiInstanceRoutesThroughTheV2Pipeline() {
        val text = """
            #pragma instance(Parent = "M_Skin", BlendMode = Masked, TwoSided = true)

            uniform float Roughness = 0.35;

            /// @static
            uniform bool UseDetail = true;

            /// @default /Game/Characters/Hero/T_Hero_Skin_D
            uniform Texture2D Albedo;
        """.trimIndent()
        val file = myFixture.configureByText("MI_HeroSkin.dsi", text)
        assertNoErrors(file)
        assertEquals(3, countNodes(file.node, DreamShader2ElementTypes.UNIFORM_DECLARATION))
        assertEquals(1, countNodes(file.node, DreamShader2ElementTypes.PRAGMA_DIRECTIVE))
    }

    fun testV1FileKeepsLegacyTree() {
        val text = """
            Shader MySurface {
                Inputs {
                    float2 UV;
                }
            }
        """.trimIndent()
        val file = myFixture.configureByText("M_Surface.dsm", text)
        assertNoErrors(file)
        assertEquals(1, countNodes(file.node, DreamShaderElementTypes.DECLARATION))
        assertEquals(1, countNodes(file.node, DreamShaderElementTypes.SECTION))
        assertEquals(0, countNodes(file.node, DreamShader2ElementTypes.UNIFORM_DECLARATION))
    }
}
