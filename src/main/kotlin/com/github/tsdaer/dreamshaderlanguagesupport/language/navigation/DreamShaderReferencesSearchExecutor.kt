package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
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

        val identifiers = PsiTreeUtil.collectElements(file) { element ->
            val t = element.node?.elementType
            t == DreamShaderTokenTypes.IDENTIFIER && element.text == declarationName
        }

        val declarationNameId = declaration.nameIdentifier
        for (identifier in identifiers) {
            if (identifier == declarationNameId) continue
            if (!isInsideDeclarationTree(identifier)) continue
            if (!consumer.process(DreamShaderLightReference(identifier, declaration))) return false
        }
        return true
    }

    private fun isInsideDeclarationTree(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        if (declaration == null) return false
        val tokenType = element.node?.elementType
        return tokenType != DreamShaderTokenTypes.LINE_COMMENT &&
            tokenType != DreamShaderTokenTypes.BLOCK_COMMENT &&
            tokenType != DreamShaderTokenTypes.STRING
    }
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

    override fun handleElementRename(newElementName: String): PsiElement = sourceElement

    override fun bindToElement(element: PsiElement): PsiElement = sourceElement

    override fun isReferenceTo(element: PsiElement): Boolean = element == targetDeclaration

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isSoft(): Boolean = true
}
