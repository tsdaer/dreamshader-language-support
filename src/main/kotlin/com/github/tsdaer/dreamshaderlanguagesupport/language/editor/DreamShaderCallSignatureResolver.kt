package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolver
import com.intellij.psi.PsiFile

/**
 * Resolves callable signatures for a file context with a declaration-first fallback chain:
 * 1) same-file declarations
 * 2) recursively imported-file declarations
 * 3) built-in signatures (UE.* / HLSL intrinsics)
 */
internal object DreamShaderCallSignatureResolver {
    fun resolveSignatures(functionName: String, sourceFile: PsiFile): List<DreamShaderCallSignature> {
        val localDeclared = DreamShaderSignatureHelpAnalyzer.resolveDeclaredSignatures(
            functionName = functionName,
            sourceText = sourceFile.text
        )
        if (localDeclared.isNotEmpty()) return localDeclared

        val importedSourceTexts = DreamShaderImportClosureResolver.resolveImportClosure(sourceFile)
            .drop(1)
            .map { it.text }
        if (importedSourceTexts.isNotEmpty()) {
            val importedDeclared = DreamShaderSignatureHelpAnalyzer.resolveDeclaredSignatures(
                functionName = functionName,
                additionalSourceTexts = importedSourceTexts
            )
            if (importedDeclared.isNotEmpty()) return importedDeclared
        }

        return DreamShaderSignatureHelpAnalyzer.resolveSignatures(functionName)
    }
}
