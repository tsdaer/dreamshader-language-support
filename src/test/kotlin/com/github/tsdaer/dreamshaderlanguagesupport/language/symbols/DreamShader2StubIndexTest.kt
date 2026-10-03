package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2StubIndexesKeys
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.system.measureTimeMillis

/**
 * C1 验收：2.0 stub 索引的落库、查询与失效重建（幂等），以及 ≥500 文件规模冒烟。
 * 文件本地 helper（非 export/extern）不得进入函数索引；include 边按书写路径入库。
 */
class DreamShader2StubIndexTest : BasePlatformTestCase() {

    private val uniformFile = """
        /// @group Tuning
        uniform float Gain = 1.0;

        /// @static
        uniform bool UseDetail = false;
    """.trimIndent()

    private val functionFile = """
        export float2 MF_Scale(float2 uv, float k)
        {
            return uv * k;
        }

        extern float3 MF_External(float value);

        float3 LocalHelper(float3 c)
        {
            return c;
        }
    """.trimIndent()

    private val structFile = """
        // A bare .dss without V2 markers keeps the 1.x pipeline (M8 dispatch, frozen),
        // so this fixture carries a uniform marker to pin the 2.0 route.
        uniform float HeaderGain = 1.0;

        struct SurfaceInput
        {
            float3 Albedo;
            float Roughness;
        };
    """.trimIndent()

    private val includeFile = """
        #include "Shared/Math.ush"
        import "Lib/Toon.dsh";
    """.trimIndent()

    private fun addProjectFile(relativePath: String, text: String) =
        myFixture.addFileToProject(relativePath, text)

    private fun <T : com.intellij.psi.PsiElement> indexElements(
        key: com.intellij.psi.stubs.StubIndexKey<String, T>,
        name: String,
        psiClass: Class<T>,
    ): List<T> = StubIndex.getElements(key, name, project, GlobalSearchScope.projectScope(project), psiClass).toList()

    fun testUniformIndexFindsDeclarationsWithStubFacts() {
        addProjectFile("DShader/Materials/M_A.dss", uniformFile)

        val gain = indexElements(DreamShader2StubIndexesKeys.UNIFORM, "Gain", DreamShader2UniformDeclaration::class.java)
        assertEquals(1, gain.size)
        assertEquals("float", gain.single().typeName())
        assertEquals(false, gain.single().isStaticSwitch())

        val useDetail = indexElements(DreamShader2StubIndexesKeys.UNIFORM, "UseDetail", DreamShader2UniformDeclaration::class.java)
        assertEquals(1, useDetail.size)
        assertTrue(useDetail.single().isStaticSwitch())
    }

    fun testFunctionIndexHoldsLinkageFunctionsOnly() {
        addProjectFile("DShader/Functions/MF_B.dss", functionFile)

        val exported = indexElements(DreamShader2StubIndexesKeys.FUNCTION, "MF_Scale", DreamShader2FunctionDeclaration::class.java)
        assertEquals(1, exported.size)
        assertEquals("float2", exported.single().returnTypeText())
        assertTrue(exported.single().isExport())

        val extern = indexElements(DreamShader2StubIndexesKeys.FUNCTION, "MF_External", DreamShader2FunctionDeclaration::class.java)
        assertEquals(1, extern.size)
        assertTrue(extern.single().isExtern())

        // File-local helpers stay file-local.
        assertEquals(
            0,
            indexElements(DreamShader2StubIndexesKeys.FUNCTION, "LocalHelper", DreamShader2FunctionDeclaration::class.java).size,
        )
    }

    fun testStructAndIncludeIndexesPopulate() {
        addProjectFile("DShader/Shared/S.dss", structFile)
        addProjectFile("DShader/Materials/I.dss", includeFile)

        assertEquals(
            1,
            indexElements(DreamShader2StubIndexesKeys.STRUCT, "SurfaceInput", DreamShader2StructDeclaration::class.java).size,
        )

        val mathInclude = indexElements(
            DreamShader2StubIndexesKeys.INCLUDE,
            "Shared/Math.ush",
            DreamShader2IncludeDirective::class.java,
        )
        assertEquals(1, mathInclude.size)
        assertEquals("Shared/Math.ush", mathInclude.single().pathText())
        assertEquals(
            1,
            indexElements(DreamShader2StubIndexesKeys.INCLUDE, "Lib/Toon.dsh", DreamShader2IncludeDirective::class.java).size,
        )
    }

    fun testRebuildAfterModificationIsIdempotent() {
        val file = addProjectFile("DShader/Materials/M_C.dss", functionFile)
        assertEquals(
            1,
            indexElements(DreamShader2StubIndexesKeys.FUNCTION, "MF_Scale", DreamShader2FunctionDeclaration::class.java).size,
        )

        // 引擎/用户重写后的失效重建：改名 → 旧名退出索引，新名进入，且连查两次结果一致。
        WriteCommandAction.runWriteCommandAction(project) {
            val document = PsiDocumentManager.getInstance(project).getDocument(file)!!
            document.replaceString(0, document.textLength, document.text.replace("MF_Scale", "MF_Renamed"))
            PsiDocumentManager.getInstance(project).commitDocument(document)
        }

        val first = indexElements(DreamShader2StubIndexesKeys.FUNCTION, "MF_Renamed", DreamShader2FunctionDeclaration::class.java)
        val second = indexElements(DreamShader2StubIndexesKeys.FUNCTION, "MF_Renamed", DreamShader2FunctionDeclaration::class.java)
        assertEquals(1, first.size)
        assertEquals(first.size, second.size)
        assertEquals(0, indexElements(DreamShader2StubIndexesKeys.FUNCTION, "MF_Scale", DreamShader2FunctionDeclaration::class.java).size)
    }

    fun testFiveHundredFileProjectIndexesWithinBudget() {
        repeat(500) { index ->
            addProjectFile(
                "DShader/Generated/M_G$index.dss",
                """
                uniform float Strength$index = 1.0;
                export float3 MF_G$index(float3 c) { return c; }
                """.trimIndent(),
            )
        }

        val elapsed = measureTimeMillis {
            val function = indexElements(DreamShader2StubIndexesKeys.FUNCTION, "MF_G250", DreamShader2FunctionDeclaration::class.java)
            assertEquals(1, function.size)
            assertEquals("float3", function.single().returnTypeText())
            val uniform = indexElements(DreamShader2StubIndexesKeys.UNIFORM, "Strength499", DreamShader2UniformDeclaration::class.java)
            assertEquals(1, uniform.size)
        }
        assertTrue(elapsed < 30_000, "500-file stub index query exceeded budget: ${elapsed}ms")
    }
}
