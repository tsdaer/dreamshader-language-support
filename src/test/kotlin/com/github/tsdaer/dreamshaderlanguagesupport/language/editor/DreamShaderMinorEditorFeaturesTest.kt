package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderBreadcrumbsInfoProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderQualifiedNameProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.github.tsdaer.dreamshaderlanguagesupport.language.templates.DreamShaderDefaultTemplatePropertiesProvider
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderMinorEditorFeaturesTest : BasePlatformTestCase() {
    fun testTemplateIdentifierDerivation() {
        val provider = DreamShaderDefaultTemplatePropertiesProvider()
        assertEquals("M_Test_Material", provider.testIdentifier("M-Test Material"))
        assertEquals("_123Start", provider.testIdentifier("123Start"))
    }

    fun testIncludeProviderExtractsImports() {
        val provider = DreamShaderFileIncludeProvider()
        val imports = provider.testIncludeInfos(
            """
            import "Shared/Common.dsh";
            import "@scope/pkg/Library/Main.dsh";
            Shader Main {}
            """.trimIndent()
        )

        assertEquals(listOf("Shared/Common.dsh", "@scope/pkg/Library/Main.dsh"), imports)
    }

    fun testQualifiedNameProviderForNamespaceMemberAndSection() {
        val file = myFixture.configureByText(
            "qualified_name.dsh",
            """
            Namespace Tools {
                Function ApplyTint {
                    Graph {
                    }
                }
            }
            """.trimIndent()
        )
        val declaration = PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java)
            .first { it.declarationName() == "ApplyTint" }
        val section = PsiTreeUtil.findChildOfType(declaration, DreamShaderSection::class.java)!!
        val provider = DreamShaderQualifiedNameProvider()

        assertEquals("Tools::ApplyTint", provider.getQualifiedName(declaration))
        assertEquals("Tools::ApplyTint#Graph", provider.getQualifiedName(section))
    }

    fun testBreadcrumbLabels() {
        val file = myFixture.configureByText(
            "breadcrumbs.dsm",
            """
            Shader Main {
                Graph {
                }
            }
            """.trimIndent()
        )
        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)!!
        val section = PsiTreeUtil.findChildOfType(file, DreamShaderSection::class.java)!!
        val provider = DreamShaderBreadcrumbsInfoProvider()

        assertEquals("Shader Main", provider.getElementInfo(declaration))
        assertEquals("Graph", provider.getElementInfo(section))
    }

    fun testColorProviderRecognizesConstantVectorColors() {
        val provider = DreamShaderColorProvider()
        val color = provider.testColorCall("float4(1.0, 0.5, 0.0, 0.25)")

        assertNotNull(color)
        assertEquals(255, color!!.red)
        assertEquals(127, color.green)
        assertEquals(0, color.blue)
        assertEquals(63, color.alpha)
        assertNull(provider.testColorCall("float3(foo, 0.5, 0.0)"))
    }
}
