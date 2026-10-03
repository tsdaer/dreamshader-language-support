package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionManifest
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2Parameter
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2ParameterList
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructField
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
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
    private val sourceFile: VirtualFile?,
    private val includeClosure: List<DreamShaderPsiFile>,
) {
    private val typeCache = HashMap<PsiElement, DreamShader2Type>()
    private val closureStructFields = HashMap<String, List<DreamShader2StructField>>()
    private val catalogReturnTypes by lazy { buildCatalogReturnTypes() }

    fun typeOf(expression: PsiElement?): DreamShader2Type {
        if (expression == null) return DreamShader2Type.Unknown
        typeCache[expression]?.let { return it }
        val computed = computeType(expression)
        typeCache[expression] = computed
        return computed
    }

    /** Declared/inferred type of a name used at [useSite]: locals before it, then file scope. */
    fun typeOfName(name: String, useSite: PsiElement): DreamShader2Type {
        enclosingFunctionOf(useSite)?.let { function ->
            localDeclaredTypeIn(function, name, useSite)?.let { return it }
            parameterType(function, name)?.let { return it }
        }
        fileScopeType(name)?.let { return it }
        return DreamShader2Type.Unknown
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
        return memberOf(typeOf(basePsi), memberNode.text)
    }

    private fun memberOf(base: DreamShader2Type, member: String): DreamShader2Type = when {
        base is DreamShader2Type.Vector -> swizzleType(base, member)
        base is DreamShader2Type.Scalar && member.length == 1 && member[0].lowercaseChar() in SWIZZLE_LETTERS -> base
        base is DreamShader2Type.Material -> materialAttributeType(member)
        base is DreamShader2Type.StructType ->
            structFields(base.name).firstOrNull { it.fieldName() == member }
                ?.let { DreamShader2Type.fromSpelling(it.typeName()) }
                ?: DreamShader2Type.Unknown
        else -> DreamShader2Type.Unknown
    }

    private fun swizzleType(base: DreamShader2Type.Vector, member: String): DreamShader2Type {
        if (member.isEmpty()) return DreamShader2Type.Unknown
        val letters = member.lowercase()
        if (letters.any { it !in SWIZZLE_LETTERS }) return DreamShader2Type.Unknown
        return when (letters.length) {
            1 -> DreamShader2Type.Scalar(base.family)
            in 2..4 -> DreamShader2Type.Vector(base.family, letters.length)
            else -> DreamShader2Type.Unknown
        }
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
        return when {
            path.startsWith("Substrate.", ignoreCase = true) -> DreamShader2Type.Substrate
            path.startsWith("UE.", ignoreCase = true) -> catalogReturnType(path) ?: DreamShader2Type.Unknown
            path.endsWith(".sample", ignoreCase = true) -> sampledType(path, element)
            path.equals("lerp", ignoreCase = true) ||
                path.equals("saturate", ignoreCase = true) ||
                path.equals("clamp", ignoreCase = true) ||
                path.equals("abs", ignoreCase = true) -> firstArgumentType(element)
            else -> userFunctionReturnType(path, element)
        }
    }

    /** HLSL operand-typed intrinsics: the result carries the first argument's type. */
    private fun firstArgumentType(call: PsiElement): DreamShader2Type {
        val arguments = call.node.findChildByType(DreamShader2ElementTypes.ARGUMENT_LIST) ?: return DreamShader2Type.Unknown
        val first = dreamShader2MeaningfulChildren(arguments.psi)
            .firstOrNull { it.elementType == DreamShader2ElementTypes.ARGUMENT } ?: return DreamShader2Type.Unknown
        return typeOf(first.psi)
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
        children.forEach { child ->
            if (child.elementType == DreamShaderTokenTypes.OPERATOR) {
                pendingOperator = child.text
                return@forEach
            }
            val operandType = typeOf(child.psi)
            accumulator = when (val op = pendingOperator) {
                null -> operandType
                "&&", "||", "==", "!=", "<", ">", "<=", ">=" -> booleanResult(accumulator, operandType)
                "?", ":" -> accumulator // ternary: keep the accumulated left-hand opinion
                else -> binaryArithmetic(accumulator, operandType, op)
            }
            pendingOperator = null
        }
        return accumulator
    }

    // ------------------------------------------------------------------
    // name resolution
    // ------------------------------------------------------------------

    private fun enclosingFunctionOf(useSite: PsiElement): DreamShader2FunctionDeclaration? =
        PsiTreeUtil.getParentOfType(useSite, DreamShader2FunctionDeclaration::class.java)

    /** Locals declared textually before [useSite] inside [function]'s body (any block depth). */
    private fun localDeclaredTypeIn(
        function: DreamShader2FunctionDeclaration,
        name: String,
        useSite: PsiElement,
    ): DreamShader2Type? {
        val functionRange = function.textRange
        if (!functionRange.contains(useSite.textRange)) return null
        val locals = psiTreeWalk(function).filter {
            it.node.elementType == DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT
        }
        locals.forEach { statement ->
            if (statement.textRange.startOffset < useSite.textRange.startOffset) {
                declaredNamesAndType(statement)?.let { (names, type) ->
                    if (name in names) return type
                }
            }
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
        analysis.functionByName(path)?.let { function ->
            function.returnTypeText()?.let { return DreamShader2Type.fromSpelling(it) }
        }
        workspace?.exportedFunctions(sourceFile)?.forEach { entry ->
            if (entry.name == path) {
                entry.returnType?.let { return DreamShader2Type.fromSpelling(it) }
                return DreamShader2Type.Unknown
            }
        }
        return DreamShader2Type.Unknown
    }

    // ------------------------------------------------------------------
    // catalog
    // ------------------------------------------------------------------

    private fun buildCatalogReturnTypes(): Map<String, DreamShader2Type> {
        if (project == null) return emptyMap()
        return DreamShaderMaterialExpressionManifest.catalogEntries(project, null)
            .mapNotNull { entry ->
                val type = when {
                    entry.namespace.equals("Substrate", ignoreCase = true) -> DreamShader2Type.Substrate
                    else -> DreamShader2Type.fromSpelling(entry.outputType)
                }
                if (type == DreamShader2Type.Unknown) null else entry.qualifiedName.lowercase() to type
            }
            .toMap()
    }

    private fun catalogReturnType(path: String): DreamShader2Type? =
        catalogReturnTypes[path.lowercase()]

    companion object {
        /** x/y/z/w, r/g/b/a, s/t/p/q — the three HLSL swizzle sets, any case. */
        private const val SWIZZLE_LETTERS = "xyzwrgbastpq"

        fun forFile(file: DreamShaderPsiFile, workspace: DreamShader2WorkspaceSymbols?): DreamShader2InferenceContext {
            val closure = workspace?.includeClosurePsi(file).orEmpty().filterIsInstance<DreamShaderPsiFile>()
            return DreamShader2InferenceContext(
                project = file.project,
                analysis = DreamShader2FileAnalysis.of(file),
                workspace = workspace,
                sourceFile = file.virtualFile,
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
            val parts = mutableListOf<String>()
            dreamShader2MeaningfulChildren(call).forEach { child ->
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

        private fun booleanResult(
            left: DreamShader2Type,
            right: DreamShader2Type,
        ): DreamShader2Type =
            if (left is DreamShader2Type.Unknown || right is DreamShader2Type.Unknown) {
                DreamShader2Type.Unknown
            } else {
                DreamShader2Type.Scalar(DreamShader2Type.Family.BOOL)
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
