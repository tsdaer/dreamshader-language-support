package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderDeclarationRenameTest : BasePlatformTestCase() {
    fun testDeclarationSetNameUpdatesNameIdentifier() {
        val file = myFixture.configureByText(
            "rename.dsf",
            """
            Function Util {
                Graph {
                    float3 v = float3(0.0, 1.0, 0.0);
                }
            }
            """.trimIndent()
        )

        val declaration = PsiTreeUtil.findChildOfType(file, DreamShaderDeclaration::class.java)
        assertNotNull("Expected declaration PSI", declaration)

        WriteCommandAction.runWriteCommandAction(project) {
            declaration!!.setName("Utility")
        }

        assertEquals("Utility", declaration!!.declarationName())
        assertEquals("Function Utility {", file.text.lineSequence().first())
    }
}
