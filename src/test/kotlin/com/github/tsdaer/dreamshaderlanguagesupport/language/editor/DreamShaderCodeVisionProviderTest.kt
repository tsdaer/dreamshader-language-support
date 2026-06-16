package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class DreamShaderCodeVisionProviderTest : BasePlatformTestCase() {
    fun testCodeVisionHintForDeclarationContainsPackageSummary() {
        val file = myFixture.configureByText(
            "code_vision.dsm",
            """
            Shader Main {
                Graph = {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )
        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)
        assertNotNull("Expected declaration PSI", declaration)

        val provider = DreamShaderCodeVisionProvider()
        val hint = provider.getHint(declaration!!, file)

        assertTrue("Expected declaration name in hint", hint.contains("Main"))
        assertTrue("Expected hint to contain localized package summary segment", hint.contains("Pkg:"))
        val bridgeReady = DreamShaderBundle.message("codeVision.label.bridgeReady")
        val bridgeUnavailable = DreamShaderBundle.message("codeVision.label.bridgeUnavailable")
        assertTrue(
            "Expected hint to contain one of localized bridge status labels",
            hint.contains(bridgeReady) || hint.contains(bridgeUnavailable)
        )
    }

    fun testCodeVisionHintShowsBridgeUnavailableForUnreachableConfiguredRoot() {
        val file = myFixture.configureByText(
            "code_vision_no_bridge.dsm",
            """
            Shader Main {
                Graph = {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )
        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)
        assertNotNull("Expected declaration PSI", declaration)

        val settings = project.getService(
            com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java
        ).state
        val previousRoot = settings.projectRoot
        try {
            settings.projectRoot = "Z:/__dreamshader_missing_root_for_test__"

            val provider = DreamShaderCodeVisionProvider()
            val hint = provider.getHint(declaration!!, file)

            assertTrue(
                "Expected bridge-unavailable label when bridge directory cannot resolve",
                hint.contains(DreamShaderBundle.message("codeVision.label.bridgeUnavailable"))
            )
            assertTrue("Expected declaration name in hint", hint.contains("Main"))
        } finally {
            settings.projectRoot = previousRoot
        }
    }

    fun testCodeVisionHintUsesFullShaderNameAttributeDisplayName() {
        val file = myFixture.configureByText(
            "code_vision_name_attribute.dsm",
            """
            Shader(Name="DreamMaterials/M_Test2") {
                Graph = {
                    float2 uv = UE.TexCoord(0);
                }
            }
            """.trimIndent()
        )
        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)
        assertNotNull("Expected declaration PSI", declaration)

        val provider = DreamShaderCodeVisionProvider()
        val hint = provider.getHint(declaration!!, file)

        assertTrue("Expected full Name attribute path in hint", hint.contains("DreamMaterials/M_Test2"))
    }

    fun testClickPlanRefreshesStoreWhenDeclarationFileIsActiveFile() {
        val file = myFixture.configureByText(
            "code_vision_click_refresh.dsm",
            """
            Shader Main {}
            """.trimIndent()
        )
        val provider = DreamShaderCodeVisionProvider()
        val plan = provider.testBuildClickPlan(project, file.virtualFile, file.virtualFile)
        assertEquals(DreamShaderCodeVisionProvider.CodeVisionClickPlan.RefreshPackageStore, plan)
    }

    fun testClickPlanOpensBridgeDirectoryWhenNotActiveAndBridgeExists() {
        val file = myFixture.configureByText(
            "code_vision_click_bridge.dsm",
            """
            Shader Main {}
            """.trimIndent()
        )
        val settings = project.getService(
            com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java
        ).state
        val provider = DreamShaderCodeVisionProvider()
        val previousRoot = settings.projectRoot
        try {
            val projectBase = project.basePath ?: error("project base path is null")
            settings.projectRoot = projectBase
            val bridgePath = Path.of(projectBase, "Saved", "DreamShader", "Bridge")
            WriteCommandAction.runWriteCommandAction(project) {
                VfsUtil.createDirectories(bridgePath.toString())
            }

            val plan = provider.testBuildClickPlan(project, file.virtualFile, null)
            assertTrue(
                "Expected open-bridge plan when bridge directory exists and file is not active",
                plan is DreamShaderCodeVisionProvider.CodeVisionClickPlan.OpenBridgeDirectory
            )
        } finally {
            settings.projectRoot = previousRoot
        }
    }

    fun testClickPlanReturnsNoActionWhenNotActiveAndBridgeMissing() {
        val file = myFixture.configureByText(
            "code_vision_click_none.dsm",
            """
            Shader Main {}
            """.trimIndent()
        )
        val settings = project.getService(
            com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java
        ).state
        val provider = DreamShaderCodeVisionProvider()
        val previousRoot = settings.projectRoot
        try {
            settings.projectRoot = "Z:/__dreamshader_missing_root_for_test__"
            val plan = provider.testBuildClickPlan(project, file.virtualFile, null)
            assertEquals(DreamShaderCodeVisionProvider.CodeVisionClickPlan.NoAction, plan)
        } finally {
            settings.projectRoot = previousRoot
        }
    }
}
