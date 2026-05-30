package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale

/**
 * 前往声明处理器：
 * - 导入字符串文字 -> 目标文件
 * - 标识符引用 -> 顶层声明与名称匹配
 */
class DreamShaderGotoDeclarationHandler : GotoDeclarationHandler {
    override fun getGotoDeclarationTargets(
        sourceElement: PsiElement?,
        offset: Int,
        editor: Editor
    ): Array<PsiElement>? {
        if (sourceElement == null || sourceElement.language != DreamShaderLanguage) return null

        val elementType = sourceElement.node?.elementType ?: return null
        return when (elementType) {
            DreamShaderTokenTypes.STRING -> resolveImportTargets(sourceElement)
            DreamShaderTokenTypes.IDENTIFIER -> resolveDeclarationTargets(sourceElement)
            else -> null
        }
    }

    override fun getActionText(context: DataContext): String? = null

    private fun resolveImportTargets(element: PsiElement): Array<PsiElement>? {
        if (!DreamShaderImportClosureResolver.isImportStringLiteralToken(element)) return null
        val rawText = element.text
        if (rawText.length < 2 || !rawText.startsWith('"') || !rawText.endsWith('"')) return null
        val importPath = rawText.substring(1, rawText.length - 1).trim()
        if (importPath.isBlank()) return null

        val project = element.project
        val psiManager = PsiManager.getInstance(project)
        val vf = DreamShaderImportResolver.resolveImport(
            projectBasePath = project.basePath ?: return null,
            containingDirectory = element.containingFile?.virtualFile?.parent,
            importPath = importPath
        ) ?: return null
        val psiFile = psiManager.findFile(vf) ?: return null
        return arrayOf(psiFile)
    }

    private fun resolveDeclarationTargets(element: PsiElement): Array<PsiElement>? {
        if (isDeclarationNameIdentifier(element)) return null
        if (!isInsideDeclarationTree(element)) return null
        val elementType = element.node?.elementType
        if (elementType != DreamShaderTokenTypes.IDENTIFIER) return null

        val file = element.containingFile ?: return null
        val text = file.text
        val range = element.textRange
        val qualifierChainBefore = readQualifierChainBeforeIdentifier(text, range.startOffset)
        val hasQualifierAfter = hasDoubleColonAfter(text, range.endOffset)

        if (hasQualifierAfter) {
            resolveNamespaceQualifierTarget(file, element, qualifierChainBefore)?.let { return arrayOf(it) }
            return null
        }

        if (qualifierChainBefore.isNotEmpty()) {
            resolveNamespaceQualifiedMemberTarget(file, element, qualifierChainBefore)?.let { return arrayOf(it) }
            return null
        }

        resolveNearestVariableLikeDeclarationTarget(element)?.let { return arrayOf(it) }

        val symbolName = element.text
        if (symbolName.isBlank()) return null

        resolveUnqualifiedNamespaceMemberTarget(file, element, symbolName)?.let { return arrayOf(it) }

        resolveTopLevelDeclarationBySymbol(file, symbolName)?.let { return arrayOf(it) }
        resolveTopLevelDeclarationBySymbolInImportedFiles(file, symbolName)?.let { return arrayOf(it) }
        return null
    }

    private fun resolveNearestVariableLikeDeclarationTarget(element: PsiElement): PsiElement? {
        val symbolName = element.text
        if (symbolName.isBlank()) return null

        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return null
        val bodyRange = declaration.bodyTextRange() ?: return null
        val searchLowerBound = if (declaration.isFunctionLike()) {
            declaration.textRange.startOffset
        } else {
            bodyRange.startOffset
        }
        val usageStart = element.textRange.startOffset
        if (usageStart <= searchLowerBound) return null

        var current = PsiTreeUtil.prevVisibleLeaf(element)
        while (current != null) {
            val start = current.textRange.startOffset
            if (start < searchLowerBound) break

            if (current.node?.elementType == DreamShaderTokenTypes.IDENTIFIER &&
                current.text == symbolName &&
                isVariableLikeDeclarationIdentifier(current)
            ) {
                return current
            }
            current = PsiTreeUtil.prevVisibleLeaf(current)
        }
        return null
    }

    private fun isVariableLikeDeclarationIdentifier(identifier: PsiElement): Boolean {
        if (identifier.node?.elementType != DreamShaderTokenTypes.IDENTIFIER) return false
        if (isDeclarationNameIdentifier(identifier)) return false
        if (isNamedCallArgumentKey(identifier)) return false
        if (isNamespaceQualifier(identifier)) return false
        if (isMemberAccessComponent(identifier)) return false

        val previous = previousNonTriviaLeaf(identifier) ?: return false
        if (previous.node?.elementType != DreamShaderTokenTypes.TYPE) return false

        val next = nextNonTriviaLeaf(identifier) ?: return false
        if (!isVariableDeclarationFollower(next.text)) return false

        return true
    }

    private fun isVariableDeclarationFollower(tokenText: String): Boolean {
        return tokenText == "=" || tokenText == ";" || tokenText == "," || tokenText == ")" || tokenText == "["
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
        var child = declaration.node.firstChildNode
        var seenKeyword = false
        while (child != null) {
            if (child.elementType == DreamShaderTokenTypes.KEYWORD) {
                seenKeyword = true
            } else if (seenKeyword && child.elementType == DreamShaderTokenTypes.IDENTIFIER) {
                return child.psi == identifier
            }
            child = child.treeNext
        }
        return false
    }

    private fun resolveNamespaceQualifierTarget(
        file: PsiElement,
        element: PsiElement,
        qualifierChainBefore: List<String>
    ): DreamShaderDeclaration? {
        val namespacePath = qualifierChainBefore + element.text
        return resolveNamespaceByPath(file, namespacePath)
            ?: resolveNamespaceByPathInImportedFiles(file, namespacePath)
    }

    private fun resolveNamespaceQualifiedMemberTarget(
        file: PsiElement,
        element: PsiElement,
        qualifierChainBefore: List<String>
    ): DreamShaderDeclaration? {
        val namespacePath = qualifierChainBefore
        if (namespacePath.isEmpty()) return null
        val memberName = element.text

        val namespaceDeclaration = resolveNamespaceByPath(file, namespacePath)
        if (namespaceDeclaration != null) {
            return directChildDeclarations(namespaceDeclaration)
                .firstOrNull { declaration -> declaration.declarationName() == memberName }
        }

        return resolveNamespaceMemberByPathInImportedFiles(file, namespacePath, memberName)
    }

    private fun resolveUnqualifiedNamespaceMemberTarget(
        file: PsiElement,
        element: PsiElement,
        symbolName: String
    ): DreamShaderDeclaration? {
        val namespacePath = enclosingNamespacePath(element)
        if (namespacePath.isEmpty()) return null

        for (depth in namespacePath.size downTo 1) {
            val candidatePath = namespacePath.subList(0, depth)
            val namespaceDeclaration = resolveNamespaceByPath(file, candidatePath) ?: continue
            val member = directChildDeclarations(namespaceDeclaration)
                .firstOrNull { declaration -> declaration.declarationName() == symbolName }
            if (member != null) return member
        }
        return null
    }

    private fun resolveNamespaceByPathInImportedFiles(
        file: PsiElement,
        namespacePath: List<String>
    ): DreamShaderDeclaration? {
        if (namespacePath.isEmpty()) return null
        for (importedFile in resolveImportedDreamShaderFiles(file)) {
            val target = resolveNamespaceByPath(importedFile, namespacePath)
            if (target != null) return target
        }
        return null
    }

    private fun resolveNamespaceMemberByPathInImportedFiles(
        file: PsiElement,
        namespacePath: List<String>,
        memberName: String
    ): DreamShaderDeclaration? {
        if (namespacePath.isEmpty() || memberName.isBlank()) return null
        for (importedFile in resolveImportedDreamShaderFiles(file)) {
            val namespace = resolveNamespaceByPath(importedFile, namespacePath) ?: continue
            val member = directChildDeclarations(namespace)
                .firstOrNull { declaration -> declaration.declarationName() == memberName }
            if (member != null) return member
        }
        return null
    }

    private fun resolveTopLevelDeclarationBySymbol(file: PsiElement, symbolName: String): DreamShaderDeclaration? {
        if (symbolName.isBlank()) return null
        return topLevelDeclarations(file).firstOrNull { declarationMatchesSymbolName(it, symbolName) }
    }

    private fun resolveTopLevelDeclarationBySymbolInImportedFiles(
        file: PsiElement,
        symbolName: String
    ): DreamShaderDeclaration? {
        if (symbolName.isBlank()) return null
        for (importedFile in resolveImportedDreamShaderFiles(file)) {
            val declaration = resolveTopLevelDeclarationBySymbol(importedFile, symbolName)
            if (declaration != null) return declaration
        }
        return null
    }

    private fun declarationMatchesSymbolName(declaration: DreamShaderDeclaration, symbolName: String): Boolean {
        return declarationSymbolNames(declaration)
            .any { candidate -> candidate.equals(symbolName, ignoreCase = false) }
    }

    private fun declarationSymbolNames(declaration: DreamShaderDeclaration): Set<String> {
        val names = linkedSetOf<String>()
        val explicit = declaration.declarationName().orEmpty().trim()
        if (explicit.isNotBlank() && !explicit.equals("name", ignoreCase = true)) {
            names.add(explicit)
        }

        val keyword = declaration.keywordText().orEmpty().lowercase(Locale.ROOT)
        if (keyword in CALLABLE_NAME_ATTRIBUTE_DECLARATIONS) {
            val attrName = extractNameAttributeValue(declaration)
            if (!attrName.isNullOrBlank()) {
                names.add(attrName)
                names.add(attrName.substringAfterLast('/').substringAfterLast('\\'))
            }
        }

        return names.filter { it.isNotBlank() }.toSet()
    }

    private fun extractNameAttributeValue(declaration: DreamShaderDeclaration): String? {
        val bodyStart = declaration.bodyTextRange()?.startOffset ?: declaration.text.length
        if (bodyStart <= 0 || bodyStart > declaration.text.length) return null
        val head = declaration.text.substring(0, bodyStart)
        return NAME_ATTRIBUTE_REGEX.find(head)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun resolveImportedDreamShaderFiles(file: PsiElement): List<PsiFile> {
        val sourceFile = file.containingFile ?: return emptyList()
        return DreamShaderImportClosureResolver.resolveImportClosure(sourceFile)
            .drop(1)
    }

    private fun topLevelDeclarations(file: PsiElement): List<DreamShaderDeclaration> {
        return PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java)
            .filter { declaration ->
                PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true) == null
            }
            .toList()
    }

    private fun directChildDeclarations(parentDeclaration: DreamShaderDeclaration): List<DreamShaderDeclaration> {
        return PsiTreeUtil.findChildrenOfType(parentDeclaration, DreamShaderDeclaration::class.java)
            .filter { declaration ->
                declaration != parentDeclaration &&
                    PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true) == parentDeclaration
            }
            .toList()
    }

    private fun hasDoubleColonAfter(text: String, startOffset: Int): Boolean {
        var i = startOffset
        while (i < text.length && text[i].isWhitespace()) i++
        return i + 1 < text.length && text[i] == ':' && text[i + 1] == ':'
    }

    private fun isNamespaceQualifier(element: PsiElement): Boolean {
        val text = element.containingFile.text
        var i = element.textRange.endOffset
        while (i < text.length && text[i].isWhitespace()) i++
        if (i + 1 >= text.length) return false
        return text[i] == ':' && text[i + 1] == ':'
    }

    private fun isNamedCallArgumentKey(element: PsiElement): Boolean {
        val next = nextNonTriviaLeaf(element) ?: return false
        if (next.text != "=") return false

        val prev = previousNonTriviaLeaf(element) ?: return false
        return prev.text == "(" || prev.text == ","
    }

    private fun isMemberAccessComponent(element: PsiElement): Boolean {
        val prev = previousNonTriviaLeaf(element) ?: return false
        return prev.text == "."
    }

    private fun previousNonTriviaLeaf(element: PsiElement): PsiElement? {
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

    private fun nextNonTriviaLeaf(element: PsiElement): PsiElement? {
        var leaf = PsiTreeUtil.nextVisibleLeaf(element)
        while (leaf != null) {
            val type = leaf.node?.elementType
            if (type != DreamShaderTokenTypes.WHITE_SPACE &&
                type != DreamShaderTokenTypes.LINE_COMMENT &&
                type != DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                return leaf
            }
            leaf = PsiTreeUtil.nextVisibleLeaf(leaf)
        }
        return null
    }

    private fun resolveNamespaceByPath(file: PsiElement, namespacePath: List<String>): DreamShaderDeclaration? {
        if (namespacePath.isEmpty()) return null
        var current = topLevelDeclarations(file)
            .firstOrNull { declaration ->
                declaration.keywordText() == "namespace" && declaration.declarationName() == namespacePath.first()
            } ?: return null

        for (i in 1 until namespacePath.size) {
            val segment = namespacePath[i]
            current = directChildDeclarations(current)
                .firstOrNull { declaration ->
                    declaration.keywordText() == "namespace" && declaration.declarationName() == segment
                } ?: return null
        }
        return current
    }

    private fun enclosingNamespacePath(element: PsiElement): List<String> {
        val path = mutableListOf<String>()
        var current = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        while (current != null) {
            if (current.keywordText() == "namespace") {
                val namespaceName = current.declarationName()
                if (!namespaceName.isNullOrBlank()) {
                    path.add(namespaceName)
                }
            }
            current = PsiTreeUtil.getParentOfType(current, DreamShaderDeclaration::class.java, true)
        }
        path.reverse()
        return path
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

    companion object {
        private val CALLABLE_NAME_ATTRIBUTE_DECLARATIONS = setOf(
            "virtualfunction",
            "shaderfunction",
            "shaderlayer",
            "shaderlayerblend"
        )
        private val NAME_ATTRIBUTE_REGEX = Regex("\\bName\\s*=\\s*\"([^\"]+)\"")
    }
}
