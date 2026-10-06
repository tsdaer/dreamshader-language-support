package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2DiagnosticsPass
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.dreamShader2DeclaratorNames
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.psiTreeWalk
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCompletionDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderSignatureHelpAnalyzer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2ParameterList
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2Leafs
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.util.CommonRefactoringUtil

/**
 * Inline Variable for the 2.0 dialect (plan C25): a single-declarator local with an initializer
 * has its value substituted into every usage and the declaration is removed — the Introduce
 * counterpart of Inline Uniform, sharing its "check everything, then write once" contract.
 *
 * Inlining a local evaluates the initializer at each use site instead of once at the
 * declaration, so the initializer must be drift-proof (the plan card's purity precheck):
 * - literals, operators, type constructors, and builtin/catalog calls only — a call to a
 *   function declared in this file, or to anything unknown, refuses (it could observe state
 *   that changed since the declaration);
 * - references must name uniforms (file-scope inputs the body cannot rewrite); locals,
 *   parameters, and globals refuse;
 * - assignments, compound assignments, and `++`/`--` in the initializer refuse.
 *
 * Usages must be reads (assignment LHS, compound targets, and increment operands refuse, as do
 * writes through a member or index chain) and lexically visible where they stand (the M15 C24.1
 * scope rule). `@custom` bodies carry no structured statements, so a declaration or usage
 * inside one is invisible here by construction — the target resolution simply refuses.
 */
internal class DreamShader2InlineVariableHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is DreamShaderPsiFile) return
        if (!DreamShaderCompletionDialects.servesV2(file)) return

        val target = resolveTarget(file, editor)
        if (target == null) {
            showHint(project, editor, "refactoring.inlineVariable.notLocal")
            return
        }
        val name = target.name

        if (declaratorNames(target.statement).size != 1) {
            showHint(project, editor, "refactoring.inlineVariable.multiDeclarator")
            return
        }
        val initializer = initializerText(file, target.statement)
        if (initializer.isNullOrEmpty()) {
            showHint(project, editor, "refactoring.inlineVariable.noInitializer")
            return
        }

        val uniformNames = DreamShader2FileAnalysis.of(file)
            .uniforms
            .flatMap { dreamShader2DeclaratorNames(it) }
            .toSet()
        val purity = initializerPurityProblem(target.statement, uniformNames, file.text)
        if (purity != null) {
            showHint(project, editor, purity)
            return
        }

        val scopeOwner = scopeOwnerOf(target.statement)
        val usages = psiTreeWalk(file)
            .filter { it.node?.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION && it.text == name }
            .filter { it.textRange.startOffset > target.statement.textRange.endOffset }
            .filter { scopeOwner == null || scopeOwner in ancestorScopesOf(it) }
            .toList()
        if (usages.any { sitsInWritePosition(it) }) {
            showHint(project, editor, "refactoring.inlineVariable.usageIsWrite")
            return
        }

        runInline(project, editor, file, target.statement, usages.sortedByDescending { it.textRange.startOffset }, initializer)
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
        if (PsiTreeUtil.getParentOfType(identifier, DreamShader2ParameterList::class.java) != null) return null
        val name = identifier.text

        // The declaration name leaf, or a reference nested in another declaration's initializer.
        val statement = ancestorOfElementType(identifier, DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT)
        if (statement != null && name in declaratorNames(statement)) {
            return InlineTarget(statement, name)
        }
        // A usage: the nearest lexically visible declaration of this name before the caret.
        val function = PsiTreeUtil.getParentOfType(identifier, DreamShader2FunctionDeclaration::class.java)
            ?: return null
        val visibleScopes = ancestorScopesOf(identifier)
        var match: PsiElement? = null
        var matchStart = Int.MIN_VALUE
        psiTreeWalk(function).forEach { node ->
            if (node.node?.elementType != DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT) return@forEach
            val start = node.textRange.startOffset
            if (start > matchStart && start < identifier.textRange.startOffset &&
                name in declaratorNames(node) &&
                (scopeOwnerOf(node) == null || scopeOwnerOf(node) in visibleScopes)
            ) {
                match = node
                matchStart = start
            }
        }
        return match?.let { InlineTarget(it, name) }
    }

    /** Nearest ancestor of [element] with [elementType], stopping at the function boundary. */
    private fun ancestorOfElementType(element: PsiElement, elementType: com.intellij.psi.tree.IElementType): PsiElement? {
        var current: PsiElement? = element.parent
        while (current != null) {
            val type = current.node?.elementType ?: return null
            if (type == elementType) return current
            if (type == DreamShader2ElementTypes.FUNCTION_DECLARATION) return null
            current = current.parent
        }
        return null
    }

    // ------------------------------------------------------------------
    // initializer extraction and purity
    // ------------------------------------------------------------------

    /** The text between a declaration's `=` and its `;` (the whole initializer slot). */
    private fun initializerText(file: DreamShaderPsiFile, statement: PsiElement): String? {
        val leafs = dreamShader2Leafs(statement).toList()
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

    /**
     * One message key when the initializer cannot be repeated at the use sites: side effects,
     * a call that could observe drifted state, or a reference to something mutable. Uniforms
     * are the only references allowed — file-scope inputs the body cannot rewrite.
     */
    private fun initializerPurityProblem(
        statement: PsiElement,
        uniformNames: Set<String>,
        fileText: String,
    ): String? {
        val leafs = dreamShader2Leafs(statement).toList()
        val assignIndex = leafs.indexOfFirst {
            it.elementType == DreamShaderTokenTypes.OPERATOR && it.text == "="
        }
        val semicolonIndex = leafs.indexOfLast { it.elementType == DreamShaderTokenTypes.SEMICOLON }
        if (assignIndex < 0 || semicolonIndex <= assignIndex) return null
        val start = leafs[assignIndex].startOffset
        val end = leafs[semicolonIndex].startOffset

        fun walk(element: PsiElement): String? {
            val range = element.textRange
            // Everything below the statement must sit inside the initializer slot: after the
            // `=` (left-inclusive keeps the declaration's own operator out) and before the
            // `;` (right-exclusive keeps the initializer that touches the semicolon).
            if (range.startOffset <= start || range.endOffset > end) return null
            val elementType = element.node?.elementType
            if (elementType == DreamShader2ElementTypes.CALL_EXPRESSION) {
                val path = callPath(element) ?: return "refactoring.inlineVariable.unsupportedCall"
                val declaredHere = DreamShaderSignatureHelpAnalyzer
                    .resolveDeclaredSignatures(path, fileText)
                    .isNotEmpty()
                val knownBuiltin = DreamShaderSignatureHelpAnalyzer.resolveSignatures(path).isNotEmpty()
                if (declaredHere || !knownBuiltin) {
                    return "refactoring.inlineVariable.unsupportedCall"
                }
                // Callee leaves are path segments, not value reads; only the arguments recurse.
                val arguments = element.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST)?.psi
                    ?: return null
                return walk(arguments)
            }
            if (elementType == DreamShaderTokenTypes.OPERATOR && element.text in SIDE_EFFECT_OPERATORS) {
                return "refactoring.inlineVariable.sideEffects"
            }
            if (elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION && element.text !in uniformNames) {
                return "refactoring.inlineVariable.readsMutable"
            }
            var child = element.firstChild
            while (child != null) {
                walk(child)?.let { return it }
                child = child.nextSibling
            }
            return null
        }

        // The statement straddles its own initializer slot (its head is the type and the
        // name), so the scan starts at the children and the per-element guard takes over.
        var child = statement.firstChild
        while (child != null) {
            walk(child)?.let { return it }
            child = child.nextSibling
        }
        return null
    }

    /** Dotted callee spelling of a flat call node (the inference pass's rule, locally). */
    private fun callPath(call: PsiElement): String? {
        val parts = mutableListOf<String>()
        dreamShader2MeaningfulChildren(call).forEach { child ->
            when (child.elementType) {
                DreamShader2ElementTypes.ARGUMENT_LIST -> return parts.takeIf { it.isNotEmpty() }?.joinToString(".")
                DreamShaderTokenTypes.DOT -> Unit
                DreamShader2ElementTypes.REFERENCE_EXPRESSION,
                DreamShaderTokenTypes.IDENTIFIER,
                DreamShaderTokenTypes.KEYWORD,
                DreamShaderTokenTypes.TYPE,
                -> parts.add(child.text)
                else -> Unit
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(".")
    }

    // ------------------------------------------------------------------
    // usage checks
    // ------------------------------------------------------------------

    /**
     * True when [usage] sits on the written side of an assignment, a compound assignment, or
     * `++`/`--`, possibly through member/index chains (`v[0] = …`, `s.field = …`): those
     * become assignments to literals after inlining.
     */
    private fun sitsInWritePosition(usage: PsiElement): Boolean {
        var node: PsiElement = usage
        while (true) {
            val parent = node.parent ?: return false
            val parentType = parent.node?.elementType ?: return false
            if (parentType == DreamShader2ElementTypes.EXPRESSION) {
                val children = dreamShader2MeaningfulChildren(parent)
                val index = children.indexOfFirst { it.psi === node }
                if (index >= 0) {
                    val after = children.getOrNull(index + 1)
                    if (after?.elementType == DreamShaderTokenTypes.OPERATOR &&
                        (after.text == "=" || after.text in DreamShader2DiagnosticsPass.COMPOUND_ASSIGNMENTS ||
                            after.text == "++" || after.text == "--")
                    ) {
                        return true
                    }
                    val before = children.getOrNull(index - 1)
                    if (index > 0 &&
                        before?.elementType == DreamShaderTokenTypes.OPERATOR &&
                        (before.text == "++" || before.text == "--")
                    ) {
                        return true
                    }
                }
            }
            when (parentType) {
                DreamShader2ElementTypes.EXPRESSION,
                DreamShader2ElementTypes.REFERENCE_EXPRESSION,
                DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION,
                DreamShader2ElementTypes.INDEX_EXPRESSION,
                DreamShader2ElementTypes.PARENTHESIZED_EXPRESSION,
                -> node = parent
                else -> return false
            }
        }
    }

    /** Every BLOCK / `for` header containing [element] — the scopes it can see. */
    private fun ancestorScopesOf(element: PsiElement): Set<PsiElement> {
        val scopes = HashSet<PsiElement>()
        var current: PsiElement? = element.parent
        while (current != null) {
            val type = current.node?.elementType
            if (type == DreamShader2ElementTypes.BLOCK || type == DreamShader2ElementTypes.FOR_STATEMENT) {
                scopes.add(current)
            }
            current = current.parent
        }
        return scopes
    }

    /** The BLOCK (or `for` header) that directly owns [statement]'s scope, or null. */
    private fun scopeOwnerOf(statement: PsiElement): PsiElement? {
        var current: PsiElement? = statement.parent
        while (current != null) {
            when (current.node?.elementType) {
                DreamShader2ElementTypes.BLOCK, DreamShader2ElementTypes.FOR_STATEMENT -> return current
                DreamShader2ElementTypes.FUNCTION_DECLARATION -> return null
            }
            current = current.parent
        }
        return null
    }

    // ------------------------------------------------------------------
    // the write
    // ------------------------------------------------------------------

    private fun runInline(
        project: Project,
        editor: Editor,
        file: DreamShaderPsiFile,
        statement: PsiElement,
        usages: List<PsiElement>,
        initializer: String,
    ) {
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(
            project,
            DreamShaderBundle.message("refactoring.inlineVariable.title"),
            null,
            Runnable {
                // Usage replacements first, highest offset first, so earlier ranges stay valid.
                usages.forEach { usage ->
                    document.replaceString(usage.textRange.startOffset, usage.textRange.endOffset, "($initializer)")
                }

                // The declaration goes last, together with its doc block and the line break.
                // Body comments above a local are lexer trivia — no DOC_BLOCK node — so the
                // declaration's documentation lines are swallowed textually (C22 semantics).
                val docBlock = docBlockBefore(statement)
                var start = document.getLineStartOffset(
                    document.getLineNumber((docBlock ?: statement).textRange.startOffset),
                )
                if (docBlock == null) {
                    var firstLine = document.getLineNumber(start)
                    while (firstLine > 0) {
                        val previousStart = document.getLineStartOffset(firstLine - 1)
                        val previousEnd = document.getLineEndOffset(firstLine - 1)
                        val line = document.charsSequence.subSequence(previousStart, previousEnd).trim()
                        if (line.startsWith("//")) firstLine-- else break
                    }
                    start = document.getLineStartOffset(firstLine)
                }
                var end = statement.textRange.endOffset
                val text = document.charsSequence
                if (end < text.length && text[end] == '\r') end++
                if (end < text.length && text[end] == '\n') end++
                document.deleteString(start, end)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            },
        )
    }

    private fun docBlockBefore(statement: PsiElement): PsiElement? {
        val previous = statement.prevSibling ?: return null
        return previous.takeIf { it.node?.elementType == DreamShader2ElementTypes.DOC_BLOCK }
    }

    private fun showHint(project: Project, editor: Editor, messageKey: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            DreamShaderBundle.message(messageKey),
            DreamShaderBundle.message("refactoring.inlineVariable.title"),
            null,
        )
    }

    private fun declaratorNames(statement: PsiElement): List<String> {
        val names = mutableListOf<String>()
        var sawType = false
        var expectName = false
        dreamShader2MeaningfulChildren(statement).forEach { child ->
            when (child.elementType) {
                DreamShaderTokenTypes.TYPE -> sawType = true
                DreamShaderTokenTypes.IDENTIFIER -> when {
                    !sawType -> sawType = true
                    expectName || names.isEmpty() -> {
                        names.add(child.text)
                        expectName = false
                    }
                }
                DreamShaderTokenTypes.COMMA -> expectName = true
                else -> Unit
            }
        }
        return names
    }

    private data class InlineTarget(val statement: PsiElement, val name: String)

    companion object {
        private val SIDE_EFFECT_OPERATORS: Set<String> =
            DreamShader2DiagnosticsPass.COMPOUND_ASSIGNMENTS + setOf("=", "++", "--")
    }
}
