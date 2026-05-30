package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiReference
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor

/**
 * 自定义参考文献搜索顶级DreamShader声明。
 *
 * 当前实现为文件本地和文本/PSI混合：它找到匹配
 * 标识符标记标记并过滤声明的头/注释/字符串。
 */
class DreamShaderReferencesSearchExecutor : com.intellij.util.QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val declaration = queryParameters.elementToSearch as? DreamShaderDeclaration ?: return true
        val file = declaration.containingFile ?: return true
        val declarationName = declaration.declarationName().orEmpty()
        if (declarationName.isBlank()) return true
        val declarationProfile = buildDeclarationProfile(declaration)

        val identifiers = PsiTreeUtil.collectElements(file) { element ->
            val t = element.node?.elementType
            t == DreamShaderTokenTypes.IDENTIFIER && element.text == declarationName
        }

        val declarationNameId = declaration.nameIdentifier
        for (identifier in identifiers) {
            if (identifier == declarationNameId) continue
            if (!isInsideDeclarationTree(identifier)) continue
            if (isDeclarationNameIdentifier(identifier)) continue
            if (!matchesDeclarationProfile(identifier, declarationProfile)) continue
            if (!consumer.process(DreamShaderLightReference(identifier, declaration))) return false
        }
        return true
    }

    private fun isInsideDeclarationTree(element: PsiElement): Boolean {
        val declaration =
            PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val tokenType = element.node?.elementType
        return tokenType != DreamShaderTokenTypes.LINE_COMMENT &&
            tokenType != DreamShaderTokenTypes.BLOCK_COMMENT &&
            tokenType != DreamShaderTokenTypes.STRING
    }

    private fun isDeclarationNameIdentifier(identifier: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(identifier, DreamShaderDeclaration::class.java, false) ?: return false
        return declaration.nameIdentifier == identifier
    }

    private fun buildDeclarationProfile(declaration: DreamShaderDeclaration): DeclarationProfile {
        val declarationName = declaration.declarationName().orEmpty()
        if (declaration.keywordText() == "namespace") {
            val namespacePath = enclosingNamespacePath(declaration) + declarationName
            return DeclarationProfile(
                namespacePath = namespacePath,
                isNamespaceDeclaration = true
            )
        }

        return DeclarationProfile(
            namespacePath = enclosingNamespacePath(declaration),
            isNamespaceDeclaration = false
        )
    }

    private fun matchesDeclarationProfile(identifier: PsiElement, profile: DeclarationProfile): Boolean {
        val fileText = identifier.containingFile?.text ?: return false
        val identifierRange = identifier.textRange ?: return false
        val qualifierChainBefore = readQualifierChainBeforeIdentifier(fileText, identifierRange.startOffset)
        val hasDoubleColonAfter = hasDoubleColonAfter(fileText, identifierRange.endOffset)

        if (profile.isNamespaceDeclaration) {
            if (!hasDoubleColonAfter) return false
            return qualifierChainBefore + identifier.text == profile.namespacePath
        }

        if (profile.namespacePath.isEmpty()) {
            // Top-level declarations should not consume namespace-qualified member usages.
            return qualifierChainBefore.isEmpty()
        }

        if (qualifierChainBefore.isNotEmpty()) {
            return qualifierChainBefore == profile.namespacePath
        }

        val enclosingNamespacePath = enclosingNamespacePath(identifier)
        return enclosingNamespacePath == profile.namespacePath
    }

    private fun enclosingNamespacePath(element: PsiElement): List<String> {
        val path = mutableListOf<String>()
        var current = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        while (current != null) {
            if (current.keywordText() == "namespace") {
                val name = current.declarationName()
                if (!name.isNullOrBlank()) {
                    path.add(name)
                }
            }
            current = PsiTreeUtil.getParentOfType(current, DreamShaderDeclaration::class.java, true)
        }
        path.reverse()
        return path
    }

    private fun hasDoubleColonAfter(text: String, startOffset: Int): Boolean {
        var i = startOffset
        while (i < text.length && text[i].isWhitespace()) i++
        return i + 1 < text.length && text[i] == ':' && text[i + 1] == ':'
    }

    private fun readQualifierChainBeforeIdentifier(text: String, anchorOffset: Int): List<String> {
        val qualifiers = mutableListOf<String>()
        var i = anchorOffset - 1
        while (true) {
            while (i >= 0 && text[i].isWhitespace()) i--
            if (i < 1 || text[i] != ':' || text[i - 1] != ':') break

            i -= 2
            while (i >= 0 && text[i].isWhitespace()) i--
            if (i < 0 || !isIdentifierChar(text[i])) return emptyList()

            val end = i
            while (i >= 0 && isIdentifierChar(text[i])) i--
            val start = i + 1
            if (start > end) return emptyList()
            qualifiers.add(text.substring(start, end + 1))
        }

        qualifiers.reverse()
        return qualifiers
    }

    private fun isIdentifierChar(ch: Char): Boolean = ch == '_' || ch.isLetterOrDigit()

    private data class DeclarationProfile(
        val namespacePath: List<String>,
        val isNamespaceDeclaration: Boolean
    )
}

/**
 * Lightweight synthetic reference used by [DreamShaderReferencesSearchExecutor].
 */
private class DreamShaderLightReference(
    private val sourceElement: PsiElement,
    private val targetDeclaration: DreamShaderDeclaration
) : com.intellij.psi.PsiReference {
    override fun getElement(): PsiElement = sourceElement

    override fun getRangeInElement(): TextRange = TextRange(0, sourceElement.textLength)

    override fun resolve(): PsiElement = targetDeclaration

    override fun getCanonicalText(): String = sourceElement.text

    override fun handleElementRename(newElementName: String): PsiElement {
        if (newElementName.isBlank()) return sourceElement
        val replacement = createIdentifierFromText(newElementName) ?: return sourceElement
        return sourceElement.replace(replacement)
    }

    override fun bindToElement(element: PsiElement): PsiElement = sourceElement

    override fun isReferenceTo(element: PsiElement): Boolean = element == targetDeclaration

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isSoft(): Boolean = true

    private fun createIdentifierFromText(newName: String): PsiElement? {
        val dummyFile = PsiFileFactory.getInstance(sourceElement.project).createFileFromText(
            "dummy.dsh",
            DreamShaderLanguage,
            "Function $newName { }"
        )
        val declaration = PsiTreeUtil.findChildOfType(dummyFile, DreamShaderDeclaration::class.java) ?: return null
        return declaration.nameIdentifier
    }
}
