package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration

internal object DreamShaderDeclarationPresentation {
    fun symbolName(declaration: DreamShaderDeclaration): String? =
        declaration.declarationName()?.takeIf { it.isNotBlank() }

    fun displayName(declaration: DreamShaderDeclaration): String? {
        val explicit = declaration.explicitNameAttributeValue()?.takeIf { it.isNotBlank() }
        return explicit ?: symbolName(declaration)
    }

    fun qualifiedSymbolName(declaration: DreamShaderDeclaration): String? {
        val ownName = symbolName(declaration) ?: return null
        val namespacePath = DreamShaderDeclarationSearch.namespacePath(declaration)
        if (namespacePath.isEmpty()) return ownName
        return (namespacePath + ownName).joinToString("::")
    }

    fun qualifiedDisplayName(declaration: DreamShaderDeclaration): String? {
        val ownName = displayName(declaration) ?: return null
        val namespacePath = DreamShaderDeclarationSearch.namespacePath(declaration)
        if (namespacePath.isEmpty()) return ownName
        return (namespacePath + ownName).joinToString("::")
    }
}

