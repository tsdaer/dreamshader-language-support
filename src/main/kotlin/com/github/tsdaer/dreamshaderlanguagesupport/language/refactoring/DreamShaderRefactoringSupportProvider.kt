package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCompletionDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.lang.refactoring.RefactoringSupportProvider
import com.intellij.psi.PsiElement
import com.intellij.refactoring.RefactoringActionHandler

class DreamShaderRefactoringSupportProvider : RefactoringSupportProvider() {
    override fun isSafeDeleteAvailable(element: PsiElement): Boolean {
        return (element is DreamShaderDeclaration || element is com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration) &&
            (element as? com.intellij.psi.PsiNameIdentifierOwner)?.nameIdentifier != null
    }

    override fun isInplaceRenameAvailable(element: PsiElement, context: PsiElement?): Boolean {
        return (element is DreamShaderDeclaration || element is com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration) &&
            (element as? com.intellij.psi.PsiNameIdentifierOwner)?.nameIdentifier != null
    }

    /** Introduce Variable is wired for pure 2.0 files only (C20); 1.x keeps the platform default. */
    override fun getIntroduceVariableHandler(element: PsiElement): RefactoringActionHandler? {
        val file = element.containingFile as? DreamShaderPsiFile ?: return null
        return if (DreamShaderCompletionDialects.servesV2(file)) DreamShader2ExtractVariableHandler() else null
    }

    /** The action dispatch path has no element context; the handler itself re-checks the dialect. */
    override fun getIntroduceVariableHandler(): RefactoringActionHandler = DreamShader2ExtractVariableHandler()

    /** Extract Function is wired for pure 2.0 files only (M15 C21); the handler re-checks. */
    override fun getExtractMethodHandler(): RefactoringActionHandler = DreamShader2ExtractFunctionHandler()
}
