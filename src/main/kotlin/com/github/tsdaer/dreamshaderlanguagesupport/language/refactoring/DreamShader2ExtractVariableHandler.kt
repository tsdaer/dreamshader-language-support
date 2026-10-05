package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2InferenceContext
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2Type
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCompletionDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.codeInsight.template.TemplateBuilderImpl
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.TextExpression
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.refactoring.RefactoringActionHandler
import com.intellij.refactoring.util.CommonRefactoringUtil

/**
 * Extract Variable for the 2.0 dialect (plan C20): the selected (or caret-adjacent) expression
 * becomes a local declaration inserted before the enclosing statement, and the occurrence
 * becomes an in-place rename template so the suggested name is editable with the declaration
 * and the use site linked.
 *
 * Conservative edges, matching the plugin's "never guess" contract: the new declaration's type
 * spelling must come from a confidently inferred type (C17) or an explicit constructor/cast
 * spelling — otherwise the refactoring refuses with a hint instead of emitting a wrong type;
 * statements inside `if` arms and loop bodies stay untouched (hoisting out of those would
 * change evaluation semantics).
 */
internal class DreamShader2ExtractVariableHandler : RefactoringActionHandler {

    override fun invoke(project: Project, editor: Editor, file: PsiFile, dataContext: DataContext?) {
        if (file !is DreamShaderPsiFile) return
        if (!DreamShaderCompletionDialects.servesV2(file)) return

        val expression = extractableExpression(file, editor)
        if (expression == null) {
            showHint(project, editor, "refactoring.extractVariable.noExpression")
            return
        }

        val statement = enclosingStatement(expression)
        if (statement == null) {
            showHint(project, editor, "refactoring.extractVariable.unsupportedContext")
            return
        }

        val workspace = project.getService(DreamShader2WorkspaceSymbols::class.java)
        val inference = DreamShader2InferenceContext.forFile(file, workspace)
        val spelling = declarationTypeSpelling(inference, expression)
        if (spelling == null) {
            showHint(project, editor, "refactoring.extractVariable.unknownType")
            return
        }
        val name = suggestedName(expression, inference)

        runRefactoring(project, editor, file, statement, expression, spelling, name)
    }

    override fun invoke(project: Project, elements: Array<out PsiElement>, dataContext: DataContext?) = Unit

    // ------------------------------------------------------------------
    // target expression
    // ------------------------------------------------------------------

    private fun extractableExpression(file: DreamShaderPsiFile, editor: Editor): PsiElement? {
        val selection = editor.selectionModel
        return if (selection.hasSelection()) {
            coveringExpression(file, selection.selectionStart, selection.selectionEnd)
        } else {
            val caret = editor.caretModel.offset
            expressionEndingAt(file, caret)
        }
    }

    /** The expression-shaped node exactly covering [start] until [end]; null when none matches. */
    private fun coveringExpression(file: DreamShaderPsiFile, start: Int, end: Int): PsiElement? {
        var safeStart = start
        var safeEnd = end
        val text = file.text
        while (safeStart < safeEnd && text[safeStart].isWhitespace()) safeStart++
        while (safeEnd > safeStart && text[safeEnd - 1].isWhitespace()) safeEnd--
        val target = TextRange(safeStart, safeEnd)
        val leaf = file.findElementAt(safeStart) ?: return null
        var current: PsiElement? = leaf
        while (current != null) {
            if (current.textRange == target && current.node?.elementType in EXPRESSION_NODE_TYPES) return current
            if (current.textRange.startOffset < safeStart) return null
            current = current.parent
        }
        return null
    }

    /** The outermost expression-shaped node ending exactly at [offset] (caret gesture). */
    private fun expressionEndingAt(file: DreamShaderPsiFile, offset: Int): PsiElement? {
        val leaf = file.findElementAt((offset - 1).coerceAtLeast(0)) ?: return null
        var best: PsiElement? = null
        var current: PsiElement? = leaf
        while (current != null) {
            if (current.textRange.endOffset > offset) break
            if (current.node?.elementType in EXPRESSION_NODE_TYPES) best = current
            current = current.parent
        }
        return best
    }

    // ------------------------------------------------------------------
    // context validation
    // ------------------------------------------------------------------

    /**
     * The innermost statement ancestor usable as the insertion anchor, or null when the
     * context must not be refactored: outside any statement, or inside an `if` arm / loop
     * body (hoisting the value out of those changes when it evaluates).
     */
    private fun enclosingStatement(expression: PsiElement): PsiElement? {
        var anchor: PsiElement? = null
        var current: PsiElement? = expression.parent
        while (current != null) {
            val type = current.node?.elementType
            when {
                type == DreamShader2ElementTypes.FUNCTION_DECLARATION -> return anchor
                type in STATEMENT_TYPES -> {
                    when (type) {
                        DreamShader2ElementTypes.FOR_STATEMENT,
                        DreamShader2ElementTypes.WHILE_STATEMENT,
                        DreamShader2ElementTypes.DO_WHILE_STATEMENT,
                        -> return null
                        DreamShader2ElementTypes.IF_STATEMENT -> {
                            val armStart = current.node.findChildByType(DreamShaderTokenTypes.RPAREN) ?: return null
                            if (expression.textRange.endOffset > armStart.startOffset) return null
                        }
                    }
                    if (anchor == null) anchor = current
                }
            }
            current = current.parent
        }
        return anchor
    }

    // ------------------------------------------------------------------
    // declaration text
    // ------------------------------------------------------------------

    /**
     * The type spelling for the new declaration: the inferred type when confident, else the
     * explicit spelling of a constructor/cast; null when the type is not knowable.
     */
    private fun declarationTypeSpelling(inference: DreamShader2InferenceContext, expression: PsiElement): String? {
        when (val type = inference.typeOf(expression)) {
            is DreamShader2Type.Scalar -> return when (type.family) {
                DreamShader2Type.Family.FLOAT -> "float"
                DreamShader2Type.Family.HALF -> "half"
                DreamShader2Type.Family.INT -> "int"
                DreamShader2Type.Family.UINT -> "uint"
                DreamShader2Type.Family.BOOL -> "bool"
            }
            is DreamShader2Type.Vector -> return "${type.family.spelling}${type.size}"
            is DreamShader2Type.Matrix -> return "${type.family.spelling}${type.rows}x${type.cols}"
            is DreamShader2Type.Texture -> return type.kind
            DreamShader2Type.Sampler -> return "SamplerState"
            DreamShader2Type.Material -> return "material"
            DreamShader2Type.Substrate -> return "Substrate"
            is DreamShader2Type.StructType -> return type.name
            else -> Unit
        }
        val explicitType = expression.node?.findChildByType(DreamShaderTokenTypes.TYPE)?.text
        return explicitType?.takeIf { it.isNotBlank() }
    }

    /** Decapitalized last identifier of the expression, de-duplicated against visible names. */
    private fun suggestedName(expression: PsiElement, inference: DreamShader2InferenceContext): String {
        val identifiers = IDENTIFIER_PATTERN.findAll(expression.text).toList()
        val raw = identifiers.lastOrNull()?.value ?: "value"
        val base = when {
            raw.length > 1 && raw.all { it.isUpperCase() } -> raw.lowercase()
            else -> raw.decapitalize()
        }.takeIf { it.isNotBlank() && it.all { ch -> ch.isLetterOrDigit() || ch == '_' } } ?: "value"
        var candidate = base
        var suffix = 2
        while (inference.typeOfName(candidate, expression) != DreamShader2Type.Unknown) {
            candidate = "$base$suffix"
            suffix++
        }
        return candidate
    }

    // ------------------------------------------------------------------
    // the write
    // ------------------------------------------------------------------

    private fun runRefactoring(
        project: Project,
        editor: Editor,
        file: DreamShaderPsiFile,
        statement: PsiElement,
        expression: PsiElement,
        spelling: String,
        name: String,
    ) {
        val document = editor.document
        WriteCommandAction.runWriteCommandAction(
            project,
            DreamShaderBundle.message("refactoring.extractVariable.title"),
            null,
            Runnable {
                val indent = lineIndentOf(document, statement.textRange.startOffset)
                val inserted = "$spelling $name = ${expression.text};\n$indent"
                val statementStart = statement.textRange.startOffset
                document.insertString(statementStart, inserted)

                val usageStart = expression.textRange.startOffset + inserted.length
                document.replaceString(usageStart, expression.textRange.endOffset + inserted.length, name)
                PsiDocumentManager.getInstance(project).commitDocument(document)

                val declarationNameOffset = statementStart + spelling.length + 1
                val declarationLeaf = identifierLeafAt(file, declarationNameOffset) ?: return@Runnable
                val usageLeaf = identifierLeafAt(file, usageStart) ?: return@Runnable

                val builder = TemplateBuilderImpl(file)
                builder.replaceElement(declarationLeaf, NAME_VARIABLE, TextExpression(name), true)
                builder.replaceElement(usageLeaf, NAME_VARIABLE, TextExpression(name), true)
                TemplateManager.getInstance(project).startTemplate(editor, builder.buildTemplate())
            },
        )
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
            DreamShaderBundle.message("refactoring.extractVariable.title"),
            null,
        )
    }

    companion object {
        private const val NAME_VARIABLE = "ExtractedName"

        private val IDENTIFIER_PATTERN = Regex("[A-Za-z_][A-Za-z0-9_]*")

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

        private val EXPRESSION_NODE_TYPES = setOf(
            DreamShader2ElementTypes.EXPRESSION,
            DreamShader2ElementTypes.LITERAL_EXPRESSION,
            DreamShader2ElementTypes.REFERENCE_EXPRESSION,
            DreamShader2ElementTypes.CONSTRUCTOR_EXPRESSION,
            DreamShader2ElementTypes.CAST_EXPRESSION,
            DreamShader2ElementTypes.PARENTHESIZED_EXPRESSION,
            DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION,
            DreamShader2ElementTypes.INDEX_EXPRESSION,
            DreamShader2ElementTypes.CALL_EXPRESSION,
        )
    }
}
