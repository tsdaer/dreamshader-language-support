package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import java.util.*

/**
 * Singleton for DreamShaderSemanticTokenClassifier.
 */
internal object DreamShaderSemanticTokenClassifier {
    /**
     * Returns the semantic text-attribute key for a PSI leaf token, or null when
     * the token should keep plain lexer-based highlighting only.
     */
    fun classify(element: PsiElement): TextAttributesKey? {
        if (element.text.isBlank()) return null
        return when (element.node.elementType) {
            DreamShaderTokenTypes.KEYWORD -> {
                if (isDeclarationKeywordElement(element)) DreamShaderTextAttributes.KEYWORD else null
            }

            DreamShaderTokenTypes.SECTION -> DreamShaderTextAttributes.SECTION
            DreamShaderTokenTypes.IDENTIFIER -> classifyIdentifier(element)
            else -> null
        }
    }

    private fun classifyIdentifier(element: PsiElement): TextAttributesKey? {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        if (declaration != null && element == declaration.nameIdentifier) {
            return DreamShaderTextAttributes.DECLARATION_NAME
        }

        // Namespace-oriented semantic classes.
        if (isUeNamespaceIdentifier(element)) return DreamShaderTextAttributes.BUILTIN_NAMESPACE
        if (isNamespaceQualifier(element)) return DreamShaderTextAttributes.NAMESPACE_QUALIFIER

        // Material-output member classes.
        if (isMaterialOutputMemberIdentifier(element)) return DreamShaderTextAttributes.MATERIAL_OUTPUT_MEMBER

        // Local symbol split: declaration site vs usage site.
        if (isLocalSymbolDeclaration(element)) return DreamShaderTextAttributes.LOCAL_SYMBOL_DECLARATION
        if (isLocalSymbolUsage(element)) return DreamShaderTextAttributes.LOCAL_SYMBOL

        // Callable reference fallback.
        val text = element.text
        if (!looksLikeCallableReference(text, element)) return null
        if (isDeclarationHeadIdentifier(element)) return null
        if (!isInsideDeclarationBody(element)) return null
        return DreamShaderTextAttributes.CALLABLE_REFERENCE
    }

    // Declaration/body scope helpers.
    private fun isDeclarationKeywordElement(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val firstKeyword = declaration.node.getChildren(null).firstOrNull { it.elementType == DreamShaderTokenTypes.KEYWORD }
        return firstKeyword?.psi == element
    }

    private fun isDeclarationHeadIdentifier(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        if (element == declaration.nameIdentifier) return true

        val declarationRange = declaration.textRange
        val bodyRange = declaration.bodyTextRange() ?: return false
        val elementRange = element.textRange
        return declarationRange.contains(elementRange) && !bodyRange.contains(elementRange)
    }

    private fun isInsideDeclarationBody(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val bodyRange = declaration.bodyTextRange() ?: return false
        if (!bodyRange.contains(element.textRange)) return false

        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false)
        if (section != null) {
            val sectionName = section.sectionName()?.lowercase(Locale.ROOT)
            if (sectionName == "settings" || sectionName == "options" || sectionName == "properties") {
                return false
            }
        }
        return true
    }

    // Namespace and callable helpers.
    private fun isUeNamespaceIdentifier(element: PsiElement): Boolean {
        if (!element.text.equals("UE", ignoreCase = true)) return false
        if (!isInsideDeclarationBody(element)) return false
        return skipWhitespaceForward(element) == "."
    }

    private fun isNamespaceQualifier(element: PsiElement): Boolean {
        val next = skipWhitespaceForward(element)
        if (next != ":") return false
        val text = element.containingFile.text
        var i = element.textRange.endOffset
        while (i < text.length && text[i].isWhitespace()) i++
        if (i + 1 >= text.length) return false
        return text[i] == ':' && text[i + 1] == ':'
    }

    private fun looksLikeCallableReference(text: String, element: PsiElement): Boolean {
        if (text.equals("ue", ignoreCase = true)) return false
        val next = skipWhitespaceForward(element)
        return next == "("
    }

    // Material-output helpers.
    private fun isMaterialOutputMemberIdentifier(element: PsiElement): Boolean {
        if (!isInsideDeclarationBody(element)) return false
        val prev = previousSignificantElement(element) ?: return false
        if (prev.node.elementType != DreamShaderTokenTypes.OPERATOR || prev.text != ".") return false
        val base = previousSignificantElement(prev) ?: return false
        if (base.node.elementType != DreamShaderTokenTypes.IDENTIFIER || !base.text.equals("Base", ignoreCase = true)) {
            return false
        }
        return element.text.lowercase(Locale.ROOT) in BASE_OUTPUT_MEMBERS
    }

    // Local-symbol helpers.
    private fun isLocalSymbolDeclaration(element: PsiElement): Boolean {
        if (!isLocalSymbolCandidate(element)) return false
        val prev = previousSignificantElement(element) ?: return false
        return prev.node?.elementType == DreamShaderTokenTypes.TYPE
    }

    private fun isLocalSymbolUsage(element: PsiElement): Boolean {
        if (!isLocalSymbolCandidate(element)) return false
        return !isLocalSymbolDeclaration(element)
    }

    private fun isLocalSymbolCandidate(element: PsiElement): Boolean {
        if (!isInsideDeclarationBody(element)) return false
        if (isDeclarationHeadIdentifier(element)) return false
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        val parentSection = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false)
        val sectionName = parentSection?.sectionName()
        val allowedBySection = sectionName == "graph" || sectionName == "outputs" || sectionName == "results" || sectionName == "inputs"
        val allowedByFunctionBody = declaration?.isFunctionLike() == true
        if (!allowedBySection && !allowedByFunctionBody) return false
        if (looksLikeCallableReference(element.text, element)) return false
        if (isNamespaceQualifier(element)) return false
        if (isSwizzleOrMemberComponent(element)) return false
        if (isNamedCallArgumentKey(element)) return false
        return true
    }

    private fun isSwizzleOrMemberComponent(element: PsiElement): Boolean {
        val prev = previousSignificantElement(element) ?: return false
        return prev.node?.elementType == DreamShaderTokenTypes.OPERATOR && prev.text == "."
    }

    private fun isNamedCallArgumentKey(element: PsiElement): Boolean {
        val next = skipWhitespaceForward(element) ?: return false
        if (next != "=") return false
        val prev = previousSignificantElement(element) ?: return false
        if (prev.node?.elementType == DreamShaderTokenTypes.LPAREN) return true
        return prev.node?.elementType == DreamShaderTokenTypes.OPERATOR && prev.text == ","
    }

    // Token navigation helpers.
    private fun previousSignificantElement(element: PsiElement): PsiElement? {
        var current = element.prevSibling
        while (current != null) {
            if (!current.text.isNullOrBlank()) {
                val type = current.node?.elementType
                if (type != DreamShaderTokenTypes.WHITE_SPACE &&
                    type != DreamShaderTokenTypes.LINE_COMMENT &&
                    type != DreamShaderTokenTypes.BLOCK_COMMENT
                ) {
                    return current
                }
            }
            current = current.prevSibling
        }

        var leaf = PsiTreeUtil.prevVisibleLeaf(element)
        while (leaf != null) {
            val type = leaf.node?.elementType
            if (type != DreamShaderTokenTypes.WHITE_SPACE &&
                type != DreamShaderTokenTypes.LINE_COMMENT &&
                type != DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                return leaf
            }
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
        }
        return null
    }

    private fun skipWhitespaceForward(element: PsiElement): String? {
        val text = element.containingFile.text
        val start = element.textRange.endOffset
        var i = start
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) return null
        return text[i].toString()
    }

    private val BASE_OUTPUT_MEMBERS = setOf(
        "materialattributes", "attributes", "basecolor", "emissivecolor", "emissive", "opacity", "opacitymask",
        "metallic", "specular", "roughness", "normal", "ambientocclusion", "ao", "refraction", "worldpositionoffset",
        "wpo", "pixeldepthoffset", "pdo", "subsurfacecolor", "clearcoat", "clearcoatroughness", "customdata0",
        "customdata1", "diffusecolor", "specularcolor", "surfacethickness", "displacement", "customizeduv0",
        "customizeduv1", "customizeduv2", "customizeduv3", "customizeduv4", "customizeduv5", "customizeduv6",
        "customizeduv7", "customizeduvs0", "customizeduvs1", "customizeduvs2", "customizeduvs3", "customizeduvs4",
        "customizeduvs5", "customizeduvs6", "customizeduvs7", "mooaencodedattribute0", "mooaencodedattribute1",
        "mooaencodedattribute2", "mooaencodedattribute3", "mooaencodedattribute4", "anisotropy", "tangent"
    )
}
