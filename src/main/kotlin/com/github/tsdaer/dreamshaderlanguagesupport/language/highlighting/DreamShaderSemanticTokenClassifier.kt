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
        val node = element.node ?: return null
        return when (node.elementType) {
            DreamShaderTokenTypes.KEYWORD -> classifyKeyword(element)
            DreamShaderTokenTypes.SECTION -> DreamShaderTextAttributes.SECTION
            DreamShaderTokenTypes.IDENTIFIER -> classifyIdentifier(element)
            else -> null
        }
    }

    private fun classifyKeyword(element: PsiElement): TextAttributesKey? {
        val text = element.text.lowercase(Locale.ROOT)
        if (isDeclarationKeywordElement(element)) return DreamShaderTextAttributes.KEYWORD
        if (text in CONTROL_FLOW_KEYWORDS) return DreamShaderTextAttributes.CONTROL_FLOW
        if (text in QUALIFIER_KEYWORDS) return DreamShaderTextAttributes.QUALIFIER
        if (text in CONSTANT_KEYWORDS) return DreamShaderTextAttributes.CONSTANT
        if (text == "import") return DreamShaderTextAttributes.IMPORT
        return DreamShaderTextAttributes.KEYWORD
    }

    private fun classifyIdentifier(element: PsiElement): TextAttributesKey? {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        if (declaration != null && element == declaration.nameIdentifier) {
            return DreamShaderTextAttributes.DECLARATION_NAME
        }

        if (isUeNamespaceIdentifier(element)) return DreamShaderTextAttributes.BUILTIN_NAMESPACE
        if (isNamespaceQualifier(element)) return DreamShaderTextAttributes.NAMESPACE_QUALIFIER

        if (isMaterialOutputMemberIdentifier(element)) return DreamShaderTextAttributes.MATERIAL_OUTPUT_MEMBER

        if (isSettingsKey(element)) return DreamShaderTextAttributes.SETTINGS_KEY

        if (isBuiltinFunctionCall(element)) return DreamShaderTextAttributes.BUILTIN_FUNCTION

        if (isLocalSymbolDeclaration(element)) return DreamShaderTextAttributes.LOCAL_SYMBOL_DECLARATION
        if (isLocalSymbolUsage(element)) return DreamShaderTextAttributes.LOCAL_SYMBOL

        val text = element.text
        if (!looksLikeCallableReference(text, element)) return null
        if (isDeclarationHeadIdentifier(element)) return null
        if (!isCallableContext(element)) return null
        return DreamShaderTextAttributes.CALLABLE_REFERENCE
    }

    // Declaration/body scope helpers.
    private fun isDeclarationKeywordElement(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val declNode = declaration.node ?: return false
        val firstKeyword = declNode.getChildren(null).firstOrNull { it.elementType == DreamShaderTokenTypes.KEYWORD }
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

    private fun isCallableContext(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val bodyRange = declaration.bodyTextRange() ?: return false
        if (!bodyRange.contains(element.textRange)) return false

        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false) ?: return true
        return when (section.sectionName()?.lowercase(Locale.ROOT)) {
            "settings" -> false
            else -> true
        }
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
        if (prev.node?.elementType != DreamShaderTokenTypes.OPERATOR || prev.text != ".") return false
        val base = previousSignificantElement(prev) ?: return false
        if (base.node?.elementType != DreamShaderTokenTypes.IDENTIFIER || !base.text.equals("Base", ignoreCase = true)) {
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

    private val CONTROL_FLOW_KEYWORDS = setOf(
        "if", "else", "for", "while", "do", "switch", "case", "default", "break", "continue", "return"
    )

    private val QUALIFIER_KEYWORDS = setOf(
        "const", "static", "in", "out", "inout", "opt"
    )

    private val CONSTANT_KEYWORDS = setOf(
        "true", "false"
    )

    private val KNOWN_SETTINGS_KEYS = setOf(
        "domain", "materialdomain", "shadingmodel", "blendmode", "rendertype",
        "translucencylightingmode", "lightingmode", "twosided", "wireframe",
        "ditheredlodtransition", "ditheropacitymask", "allownegativeemissivecolor",
        "castdynamicshadowasmasked", "responsiveaa", "screenspacereflections",
        "contactshadows", "disabledepthtest", "outputtranslucentvelocity",
        "tangentspacenormal", "fullyrough", "issky", "thinsurface",
        "numcustomizeduvs", "refractionmethod", "refractionmode",
        "asset", "description"
    )

    private val BUILTIN_FUNCTION_NAMES: Set<String> = setOf(
        "texcoord", "time", "panner", "worldposition", "objectpositionws",
        "cameravectorws", "screenposition", "vertexcolor",
        "transformvector", "transformposition", "expression",
        "collectionparam", "staticswitchparameter",
        "abs", "acos", "asin", "atan", "atan2", "ceil", "clamp", "cos", "cross",
        "ddx", "ddy", "distance", "dot", "exp", "exp2", "floor", "frac", "fmod",
        "length", "lerp", "log", "log2", "max", "min", "mul", "normalize", "pow",
        "reflect", "rsqrt", "saturate", "sin", "smoothstep", "sqrt", "step", "tan"
    )

    private fun isSettingsKey(element: PsiElement): Boolean {
        if (element.text.isBlank()) return false
        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false) ?: return false
        val sectionName = section.sectionName()?.lowercase(Locale.ROOT) ?: return false
        if (sectionName != "settings" && sectionName != "options") return false
        val text = element.text.lowercase(Locale.ROOT)
        if (text !in KNOWN_SETTINGS_KEYS) return false
        val next = skipWhitespaceForward(element)
        return next == "=" || next == ";"
    }

    private fun isBuiltinFunctionCall(element: PsiElement): Boolean {
        if (!isInsideDeclarationBody(element)) return false
        val text = element.text.lowercase(Locale.ROOT)
        if (text !in BUILTIN_FUNCTION_NAMES) return false
        if (!looksLikeCallableReference(element.text, element)) return false
        val prev = previousSignificantElement(element) ?: return true
        if (prev.node.elementType == DreamShaderTokenTypes.IDENTIFIER) {
            val prevText = prev.text
            val prevPrev = previousSignificantElement(prev)
            val isMemberAccess = prevPrev?.node?.elementType == DreamShaderTokenTypes.OPERATOR && prevPrev.text == "."
            if (!isMemberAccess) return false
        }
        return true
    }
}
