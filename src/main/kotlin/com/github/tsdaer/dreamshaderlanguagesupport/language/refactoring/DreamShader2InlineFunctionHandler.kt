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
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2ArgumentList
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedArgument
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2Parameter
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2Leafs
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
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
 * Inline Function for the 2.0 dialect (plan C31): a call to a user function declared in this
 * file or its include closure is replaced by the function's body statements — the Introduce
 * counterpart of Extract Function, sharing the "check everything, then write once" contract.
 * The callee declaration stays (the caller is not known to be the only user).
 *
 * Expanding a call re-evaluates every by-value argument once per substituted occurrence, so
 * the argument matrix is conservative (the plan card's side-effect matrix):
 * - by-value arguments must be drift-proof like the Inline Variable initializer: literals,
 *   operators, type constructors, and builtin/catalog calls only — a call to a function of
 *   the callee's file, or to anything unknown, refuses, and side-effect operators refuse.
 *   References are unrestricted: every substituted occurrence sits at the same program
 *   point, so nothing can rewrite a local between them;
 * - `out`/`inout` arguments must be plain variable references (the C30 write-back rule) —
 *   the body's writes to the formal become writes to that variable;
 * - by-value formals may never be written in the body (including through member/index
 *   chains): substitution would turn a local formal write into a write through to the
 *   caller's variable;
 * - `out`/`inout` arguments must not alias: two formals bound to the same variable reorder
 *   the writes, and a write-back target read by another argument drifts.
 *
 * The body must be structured (no `@custom`), transfer control only through a single
 * top-level trailing `return` (dropped in statement position, rewritten to an assignment at
 * value sites), and keep `break`/`continue` inside its own loops (`discard` travels with
 * the body). Locals declared at the body's top level would outlive the expanded statements
 * in the caller's scope, so any name they share with a caller-visible name refuses — no
 * renaming is attempted.
 */
internal class DreamShader2InlineFunctionHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is DreamShaderPsiFile) return
        if (!DreamShaderCompletionDialects.servesV2(file)) return

        val call = resolveCall(file, editor)
        if (call == null) {
            showHint(project, editor, "refactoring.inlineFunction.notCallable")
            return
        }
        val name = callPath(call)?.takeIf { !it.contains('.') } ?: run {
            showHint(project, editor, "refactoring.inlineFunction.notCallable")
            return
        }
        val callee = resolveCallee(file, name)
        if (callee == null) {
            showHint(project, editor, "refactoring.inlineFunction.notCallable")
            return
        }
        if (callee.hasCustomBody()) {
            showHint(project, editor, "refactoring.inlineFunction.noBody")
            return
        }
        val body = bodyBlock(callee) ?: run {
            showHint(project, editor, "refactoring.inlineFunction.noBody")
            return
        }
        val statements = bodyStatements(body) ?: run {
            showHint(project, editor, "refactoring.inlineFunction.emptyBody")
            return
        }

        val bodyProblem = bodyShapeProblem(body, statements, name)
        if (bodyProblem != null) {
            showHint(project, editor, bodyProblem)
            return
        }

        val calleeFile = callee.containingFile as? DreamShaderPsiFile ?: return
        val calleeText = calleeFile.text
        val bindings = bindArguments(call, callee, file.text) { key ->
            showHint(project, editor, key)
        } ?: return

        val bindingProblem = bindingMatrixProblem(bindings, body, calleeText)
        if (bindingProblem != null) {
            showHint(project, editor, bindingProblem)
            return
        }

        val statement = enclosingStatement(call)
        val site = statement?.let { classifyCallSite(it, call, file.text) }
        if (statement == null || site == null) {
            showHint(project, editor, "refactoring.inlineFunction.callSite")
            return
        }
        val trailing = topLevelReturnOf(statements)
        if (site !is CallSite.Statement && trailing?.first == null) {
            showHint(project, editor, "refactoring.inlineFunction.noReturnValue")
            return
        }

        val callerFunction = PsiTreeUtil.getParentOfType(statement, DreamShader2FunctionDeclaration::class.java)
        if (callerFunction == null) {
            showHint(project, editor, "refactoring.inlineFunction.callSite")
            return
        }
        val collision = nameCollisionProblem(callerFunction, statement, file, statements)
        if (collision != null) {
            showHint(project, editor, collision)
            return
        }

        runInline(project, editor, statement, site, statements, bindings, calleeText)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) = Unit

    // ------------------------------------------------------------------
    // target resolution
    // ------------------------------------------------------------------

    /** The innermost call containing the caret leaf (or the leaf one character back). */
    private fun resolveCall(file: DreamShaderPsiFile, editor: Editor): PsiElement? {
        val caret = editor.caretModel.offset.coerceIn(0, file.textLength)
        val candidates = listOfNotNull(
            file.findElementAt(caret),
            file.findElementAt((caret - 1).coerceAtLeast(0)),
        )
        for (leaf in candidates) {
            var current: PsiElement? = leaf
            while (current != null) {
                val type = current.node?.elementType
                if (type == DreamShader2ElementTypes.CALL_EXPRESSION) return current
                if (type == DreamShader2ElementTypes.FUNCTION_DECLARATION) break
                current = current.parent
            }
        }
        return null
    }

    private fun resolveCallee(file: DreamShaderPsiFile, name: String): DreamShader2FunctionDeclaration? {
        DreamShader2FileAnalysis.of(file).functionByName(name)?.let { return it }
        val workspace = file.project.getService(DreamShader2WorkspaceSymbols::class.java) ?: return null
        workspace.includeClosurePsi(file).filterIsInstance<DreamShaderPsiFile>().forEach { closure ->
            DreamShader2FileAnalysis.of(closure).functionByName(name)?.let { return it }
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
                DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION,
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
    // body shape
    // ------------------------------------------------------------------

    private fun bodyBlock(function: DreamShader2FunctionDeclaration): PsiElement? =
        function.node.findChildByType(DreamShader2ElementTypes.BLOCK)?.psi

    /** The body's meaningful children: statements plus doc blocks, without the braces. */
    private fun bodyStatements(body: PsiElement): List<PsiElement>? {
        val statements = dreamShader2MeaningfulChildren(body)
            .filter {
                it.elementType != DreamShaderTokenTypes.LBRACE && it.elementType != DreamShaderTokenTypes.RBRACE
            }
            .map { it.psi }
        return statements.ifEmpty { null }
    }

    /**
     * One message key when the body cannot move as a whole: a recursive call, a return
     * outside the single top-level trailing slot, or a `break`/`continue` that no loop
     * inside the body owns.
     */
    private fun bodyShapeProblem(
        body: PsiElement,
        statements: List<PsiElement>,
        calleeName: String,
    ): String? {
        psiTreeWalk(body).forEach { node ->
            when (node.node?.elementType) {
                DreamShader2ElementTypes.CALL_EXPRESSION -> {
                    if (callPath(node) == calleeName) return "refactoring.inlineFunction.recursive"
                }
                DreamShader2ElementTypes.RETURN_STATEMENT -> {
                    if (node.parent !== body) return "refactoring.inlineFunction.returnShape"
                }
                DreamShader2ElementTypes.JUMP_STATEMENT -> {
                    val word = node.text.trim().substringBefore('(').trim()
                    if (word != "discard" && !loopBoundedWithin(node, body)) {
                        return "refactoring.inlineFunction.jumpOutsideLoop"
                    }
                }
            }
        }
        val trailing = topLevelReturnOf(statements)
        if (trailing != null && statements.last() !== trailing.second) {
            return "refactoring.inlineFunction.returnShape"
        }
        return null
    }

    /** `(returnExprText, statement)` for the body's single top-level return; text null for `return;`. */
    private fun topLevelReturnOf(statements: List<PsiElement>): Pair<String?, PsiElement>? {
        val returns = statements.filter { it.node?.elementType == DreamShader2ElementTypes.RETURN_STATEMENT }
        val only = returns.singleOrNull() ?: return null
        val leafs = dreamShader2Leafs(only).toList()
        val keyword = leafs.firstOrNull { it.elementType == DreamShaderTokenTypes.KEYWORD } ?: return null
        val semicolon = leafs.lastOrNull { it.elementType == DreamShaderTokenTypes.SEMICOLON }
        val expression = if (semicolon != null && semicolon.startOffset > keyword.startOffset + keyword.textLength) {
            only.containingFile.text
                .substring(keyword.startOffset + keyword.textLength, semicolon.startOffset)
                .trim()
                .takeIf { it.isNotEmpty() }
        } else {
            null
        }
        return expression to only
    }

    private fun loopBoundedWithin(jump: PsiElement, body: PsiElement): Boolean {
        var current: PsiElement? = jump.parent
        while (current != null && current !== body) {
            when (current.node?.elementType) {
                DreamShader2ElementTypes.FOR_STATEMENT,
                DreamShader2ElementTypes.WHILE_STATEMENT,
                DreamShader2ElementTypes.DO_WHILE_STATEMENT,
                -> return true
            }
            current = current.parent
        }
        return false
    }

    // ------------------------------------------------------------------
    // argument binding and the side-effect matrix
    // ------------------------------------------------------------------

    private class Binding(
        val parameter: DreamShader2Parameter,
        val argument: PsiElement,
        /** Inclusive first character of the value slot (the substitution/purity text). */
        val textStart: Int,
        /** One before the value slot for the purity walk's guard (the binding `=`'s own start). */
        val guardStart: Int,
        val valueEnd: Int,
        val valueText: String,
    ) {
        val qualifier: String? = qualifierOf(parameter)

        /** The plain variable name when the argument can carry a write back, else null. */
        val writeBackName: String? by lazy { plainReferenceName(argument, textStart, valueEnd) }

        companion object {
            /** `out`/`inout` when the parameter opens with that keyword; plain `in` parameters are null. */
            fun qualifierOf(parameter: DreamShader2Parameter): String? =
                dreamShader2MeaningfulChildren(parameter).firstOrNull()
                    ?.takeIf { it.elementType == DreamShaderTokenTypes.KEYWORD }
                    ?.text
                    ?.takeIf { it in WRITE_BACK_QUALIFIERS }

            /** The variable name when the argument's value is a plain reference (the C30 rule, locally). */
            private fun plainReferenceName(argument: PsiElement, textStart: Int, valueEnd: Int): String? {
                var value = dreamShader2MeaningfulChildren(argument).lastOrNull() ?: return null
                while (value.elementType == DreamShader2ElementTypes.EXPRESSION) {
                    value = dreamShader2MeaningfulChildren(value.psi).singleOrNull() ?: return null
                }
                if (value.startOffset < textStart || value.startOffset + value.textLength > valueEnd) return null
                return value.takeIf { it.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION }?.text
            }
        }
    }

    /**
     * Binds call arguments to formals: positionally in order, by name for `Name = expr`, and
     * never a positional after a named one. Argument offsets belong to the caller file, so
     * value texts come from [callerText]; [refuse] reports the mismatch and stops.
     */
    private fun bindArguments(
        call: PsiElement,
        callee: DreamShader2FunctionDeclaration,
        callerText: String,
        refuse: (String) -> Unit,
    ): List<Binding>? {
        val parameters = callee.parameterList()?.parameters().orEmpty()
        val argumentList = dreamShader2MeaningfulChildren(call)
            .firstOrNull { it.elementType == DreamShader2ElementTypes.ARGUMENT_LIST }?.psi
            as? DreamShader2ArgumentList
        val arguments = argumentList?.arguments().orEmpty()
        if (arguments.size != parameters.size) {
            refuse("refactoring.inlineFunction.binding")
            return null
        }

        val bindings = ArrayList<Binding>(parameters.size)
        var seenNamed = false
        for ((index, argument) in arguments.withIndex()) {
            val parameter: DreamShader2Parameter
            if (argument is DreamShader2NamedArgument) {
                seenNamed = true
                val name = argument.argumentName()
                parameter = parameters.firstOrNull { it.parameterName() == name } ?: run {
                    refuse("refactoring.inlineFunction.binding")
                    return null
                }
            } else {
                if (seenNamed) {
                    refuse("refactoring.inlineFunction.binding")
                    return null
                }
                parameter = parameters.getOrNull(index) ?: run {
                    refuse("refactoring.inlineFunction.binding")
                    return null
                }
            }
            val binding = newBinding(parameter, argument, callerText) ?: run {
                refuse("refactoring.inlineFunction.binding")
                return null
            }
            bindings.add(binding)
        }
        return bindings
    }

    /** A binding whose value slot spans everything after the named-argument `=` (or the whole argument). */
    private fun newBinding(
        parameter: DreamShader2Parameter,
        argument: PsiElement,
        callerText: String,
    ): Binding? {
        val argumentStart = argument.textRange.startOffset
        val valueEnd = argument.textRange.endOffset
        val textStart: Int
        val guardStart: Int
        if (argument is DreamShader2NamedArgument) {
            val equals = dreamShader2MeaningfulChildren(argument)
                .firstOrNull { it.elementType == DreamShaderTokenTypes.OPERATOR && it.text == "=" }
                ?: return null
            textStart = equals.startOffset + equals.textLength
            guardStart = equals.startOffset
        } else {
            textStart = argumentStart
            guardStart = argumentStart - 1
        }
        if (textStart >= valueEnd || valueEnd > callerText.length) return null
        return Binding(
            parameter,
            argument,
            textStart,
            guardStart,
            valueEnd,
            callerText.substring(textStart, valueEnd).trim(),
        )
    }

    /**
     * The write-through matrix over the bound arguments: by-value formals never written in
     * the body, write-backs only onto plain unaliased variables, by-value argument texts
     * drift-proof. Returns one message key or null.
     */
    private fun bindingMatrixProblem(
        bindings: List<Binding>,
        body: PsiElement,
        calleeText: String,
    ): String? {
        val writeBacks = bindings.filter { it.qualifier != null }
        val byValue = bindings.filter { it.qualifier == null }

        for (binding in byValue) {
            val occurrences = parameterOccurrences(binding.parameter.parameterName(), body)
            if (occurrences.any { sitsInWritePosition(it) }) {
                return "refactoring.inlineFunction.writesValueParameter"
            }
            if (argumentPurityProblem(binding, calleeText)) {
                return "refactoring.inlineFunction.impureArgument"
            }
        }
        if (writeBacks.any { it.writeBackName == null }) {
            return "refactoring.inlineFunction.writebackArgument"
        }

        val writeBackNames = writeBacks.mapNotNull { it.writeBackName }
        if (writeBackNames.distinct().size != writeBackNames.size) {
            return "refactoring.inlineFunction.aliasedWriteback"
        }
        val readByValueArguments = byValue.flatMap { binding ->
            psiTreeWalk(binding.argument)
                .filter { it.node?.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION }
                .filter { it.textRange.startOffset >= binding.textStart && it.textRange.endOffset <= binding.valueEnd }
                .map { it.text }
        }.toSet()
        if (writeBackNames.any { it in readByValueArguments }) {
            return "refactoring.inlineFunction.aliasedWriteback"
        }
        return null
    }

    private fun parameterOccurrences(name: String?, body: PsiElement): List<PsiElement> {
        if (name == null) return emptyList()
        return psiTreeWalk(body)
            .filter { it.node?.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION && it.text == name }
            .filter { !isCallHead(it) }
            .toList()
    }

    /**
     * True when the by-value argument text cannot be repeated per occurrence: side effects,
     * or a call to a function declared in the callee's file or to anything unknown.
     * Builtins and catalog calls are deterministic — they may repeat.
     */
    private fun argumentPurityProblem(binding: Binding, calleeText: String): Boolean {
        val guardStart = binding.guardStart
        val end = binding.valueEnd

        fun walk(element: PsiElement): Boolean {
            val range = element.textRange
            // Only subtrees inside the value slot count: after the binding `=` (left-inclusive
            // keeps the named-argument's own operator out) and up to the argument's end.
            if (range.startOffset <= guardStart || range.endOffset > end) return false
            val elementType = element.node?.elementType
            if (elementType == DreamShader2ElementTypes.CALL_EXPRESSION) {
                val path = callPath(element) ?: return true
                val declaredHere = DreamShaderSignatureHelpAnalyzer
                    .resolveDeclaredSignatures(path, calleeText)
                    .isNotEmpty()
                val knownBuiltin = DreamShaderSignatureHelpAnalyzer.resolveSignatures(path).isNotEmpty()
                if (declaredHere || !knownBuiltin) return true
                val arguments = element.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST)?.psi
                    ?: return false
                return walk(arguments)
            }
            if (elementType == DreamShaderTokenTypes.OPERATOR && element.text in SIDE_EFFECT_OPERATORS) {
                return true
            }
            var child = element.firstChild
            while (child != null) {
                if (walk(child)) return true
                child = child.nextSibling
            }
            return false
        }

        return walk(binding.argument)
    }

    /**
     * True when [usage] sits on the written side of an assignment, a compound assignment, or
     * `++`/`--`, possibly through member/index chains — the Inline Variable rule.
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

    /** True when [reference] is the callee head of a call (`Foo(`), not a value read. */
    private fun isCallHead(reference: PsiElement): Boolean {
        val parent = reference.parent ?: return false
        if (parent.node?.elementType != DreamShader2ElementTypes.CALL_EXPRESSION) return false
        val siblings = dreamShader2MeaningfulChildren(parent)
        val index = siblings.indexOfFirst { it.psi === reference }
        return index >= 0 && siblings.getOrNull(index + 1)?.elementType == DreamShader2ElementTypes.ARGUMENT_LIST
    }

    // ------------------------------------------------------------------
    // call site shape
    // ------------------------------------------------------------------

    private sealed class CallSite {
        /** `Foo(…);` as a whole statement — the return value, if any, is discarded. */
        object Statement : CallSite()

        /** `lhs = Foo(…);` — the trailing return is rewritten onto [lhsText]. */
        class Assignment(val lhsText: String) : CallSite()

        /** `Type name = Foo(…);` — a bare declaration is inserted, the return assigns [name]. */
        class Initializer(val prefix: String, val name: String) : CallSite()
    }

    /** The statement holding [call]: the nearest node whose parent is a body BLOCK. */
    private fun enclosingStatement(call: PsiElement): PsiElement? {
        var current: PsiElement? = call
        while (current != null) {
            val parent = current.parent ?: return null
            if (parent.node?.elementType == DreamShader2ElementTypes.BLOCK) return current
            if (current.node?.elementType == DreamShader2ElementTypes.FUNCTION_DECLARATION) return null
            current = parent
        }
        return null
    }

    private fun classifyCallSite(statement: PsiElement, call: PsiElement, callerText: String): CallSite? {
        return when (statement.node?.elementType) {
            DreamShader2ElementTypes.EXPRESSION_STATEMENT -> {
                val expression = dreamShader2MeaningfulChildren(statement)
                    .singleOrNull { it.elementType == DreamShader2ElementTypes.EXPRESSION } ?: return null
                val children = dreamShader2MeaningfulChildren(expression.psi)
                val callIndex = children.indexOfFirst { it === call.node }
                when {
                    children.size == 1 && callIndex == 0 -> CallSite.Statement
                    callIndex == children.lastIndex && callIndex >= 2 &&
                        children[callIndex - 1].elementType == DreamShaderTokenTypes.OPERATOR &&
                        children[callIndex - 1].text == "=" -> {
                        val lhs = children[callIndex - 2]
                        val lhsEnd = lhs.startOffset + lhs.textLength
                        CallSite.Assignment(callerText.substring(lhs.startOffset, lhsEnd).trim())
                    }
                    else -> null
                }
            }
            DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT -> initializerShape(statement, call, callerText)
            else -> null
        }
    }

    /** `T x = Foo(…);` — optional modifiers, one spelling, one declarator, whole-value call. */
    private fun initializerShape(
        statement: PsiElement,
        call: PsiElement,
        callerText: String,
    ): CallSite.Initializer? {
        val tokens = dreamShader2MeaningfulChildren(statement)
            .filter { it.elementType != DreamShaderTokenTypes.SEMICOLON }
        if (tokens.any { it.elementType == DreamShaderTokenTypes.COMMA }) return null
        val equalsIndex = tokens.indexOfLast {
            it.elementType == DreamShaderTokenTypes.OPERATOR && it.text == "="
        }
        if (equalsIndex < 1 || equalsIndex != tokens.size - 2) return null
        // The initializer parses as an EXPRESSION wrapper; the call must be its whole value.
        val valueNode = tokens.last()
        val holdsCall = valueNode === call.node ||
            (
                valueNode.elementType == DreamShader2ElementTypes.EXPRESSION &&
                    dreamShader2MeaningfulChildren(valueNode.psi).singleOrNull() === call.node
                )
        if (!holdsCall) return null

        val head = tokens.subList(0, equalsIndex)
        // The last spelling token of the head is the declarator name, the one before it is
        // the type; leading keyword modifiers keep their spelling in the bare declaration.
        val name = head.lastOrNull()?.takeIf {
            it.elementType == DreamShaderTokenTypes.IDENTIFIER || it.elementType == DreamShaderTokenTypes.KEYWORD
        } ?: return null
        val spelling = head.getOrNull(head.size - 2)?.takeIf {
            it.elementType == DreamShaderTokenTypes.TYPE || it.elementType == DreamShaderTokenTypes.IDENTIFIER
        } ?: return null
        // Nothing but whitespace may sit between the spelling and the name, and only
        // modifier keywords before the spelling (array suffixes, extra declarators, and
        // other decorations are not the plain shape).
        val leading = head.take(maxOf(0, head.size - 2))
        val between = callerText.substring(spelling.startOffset + spelling.textLength, name.startOffset)
        if (between.any { !it.isWhitespace() } || leading.any { it.elementType != DreamShaderTokenTypes.KEYWORD }) {
            return null
        }
        val modifiers = leading.joinToString(" ") { it.text }
        val prefix = buildList {
            if (modifiers.isNotEmpty()) add(modifiers)
            add(spelling.text)
        }.joinToString(" ")
        return CallSite.Initializer(prefix, name.text)
    }

    // ------------------------------------------------------------------
    // name collisions
    // ------------------------------------------------------------------

    /**
     * The body's top-level declarations outlive the expanded statements in the caller's
     * scope, so any name shared with a caller-visible name (parameters, lexically visible
     * locals, file-scope uniforms/globals of the caller and its include closure) refuses.
     */
    private fun nameCollisionProblem(
        callerFunction: DreamShader2FunctionDeclaration,
        statement: PsiElement,
        callerFile: DreamShaderPsiFile,
        statements: List<PsiElement>,
    ): String? {
        val bodyNames = statements
            .filter { it.node?.elementType == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT }
            .flatMap { localDeclaratorNames(it) }
            .toSet()
        if (bodyNames.isEmpty()) return null

        val visible = HashSet<String>()
        visible += callerFunction.parameterList()?.parameters().orEmpty().mapNotNull { it.parameterName() }
        val visibleScopes = ancestorScopesOf(statement)
        psiTreeWalk(callerFunction)
            .filter { it.node?.elementType == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT }
            .filter { it.textRange.startOffset < statement.textRange.startOffset }
            .forEach { declaration ->
                val owner = scopeOwnerOf(declaration)
                if (owner == null || owner in visibleScopes) {
                    visible += localDeclaratorNames(declaration)
                }
            }

        val callerAnalysis = DreamShader2FileAnalysis.of(callerFile)
        (callerAnalysis.uniforms + callerAnalysis.globals).forEach { visible += dreamShader2DeclaratorNames(it) }
        val workspace = callerFile.project.getService(DreamShader2WorkspaceSymbols::class.java)
        workspace.includeClosurePsi(callerFile).filterIsInstance<DreamShaderPsiFile>().forEach { closure ->
            val closureAnalysis = DreamShader2FileAnalysis.of(closure)
            (closureAnalysis.uniforms + closureAnalysis.globals).forEach { visible += dreamShader2DeclaratorNames(it) }
        }

        return if (bodyNames.any { it in visible }) "refactoring.inlineFunction.nameCollision" else null
    }

    /** One local declaration's declarator names (type leaves excluded; the C25 rule, locally). */
    private fun localDeclaratorNames(statement: PsiElement): List<String> {
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
        statement: PsiElement,
        site: CallSite,
        statements: List<PsiElement>,
        bindings: List<Binding>,
        calleeText: String,
    ) {
        val substitutions = HashMap<String, String>()
        bindings.forEach { binding ->
            val name = binding.parameter.parameterName() ?: return@forEach
            substitutions[name] = if (binding.qualifier != null) {
                binding.writeBackName ?: binding.valueText
            } else {
                "(${binding.valueText})"
            }
        }

        val document = editor.document
        val callerText = document.text
        val indent = indentInText(callerText, statement.textRange.startOffset)
        val trailing = topLevelReturnOf(statements)

        WriteCommandAction.runWriteCommandAction(
            project,
            DreamShaderBundle.message("refactoring.inlineFunction.title"),
            null,
            Runnable {
                val lines = ArrayList<String>()
                if (site is CallSite.Initializer) {
                    lines.add("${site.prefix} ${site.name};")
                }
                statements.forEach { bodyStatement ->
                    if (trailing != null && bodyStatement === trailing.second) return@forEach
                    val raw = substitutedText(bodyStatement, calleeText, substitutions)
                    lines.add(reindentStatement(raw, indentInText(calleeText, bodyStatement.textRange.startOffset), indent))
                }
                when (site) {
                    is CallSite.Assignment -> lines.add("${site.lhsText} = ${substitutedReturnText(trailing, substitutions) ?: return@Runnable};")
                    is CallSite.Initializer -> lines.add("${site.name} = ${substitutedReturnText(trailing, substitutions) ?: return@Runnable};")
                    CallSite.Statement -> Unit
                }
                document.replaceString(
                    statement.textRange.startOffset,
                    statement.textRange.endOffset,
                    lines.joinToString("\n"),
                )
                PsiDocumentManager.getInstance(project).commitDocument(document)
            },
        )
    }

    /** The trailing return's expression with arguments substituted, or null for a bare `return;`. */
    private fun substitutedReturnText(
        trailing: Pair<String?, PsiElement>?,
        substitutions: Map<String, String>,
    ): String? {
        val returnStatement = trailing?.second ?: return null
        return substitutedText(returnStatement, returnStatement.containingFile.text, substitutions)
            .trim()
            .removePrefix("return")
            .trim()
            .trimEnd(';')
            .trim()
            .takeIf { it.isNotEmpty() }
    }

    /** The statement's text with every parameter reference replaced by its argument text. */
    private fun substitutedText(
        statement: PsiElement,
        calleeText: String,
        substitutions: Map<String, String>,
    ): String {
        val start = statement.textRange.startOffset
        val end = statement.textRange.endOffset
        val replacements = psiTreeWalk(statement)
            .filter { it.node?.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION }
            .filter { it.text in substitutions && !isCallHead(it) }
            .map {
                Triple(
                    it.textRange.startOffset - start,
                    it.textRange.endOffset - start,
                    substitutions.getValue(it.text),
                )
            }
            .sortedByDescending { it.first }
            .toList()
        val builder = StringBuilder(calleeText.substring(start, end))
        replacements.forEach { (from, to, text) -> builder.replace(from, to, text) }
        return builder.toString()
    }

    /**
     * Moves one statement from its original indentation to [toIndent], keeping the relative
     * nesting of its continuation lines (the Extract Function rule, locally).
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

    private fun indentInText(text: String, offset: Int): String {
        val lineStart = text.lastIndexOfAny(charArrayOf('\n', '\r'), (offset - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        var i = lineStart
        while (i < offset && text[i].isWhitespace()) i++
        return text.substring(lineStart, i)
    }

    private fun showHint(project: Project, editor: Editor, messageKey: String) {
        CommonRefactoringUtil.showErrorHint(
            project,
            editor,
            DreamShaderBundle.message(messageKey),
            DreamShaderBundle.message("refactoring.inlineFunction.title"),
            null,
        )
    }

    companion object {
        private val SIDE_EFFECT_OPERATORS: Set<String> =
            DreamShader2DiagnosticsPass.COMPOUND_ASSIGNMENTS + setOf("=", "++", "--")

        private val WRITE_BACK_QUALIFIERS = setOf("out", "inout")
    }
}
