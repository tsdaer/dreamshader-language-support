package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionManifest
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedArgument
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2Parameter
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2ParameterList
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructField
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2SemanticResolver
import com.intellij.lang.ASTNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Conservative type inference over the 2.0 PSI (plan C2).
 *
 * Contract: a confidently-inferred type is returned; everything else is [DreamShader2Type.Unknown],
 * and **Unknown never produces a diagnostic** — the engine bridge stays the referee. Vector shapes
 * follow HLSL component-wise rules (scalar ⊕ vector → vector; swizzle narrows by letter count);
 * the flat expression shape means binary operators fold left-to-right, which is exact enough for
 * category checks and deliberately not a precedence-faithful evaluator.
 */
internal class DreamShader2InferenceContext private constructor(
    private val project: Project?,
    private val analysis: DreamShader2FileAnalysis,
    private val workspace: DreamShader2WorkspaceSymbols?,
    private val sourceFile: DreamShaderPsiFile?,
    private val includeClosure: List<DreamShaderPsiFile>,
) {
    private val typeCache = HashMap<PsiElement, DreamShader2Type>()
    private val closureStructFields = HashMap<String, List<DreamShader2StructField>>()
    private val writeBacks = HashMap<DreamShader2FunctionDeclaration, List<WriteBackRecord>>()
    private val catalogEntries by lazy { DreamShaderMaterialExpressionManifest.catalogEntries(project,
        project?.getService(com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java)?.state?.materialExpressionManifestPath) }
    private val catalogReturnTypes by lazy { buildCatalogReturnTypes() }

    fun typeOf(expression: PsiElement?): DreamShader2Type {
        if (expression == null) return DreamShader2Type.Unknown
        typeCache[expression]?.let { return it }
        val computed = computeType(expression)
        typeCache[expression] = computed
        return computed
    }

    /**
     * Declared/inferred type of a name used at [useSite]. The out/inout write-back table
     * (M16 C30) comes first — a call that wrote the name re-typed it — but a re-declaration
     * of the name nearer to the use site than that call wins back: the record rewrote the
     * previous binding, not the new one. Otherwise locals (lexically visible, nearest
     * first), then parameters, then file scope.
     */
    fun typeOfName(name: String, useSite: PsiElement): DreamShader2Type {
        val binding = DreamShader2SemanticResolver.resolve(name, useSite) ?: return DreamShader2Type.Unknown
        enclosingFunctionOf(useSite)?.let { function ->
            latestWriteBackBefore(function, name, useSite)?.let { return it.type }
        }
        return DreamShader2Type.fromSpelling(binding.type)
    }

    fun structFields(typeName: String): List<DreamShader2StructField> {
        analysis.structFields(typeName).let { if (it.isNotEmpty()) return it }
        val fields = includeClosure.asSequence()
            .map { DreamShader2FileAnalysis.of(it) }
            .mapNotNull { it.structByName(typeName) }
            .flatMap { PsiTreeUtil.getChildrenOfTypeAsList(it, DreamShader2StructField::class.java).asSequence() }
            .toList()
        return fields
    }

    // ------------------------------------------------------------------
    // dispatch
    // ------------------------------------------------------------------

    private fun computeType(element: PsiElement): DreamShader2Type = when (element.node.elementType) {
        DreamShader2ElementTypes.LITERAL_EXPRESSION -> literalType(element)
        DreamShader2ElementTypes.REFERENCE_EXPRESSION -> referenceType(element)
        DreamShader2ElementTypes.CONSTRUCTOR_EXPRESSION -> constructorType(element)
        DreamShader2ElementTypes.CAST_EXPRESSION -> castType(element)
        DreamShader2ElementTypes.PARENTHESIZED_EXPRESSION ->
            typeOf(firstChildPsiOf(element, DreamShader2ElementTypes.EXPRESSION))
        DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION -> memberAccessType(element)
        DreamShader2ElementTypes.INDEX_EXPRESSION -> indexType(element)
        DreamShader2ElementTypes.CALL_EXPRESSION -> callType(element)
        DreamShader2ElementTypes.EXPRESSION -> flatExpressionType(element)
        // A positional argument is a wrapper around its value expression.
        DreamShader2ElementTypes.ARGUMENT ->
            typeOf(dreamShader2MeaningfulChildren(element).firstOrNull()?.psi)
        else -> DreamShader2Type.Unknown
    }

    private fun literalType(element: PsiElement): DreamShader2Type {
        val text = element.text
        if (text.isEmpty()) return DreamShader2Type.Unknown
        val lower = text.lowercase()
        return when {
            lower.endsWith("u") -> DreamShader2Type.Scalar(DreamShader2Type.Family.UINT)
            lower.endsWith("h") -> DreamShader2Type.Scalar(DreamShader2Type.Family.HALF)
            lower.endsWith("f") -> DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT)
            lower.contains('.') || 'e' in lower -> DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT)
            lower.startsWith("0x") -> DreamShader2Type.Scalar(DreamShader2Type.Family.INT)
            lower.all { it.isDigit() } -> DreamShader2Type.Scalar(DreamShader2Type.Family.INT)
            else -> DreamShader2Type.Unknown
        }
    }

    private fun referenceType(element: PsiElement): DreamShader2Type = when (element.text) {
        "true", "false" -> DreamShader2Type.Scalar(DreamShader2Type.Family.BOOL)
        else -> typeOfName(element.text, element)
    }

    private fun constructorType(element: PsiElement): DreamShader2Type =
        DreamShader2Type.fromSpelling(element.node.findChildByType(DreamShaderTokenTypes.TYPE)?.text)

    private fun castType(element: PsiElement): DreamShader2Type =
        DreamShader2Type.fromSpelling(element.node.findChildByType(DreamShaderTokenTypes.TYPE)?.text)

    private fun memberAccessType(element: PsiElement): DreamShader2Type {
        val baseNode = element.node.firstChildNode ?: return DreamShader2Type.Unknown
        val memberNode = dreamShader2MeaningfulChildren(element)
            .lastOrNull {
                it.elementType == DreamShaderTokenTypes.IDENTIFIER ||
                    it.elementType == DreamShaderTokenTypes.KEYWORD
            }
            ?: return DreamShader2Type.Unknown
        val basePsi = baseNode.psi
        if (basePsi === element || baseNode.elementType == DreamShaderTokenTypes.DOT) {
            return DreamShader2Type.Unknown
        }
        if (baseNode.elementType == DreamShader2ElementTypes.CALL_EXPRESSION) {
            val path = calleePath(basePsi)
            val output = catalogEntries.firstOrNull { it.qualifiedName == path || path in it.aliases }
                ?.outputs?.firstOrNull { it.name == memberNode.text }
            if (output != null) return DreamShader2Type.fromSpelling(output.type)
            val formal = path?.let { calleeParameters(it) }?.firstOrNull {
                it.parameterName() == memberNode.text && firstKeywordText(it) in WRITE_BACK_QUALIFIERS
            }
            if (formal != null) return DreamShader2Type.fromSpelling(formal.typeName())
        }
        return memberOf(typeOf(basePsi), memberNode.text)
    }

    fun memberOf(base: DreamShader2Type, member: String): DreamShader2Type = when {
        base is DreamShader2Type.Vector || base is DreamShader2Type.Scalar -> DreamShader2Swizzle.type(base, member)
        base is DreamShader2Type.Material -> catalogEntries.asSequence().flatMap { it.materialAttributes.asSequence() }
            .firstOrNull { it.name == member || member in it.aliases }?.let { DreamShader2Type.fromSpelling(it.type) }
            ?: materialAttributeType(member)
        base is DreamShader2Type.StructType ->
            structFields(base.name).firstOrNull { it.fieldName() == member }
                ?.let { DreamShader2Type.fromSpelling(it.typeName()) }
                ?: DreamShader2Type.Unknown
        else -> DreamShader2Type.Unknown
    }

    private fun indexType(element: PsiElement): DreamShader2Type {
        val baseNode = element.node.firstChildNode ?: return DreamShader2Type.Unknown
        val basePsi = baseNode.psi
        if (basePsi === element) return DreamShader2Type.Unknown
        return when (val baseType = typeOf(basePsi)) {
            is DreamShader2Type.Vector -> DreamShader2Type.Scalar(baseType.family)
            is DreamShader2Type.Matrix -> DreamShader2Type.Vector(baseType.family, baseType.cols)
            else -> DreamShader2Type.Unknown
        }
    }

    private fun callType(element: PsiElement): DreamShader2Type {
        val path = calleePath(element) ?: return DreamShader2Type.Unknown
        val lowerPath = path.lowercase()
        return when {
            lowerPath.startsWith("substrate.") -> DreamShader2Type.Substrate
            lowerPath == "ue.vertexcolor" -> DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 4)
            lowerPath.startsWith("ue.") -> catalogReturnType(path) ?: DreamShader2Type.Unknown
            lowerPath.endsWith(".sample") -> sampledType(path, element)
            lowerPath == "dot" -> dotResultType(element)
            lowerPath == "cross" -> crossResultType(element)
            lowerPath in FIRST_ARGUMENT_INTRINSICS -> firstArgumentType(element)
            else -> userFunctionReturnType(path, element)
        }
    }

    /**
     * Declared parameters of a user function call target (M15 C24.3), resolved in the same
     * order as [userFunctionReturnType]: this file first, then the include closure. The
     * workspace export layer stays unchecked — its entries carry no parameter shapes.
     */
    fun calleeParameters(path: String): List<DreamShader2Parameter>? {
        val file = sourceFile ?: return null
        val signatures = DreamShader2SemanticResolver.functions(file, path).map { it.parameterList()?.parameters().orEmpty() }
        return signatures.distinctBy { parameters -> parameters.map { Triple(it.parameterName(), it.typeName(), firstKeywordText(it)) } }
            .singleOrNull()
    }

    /** HLSL operand-typed intrinsics: the result carries the first argument's type. */
    private fun firstArgumentType(call: PsiElement): DreamShader2Type {
        val arguments = call.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST) ?: return DreamShader2Type.Unknown
        val first = dreamShader2MeaningfulChildren(arguments.psi)
            .firstOrNull { it.elementType == DreamShader2ElementTypes.ARGUMENT } ?: return DreamShader2Type.Unknown
        return typeOf(first.psi)
    }

    /** HLSL `dot`: two equally sized confident numeric operands give a scalar of the wider family. */
    private fun dotResultType(call: PsiElement): DreamShader2Type {
        val (first, second) = positionalArgumentTypes(call, 2)
        if (first == DreamShader2Type.Unknown || second == DreamShader2Type.Unknown) return DreamShader2Type.Unknown
        if (!first.isNumeric || !second.isNumeric) return DreamShader2Type.Unknown
        if (first.componentCount != second.componentCount) return DreamShader2Type.Unknown
        return widerFamily(first, second)?.let { DreamShader2Type.Scalar(it) } ?: DreamShader2Type.Unknown
    }

    /** HLSL `cross`: two confident same-family 3-vectors give the same 3-vector. */
    private fun crossResultType(call: PsiElement): DreamShader2Type {
        val (first, second) = positionalArgumentTypes(call, 2)
        val vector = first as? DreamShader2Type.Vector ?: return DreamShader2Type.Unknown
        return if (vector.size == 3 && vector == second) vector else DreamShader2Type.Unknown
    }

    /** The first [count] positional arguments' types; fewer arguments or named ones stay Unknown. */
    private fun positionalArgumentTypes(call: PsiElement, count: Int): List<DreamShader2Type> {
        val arguments = call.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST)
            ?: return List(count) { DreamShader2Type.Unknown }
        val positional = dreamShader2MeaningfulChildren(arguments.psi)
            .filter { it.elementType == DreamShader2ElementTypes.ARGUMENT }
            .map { typeOf(it.psi) }
            .toList()
        return if (positional.size < count) List(count) { DreamShader2Type.Unknown } else positional.take(count)
    }

    private fun sampledType(path: String, useSite: PsiElement): DreamShader2Type {
        val baseName = path.substringBeforeLast('.', missingDelimiterValue = "")
        return if (baseName.isNotEmpty() && typeOfName(baseName, useSite) is DreamShader2Type.Texture) {
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 4)
        } else {
            DreamShader2Type.Unknown
        }
    }

    /**
     * Flat binary/unary/ternary folds: operands are the non-OPERATOR direct children, folded
     * left to right. A leading operator means the prefix form (unary `-`, `!`).
     *
     * Ternary folding keeps a frame stack: `?` opens a branch pair (the accumulated condition
     * is discarded — it is `bool`, never the result), `:` snapshots the then-arm, and frames
     * close innermost-first so right-nested chains (`a ? b : c ? d : e`) merge correctly.
     * Compound assignments keep the LHS type — the RHS never widens the stored shape.
     */
    private fun flatExpressionType(element: PsiElement): DreamShader2Type {
        val children = dreamShader2MeaningfulChildren(element)
        val first = children.firstOrNull() ?: return DreamShader2Type.Unknown
        if (children.size == 1) return typeOf(first.psi)

        if (children.first().elementType == DreamShaderTokenTypes.OPERATOR) {
            return when (children.first().text) {
                "!" -> DreamShader2Type.Scalar(DreamShader2Type.Family.BOOL)
                else -> typeOf(children.getOrNull(1)?.psi).takeIf { it.isNumeric } ?: DreamShader2Type.Unknown
            }
        }

        var accumulator: DreamShader2Type = DreamShader2Type.Unknown
        var pendingOperator: String? = null
        val ternaryFrames = ArrayDeque<TernaryFrame>()
        children.forEach { child ->
            if (child.elementType == DreamShaderTokenTypes.OPERATOR) {
                when (val op = child.text) {
                    "?" -> {
                        ternaryFrames.addLast(TernaryFrame())
                        pendingOperator = null
                    }
                    ":" -> {
                        while (ternaryFrames.isNotEmpty() && ternaryFrames.last().sawColon) {
                            accumulator = ternaryFrames.removeLast().mergeWith(accumulator)
                        }
                        ternaryFrames.lastOrNull()?.let { frame ->
                            frame.sawColon = true
                            frame.thenBranch = accumulator
                        }
                        accumulator = DreamShader2Type.Unknown
                        pendingOperator = null
                    }
                    else -> pendingOperator = op
                }
                return@forEach
            }
            val operandType = typeOf(child.psi)
            val op = pendingOperator
            accumulator = when {
                op == null -> operandType
                op in BOOLEAN_RESULT_OPERATORS -> booleanResult(accumulator, operandType)
                op in DreamShader2DiagnosticsPass.COMPOUND_ASSIGNMENTS -> accumulator
                else -> binaryArithmetic(accumulator, operandType, op)
            }
            pendingOperator = null
        }
        while (ternaryFrames.isNotEmpty()) {
            accumulator = ternaryFrames.removeLast().mergeWith(accumulator)
        }
        return accumulator
    }

    // ------------------------------------------------------------------
    // name resolution
    // ------------------------------------------------------------------

    private fun enclosingFunctionOf(useSite: PsiElement): DreamShader2FunctionDeclaration? =
        PsiTreeUtil.getParentOfType(useSite, DreamShader2FunctionDeclaration::class.java)

    /**
     * Locals declared before [useSite] and lexically visible there (M15 C24.1): the
     * declaration's scope owner — its nearest BLOCK, or the `for` header for loop-init
     * declarations — must also be an ancestor of the use site, so a sibling block's
     * declaration (an earlier `if` arm, a finished loop) can no longer lend its type.
     * The nearest matching declaration wins, which resolves shadowing innermost-first.
     */
    private fun localDeclaredTypeIn(
        function: DreamShader2FunctionDeclaration,
        name: String,
        useSite: PsiElement,
    ): DreamShader2Type? = localDeclarationBefore(function, name, useSite)?.second

    /** The nearest visible declaration: its start offset (for the write-back merge) and type. */
    private fun localDeclarationBefore(
        function: DreamShader2FunctionDeclaration,
        name: String,
        useSite: PsiElement,
    ): Pair<Int, DreamShader2Type>? {
        val functionRange = function.textRange
        if (!functionRange.contains(useSite.textRange)) return null
        val visibleScopes = ancestorScopesOf(useSite)
        var match: Pair<Int, DreamShader2Type>? = null
        var matchStart = Int.MIN_VALUE
        psiTreeWalk(function).filter {
            it.node.elementType == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT
        }.forEach { statement ->
            val start = statement.textRange.startOffset
            if (start > matchStart && start < useSite.textRange.startOffset && scopeOwnerOf(statement) in visibleScopes) {
                declaredNamesAndType(statement)?.let { (names, type) ->
                    if (name in names) {
                        match = start to type
                        matchStart = start
                    }
                }
            }
        }
        return match
    }

    // ------------------------------------------------------------------
    // out/inout write-back table (M16 C30)
    // ------------------------------------------------------------------

    /**
     * The latest call before [useSite] that writes [name] through an `out`/`inout` formal
     * with a confident spelling type. Uses at or inside the call itself stay pre-call: the
     * write-back happens when the call returns, so only sites at or after the call's end
     * consume the record — which also keeps the C24.3 argument mismatch check reading the
     * declared type of the very argument that receives the write.
     */
    private fun latestWriteBackBefore(
        function: DreamShader2FunctionDeclaration,
        name: String,
        useSite: PsiElement,
    ): WriteBackRecord? {
        val useOffset = useSite.textRange.startOffset
        val binding = DreamShader2SemanticResolver.resolve(name, useSite)?.identifier ?: return null
        return writeBacksIn(function).lastOrNull {
            it.name == name && it.callEnd <= useOffset && it.binding == binding &&
                it.guards.all { guard -> PsiTreeUtil.isAncestor(guard, useSite, false) }
        }
    }

    /**
     * One record per (call, argument) pair where a plain variable reference is passed to an
     * `out`/`inout` formal whose spelling type is confident: after the call, that variable
     * holds the formal's type. Lazy per enclosing function; the scan only reads PSI and
     * declared spellings — no inference recursion.
     */
    private fun writeBacksIn(function: DreamShader2FunctionDeclaration): List<WriteBackRecord> =
        writeBacks.getOrPut(function) {
            val records = mutableListOf<WriteBackRecord>()
            psiTreeWalk(function).forEach { node ->
                if (node.node.elementType != DreamShader2ElementTypes.CALL_EXPRESSION) return@forEach
                val source = function.containingFile as? DreamShaderPsiFile ?: return@forEach
                if (DreamShaderPreprocessorModel.of(source).stateAt(node.textOffset) != DreamShaderPreprocessorModel.State.ACTIVE) return@forEach
                // A short-circuit or conditional expression does not prove the call ran.
                if (generateSequence(node.parent) { it.parent }.takeWhile { it != function }.any { parent ->
                        parent.node.elementType == DreamShader2ElementTypes.EXPRESSION && dreamShader2MeaningfulChildren(parent)
                            .any { it.elementType == DreamShaderTokenTypes.OPERATOR && it.text in setOf("&&", "||", "?") }
                    }) return@forEach
                val path = calleePath(node) ?: return@forEach
                val formals = calleeParameters(path) ?: return@forEach
                val argumentList = node.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST)?.psi
                    ?: return@forEach
                val arguments = dreamShader2MeaningfulChildren(argumentList)
                    .filter {
                        it.elementType == DreamShader2ElementTypes.ARGUMENT ||
                            it.elementType == DreamShader2ElementTypes.NAMED_ARGUMENT
                    }
                // More arguments than parameters means defaults/overloads we cannot see - stay
                // silent (the C24.3 pairing rule).
                if (arguments.size > formals.size) return@forEach
                arguments.forEachIndexed { index, argumentNode ->
                    val formal = when (argumentNode.elementType) {
                        DreamShader2ElementTypes.NAMED_ARGUMENT ->
                            (argumentNode.psi as? DreamShader2NamedArgument)?.argumentName()
                                ?.let { argumentName ->
                                    formals.firstOrNull {
                                        it.parameterName()?.equals(argumentName, ignoreCase = true) == true
                                    }
                                }
                        else -> formals.getOrNull(index)
                    } ?: return@forEachIndexed
                    if (firstKeywordText(formal) !in WRITE_BACK_QUALIFIERS) return@forEachIndexed
                    val formalType = formal.typeName()?.let(DreamShader2Type::fromSpelling)
                        ?.takeIf { it != DreamShader2Type.Unknown }
                        ?: return@forEachIndexed
                    val written = plainReferenceArgument(argumentNode) ?: return@forEachIndexed
                    records.add(
                        WriteBackRecord(
                            node.textRange.startOffset,
                            node.textRange.endOffset,
                            written,
                            formalType,
                            DreamShader2SemanticResolver.resolve(written, node)?.identifier,
                            generateSequence(node.parent) { it.parent }.takeWhile { it != function }
                                .filter { it.node?.elementType in setOf(DreamShader2ElementTypes.BLOCK,
                                    DreamShader2ElementTypes.IF_STATEMENT, DreamShader2ElementTypes.FOR_STATEMENT,
                                    DreamShader2ElementTypes.WHILE_STATEMENT, DreamShader2ElementTypes.DO_WHILE_STATEMENT) }.toList(),
                        ),
                    )
                }
            }
            records.sortedBy { it.callStart }
        }

    /** `out`/`inout` when the parameter opens with that keyword; plain `in` parameters are null. */
    private fun firstKeywordText(parameter: DreamShader2Parameter): String? =
        dreamShader2MeaningfulChildren(parameter).firstOrNull()
            ?.takeIf { it.elementType == DreamShaderTokenTypes.KEYWORD }
            ?.text

    /**
     * The variable name when the argument's value is a plain reference (possibly through
     * single-child EXPRESSION wrappers) — member/index/computed arguments never write back.
     */
    private fun plainReferenceArgument(argumentNode: ASTNode): String? {
        var value = dreamShader2MeaningfulChildren(argumentNode.psi).lastOrNull() ?: return null
        while (value.elementType == DreamShader2ElementTypes.EXPRESSION) {
            value = dreamShader2MeaningfulChildren(value.psi).singleOrNull() ?: return null
        }
        return value.takeIf { it.elementType == DreamShader2ElementTypes.REFERENCE_EXPRESSION }?.text
    }

    private class WriteBackRecord(
        val callStart: Int,
        val callEnd: Int,
        val name: String,
        val type: DreamShader2Type,
        val binding: PsiElement?,
        val guards: List<PsiElement>,
    )

    /** Every BLOCK / `for` header on [useSite]'s ancestor chain — the scopes it can see. */
    private fun ancestorScopesOf(useSite: PsiElement): Set<PsiElement> {
        val scopes = HashSet<PsiElement>()
        var current: PsiElement? = useSite.parent
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

    private fun parameterType(
        function: DreamShader2FunctionDeclaration,
        name: String,
    ): DreamShader2Type? {
        val parameters: DreamShader2ParameterList = function.parameterList() ?: return null
        parameters.parameters().forEach { parameter: DreamShader2Parameter ->
            if (parameter.parameterName() == name) {
                return parameter.typeName()?.let(DreamShader2Type::fromSpelling)
            }
        }
        return null
    }

    private fun fileScopeType(name: String): DreamShader2Type? {
        analysis.uniforms.forEach { uniform ->
            if (name in dreamShader2DeclaratorNames(uniform)) {
                return DreamShader2Type.fromSpelling(uniform.typeName())
            }
        }
        analysis.globals.forEach { global ->
            if (name in dreamShader2DeclaratorNames(global)) {
                return DreamShader2Type.fromSpelling(global.typeName())
            }
        }
        return null
    }

    /** Declarator names + the declared type of one local declaration statement node. */
    private fun declaredNamesAndType(statement: PsiElement): Pair<List<String>, DreamShader2Type>? {
        val typeNode = dreamShader2MeaningfulChildren(statement).firstOrNull {
            it.elementType == DreamShaderTokenTypes.TYPE || it.elementType == DreamShaderTokenTypes.IDENTIFIER
        } ?: return null
        val type = DreamShader2Type.fromSpelling(typeNode.text)
        val names = mutableListOf<String>()
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
        if (names.isEmpty()) return null
        return names to type
    }

    private fun userFunctionReturnType(path: String, useSite: PsiElement): DreamShader2Type {
        val binding = DreamShader2SemanticResolver.resolve(path, useSite)
        if (binding?.owner !is DreamShader2FunctionDeclaration) return DreamShader2Type.Unknown
        if (binding.type == null) return DreamShader2Type.Unknown
        val file = useSite.containingFile as? DreamShaderPsiFile ?: return DreamShader2Type.Unknown
        val returns = DreamShader2SemanticResolver.functions(file, path).map { DreamShader2Type.fromSpelling(it.returnTypeText()) }.distinct()
        return returns.singleOrNull() ?: DreamShader2Type.Unknown
    }

    private fun buildCatalogReturnTypes(): Map<String, DreamShader2Type> {
        if (project == null) return emptyMap()
        return catalogEntries
            .mapNotNull { entry ->
                val type = when {
                    entry.namespace.equals("Substrate", ignoreCase = true) -> DreamShader2Type.Substrate
                    else -> DreamShader2Type.fromSpelling(entry.outputType)
                }
                if (type == DreamShader2Type.Unknown) null else entry.qualifiedName to type
            }
            .toMap()
    }

    private fun catalogReturnType(path: String): DreamShader2Type? =
        catalogReturnTypes[path]

    companion object {

        /**
         * HLSL component-wise intrinsics whose result carries the first argument's type
         * (M15 C24.2). Anything not listed stays Unknown — the list only grows with
         * documented, unambiguous operand-typed semantics.
         */
        private val FIRST_ARGUMENT_INTRINSICS = setOf(
            "lerp", "saturate", "clamp", "abs", "normalize",
            "min", "max", "floor", "ceil", "round", "frac",
        )

        /** Comparison/logical operators: confident operands always yield `bool`. */
        private val BOOLEAN_RESULT_OPERATORS = setOf("&&", "||", "==", "!=", "<", ">", "<=", ">=")

        /** Parameter qualifiers that rewrite the argument variable when the call returns. */
        private val WRITE_BACK_QUALIFIERS = setOf("out", "inout")

        fun forFile(file: DreamShaderPsiFile, workspace: DreamShader2WorkspaceSymbols?): DreamShader2InferenceContext {
            val closure = workspace?.includeClosurePsi(file).orEmpty().filterIsInstance<DreamShaderPsiFile>()
            return DreamShader2InferenceContext(
                project = file.project,
                analysis = DreamShader2FileAnalysis.of(file),
                workspace = workspace,
                sourceFile = file,
                includeClosure = closure,
            )
        }

        private fun firstChildPsiOf(element: PsiElement, type: com.intellij.psi.tree.IElementType): PsiElement? =
            element.node.findChildByType(type)?.psi

        /**
         * Dotted callee spelling of a flat call node: the reference and member texts before the
         * argument list, joined with dots. Nested calls live inside the argument subtree and so
         * never contaminate the path.
         */
        private fun calleePath(call: PsiElement): String? {
            val arguments = call.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST)?.psi ?: return null
            return call.text.substring(0, arguments.textOffset - call.textOffset).trim().takeIf { it.isNotEmpty() }
        }

        private fun booleanResult(
            left: DreamShader2Type,
            right: DreamShader2Type,
        ): DreamShader2Type =
            if (left is DreamShader2Type.Unknown || right is DreamShader2Type.Unknown) {
                DreamShader2Type.Unknown
            } else {
                DreamShader2Type.Scalar(DreamShader2Type.Family.BOOL)
            }

        /** Either-or merge for ternary arms: a confident arm beats Unknown, shapes promote like `+`. */
        private fun branchMerge(
            thenBranch: DreamShader2Type,
            elseBranch: DreamShader2Type,
        ): DreamShader2Type = when {
            thenBranch is DreamShader2Type.Unknown -> elseBranch
            elseBranch is DreamShader2Type.Unknown -> thenBranch
            thenBranch == elseBranch -> thenBranch
            thenBranch.isNumeric && elseBranch.isNumeric -> binaryArithmetic(thenBranch, elseBranch, "+")
            else -> DreamShader2Type.Unknown
        }

        private class TernaryFrame {
            var thenBranch: DreamShader2Type = DreamShader2Type.Unknown
            var sawColon: Boolean = false

            fun mergeWith(elseBranch: DreamShader2Type): DreamShader2Type = branchMerge(thenBranch, elseBranch)
        }

        private fun binaryArithmetic(
            left: DreamShader2Type,
            right: DreamShader2Type,
            op: String,
        ): DreamShader2Type {
            if (left is DreamShader2Type.Unknown || right is DreamShader2Type.Unknown) {
                return DreamShader2Type.Unknown
            }
            if (left == DreamShader2Type.Substrate || right == DreamShader2Type.Substrate) {
                return substrateResult(left, right, op)
            }
            if (!left.isNumeric || !right.isNumeric) return DreamShader2Type.Unknown
            val family = widerFamily(left, right) ?: return DreamShader2Type.Unknown
            val leftSize = left.componentCount ?: return DreamShader2Type.Unknown
            val rightSize = right.componentCount ?: return DreamShader2Type.Unknown
            val size = when {
                leftSize == rightSize -> leftSize
                leftSize == 1 -> rightSize
                rightSize == 1 -> leftSize
                else -> return DreamShader2Type.Unknown
            }
            val leftIsMatrix = left is DreamShader2Type.Matrix
            val rightIsMatrix = right is DreamShader2Type.Matrix
            return when {
                leftIsMatrix || rightIsMatrix -> {
                    val matrix = if (leftIsMatrix) left as DreamShader2Type.Matrix else right as DreamShader2Type.Matrix
                    val other = if (leftIsMatrix) right else left
                    if (other.componentCount == 1) {
                        DreamShader2Type.Matrix(family, matrix.rows, matrix.cols)
                    } else {
                        DreamShader2Type.Unknown
                    }
                }
                size == 1 -> DreamShader2Type.Scalar(family)
                else -> DreamShader2Type.Vector(family, size)
            }
        }

        /** Substrate sugar results: Add and Weight keep Substrate; anything else is not ours to judge. */
        private fun substrateResult(
            left: DreamShader2Type,
            right: DreamShader2Type,
            op: String,
        ): DreamShader2Type = when {
            op == "+" && left == DreamShader2Type.Substrate && right == DreamShader2Type.Substrate ->
                DreamShader2Type.Substrate
            op == "*" && left == DreamShader2Type.Substrate && right.isNumericScalar -> DreamShader2Type.Substrate
            op == "*" && right == DreamShader2Type.Substrate && left.isNumericScalar -> DreamShader2Type.Substrate
            else -> DreamShader2Type.Unknown
        }

        private fun widerFamily(
            left: DreamShader2Type,
            right: DreamShader2Type,
        ): DreamShader2Type.Family? {
            val leftFamily = familyOf(left) ?: return null
            val rightFamily = familyOf(right) ?: return null
            if (leftFamily == rightFamily) return leftFamily
            val floatish = setOf(DreamShader2Type.Family.FLOAT, DreamShader2Type.Family.HALF)
            return when {
                leftFamily in floatish || rightFamily in floatish -> DreamShader2Type.Family.FLOAT
                else -> null
            }
        }

        private fun familyOf(type: DreamShader2Type): DreamShader2Type.Family? = when (type) {
            is DreamShader2Type.Scalar -> type.family
            is DreamShader2Type.Vector -> type.family
            is DreamShader2Type.Matrix -> type.family
            else -> null
        }
    }
}

/** The `material` attribute spellings the type pass knows; anything else is Unknown. */
internal fun materialAttributeType(member: String): DreamShader2Type = when (member) {
    "FrontMaterial" -> DreamShader2Type.Substrate
    "BaseColor", "EmissiveColor", "Normal", "Tangent", "WorldPositionOffset" ->
        DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 3)
    "Opacity", "Roughness", "Metallic", "Specular", "AmbientOcclusion", "PixelDepthOffset" ->
        DreamShader2Type.Scalar(DreamShader2Type.Family.FLOAT)
    else -> DreamShader2Type.Unknown
}
