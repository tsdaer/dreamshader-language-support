package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiUtilCore

/**
 * Semantic token classification for the 2.0 dialect (M12 C4). The frozen 1.x classifier
 * path is untouched: these branches only fire on `.dss`/`.dsi` files.
 *
 * Three-level identifier colors - `uniform` parameters vs function parameters vs locals -
 * are structural decisions (nearest statement-level node + previous significant token).
 * `Substrate`/`material`-typed variables use the C2 inference over a per-file cached
 * variable-name set, so usage sites stay O(1) per token.
 */
internal object DreamShader2SemanticClassifier {
    fun isV2File(element: PsiElement): Boolean {
        val extension = DreamShaderDialects.extensionOf(element.containingFile?.name ?: return false)
        return extension == "dss" || extension == "dsi"
    }

    /** First V2 hit for an identifier leaf, or null to fall through to the shared rules. */
    fun classifyIdentifier(element: PsiElement): TextAttributesKey? {
        val uniform = nearestNode(element, DreamShader2ElementTypes.UNIFORM_DECLARATION)
        if (uniform != null && isNameLeafAfterTypeOrComma(element, uniform)) {
            return DreamShaderTextAttributes.UNIFORM_PARAMETER
        }

        val parameter = nearestNode(element, DreamShader2ElementTypes.PARAMETER)
        if (parameter != null && isNameLeafAfterTypeOrComma(element, parameter)) {
            return DreamShaderTextAttributes.FUNCTION_PARAMETER
        }

        if (isSubstrateMaterialValue(element)) {
            return DreamShaderTextAttributes.SUBSTRATE_VALUE
        }
        return null
    }

    /**
     * True when [element] sits in a name slot: its previous significant token is a type
     * spelling or a comma (the `uniform float3 A, B` / `float a, b` name lists).
     */
    private fun isNameLeafAfterTypeOrComma(element: PsiElement, scope: PsiElement): Boolean {
        if (PsiUtilCore.getElementType(element) != DreamShaderTokenTypes.IDENTIFIER) return false
        if (!scope.textRange.contains(element.textRange)) return false
        val prev = previousSignificant(element) ?: return false
        return when (PsiUtilCore.getElementType(prev)) {
            DreamShaderTokenTypes.TYPE -> true
            DreamShaderTokenTypes.COMMA -> true
            else -> false
        }
    }

    private fun isSubstrateMaterialValue(element: PsiElement): Boolean {
        if (PsiUtilCore.getElementType(element) != DreamShaderTokenTypes.IDENTIFIER) return false
        val file = element.containingFile as? DreamShaderPsiFile ?: return false
        val names = cachedValueNames(file)
        return element.text in names.substrateMaterialVariables
    }

    private fun isSubstrateMaterialSpelling(typeSpelling: String): Boolean =
        typeSpelling.equals("Substrate", ignoreCase = true) || typeSpelling.equals("material", ignoreCase = true)

    private fun cachedValueNames(file: DreamShaderPsiFile): ValueNames {
        return CachedValuesManager.getCachedValue(file, VALUE_NAMES_KEY) {
            CachedValueProvider.Result.create(computeValueNames(file), file)
        }
    }

    private fun computeValueNames(file: DreamShaderPsiFile): ValueNames {
        val names = mutableSetOf<String>()
        collectVariableDeclarations(file) { variableName, typeSpelling ->
            // Conservative: only the explicit `Substrate` / `material` declared spellings
            // get the dedicated color; anything inferred stays on the shared colors.
            if (isSubstrateMaterialSpelling(typeSpelling)) names.add(variableName)
        }
        return ValueNames(names.toSet())
    }

    /** Visits every 2.0 variable declaration: `(declared name, declared type spelling)`. */
    private fun collectVariableDeclarations(scope: PsiElement, visit: (String, String) -> Unit) {
        var current = scope.firstChild
        while (current != null) {
            val type = PsiUtilCore.getElementType(current)
            if (type == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT ||
                type == DreamShader2ElementTypes.GLOBAL_DECLARATION
            ) {
                val children = current.node.getChildren(null)
                var sawType = false
                var typeSpelling: String? = null
                var nameLeaf: String? = null
                children.forEach { child ->
                    when {
                        child.elementType == DreamShaderTokenTypes.TYPE -> {
                            if (typeSpelling == null) typeSpelling = child.text
                            sawType = true
                        }
                        child.elementType == DreamShaderTokenTypes.IDENTIFIER && sawType && nameLeaf == null -> nameLeaf = child.text
                    }
                }
                if (typeSpelling != null && nameLeaf != null) {
                    visit(nameLeaf!!, typeSpelling!!)
                }
            }
            collectVariableDeclarations(current, visit)
            current = current.nextSibling
        }
    }

    private fun nearestNode(element: PsiElement, type: com.intellij.psi.tree.IElementType): PsiElement? {
        var current: PsiElement? = element.parent
        while (current != null) {
            if (PsiUtilCore.getElementType(current) == type) return current
            current = current.parent
        }
        return null
    }

    /** AST-layer walk: whitespace nodes always exist at the tree level (they can be lazy in PSI). */
    private fun previousSignificant(element: PsiElement): PsiElement? {
        var node = element.node?.treePrev ?: return null
        while (node != null) {
            if (node.elementType !in IGNORABLE) return node.psi
            node = node.treePrev
        }
        return null
    }

    private val IGNORABLE = setOf(
        DreamShaderTokenTypes.WHITE_SPACE,
        DreamShaderTokenTypes.LINE_COMMENT,
        DreamShaderTokenTypes.BLOCK_COMMENT,
        DreamShaderTokenTypes.DOC_COMMENT,
    )

    private data class ValueNames(val substrateMaterialVariables: Set<String>)

    private val VALUE_NAMES_KEY = com.intellij.openapi.util.Key.create<CachedValue<ValueNames>>(
        "dreamshader2.semantic.value.names"
    )
}
