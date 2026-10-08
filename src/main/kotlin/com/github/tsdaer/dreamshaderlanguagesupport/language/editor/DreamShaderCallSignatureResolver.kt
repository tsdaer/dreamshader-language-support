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
        (sourceFile as? com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile)?.let { file ->
            val declared = com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2SemanticResolver.functions(file, functionName)
            if (declared.isNotEmpty()) return declared.map(::signatureOf)
        }
        val localDeclared = DreamShaderSignatureHelpAnalyzer.resolveDeclaredSignatures(
            functionName = functionName,
            sourceText = legacyDeclarationText(sourceFile)
        )
        if (localDeclared.isNotEmpty()) return localDeclared

        val importedSourceTexts = DreamShaderImportClosureResolver.resolveImportClosure(sourceFile)
            .drop(1)
            .map(::legacyDeclarationText)
        if (importedSourceTexts.isNotEmpty()) {
            val importedDeclared = DreamShaderSignatureHelpAnalyzer.resolveDeclaredSignatures(
                functionName = functionName,
                additionalSourceTexts = importedSourceTexts
            )
            if (importedDeclared.isNotEmpty()) return importedDeclared
        }

        // Catalog row first (authoritative), then any documented built-in spelling of the same
        // callable that differs — overload expansion instead of first-tier-wins.
        val catalogEntries = collectCatalogEntries(sourceFile)
        val catalogSignatures = DreamShaderSignatureHelpAnalyzer.resolveCatalogSignatures(
            functionName = functionName,
            catalogEntries = catalogEntries,
            requireComplete = true
        )
        if (catalogSignatures.isNotEmpty()) {
            return DreamShaderSignatureHelpAnalyzer.mergeDistinctSignatures(
                primary = catalogSignatures,
                secondary = DreamShaderSignatureHelpAnalyzer.resolveSignatures(functionName)
            )
        }

        return DreamShaderSignatureHelpAnalyzer.resolveSignatures(functionName)
    }

    private fun legacyDeclarationText(file: PsiFile): String =
        if (file is com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile &&
            DreamShaderCompletionDialects.dialectOf(file) != com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialect.V1) {
            file.children.filter { it.node.elementType == com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes.LEGACY_BLOCK }
                .joinToString("\n") { it.text }
        } else file.text

    fun signatureOf(function: com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration): DreamShaderCallSignature {
        val parameters = function.parameterList()?.parameters().orEmpty()
        val names = parameters.map { it.parameterName().orEmpty() }
        val text = "${function.returnTypeText()} ${function.declarationName()}(" + parameters.joinToString(", ") { it.text.trim() } + ")"
        val doc = function.children.firstOrNull { it.node.elementType ==
            com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes.DOC_BLOCK }?.text
        return DreamShaderSignatureHelpAnalyzer.signature(text, *names.toTypedArray()).copy(
            parameterTypes = parameters.map { it.typeName() },
            parameterQualifiers = parameters.map { it.text.trim().substringBefore(' ').takeIf { word -> word in setOf("in", "out", "inout") } },
            returnType = function.returnTypeText(), documentation = doc)
    }

    private fun collectCatalogEntries(sourceFile: PsiFile): List<DreamShaderMaterialExpressionInfo> {
        val settings = sourceFile.project.getService(DreamShaderProjectSettings::class.java)
        return DreamShaderMaterialExpressionManifest.catalogEntries(
            project = sourceFile.project,
            explicitManifestPath = settings?.state?.materialExpressionManifestPath
        )
    }
}
