package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2DiagnosticsPass
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.dreamShader2DeclaratorNames
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.psiTreeWalk
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCompletionDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.lang.ASTNode
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.util.CommonRefactoringUtil

/**
 * Extract Function for the 2.0 dialect (plan C21): a selection of complete statements inside
 * a function body becomes a file-local helper function; the selection is replaced with a call.
 *
 * Signature synthesis is conservative, matching the plugin's "never guess" contract:
 * - names read by the selection and declared outside become value parameters with the
 *   declaration's own type spelling; names that resolve to functions or to nothing
 *   (namespace roots like `UE`) stay as-is instead of becoming parameters;
 * - names assigned in the selection and used after it come back as `out` parameters
 *   (`inout` when the selection also reads them, e.g. `x = x + 1`); their types must be
 *   declared outside, otherwise the refactoring refuses;
 * - statements that transfer control (`return`/`break`/`continue`), assignments through
 *   a member or index, partial selections, and declarations that would change scope are
 *   refused with a hint — the document is never left half-refactored.
 */
internal class DreamShader2ExtractFunctionHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is DreamShaderPsiFile) return
        if (!DreamShaderCompletionDialects.servesV2(file)) return

        val selection = selectionBounds(file, editor)
        if (selection == null) {
            showHint(project, editor, "refactoring.extractFunction.noSelection")
            return
        }
        val statements = completeStatements(file, selection.first, selection.second)
        if (statements == null) {
            showHint(project, editor, "refactoring.extractFunction.partialSelection")
            return
        }
        val function = PsiTreeUtil.getParentOfType(statements.first(), DreamShader2FunctionDeclaration::class.java)
        if (function == null) {
            showHint(project, editor, "refactoring.extractFunction.unsupportedContext")
            return
        }

        val usage = analyzeSelectionUsage(statements)
        if (usage.hasControlTransfer) {
            showHint(project, editor, "refactoring.extractFunction.controlFlow")
            return
        }
        if (usage.writesThroughReference) {
            showHint(project, editor, "refactoring.extractFunction.memberWrite")
            return
        }

        val analysis = DreamShader2FileAnalysis.of(file)
        val workspace = project.getService(DreamShader2WorkspaceSymbols::class.java)
        val selectionStart = statements.first().textRange.startOffset
        val selectionEnd = statements.last().textRange.endOffset
        val closureFunctions = workspace.includeClosurePsi(file).orEmpty()
            .filterIsInstance<DreamShaderPsiFile>()
            .flatMap { DreamShader2FileAnalysis.of(it).functions }
            .mapNotNull { it.declarationName() }
            .toSet()
        val resolver = VisibleNameResolver(function, statements.first(), selectionStart, analysis, closureFunctions)

        val usedAfter = usage.assignedNames.filter { isUsedAfter(function, it, selectionEnd) }
        if (usedAfter.any { it in usage.declaredInside }) {
            showHint(project, editor, "refactoring.extractFunction.declaredInsideUsedAfter")
            return
        }

        // Out parameters first: they claim the name and decide the qualifier.
        val outParams = LinkedHashMap<String, OutParam>()
        for (name in usedAfter) {
            val spelling = resolver.declaredSpelling(name)
            if (spelling == null) {
                showHint(project, editor, "refactoring.extractFunction.untypedOut")
                return
            }
            val qualifier = if (name in usage.readNames) "inout" else "out"
            outParams[name] = OutParam(spelling, qualifier)
        }
        val inParams = LinkedHashMap<String, String>()
        for (name in usage.readNames) {
            if (name in usage.declaredInside || name in outParams) continue
            if (resolver.kindOf(name) != NameKind.Declared) continue
            inParams[name] = resolver.declaredSpelling(name) ?: continue
        }

        val name = suggestedName(analysis, workspace, file)
        runRefactoring(project, editor, file, function, statements, selection.first, selection.second, name, inParams, outParams)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) = Unit

    // ------------------------------------------------------------------
    // selection shaping
    // ------------------------------------------------------------------

    private fun selectionBounds(file: DreamShaderPsiFile, editor: Editor): Pair<Int, Int>? {
        val selection = editor.selectionModel
        if (!selection.hasSelection()) return null
        var start = selection.selectionStart
        var end = selection.selectionEnd
        val text = file.text
        while (start < end && text[start].isWhitespace()) start++
        while (end > start && text[end - 1].isWhitespace()) end--
        return if (start < end) start to end else null
    }

    /**
     * The statements exactly covered by [start] until [end]: the first statement must open on
     * [start], the last one must close on [end], and both must be siblings inside one block.
     */
    private fun completeStatements(file: DreamShaderPsiFile, start: Int, end: Int): List<PsiElement>? {
        val first = boundaryStatement(file, start, atEnd = false) ?: return null
        val last = boundaryStatement(file, end, atEnd = true) ?: return null
        if (first.parent !== last.parent) return null
        if (first.parent.node?.elementType != DreamShader2ElementTypes.BLOCK) return null
        val siblings = first.parent.children.toList()
        val from = siblings.indexOf(first)
        val to = siblings.indexOf(last)
        if (from < 0 || to < from) return null
        return siblings.subList(from, to + 1)
    }

    private fun boundaryStatement(file: DreamShaderPsiFile, offset: Int, atEnd: Boolean): PsiElement? {
        val leaf = file.findElementAt(if (atEnd) (offset - 1).coerceAtLeast(0) else offset) ?: return null
        var current: PsiElement? = leaf
        while (current != null) {
            val type = current.node?.elementType
            if (type in EXTRACTABLE_STATEMENTS) {
                return if (atEnd) {
                    if (current.textRange.endOffset == offset) current else null
                } else {
                    if (current.textRange.startOffset == offset) current else null
                }
            }
            if (type == DreamShader2ElementTypes.FUNCTION_DECLARATION) return null
            current = current.parent
        }
        return null
    }

    // ------------------------------------------------------------------
    // selection data flow
    // ------------------------------------------------------------------

    private fun isUsedAfter(function: DreamShader2FunctionDeclaration, name: String, afterOffset: Int): Boolean =
        psiTreeWalk(function).any {
            it.node?.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION &&
                it.text == name &&
                it.textRange.startOffset >= afterOffset
        }

    // ------------------------------------------------------------------
    // name suggestion
    // ------------------------------------------------------------------

    private fun suggestedName(
        analysis: DreamShader2FileAnalysis,
        workspace: DreamShader2WorkspaceSymbols,
        file: DreamShaderPsiFile,
    ): String {
        var candidate = "Extracted"
        var suffix = 2
        while (analysis.functionByName(candidate) != null || closureDeclaresFunction(workspace, file, candidate)) {
            candidate = "Extracted$suffix"
            suffix++
        }
        return candidate
    }

    private fun closureDeclaresFunction(
        workspace: DreamShader2WorkspaceSymbols,
        file: DreamShaderPsiFile,
        name: String,
    ): Boolean = workspace.includeClosurePsi(file).orEmpty().filterIsInstance<DreamShaderPsiFile>()
        .any { DreamShader2FileAnalysis.of(it).functionByName(name) != null }

    // ------------------------------------------------------------------
    // the write
    // ------------------------------------------------------------------

    private fun runRefactoring(
        project: Project,
        editor: Editor,
        file: DreamShaderPsiFile,
        function: DreamShader2FunctionDeclaration,
        statements: List<PsiElement>,
        selectionStart: Int,
        selectionEnd: Int,
        name: String,
        inParams: Map<String, String>,
        outParams: Map<String, OutParam>,
    ) {
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(
            project,
            DreamShaderBundle.message("refactoring.extractFunction.title"),
            null,
            Runnable {
                val indent = lineIndentOf(document, statements.first().textRange.startOffset)
                val bodyText = statements.joinToString("\n") { statement ->
                    reindentStatement(statement.text, indent, BODY_INDENT)
                }
                val parameters = ArrayList<String>()
                inParams.forEach { (paramName, spelling) -> parameters.add("$spelling $paramName") }
                outParams.forEach { (paramName, out) -> parameters.add("${out.qualifier} ${out.spelling} $paramName") }
                val newFunction = "void $name(${parameters.joinToString(", ")})\n{\n$bodyText\n}"

                val insertOffset = function.textRange.endOffset
                document.insertString(insertOffset, "\n\n$newFunction")

                val arguments = ArrayList<String>()
                arguments.addAll(inParams.keys)
                arguments.addAll(outParams.keys)
                val callText = "$name(${arguments.joinToString(", ")});"
                document.replaceString(selectionStart, selectionEnd, callText)
                PsiDocumentManager.getInstance(project).commitDocument(document)

                val callNameOffset = selectionStart
                val functionNameOffset = insertOffset + "\n\n".length + "void ".length
                val callLeaf = identifierLeafAt(file, callNameOffset) ?: return@Runnable
                val functionLeaf = identifierLeafAt(file, functionNameOffset) ?: return@Runnable

                val builder = TemplateBuilderImpl(file)
                builder.replaceElement(functionLeaf, NAME_VARIABLE, TextExpression(name), true)
                builder.replaceElement(callLeaf, NAME_VARIABLE, TextExpression(name), true)
                TemplateManager.getInstance(project).startTemplate(editor, builder.buildTemplate())
            },
        )
    }

    /**
     * Moves one statement from its original indentation to [toIndent], keeping the relative
     * nesting of its continuation lines. The statement's own first line carries no leading
     * whitespace (it starts at the node), so it is treated as sitting at [fromIndent].
     */
    private fun reindentStatement(text: String, fromIndent: String, toIndent: String): String {
        val delta = toIndent.length - fromIndent.length
        return text.split("\n").mapIndexed { index, line ->
            if (line.isBlank()) {
                line
            } else {
                val originalWidth = if (index == 0) fromIndent.length else line.length - line.trimStart().length
                " ".repeat((originalWidth + delta).coerceAtLeast(0)) + line.trimStart()
            }
        }.joinToString("\n")
    }

    private fun identifierLeafAt(file: DreamShaderPsiFile, offset: Int): PsiElement? {
        val leaf = file.findElementAt(offset) ?: return null
        return leaf.takeIf { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER }
    }

    private fun lineIndentOf(document: Document, offset: Int): String {
        val lineStart = document.getLineStartOffset(document.getLineNumber(offset))
        val text = document.charsSequence
        var i = lineStart
        while (i < offset && text[i].isWhitespace()) i++
        return text.subSequence(lineStart, i).toString()
    }

    private fun showHint(project: Project, editor: Editor, messageKey: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            DreamShaderBundle.message(messageKey),
            DreamShaderBundle.message("refactoring.extractFunction.title"),
            null,
        )
    }

    private data class OutParam(val spelling: String, val qualifier: String)

    companion object {
        private const val NAME_VARIABLE = "ExtractedFunctionName"
        private const val BODY_INDENT = "    "

        private val EXTRACTABLE_STATEMENTS = setOf(
            DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT,
            DreamShader2ElementTypes.EXPRESSION_STATEMENT,
            DreamShader2ElementTypes.IF_STATEMENT,
            DreamShader2ElementTypes.FOR_STATEMENT,
            DreamShader2ElementTypes.WHILE_STATEMENT,
            DreamShader2ElementTypes.DO_WHILE_STATEMENT,
            DreamShader2ElementTypes.RETURN_STATEMENT,
            DreamShader2ElementTypes.JUMP_STATEMENT,
            DreamShader2ElementTypes.EMPTY_STATEMENT,
        )
    }
}

// ---------------------------------------------------------------------------
// selection data flow (file level: shared by the handler without receiver capture)
// ---------------------------------------------------------------------------

private class SelectionUsage(
    val declaredInside: Set<String>,
    val readNames: Set<String>,
    val assignedNames: Set<String>,
    val hasControlTransfer: Boolean,
    val writesThroughReference: Boolean,
)

/** Reads/writes/control-transfer analysis over the selected statements (M15 C21). */
private fun analyzeSelectionUsage(statements: List<PsiElement>): SelectionUsage {
    val declaredInside = mutableSetOf<String>()
    val writes = LinkedHashSet<String>()
    val writeTargets = mutableSetOf<PsiElement>()
    var control = false
    var memberWrite = false
    statements.forEach { statement ->
        psiTreeWalk(statement).forEach { node ->
            when (node.node?.elementType) {
                DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT -> {
                    // A declarator with an initializer writes its name: declaration-only
                    // names still reach the used-after check, which guards scope changes.
                    val names = extractFunctionDeclarationNames(node)
                    declaredInside += names
                    writes += names
                }
                DreamShader2ElementTypes.RETURN_STATEMENT,
                DreamShader2ElementTypes.JUMP_STATEMENT,
                -> control = true
                DreamShader2ElementTypes.EXPRESSION -> {
                    if (collectExpressionWrites(node, writes, writeTargets)) memberWrite = true
                }
            }
        }
    }
    // Reads are every reference occurrence that is not a write target (assignment LHS,
    // ++/-- operand). Flat expressions wrap operands in single-child EXPRESSION nodes, so
    // the sweep goes through the whole subtree rather than direct children only.
    val reads = LinkedHashSet<String>()
    statements.forEach { statement ->
        psiTreeWalk(statement).forEach { node ->
            if (node.node?.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION &&
                node !in writeTargets
            ) {
                reads.add(node.text)
            }
        }
    }
    return SelectionUsage(declaredInside, reads, writes, control, memberWrite)
}

/**
 * Assignment LHS and ++/-- targets of one flat EXPRESSION: true when a write goes through
 * a member or index (those cannot be carried out of the function conservatively).
 */
private fun collectExpressionWrites(
    expression: PsiElement,
    writes: MutableSet<String>,
    writeTargets: MutableSet<PsiElement>,
): Boolean {
    var writesThroughReference = false
    val children = dreamShader2MeaningfulChildren(expression)
    children.forEachIndexed { index, child ->
        if (child.elementType != DreamShaderTokenTypes.OPERATOR) return@forEachIndexed
        when (child.text) {
            "=" -> {
                if (recordSelectionWrite(children.getOrNull(index - 1), false, writes, writeTargets)) {
                    writesThroughReference = true
                }
            }
            in DreamShader2DiagnosticsPass.COMPOUND_ASSIGNMENTS -> {
                if (recordSelectionWrite(children.getOrNull(index - 1), false, writes, writeTargets)) {
                    writesThroughReference = true
                }
            }
            "++", "--" -> recordSelectionWrite(children.getOrNull(index - 1), true, writes, writeTargets)
        }
    }
    return writesThroughReference
}

/** Records one write target (unwrapping its EXPRESSION wrapper); true when it is a member/index. */
private fun recordSelectionWrite(
    operand: ASTNode?,
    incrementsOnly: Boolean,
    writes: MutableSet<String>,
    writeTargets: MutableSet<PsiElement>,
): Boolean {
    val target = unwrapOperand(operand) ?: return false
    return when (target.elementType) {
        DreamShader2ElementTypes.REFERENCE_EXPRESSION -> {
            writes.add(target.text)
            writeTargets.add(target.psi)
            false
        }
        else -> !incrementsOnly
    }
}

/** Strips the single-child EXPRESSION wrappers flat parsing puts around operands. */
private fun unwrapOperand(node: ASTNode?): ASTNode? {
    var current = node ?: return null
    while (current.elementType == DreamShader2ElementTypes.EXPRESSION) {
        current = dreamShader2MeaningfulChildren(current.psi).singleOrNull() ?: return null
    }
    return current
}

/** One declarator name set of a local declaration statement (type leaves excluded). */
private fun extractFunctionDeclarationNames(statement: PsiElement): List<String> {
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

/** How a free name read by the selection resolves at the selection site. */
private enum class NameKind {
    /** A value declaration (parameter, local, uniform, global) — becomes a parameter. */
    Declared,

    /** A callable — the extracted function keeps calling it directly. */
    Function,

    /** Nothing visible — namespace roots and the like stay as-is. */
    Unresolved,
}

/**
 * Resolves free names at the selection start (M15 C21): function parameters, lexically
 * visible locals, and file-scope uniforms/globals are value declarations; names matching
 * file or include-closure functions are callables; everything else is unresolved. The
 * declared spelling is taken from the declaration itself — never guessed.
 */
private class VisibleNameResolver(
    function: DreamShader2FunctionDeclaration,
    anchor: PsiElement,
    selectionStart: Int,
    private val analysis: DreamShader2FileAnalysis,
    closureFunctions: Set<String>,
) {
    private val parameters = function.parameterList()?.parameters().orEmpty()
    private val closureFunctions: Set<String> = closureFunctions
    private val visibleScopes = ancestorScopesOf(anchor)
    private val localDeclarations: MutableList<Pair<Int, Pair<List<String>, String>>> = mutableListOf()
    private val fileScopeNames: List<Pair<List<String>, String>>

    init {
        psiTreeWalk(function).filter {
            it.node?.elementType == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT
        }.forEach { statement ->
            val start = statement.textRange.startOffset
            if (start < selectionStart && scopeOwnerOf(statement) in visibleScopes) {
                declaredSpellingAndNames(statement)?.let { localDeclarations.add(start to it) }
            }
        }
        // Nearest declaration first so shadowing resolves innermost-first.
        localDeclarations.sortByDescending { it.first }
        fileScopeNames = buildList {
            (analysis.uniforms + analysis.globals).forEach { declaration ->
                val spelling = when (declaration) {
                    is DreamShader2UniformDeclaration -> declaration.typeName()
                    is DreamShader2GlobalDeclaration -> declaration.typeName()
                    else -> null
                }
                spelling?.takeIf { it.isNotBlank() }?.let {
                    add(dreamShader2DeclaratorNames(declaration) to it)
                }
            }
        }
    }

    fun kindOf(name: String): NameKind = when {
        parameters.any { it.parameterName() == name } -> NameKind.Declared
        localDeclarations.any { name in it.second.first } -> NameKind.Declared
        fileScopeNames.any { name in it.first } -> NameKind.Declared
        analysis.functionByName(name) != null || name in closureFunctions -> NameKind.Function
        else -> NameKind.Unresolved
    }

    /** The declared type spelling of a value name; call again only when [kindOf] is `Declared`. */
    fun declaredSpelling(name: String): String? {
        parameters.firstOrNull { it.parameterName() == name }?.let { return it.typeName() }
        localDeclarations.firstOrNull { name in it.second.first }?.let { return it.second.second }
        return fileScopeNames.firstOrNull { name in it.first }?.second
    }

    /** Every BLOCK / `for` header containing [anchor] — the scopes the selection sits in. */
    private fun ancestorScopesOf(anchor: PsiElement): Set<PsiElement> {
        val scopes = HashSet<PsiElement>()
        var current: PsiElement? = anchor
        while (current != null) {
            if (current.node?.elementType == DreamShader2ElementTypes.BLOCK ||
                current.node?.elementType == DreamShader2ElementTypes.FOR_STATEMENT
            ) {
                scopes.add(current)
            }
            current = current.parent
        }
        return scopes
    }

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

    /** Declarator names + the declared type spelling of one local declaration statement. */
    private fun declaredSpellingAndNames(statement: PsiElement): Pair<List<String>, String>? {
        val children = dreamShader2MeaningfulChildren(statement)
        val spelling = children.firstOrNull {
            it.elementType == DreamShaderTokenTypes.TYPE || it.elementType == DreamShaderTokenTypes.IDENTIFIER
        }?.text ?: return null
        val names = mutableListOf<String>()
        var sawType = false
        var expectName = false
        children.forEach { child ->
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
        if (names.isEmpty()) return null
        return names to spelling
    }
}
