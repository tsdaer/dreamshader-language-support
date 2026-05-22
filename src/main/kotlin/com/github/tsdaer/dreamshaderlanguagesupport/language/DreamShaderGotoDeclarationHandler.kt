package com.github.tsdaer.dreamshaderlanguagesupport.language

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
        val symbolName = element.text
        if (symbolName.isBlank()) return null

        val file = element.containingFile ?: return null
        val declarations = PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java)
            .filter { declaration ->
                PsiTreeUtil.getParentOfType(declaration, DreamShaderDeclaration::class.java, true) == null
            }

        for (declaration in declarations) {
            if (declaration.declarationName() == symbolName) {
                return arrayOf(declaration)
            }
        }
        return null
    }

    private fun isInsideDeclarationTree(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        if (declaration == null) return false
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
}
