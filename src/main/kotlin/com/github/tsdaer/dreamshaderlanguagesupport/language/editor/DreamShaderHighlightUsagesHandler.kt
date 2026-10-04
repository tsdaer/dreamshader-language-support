package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerBase
import com.intellij.codeInsight.highlighting.HighlightUsagesHandlerFactory
import com.intellij.openapi.editor.Editor
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil

/**
 * In-file usage highlighting (D1): the caret on a declaration name or on one of its
 * identifier usages highlights every occurrence inside the current file (the
 * `Edit | Find Usages in File` semantics). DreamShader identifiers carry no permanent
 * PSI references - the file-local references are synthesized by
 * [com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.DreamShaderReferencesSearchExecutor]
 * during searches - so the platform's default usage highlighter cannot see them and the
 * language needs its own factory.
 */
class DreamShaderHighlightUsagesHandlerFactory : HighlightUsagesHandlerFactory {
    override fun createHighlightUsagesHandler(editor: Editor, file: PsiFile): HighlightUsagesHandlerBase<*>? {
        if (file.language != DreamShaderLanguage) return null
        val leaf = file.findElementAt(editor.caretModel.offset) ?: return null
        if (leaf.node?.elementType != DreamShaderTokenTypes.IDENTIFIER) return null
        if (PsiTreeUtil.getParentOfType(leaf, DreamShaderDeclaration::class.java, false) == null) return null
        return DreamShaderHighlightUsagesHandler(editor, file, leaf)
    }
}

private class DreamShaderHighlightUsagesHandler(
    editor: Editor,
    file: PsiFile,
    private val identifier: PsiElement,
) : HighlightUsagesHandlerBase<PsiElement>(editor, file) {
    override fun getTargets(): List<PsiElement> {
        return listOfNotNull(targetDeclaration())
    }

    override fun selectTargets(targets: List<PsiElement>, consumer: com.intellij.util.Consumer<in List<PsiElement>>) {
        consumer.consume(targets)
    }

    override fun computeUsages(targets: List<PsiElement>) {
        val declaration = targets.firstOrNull() as? DreamShaderDeclaration ?: return
        addOccurrence(declaration.nameIdentifier ?: declaration)
        val scope = LocalSearchScope(myFile)
        for (reference in ReferencesSearch.search(declaration, scope).findAll()) {
            addOccurrence(reference.element)
        }
    }

    /** The declaration whose name is under the caret, or that the under-caret usage resolves to. */
    private fun targetDeclaration(): DreamShaderDeclaration? {
        val containing = PsiTreeUtil.getParentOfType(identifier, DreamShaderDeclaration::class.java, false)
        if (containing != null && containing.nameIdentifier == identifier) return containing

        val scope = LocalSearchScope(myFile)
        for (candidate in PsiTreeUtil.collectElementsOfType(myFile, DreamShaderDeclaration::class.java)) {
            if (ReferencesSearch.search(candidate, scope).findAll().any { it.element == identifier }) {
                return candidate
            }
        }
        return null
    }
}
