package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.dreamShader2DeclaratorNames
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.psiTreeWalk
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCompletionDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2Leafs
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.util.CommonRefactoringUtil

/**
 * Inline Uniform for the 2.0 dialect (plan C22): the uniform's initializer value replaces
 * every usage in the current file and the declaration (with its `///` doc block) is removed.
 *
 * The write is destructive, so every precondition is checked before anything is touched and
 * any doubt refuses with a hint — the "never half-done" contract from Extract Variable:
 * - the uniform must be single-declarator and carry an initializer;
 * - material instances overriding the uniform (`.dsi`, via the rename safety net) refuse —
 *   deleting the declaration would leave their overrides dangling;
 * - usages in other files refuse (inline from those files first);
 * - another uniform/global initializer reading this uniform refuses (it would read nothing).
 */
internal class DreamShader2InlineUniformHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is DreamShaderPsiFile) return
        if (!DreamShaderCompletionDialects.servesV2(file)) return
        val extension = DreamShaderDialects.extensionOf(file.virtualFile?.name ?: file.name)
        if (extension != "dss") {
            showHint(project, editor, "refactoring.inlineUniform.onlyDss")
            return
        }

        val target = resolveTarget(file, editor)
        if (target == null) {
            showHint(project, editor, "refactoring.inlineUniform.notUniform")
            return
        }
        val declaration = target.declaration
        val name = target.name

        if (dreamShader2DeclaratorNames(declaration).size != 1) {
            showHint(project, editor, "refactoring.inlineUniform.multiDeclarator")
            return
        }
        val initializer = initializerText(file, declaration)
        if (initializer.isNullOrEmpty()) {
            showHint(project, editor, "refactoring.inlineUniform.noInitializer")
            return
        }

        val analysis = DreamShader2FileAnalysis.of(file)
        val initializerReadsName = Regex("\\b${Regex.escape(name)}\\b")
        val readElsewhere = (analysis.uniforms.asSequence() + analysis.globals.asSequence())
            .filter { it !== declaration }
            .any { other -> initializerText(file, other)?.contains(initializerReadsName) == true }
        if (readElsewhere) {
            showHint(project, editor, "refactoring.inlineUniform.referencedByInitializer")
            return
        }

        val overrides = DreamShaderRenameImpactChecker.impactsOf(declaration)
            .filter { it.kind == DreamShaderRenameImpactChecker.Impact.Kind.OVERRIDE }
        if (overrides.isNotEmpty()) {
            showHint(
                project,
                editor,
                "refactoring.inlineUniform.hasOverrides",
                overrides.joinToString("\n") { it.filePath },
            )
            return
        }

        val externalUsages = ReferencesSearch.search(declaration, GlobalSearchScope.projectScope(project))
            .findAll()
            .mapNotNull { it.element?.containingFile?.virtualFile }
            .filter { it != file.virtualFile }
            .distinct()
        if (externalUsages.isNotEmpty()) {
            showHint(
                project,
                editor,
                "refactoring.inlineUniform.crossFileUsages",
                externalUsages.size.toString(),
            )
            return
        }

        val usages = psiTreeWalk(file)
            .filter { it.node?.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION && it.text == name }
            .filter { PsiTreeUtil.getParentOfType(it, DreamShader2UniformDeclaration::class.java) !== declaration }
            .sortedByDescending { it.textRange.startOffset }
            .toList()

        runInline(project, editor, file, declaration, usages, initializer)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) = Unit

    // ------------------------------------------------------------------
    // target resolution
    // ------------------------------------------------------------------

    private fun resolveTarget(file: DreamShaderPsiFile, editor: Editor): InlineTarget? {
        val caret = editor.caretModel.offset.coerceIn(0, file.textLength)
        val candidates = listOfNotNull(
            file.findElementAt(caret),
            file.findElementAt((caret - 1).coerceAtLeast(0)),
        )
        val identifier = candidates.firstOrNull {
            it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER
        } ?: return null
        val name = identifier.text

        val enclosing = PsiTreeUtil.getParentOfType(identifier, DreamShader2UniformDeclaration::class.java)
        if (enclosing != null) {
            if (name in dreamShader2DeclaratorNames(enclosing)) return InlineTarget(enclosing, name)
            return null
        }
        val analysis = DreamShader2FileAnalysis.of(file)
        val byName = analysis.uniforms.firstOrNull { name in dreamShader2DeclaratorNames(it) }
            ?: return null
        return InlineTarget(byName, name)
    }

    // ------------------------------------------------------------------
    // initializer extraction
    // ------------------------------------------------------------------

    /** The text between a declaration's `=` and its `;` (the whole initializer slot). */
    private fun initializerText(file: DreamShaderPsiFile, declaration: PsiElement): String? {
        val leafs = dreamShader2Leafs(declaration).toList()
        val assignIndex = leafs.indexOfFirst {
            it.elementType == DreamShaderTokenTypes.OPERATOR && it.text == "="
        }
        val semicolonIndex = leafs.indexOfLast { it.elementType == DreamShaderTokenTypes.SEMICOLON }
        if (assignIndex < 0 || semicolonIndex <= assignIndex) return null
        val start = leafs[assignIndex].startOffset + leafs[assignIndex].textLength
        val end = leafs[semicolonIndex].startOffset
        if (end <= start) return null
        return file.text.substring(start, end).trim()
    }

    // ------------------------------------------------------------------
    // the write
    // ------------------------------------------------------------------

    private fun runInline(
        project: Project,
        editor: Editor,
        file: DreamShaderPsiFile,
        declaration: DreamShader2UniformDeclaration,
        usages: List<PsiElement>,
        initializer: String,
    ) {
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(
            project,
            DreamShaderBundle.message("refactoring.inlineUniform.title"),
            null,
            Runnable {
                // Usage replacements first, highest offset first, so earlier ranges stay valid.
                usages.forEach { usage ->
                    document.replaceString(usage.textRange.startOffset, usage.textRange.endOffset, "($initializer)")
                }

                // The declaration goes last, together with its doc block and the line break.
                val startElement = docBlockBefore(declaration) ?: declaration
                val start = document.getLineStartOffset(document.getLineNumber(startElement.textRange.startOffset))
                var end = declaration.textRange.endOffset
                val text = document.charsSequence
                if (end < text.length && text[end] == '\r') end++
                if (end < text.length && text[end] == '\n') end++
                document.deleteString(start, end)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            },
        )
    }

    private fun docBlockBefore(declaration: PsiElement): PsiElement? {
        var previous = declaration.prevSibling
        while (previous != null && previous.node?.elementType == DreamShader2ElementTypes.DOC_BLOCK) {
            return previous
        }
        return null
    }

    private fun showHint(project: Project, editor: Editor, messageKey: String, arg: String = "") {
        val message = if (arg.isEmpty()) {
            DreamShaderBundle.message(messageKey)
        } else {
            DreamShaderBundle.message(messageKey, arg)
        }
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            message,
            DreamShaderBundle.message("refactoring.inlineUniform.title"),
            null,
        )
    }

    private data class InlineTarget(val declaration: DreamShader2UniformDeclaration, val name: String)
}
