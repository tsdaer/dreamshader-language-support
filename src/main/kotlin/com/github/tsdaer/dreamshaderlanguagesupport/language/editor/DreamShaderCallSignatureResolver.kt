package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.psi.PsiFile

/**
 * Resolves callable signatures for a file context with a declaration-first fallback chain:
 * 1) same-file declarations
 * 2) recursively imported-file declarations
 * 3) catalog-provided signatures (UE.* / Substrate.* material expressions)
 * 4) built-in signatures (UE.* / HLSL intrinsics)
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

        val catalogEntries = collectCatalogEntries(sourceFile)
        val catalogSignatures = DreamShaderSignatureHelpAnalyzer.resolveCatalogSignatures(
            functionName = functionName,
            catalogEntries = catalogEntries,
            requireComplete = true
        )
        if (catalogSignatures.isNotEmpty()) return catalogSignatures

        return DreamShaderSignatureHelpAnalyzer.resolveSignatures(functionName)
    }

    private fun collectCatalogEntries(sourceFile: PsiFile): List<DreamShaderMaterialExpressionInfo> {
        val settings = sourceFile.project.getService(DreamShaderProjectSettings::class.java)
        return DreamShaderMaterialExpressionManifest.catalogEntries(
            project = sourceFile.project,
            explicitManifestPath = settings?.state?.materialExpressionManifestPath
        )
    }
}
