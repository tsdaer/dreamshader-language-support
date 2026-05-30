package com.github.tsdaer.dreamshaderlanguagesupport.language.integration

import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderGotoDeclarationHandler
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Paths

class DreamShaderUpstreamExamplesNavigationCoverageTest : BasePlatformTestCase() {
    private val goto = DreamShaderGotoDeclarationHandler()

    fun testExample1GraphTintResolvesToPropertiesDeclaration() {
        val file = myFixture.configureByText(
            "example1_minimal.dsm",
            """
            Shader(Name="DreamMaterials/M_Minimal")
            {
                Properties = {
                    vec3 Tint = vec3(1.0, 0.2, 0.2);
                }

                Outputs = {
                    vec3 Color;
                    Base.EmissiveColor = Color;
                }

                Graph = {
                    Color = Tint;
                }
            }
            """.trimIndent()
        )

        val offset = file.text.lastIndexOf("Tint;") + 1
        val source = file.findElementAt(offset)
        assertNotNull(source)

        val targets = goto.getGotoDeclarationTargets(source, offset, myFixture.editor)
        assertNotNull(targets)
        assertEquals("Tint", targets!!.first().text)
    }

    fun testExample2FunctionLocalAndParameterUsagesResolve() {
        val file = myFixture.configureByText(
            "example2_shared_header.dsh",
            """
            Namespace(Name="Common")
            {
                Function BuildPulse(in float t, in vec2 uv, out vec3 result) {
                    vec2 p = uv - 0.5;
                    float ring = sin(t * 2.0 + length(p) * 12.0) * 0.5 + 0.5;
                    result = vec3(ring, ring * 0.5 + 0.1, 1.0 - ring * 0.35);
                }
            }
            """.trimIndent()
        )

        val uvUsageOffset = file.text.indexOf("uv - 0.5") + 1
        val uvTargets = goto.getGotoDeclarationTargets(file.findElementAt(uvUsageOffset), uvUsageOffset, myFixture.editor)
        assertNotNull(uvTargets)
        assertEquals("uv", uvTargets!!.first().text)

        val ringUsageOffset = file.text.indexOf("ring, ring") + 1
        val ringTargets = goto.getGotoDeclarationTargets(file.findElementAt(ringUsageOffset), ringUsageOffset, myFixture.editor)
        assertNotNull(ringTargets)
        assertEquals("ring", ringTargets!!.first().text)
    }

    fun testExample3ImportedNamespaceCallResolvesAcrossImportedFile() {
        val projectBase = project.basePath ?: error("project base path is null")
        val commonPath = Paths.get(projectBase, "Shared", "Common.dsh")
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(commonPath.parent.toString())
            val file = parent.findOrCreateChildData(this, commonPath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Namespace Common {
                    Function BuildPulse(in float t, in vec2 uv, out vec3 result) {
                        result = vec3(1.0, 1.0, 1.0);
                    }
                }
                """.trimIndent()
            )
        }

        val file = myFixture.configureByText(
            "example3_imported_call.dsm",
            """
            import "Shared/Common.dsh";

            Shader(Name="DreamMaterials/M_Imported")
            {
                Outputs = {
                    vec3 Color;
                    Base.EmissiveColor = Color;
                }

                Graph = {
                    vec2 uv = UE.TexCoord(Index=0);
                    float t = UE.Time();
                    vec3 pulse;

                    Common::BuildPulse(t, uv, pulse);
                }
            }
            """.trimIndent()
        )

        val offset = file.text.indexOf("BuildPulse(") + 1
        val targets = goto.getGotoDeclarationTargets(file.findElementAt(offset), offset, myFixture.editor)
        assertNotNull(targets)
        val target = targets!!.first() as DreamShaderDeclaration
        assertEquals("BuildPulse", target.declarationName())
    }

    fun testExample4PackageNamespaceCallResolvesAcrossImportedPackageFile() {
        val projectBase = project.basePath ?: error("project base path is null")
        val texturePath = Paths.get(
            projectBase,
            "DShader",
            "Packages",
            "@typedreammoon",
            "dreamshader-texture",
            "Library",
            "Texture.dsh"
        )
        WriteCommandAction.runWriteCommandAction(project) {
            val parent = VfsUtil.createDirectories(texturePath.parent.toString())
            val file = parent.findOrCreateChildData(this, texturePath.fileName.toString())
            VfsUtil.saveText(
                file,
                """
                Namespace Texture {
                    Function Sample2DRGB(in Texture2D tex, in vec2 uv, out vec3 sampled) {
                        sampled = vec3(0.0, 0.0, 0.0);
                    }
                }
                """.trimIndent()
            )
        }

        val file = myFixture.configureByText(
            "example4_package_call.dsm",
            """
            import "@typedreammoon/dreamshader-texture/Library/Texture.dsh";

            Shader(Name="DreamMaterials/M_TexturePackage")
            {
                Properties = {
                    Texture2D MainTex = Path(Engine, "/EngineResources/DefaultTexture");
                }

                Outputs = {
                    vec3 Color;
                    Base.EmissiveColor = Color;
                }

                Graph = {
                    vec2 uv = UE.TexCoord(Index=0);
                    vec3 sampled;
                    Texture::Sample2DRGB(MainTex, uv, sampled);
                }
            }
            """.trimIndent()
        )

        val offset = file.text.indexOf("Sample2DRGB(") + 1
        val targets = goto.getGotoDeclarationTargets(file.findElementAt(offset), offset, myFixture.editor)
        assertNotNull(targets)
        val target = targets!!.first() as DreamShaderDeclaration
        assertEquals("Sample2DRGB", target.declarationName())
    }
}
