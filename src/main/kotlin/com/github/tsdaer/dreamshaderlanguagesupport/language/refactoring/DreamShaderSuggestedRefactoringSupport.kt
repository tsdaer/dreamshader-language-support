package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2Parameter
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.suggested.SuggestedRefactoringAvailability
import com.intellij.refactoring.suggested.SuggestedRefactoringExecution
import com.intellij.refactoring.suggested.SuggestedRefactoringState
import com.intellij.refactoring.suggested.SuggestedRefactoringStateChanges
import com.intellij.refactoring.suggested.SuggestedRefactoringSupport
import com.intellij.refactoring.suggested.SuggestedRefactoringUI

/**
 * Suggested refactorings (D3), rename-only: when an `export`/helper function's name is
 * edited in a 2.0 file, the platform offers the inline "rename" suggestion so callers
 * can be kept in sync. Signature changes have no execution story in the plugin yet (no
 * change-signature refactoring exists), so the change-signature UI/execution components
 * are inert - the [com.intellij.refactoring.suggested.SuggestedRefactoringAvailability.RenameOnly]
 * availability never produces change-signature data for them to be reached with.
 */
class DreamShaderSuggestedRefactoringSupport : SuggestedRefactoringSupport {
    override fun isAnchor(element: PsiElement): Boolean =
        PsiUtilCore.getElementType(element) == DreamShader2ElementTypes.FUNCTION_DECLARATION

    override fun signatureRange(element: PsiElement): TextRange {
        val declaration = element as? DreamShader2FunctionDeclaration ?: return element.textRange
        val body = declaration.parameterList() ?: return element.textRange
        return TextRange(element.textRange.startOffset, body.textRange.endOffset)
    }

    override fun importsRange(file: PsiFile): TextRange = TextRange(0, 0)

    override fun nameRange(element: PsiElement): TextRange {
        val declaration = element as? DreamShader2FunctionDeclaration ?: return TextRange.EMPTY_RANGE
        val name = declaration.declarationName() ?: return TextRange.EMPTY_RANGE
        val leaf = PsiTreeUtil.collectElements(declaration) {
            PsiUtilCore.getElementType(it) == com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.IDENTIFIER &&
                it.text == name
        }.firstOrNull() ?: return TextRange.EMPTY_RANGE
        return leaf.textRange
    }

    override fun isIdentifierStart(c: Char): Boolean = c.isLetter() || c == '_'

    override fun isIdentifierPart(c: Char): Boolean = c.isLetterOrDigit() || c == '_'

    override val stateChanges: SuggestedRefactoringStateChanges = DreamShaderStateChanges(this)

    override val availability: SuggestedRefactoringAvailability = SuggestedRefactoringAvailability.RenameOnly(this)

    override val ui: SuggestedRefactoringUI = DreamShaderSuggestedRefactoringUI()

    override val execution: SuggestedRefactoringExecution = DreamShaderSuggestedRefactoringExecution(this)
}

/** Extracts the 2.0 function signature (name, return type, parameters) from the declaration PSI. */
private class DreamShaderStateChanges(
    refactoringSupport: SuggestedRefactoringSupport,
) : SuggestedRefactoringStateChanges(refactoringSupport) {
    override fun signature(element: PsiElement, state: SuggestedRefactoringState?): SuggestedRefactoringSupport.Signature? {
        val declaration = element as? DreamShader2FunctionDeclaration ?: return null
        val name = declaration.declarationName()?.takeIf { it.isNotBlank() } ?: return null
        val parameters = declaration.parameterList()
            ?.parameters()
            ?.map { parameter ->
                SuggestedRefactoringSupport.Parameter(
                    parameter.parameterName().orEmpty(),
                    parameter.parameterName().orEmpty(),
                    parameter.typeName().orEmpty(),
                )
            }
            .orEmpty()
        return SuggestedRefactoringSupport.Signature.create(
            name,
            declaration.returnTypeText().orEmpty(),
            parameters,
            null,
        )
    }

    override fun parameterMarkerRanges(element: PsiElement): List<TextRange> {
        val declaration = element as? DreamShader2FunctionDeclaration ?: return emptyList()
        val parameters = declaration.parameterList()?.parameters().orEmpty()
        if (parameters.isEmpty()) return emptyList()
        return parameters.mapNotNull { parameter -> parameterNameRange(parameter) }
    }

    private fun parameterNameRange(parameter: DreamShader2Parameter): TextRange? {
        val name = parameter.parameterName() ?: return null
        val leaf = PsiTreeUtil.collectElements(parameter) {
            it.node?.elementType == com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.IDENTIFIER &&
                it.text == name
        }.firstOrNull() ?: return null
        return leaf.textRange
    }
}

/** Rename-only: the change-signature UI pipeline is never reached (see class comment). */
private class DreamShaderSuggestedRefactoringUI : SuggestedRefactoringUI() {
    override fun createSignaturePresentationBuilder(
        signature: SuggestedRefactoringSupport.Signature,
        otherSignature: SuggestedRefactoringSupport.Signature,
        isOldSignature: Boolean,
    ): com.intellij.refactoring.suggested.SignaturePresentationBuilder {
        return object : com.intellij.refactoring.suggested.SignaturePresentationBuilder(signature, otherSignature, isOldSignature) {
            override fun buildPresentation() {
                // Unreachable with the rename-only availability; kept inert on purpose.
            }
        }
    }

    override fun extractNewParameterData(
        data: com.intellij.refactoring.suggested.SuggestedChangeSignatureData,
    ): List<com.intellij.refactoring.suggested.SuggestedRefactoringUI.NewParameterData> = emptyList()

    override fun extractValue(
        fragment: com.intellij.psi.PsiCodeFragment,
    ): com.intellij.refactoring.suggested.SuggestedRefactoringExecution.NewParameterValue.Expression? = null
}

/** Rename-only: change-signature execution has no DreamShader implementation. */
private class DreamShaderSuggestedRefactoringExecution(
    refactoringSupport: SuggestedRefactoringSupport,
) : SuggestedRefactoringExecution(refactoringSupport) {
    override fun prepareChangeSignature(
        data: com.intellij.refactoring.suggested.SuggestedChangeSignatureData,
    ): Any = Unit

    override fun performChangeSignature(
        data: com.intellij.refactoring.suggested.SuggestedChangeSignatureData,
        newParameterValues: List<com.intellij.refactoring.suggested.SuggestedRefactoringExecution.NewParameterValue>,
        prepared: Any?,
    ) {
        // Unreachable with the rename-only availability.
    }
}
