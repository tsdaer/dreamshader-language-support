package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.DreamShaderTextAttributes
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2ArgumentList
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedArgument
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.lang.ASTNode
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * DreamShader 2.0 diagnostics (plan A6 + the C2 mismatch consumer).
 *
 * Conservative, upstream-code-keyed checks over the 2.0 PSI; the engine bridge and
 * `dsc check` stay the semantic referee, so every rule stays silent unless the parse tree
 * (or a confidently inferred type) leaves no ambiguity, and everything degrades to no
 * diagnostics when the deciding data is absent:
 *
 * - `DSH2158` — a positional argument after a named one (parse-tree fact).
 * - `DSH5293` — operators outside the Substrate sugar (`-`, `/`, `A * B`, unary `-`,
 *   compound assignment; a `lerp` mixing a Substrate value with a number).
 * - `DSH5295` — two Slab arguments parameterizing the same pins.
 * - `DSH5296` — `Haziness` without `Roughness`, `Thickness` without `Transmittance`.
 * - `DSH5297` — a member write after the builder value was used or assigned whole.
 * - `DSH5298` — a member write under an `if` the declaration is outside of.
 * - C2 mismatch — warning-level category conflicts on assignment (never an error).
 */
internal object DreamShader2DiagnosticsPass {
    private val DOC_ATTRIBUTE_KEY_PATTERN = Regex("@[A-Za-z][A-Za-z0-9_]*")

    fun annotate(file: DreamShaderPsiFile, sourceText: String, holder: AnnotationHolder) {
        if (!routesToV2Analysis(file, sourceText)) return
        val workspace = file.project.getService(DreamShader2WorkspaceSymbols::class.java)
        val inference = DreamShader2InferenceContext.forFile(file, workspace)

        annotateDocAttributeKeys(file, holder)
        annotateNamedArgumentOrder(file, holder)
        annotateSubstrateOperators(file, inference, holder)
        annotateSubstrateBuilders(file, holder)
        annotateAssignmentCategoryMismatches(file, inference, holder)
        if (DreamShaderDialects.extensionOf(file.virtualFile?.name ?: file.name) == "dsi") {
            DreamShader2InstanceOverridePass.annotate(file, holder)
            annotateParentOverrideNames(file, holder)
        }
    }

    // ------------------------------------------------------------------
    // C4 — semantic colors: `/// @key` attributes and .dsi override names
    // ------------------------------------------------------------------

    /** Colors the `@key` attribute names inside the file's `///` doc blocks. */
    private fun annotateDocAttributeKeys(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        PsiTreeUtil.processElements(file) { element ->
            if (element.node?.elementType != DreamShaderTokenTypes.DOC_COMMENT) return@processElements true
            val text = element.text
            DOC_ATTRIBUTE_KEY_PATTERN.findAll(text).forEach { match ->
                val absolute = com.intellij.openapi.util.TextRange(
                    element.textRange.startOffset + match.range.first,
                    element.textRange.startOffset + match.range.last + 1,
                )
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(absolute)
                    .textAttributes(DreamShaderTextAttributes.DOC_ATTRIBUTE_KEY)
                    .create()
            }
            true
        }
    }

    /** Colors the uniform override names that resolve to the parent's parameter schema. */
    private fun annotateParentOverrideNames(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        val schemaParameters = parentSchemaParameterNames(file) ?: return
        PsiTreeUtil.collectElementsOfType(file, DreamShader2UniformDeclaration::class.java).forEach { uniform ->
            val nameLeaf = uniform.declarationName()?.let { name ->
                PsiTreeUtil.collectElements(uniform) { it.node?.elementType == DreamShaderTokenTypes.IDENTIFIER && it.text == name }
                    .firstOrNull()
            } ?: return@forEach
            if (nameLeaf.text in schemaParameters) {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(nameLeaf)
                    .textAttributes(DreamShaderTextAttributes.PARENT_OVERRIDE)
                    .create()
            }
        }
    }

    private fun parentSchemaParameterNames(file: DreamShaderPsiFile): Set<String>? {
        val virtualFile = file.virtualFile ?: return null
        val repository = file.project.getService(DreamShaderSymbolIndexRepository::class.java)
        val schema = repository.indexForSource(virtualFile).data?.instance?.schema ?: return null
        if (!schema.valid) return null
        return schema.parameters.mapNotNull { it.name }.toSet()
    }

    private fun routesToV2Analysis(file: DreamShaderPsiFile, sourceText: String): Boolean {
        val fileName = file.virtualFile?.name ?: file.name
        if (DreamShaderDialects.extensionOf(fileName) in DreamShaderDialects.V2_EXTENSIONS) return true
        return DreamShaderDialects.routesToV2(sourceText)
    }

    // ------------------------------------------------------------------
    // DSH2158 — positional argument after a named one
    // ------------------------------------------------------------------

    private fun annotateNamedArgumentOrder(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        PsiTreeUtil.collectElementsOfType(file, DreamShader2ArgumentList::class.java).forEach { argumentList ->
            var sawNamed = false
            argumentList.arguments().forEach { argument ->
                when {
                    argument.node.elementType == DreamShader2ElementTypes.NAMED_ARGUMENT -> sawNamed = true
                    argument.node.elementType == DreamShader2ElementTypes.ARGUMENT && sawNamed -> {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            DreamShaderBundle.message("diagnostic.dsh2158.positionalAfterNamed"),
                        )
                            .range(argument)
                            .create()
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // DSH5293 — operators outside the sugar
    // ------------------------------------------------------------------

    private fun annotateSubstrateOperators(
        file: DreamShaderPsiFile,
        inference: DreamShader2InferenceContext,
        holder: AnnotationHolder,
    ) {
        psiTreeWalk(file).filter { it.node.elementType == DreamShader2ElementTypes.EXPRESSION }
            .forEach { expression -> annotateExpressionOperators(expression, inference, holder) }
        annotateLerpMixing(file, inference, holder)
    }

    private fun annotateExpressionOperators(
        expression: PsiElement,
        inference: DreamShader2InferenceContext,
        holder: AnnotationHolder,
    ) {
        val children = dreamShader2MeaningfulChildren(expression)
        if (children.isEmpty()) return

        if (children.first().elementType == DreamShaderTokenTypes.OPERATOR) {
            val operand = children.getOrNull(1)?.psi
            if (operand != null && inference.typeOf(operand) == DreamShader2Type.Substrate) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.dsh5293.operatorOutsideSugar", children.first().text),
                )
                    .range(operand)
                    .create()
            }
            return
        }

        children.forEachIndexed { index, child ->
            if (child.elementType != DreamShaderTokenTypes.OPERATOR) return@forEachIndexed
            val left = children.getOrNull(index - 1)?.psi
            val right = children.getOrNull(index + 1)?.psi
            val leftType = left?.let(inference::typeOf) ?: DreamShader2Type.Unknown
            val rightType = right?.let(inference::typeOf) ?: DreamShader2Type.Unknown
            val message = when {
                child.text == "*" && leftType == DreamShader2Type.Substrate && rightType == DreamShader2Type.Substrate ->
                    DreamShaderBundle.message("diagnostic.dsh5293.operatorOutsideSugar", child.text)
                child.text in COMPOUND_ASSIGNMENTS &&
                    (leftType == DreamShader2Type.Substrate || rightType == DreamShader2Type.Substrate) ->
                    DreamShaderBundle.message("diagnostic.dsh5293.compoundAssignment")
                (child.text == "-" || child.text == "/") &&
                    (leftType == DreamShader2Type.Substrate || rightType == DreamShader2Type.Substrate) ->
                    DreamShaderBundle.message("diagnostic.dsh5293.operatorOutsideSugar", child.text)
                else -> return@forEachIndexed
            }
            val offender = when {
                leftType == DreamShader2Type.Substrate -> left!!
                else -> right!!
            }
            holder.newAnnotation(HighlightSeverity.ERROR, message)
                .range(offender)
                .create()
        }
    }

    private fun annotateLerpMixing(
        file: DreamShaderPsiFile,
        inference: DreamShader2InferenceContext,
        holder: AnnotationHolder,
    ) {
        psiTreeWalk(file).filter { it.node.elementType == DreamShader2ElementTypes.CALL_EXPRESSION }
            .forEach { call ->
                val path = call.callPath() ?: return@forEach
                if (!path.equals("lerp", ignoreCase = true)) return@forEach
                val arguments = call.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST) ?: return@forEach
                val firstTwo = arguments.getChildren(null)
                    .filter { it.elementType == DreamShader2ElementTypes.ARGUMENT }
                    .take(2)
                    .map { it.psi }
                if (firstTwo.size < 2) return@forEach
                val first = inference.typeOf(firstTwo[0])
                val second = inference.typeOf(firstTwo[1])
                val mixed = (first == DreamShader2Type.Substrate && second.isNumeric) ||
                    (second == DreamShader2Type.Substrate && first.isNumeric)
                if (mixed) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message("diagnostic.dsh5293.lerpMixesSubstrateWithNumber"),
                    )
                        .range(call)
                        .create()
                }
            }
    }

    // ------------------------------------------------------------------
    // DSH5295 / DSH5296 — Slab argument families (call form)
    // ------------------------------------------------------------------

    /** The call itself: declarations and assignments hand us a single-child EXPRESSION wrapper. */
    private fun unwrapCall(psi: PsiElement?): PsiElement? {
        if (psi == null) return null
        return when (psi.node.elementType) {
            DreamShader2ElementTypes.CALL_EXPRESSION -> psi
            DreamShader2ElementTypes.EXPRESSION ->
                dreamShader2MeaningfulChildren(psi).singleOrNull()
                    ?.takeIf { it.elementType == DreamShader2ElementTypes.CALL_EXPRESSION }
                    ?.psi
            else -> null
        }
    }

    internal fun annotateSlabCall(call: PsiElement, holder: AnnotationHolder) {
        val arguments = call.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST) ?: return
        val namedArgs = arguments.getChildren(null)
            .map { it.psi }
            .filterIsInstance<DreamShader2NamedArgument>()
        val nameToArgument = namedArgs.mapNotNull { argument ->
            argument.argumentName()?.let { it to argument }
        }.toMap()

        CONFLICTING_PIN_PAIRS.forEach { (first, second) ->
            val firstArg = nameToArgument[first] ?: return@forEach
            val secondArg = nameToArgument[second] ?: return@forEach
            listOf(firstArg, secondArg).forEach { argument ->
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.dsh5295.conflictingParameters", first, second),
                )
                    .range(argument)
                    .create()
            }
        }
        checkCompanion(nameToArgument, "Haziness", "Roughness", holder)
        checkCompanion(nameToArgument, "Thickness", "Transmittance", holder)
    }

    private fun checkCompanion(
        nameToArgument: Map<String, DreamShader2NamedArgument>,
        argument: String,
        companion: String,
        holder: AnnotationHolder,
    ) {
        if (argument !in nameToArgument || companion in nameToArgument) return
        holder.newAnnotation(
            HighlightSeverity.ERROR,
            DreamShaderBundle.message("diagnostic.dsh5296.companionMissing", argument, companion),
        )
            .range(nameToArgument.getValue(argument))
            .create()
    }

    // ------------------------------------------------------------------
    // DSH5297 / DSH5298 — builder values
    // ------------------------------------------------------------------

    private class BuilderState(
        val pins: LinkedHashSet<String>,
        var sealed: Boolean,
    )

    private fun annotateSubstrateBuilders(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        psiTreeWalk(file).filter { it.node.elementType == DreamShader2ElementTypes.FUNCTION_DECLARATION }
            .forEach { function ->
                val body = function.node.findChildByType(DreamShader2ElementTypes.BLOCK)?.psi ?: return@forEach
                scanBlock(body, mutableMapOf(), holder, foreignIfCheckNames = null)
            }
    }

    private fun scanBlock(
        block: PsiElement,
        builders: MutableMap<String, BuilderState>,
        holder: AnnotationHolder,
        foreignIfCheckNames: Set<String>?,
    ) {
        val declaredHere = mutableListOf<String>()
        block.children.forEach { statement ->
            when (statement.node.elementType) {
                DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT ->
                    handleDeclaration(statement, builders, holder)?.let(declaredHere::add)
                DreamShader2ElementTypes.EXPRESSION_STATEMENT ->
                    handleExpressionStatement(statement, builders, holder, foreignIfCheckNames)
                DreamShader2ElementTypes.RETURN_STATEMENT,
                DreamShader2ElementTypes.JUMP_STATEMENT,
                -> markUses(statement, builders)
                DreamShader2ElementTypes.IF_STATEMENT ->
                    handleIf(statement, builders, holder)
                DreamShader2ElementTypes.FOR_STATEMENT,
                DreamShader2ElementTypes.WHILE_STATEMENT,
                DreamShader2ElementTypes.DO_WHILE_STATEMENT,
                -> handleLoop(statement, builders)
                DreamShader2ElementTypes.BLOCK ->
                    scanBlock(statement, builders, holder, null)
                else -> Unit
            }
        }
        // Builders declared inside a nested arm/block do not escape it.
        declaredHere.forEach(builders::remove)
    }

    private fun handleDeclaration(
        statement: PsiElement,
        builders: MutableMap<String, BuilderState>,
        holder: AnnotationHolder,
    ): String? {
        val typeSpelling = dreamShader2TypeSpellingOfStatement(statement) ?: return null
        if (!typeSpelling.equals("Substrate", ignoreCase = true)) return null
        val names = declarationNames(statement)
        if (names.size != 1) return null // grouped declarators: leave the shape to the engine
        val name = names.single() ?: return null
        val initializer = initializerOf(statement) ?: return null

        val callPsi = unwrapCall(initializer)
        val callPath = callPsi?.callPath()
        val emptyCall = callPsi != null &&
            callPath!!.startsWith("Substrate.", ignoreCase = true) &&
            dreamShader2MeaningfulChildren(
                callPsi.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST)?.psi ?: callPsi,
            ).none {
                it.elementType == DreamShader2ElementTypes.ARGUMENT ||
                    it.elementType == DreamShader2ElementTypes.NAMED_ARGUMENT
            }
        if (callPsi != null && !emptyCall) {
            annotateSlabCall(callPsi, holder)
        }
        builders[name] = if (emptyCall) {
            BuilderState(LinkedHashSet(), sealed = false)
        } else {
            // A finished value: any later member write is DSH5297 territory.
            BuilderState(LinkedHashSet(), sealed = true)
        }
        markUses(initializer, builders)
        return name
    }

    private fun handleExpressionStatement(
        statement: PsiElement,
        builders: MutableMap<String, BuilderState>,
        holder: AnnotationHolder,
        foreignIfCheckNames: Set<String>?,
    ) {
        val expression = statement.node.findChildByType(DreamShader2ElementTypes.EXPRESSION)?.psi ?: run {
            markUses(statement, builders)
            return
        }
        val children = dreamShader2MeaningfulChildren(expression)
        val assignIndex = children.indexOfFirst { it.elementType == DreamShaderTokenTypes.OPERATOR && it.text == "=" }
        if (assignIndex <= 0) {
            markUses(expression, builders)
            return
        }
        val target = children[assignIndex - 1].psi
        val rhs = children.getOrNull(assignIndex + 1)?.psi

        when {
            target.node.elementType == DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION -> {
                val baseName = target.node.firstChildNode?.text ?: return
                val builder = builders[baseName]
                val memberName = dreamShader2MeaningfulChildren(target)
                    .lastOrNull { it.elementType == DreamShaderTokenTypes.IDENTIFIER }?.text ?: return
                if (builder == null) {
                    markUses(rhs, builders)
                    return
                }
                if (foreignIfCheckNames != null && baseName in foreignIfCheckNames) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message("diagnostic.dsh5298.memberWriteUnderForeignIf", baseName),
                    )
                        .range(target)
                        .create()
                    return
                }
                if (builder.sealed) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message("diagnostic.dsh5297.memberWriteAfterSeal", baseName),
                    )
                        .range(target)
                        .create()
                    return
                }
                checkPinConflicts(builder, memberName, target, holder)
                builder.pins.add(memberName)
                markUses(rhs, builders)
            }
            target.node.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION && target.text in builders -> {
                val builder = builders.getValue(target.text)
                val rhsCall = unwrapCall(rhs)
                val rebindEmptySlab = rhsCall != null &&
                    rhsCall.callPath()?.startsWith("Substrate.", ignoreCase = true) == true &&
                    dreamShader2MeaningfulChildren(
                        rhsCall.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST)?.psi ?: rhsCall,
                    ).none {
                it.elementType == DreamShader2ElementTypes.ARGUMENT ||
                    it.elementType == DreamShader2ElementTypes.NAMED_ARGUMENT
            }
                if (rebindEmptySlab) {
                    builders[target.text] = BuilderState(LinkedHashSet(), sealed = false)
                } else {
                    builder.sealed = true
                }
                markUses(rhs, builders)
            }
            else -> markUses(expression, builders)
        }
    }

    private fun checkPinConflicts(
        builder: BuilderState,
        newPin: String,
        writeTarget: PsiElement,
        holder: AnnotationHolder,
    ) {
        CONFLICTING_PIN_PAIRS.forEach { (first, second) ->
            val existing = when {
                newPin.equals(first, ignoreCase = true) -> second
                newPin.equals(second, ignoreCase = true) -> first
                else -> return@forEach
            }
            if (builder.pins.any { it.equals(existing, ignoreCase = true) }) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.dsh5295.conflictingParameters", newPin, existing),
                )
                    .range(writeTarget)
                    .create()
            }
        }
    }

    private fun handleIf(
        statement: PsiElement,
        builders: MutableMap<String, BuilderState>,
        holder: AnnotationHolder,
    ) {
        val children = dreamShader2MeaningfulChildren(statement)
        var pastCondition = false
        val outerNames = builders.keys.toSet()
        children.forEach { child ->
            when {
                child.elementType == DreamShaderTokenTypes.RPAREN -> pastCondition = true
                child.elementType == DreamShader2ElementTypes.EXPRESSION && !pastCondition ->
                    markUses(child.psi, builders)
                pastCondition && child.psi.node.elementType in STATEMENT_TYPES -> {
                    scanBlock(child.psi, builders, holder, foreignIfCheckNames = outerNames)
                }
            }
        }
    }

    private fun handleLoop(statement: PsiElement, builders: MutableMap<String, BuilderState>) {
        // Inside loops the plugin stays out of the engine's way (upstream DSH4383 territory):
        // reads seal the value, member writes are ignored entirely.
        dreamShader2MeaningfulChildren(statement).forEach { child ->
            if (child.elementType == DreamShader2ElementTypes.EXPRESSION) {
                markUses(child.psi, builders)
            }
        }
        statement.node.findChildByType(DreamShader2ElementTypes.BLOCK)?.let { markUses(it.psi, builders) }
    }

    private fun markUses(root: PsiElement?, builders: MutableMap<String, BuilderState>) {
        if (root == null || builders.isEmpty()) return
        psiTreeWalk(root).filter {
            it.node.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION
        }.forEach { reference ->
            builders[reference.text]?.let { it.sealed = true }
        }
    }

    // ------------------------------------------------------------------
    // C2 — assignment category mismatches (warning-level, confident pairs only)
    // ------------------------------------------------------------------

    private fun annotateAssignmentCategoryMismatches(
        file: DreamShaderPsiFile,
        inference: DreamShader2InferenceContext,
        holder: AnnotationHolder,
    ) {
        psiTreeWalk(file).filter {
            it.node.elementType == DreamShader2ElementTypes.EXPRESSION_STATEMENT
        }.forEach { statement ->
            val expression = statement.node.findChildByType(DreamShader2ElementTypes.EXPRESSION)?.psi
                ?: return@forEach
            val children = dreamShader2MeaningfulChildren(expression)
            val assignIndex = children.indexOfFirst {
                it.elementType == DreamShaderTokenTypes.OPERATOR && it.text == "="
            }
            if (assignIndex <= 0) return@forEach
            val target = children[assignIndex - 1].psi
            val rhs = children.getOrNull(assignIndex + 1)?.psi ?: return@forEach
            reportMismatch(target, rhs, inference, holder)
        }
        psiTreeWalk(file).filter {
            it.node.elementType == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT
        }.forEach { statement ->
            val initializer = initializerOf(statement) ?: return@forEach
            reportMismatch(statement, initializer, inference, holder)
        }
    }

    private fun reportMismatch(
        target: PsiElement,
        value: PsiElement,
        inference: DreamShader2InferenceContext,
        holder: AnnotationHolder,
    ) {
        // Flat expressions wrap their operands in single-child EXPRESSION nodes — unwrap first.
        var targetNode = target.node
        while (targetNode.elementType == DreamShader2ElementTypes.EXPRESSION) {
            targetNode = dreamShader2MeaningfulChildren(targetNode.psi).singleOrNull() ?: return
        }
        val targetType = when (targetNode.elementType) {
            DreamShader2ElementTypes.REFERENCE_EXPRESSION -> inference.typeOfName(targetNode.text, targetNode.psi)
            DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION -> inference.typeOf(targetNode.psi)
            else -> return
        }
        val valueType = inference.typeOf(value)
        if (targetType is DreamShader2Type.Unknown || valueType is DreamShader2Type.Unknown) return
        val targetCategory = targetType.category
        val valueCategory = valueType.category
        if (targetCategory == valueCategory) return
        if (targetCategory !in MISMATCH_CATEGORIES || valueCategory !in MISMATCH_CATEGORIES) return
        holder.newAnnotation(
            HighlightSeverity.WEAK_WARNING,
            DreamShaderBundle.message("diagnostic.dsh2.typeMismatch"),
        )
            .range(value)
            .create()
    }

    // ------------------------------------------------------------------
    // shared node helpers
    // ------------------------------------------------------------------

    private fun declarationNames(statement: PsiElement): List<String?> {
        val names = mutableListOf<String?>()
        var sawType = false
        var expectName = false
        dreamShader2MeaningfulChildren(statement).forEach { child ->
            when (child.elementType) {
                DreamShaderTokenTypes.TYPE -> sawType = true
                DreamShaderTokenTypes.IDENTIFIER -> {
                    // A user-type spelling arrives as IDENTIFIER: the first one is the type.
                    if (!sawType) {
                        sawType = true
                    } else if (expectName || names.isEmpty()) {
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

    /** The node after the first `=` in a declaration statement (initializer lists included). */
    private fun initializerOf(statement: PsiElement): PsiElement? {
        val children = dreamShader2MeaningfulChildren(statement)
        val assignIndex = children.indexOfFirst {
            it.elementType == DreamShaderTokenTypes.OPERATOR && it.text == "="
        }
        if (assignIndex < 0) return null
        return children.getOrNull(assignIndex + 1)?.psi
    }

    /** Dotted callee spelling of a call node: reference/member texts before the argument list. */
    private fun PsiElement.callPath(): String? {
        // Callers may hand us the wrapper EXPRESSION; descend to the call itself.
        val callNode = unwrapCall(this) ?: return null
        if (callNode.node.elementType != DreamShader2ElementTypes.CALL_EXPRESSION) return null

        val parts = mutableListOf<String>()
        dreamShader2MeaningfulChildren(callNode).forEach { child: ASTNode ->
            when (child.elementType) {
                DreamShader2ElementTypes.ARGUMENT_LIST -> return parts.takeIf { it.isNotEmpty() }?.joinToString(".")
                DreamShaderTokenTypes.DOT -> Unit
                // The namespace/base of a dotted callee arrives as a reference node.
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

    internal fun dreamShader2TypeSpellingOfStatement(statement: PsiElement): String? =
        dreamShader2MeaningfulChildren(statement)
            .firstOrNull {
                it.elementType == DreamShaderTokenTypes.TYPE || it.elementType == DreamShaderTokenTypes.IDENTIFIER
            }
            ?.text

    private val STATEMENT_TYPES = setOf(
        DreamShader2ElementTypes.BLOCK,
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

    private val COMPOUND_ASSIGNMENTS = setOf("+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "|=", "^=")

    /**
     * Upstream `Docs/language-v2/substrate.md`: the argument pairs that parameterize the same
     * pins of a `Substrate.Slab`.
     */
    private val CONFLICTING_PIN_PAIRS = setOf(
        "BaseColor" to "DiffuseAlbedo",
        "BaseColor" to "F0",
        "BaseColor" to "IOR",
        "Metallic" to "DiffuseAlbedo",
        "Metallic" to "F0",
        "Metallic" to "IOR",
        "Specular" to "DiffuseAlbedo",
        "Specular" to "F0",
        "Specular" to "IOR",
        "Haziness" to "SecondRoughness",
        "Transmittance" to "SSSMFP",
    )

    /** Coarse buckets the C2 mismatch warning compares on; BOOL↔NUMERIC is legal HLSL and excluded. */
    private val MISMATCH_CATEGORIES = setOf(
        DreamShader2Type.Category.NUMERIC,
        DreamShader2Type.Category.TEXTURE,
        DreamShader2Type.Category.SAMPLER,
        DreamShader2Type.Category.MATERIAL,
        DreamShader2Type.Category.SUBSTRATE,
        DreamShader2Type.Category.STRUCT,
        DreamShader2Type.Category.VOID,
    )
}
