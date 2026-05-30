package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

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
        if (!isImportStringLiteral(element)) return null
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

        val symbolName = element.text
        if (symbolName.isBlank()) return null

        resolveUnqualifiedNamespaceMemberTarget(file, element, symbolName)?.let { return arrayOf(it) }

        val declarations = topLevelDeclarations(file)

        for (declaration in declarations) {
            if (declaration.declarationName() == symbolName) {
                return arrayOf(declaration)
            }
        }
        return null
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

    private fun isImportStringLiteral(element: PsiElement): Boolean {
        var prev = PsiTreeUtil.prevLeaf(element, true)
        while (prev != null) {
            val t = prev.node?.elementType
            if (t == DreamShaderTokenTypes.WHITE_SPACE || t == DreamShaderTokenTypes.LINE_COMMENT || t == DreamShaderTokenTypes.BLOCK_COMMENT) {
                prev = PsiTreeUtil.prevLeaf(prev, true)
                continue
            }
            return t == DreamShaderTokenTypes.KEYWORD && prev.text.equals("import", ignoreCase = true)
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
    }

    private fun resolveNamespaceQualifiedMemberTarget(
        file: PsiElement,
        element: PsiElement,
        qualifierChainBefore: List<String>
    ): DreamShaderDeclaration? {
        val namespacePath = qualifierChainBefore
        if (namespacePath.isEmpty()) return null
        val memberName = element.text

        val namespaceDeclaration = resolveNamespaceByPath(file, namespacePath) ?: return null

        return directChildDeclarations(namespaceDeclaration)
            .firstOrNull { declaration -> declaration.declarationName() == memberName }
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
}
