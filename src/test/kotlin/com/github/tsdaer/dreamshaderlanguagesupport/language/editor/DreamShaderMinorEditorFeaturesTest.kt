package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeDiagnostic
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderBreadcrumbsInfoProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderDeclarationRangeHandler
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderNameSuggestionProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderPlainTextSymbolCompletionContributor
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderQualifiedNameProvider
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.github.tsdaer.dreamshaderlanguagesupport.language.templates.DreamShaderDefaultTemplatePropertiesProvider
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Files

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

    fun testQualifiedNameProviderResolvesFqnBackToElement() {
        val file = myFixture.configureByText(
            "qualified_name_reverse.dsh",
            """
            Namespace Tools {
                Namespace Color {
                    Function ApplyTint {
                        Graph {
                        }
                    }
                }
                VirtualFunction(Name="/Game/Functions/F_Pulse") {
                    Options {
                    }
                }
            }
            Shader Main {
            }
            """.trimIndent()
        )
        val provider = DreamShaderQualifiedNameProvider()

        val nested = provider.qualifiedNameToElement("Tools::Color::ApplyTint", project) as? DreamShaderDeclaration
        val section = provider.qualifiedNameToElement("Tools::Color::ApplyTint#Graph", project) as? DreamShaderSection
        val alias = provider.qualifiedNameToElement("Tools::F_Pulse", project) as? DreamShaderDeclaration
        val topLevel = provider.qualifiedNameToElement("Main", project) as? DreamShaderDeclaration

        assertNotNull(nested)
        assertEquals("ApplyTint", nested!!.declarationName())
        assertNotNull(section)
        assertEquals("graph", section!!.sectionName())
        assertNotNull(alias)
        assertEquals("F_Pulse", alias!!.declarationName())
        assertNotNull(topLevel)
        assertEquals(file, topLevel!!.containingFile)
        assertNull(provider.qualifiedNameToElement("Tools::Missing", project))
        assertNull(provider.qualifiedNameToElement("Tools::Color::ApplyTint#Missing", project))
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

    fun testColorProviderRecognizesColorAliasesHexAndByteChannels() {
        val provider = DreamShaderColorProvider()

        val byteColor = provider.testColorCall("Color(255, 128, 0, 64)")
        assertNotNull(byteColor)
        assertEquals(255, byteColor!!.red)
        assertEquals(128, byteColor.green)
        assertEquals(0, byteColor.blue)
        assertEquals(64, byteColor.alpha)

        val hexRgb = provider.testColorCall("0xFF8040")
        assertNotNull(hexRgb)
        assertEquals(255, hexRgb!!.red)
        assertEquals(128, hexRgb.green)
        assertEquals(64, hexRgb.blue)
        assertEquals(255, hexRgb.alpha)

        val hexRgba = provider.testColorCall("0xFF804020")
        assertNotNull(hexRgba)
        assertEquals(32, hexRgba!!.alpha)

        assertNull(provider.testColorCall("Color(256, 0, 0)"))
        assertNull(provider.testColorCall("Color(1.0, 128, 0)"))
    }

    fun testDeclarationRangeHandlerReturnsHeaderRanges() {
        val file = myFixture.configureByText(
            "context_info.dsm",
            """
            Shader Main {
                Graph {
                    Base.BaseColor = float3(1, 1, 1);
                }
            }
            """.trimIndent()
        )
        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)!!
        val section = PsiTreeUtil.findChildOfType(file, DreamShaderSection::class.java)!!
        val provider = DreamShaderDeclarationRangeHandler()

        assertEquals("Shader Main ", provider.getDeclarationRange(declaration)!!.substring(file.text))
        assertEquals("Graph ", provider.getDeclarationRange(section)!!.substring(file.text))
    }

    fun testPlainTextSymbolCompletionExposesDreamShaderFqns() {
        val file = myFixture.configureByText(
            "plain_text_symbols.dsh",
            """
            Namespace Tools {
                Function ApplyTint {
                    Graph {
                    }
                }
            }
            """.trimIndent()
        )
        val contributor = DreamShaderPlainTextSymbolCompletionContributor()
        val lookupStrings = contributor.getLookupElements(file, 0, "")
            .map { it.lookupString }
            .toSet()

        assertTrue(lookupStrings.contains("Tools"))
        assertTrue(lookupStrings.contains("Tools::ApplyTint"))
        assertTrue(lookupStrings.contains("Tools::ApplyTint#Graph"))
    }

    fun testNameSuggestionProviderSanitizesAndPrefixesDreamShaderNames() {
        val provider = DreamShaderNameSuggestionProvider()

        assertEquals(
            listOf("Bad_Name", "M_Bad_Name", "M_Material"),
            provider.testSuggestedNames("shader", "Bad Name")
        )
        assertTrue(provider.testSuggestedNames("virtualfunction", "/Game/Foo/Pulse Tint").contains("VF_Pulse_Tint"))
    }

    fun testBridgeDiagnosticMarkerDescriptorAndTooltip() {
        val localFile = Files.createTempFile("bridge_marker_target", ".dsm").toFile()
        localFile.writeText("Shader Main {\n}\n")
        val virtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(localFile)!!
        val diagnostic = DreamShaderBridgeDiagnostic(
            sourcePath = virtualFile.path,
            line = 2,
            column = 3,
            severity = "warning",
            message = "Bridge warning"
        )
        val provider = DreamShaderLineMarkerProvider()
        val descriptor = provider.testBridgeDiagnosticDescriptor(project, diagnostic)

        assertNotNull(descriptor)
        assertEquals(virtualFile, descriptor!!.file)
        assertTrue(provider.testBridgeDiagnosticTooltip(diagnostic).contains("Bridge warning"))
        assertNull(provider.testBridgeDiagnosticDescriptor(project, diagnostic.copy(sourcePath = "Z:/missing/file.dsm")))
    }
}
