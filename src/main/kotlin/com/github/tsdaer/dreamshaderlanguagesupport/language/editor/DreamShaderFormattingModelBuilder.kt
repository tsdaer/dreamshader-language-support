package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.formatting.*
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.TokenType
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.CommonCodeStyleSettings
import com.intellij.psi.formatter.FormatterUtil
import com.intellij.psi.formatter.common.AbstractBlock
import java.util.*

/**
 * Builder implementation for DreamShaderFormattingModelBuilder.
 */
class DreamShaderFormattingModelBuilder : FormattingModelBuilder {
    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val psiFile = formattingContext.containingFile
        val settings = formattingContext.codeStyleSettings
        val root = DreamShaderFormattingBlock(
            node = psiFile.node,
            settings = settings,
            indent = Indent.getNoneIndent()
        )
        return FormattingModelProvider.createFormattingModelForPsiFile(psiFile, root, settings)
    }

    override fun getRangeAffectingIndent(
        file: com.intellij.psi.PsiFile,
        offset: Int,
        elementAtOffset: ASTNode
    ): TextRange? = null
}

/**
 * Implementation of DreamShaderFormattingBlock.
 */
private class DreamShaderFormattingBlock(
    node: ASTNode,
    private val settings: CodeStyleSettings,
    private val indent: Indent?
) : AbstractBlock(node, Wrap.createWrap(WrapType.NONE, false), Alignment.createAlignment()) {
    private val common: CommonCodeStyleSettings = settings.getCommonSettings(DreamShaderLanguage)

    override fun buildChildren(): List<Block> {
        val result = ArrayList<Block>()
        var child = myNode.firstChildNode
        var braceDepth = 0
        while (child != null) {
            if (!FormatterUtil.containsWhiteSpacesOnly(child)) {
                val childIndent = when {
                    child.elementType == DreamShaderTokenTypes.RBRACE -> Indent.getNoneIndent()
                    braceDepth > 0 -> Indent.getNormalIndent()
                    else -> Indent.getNoneIndent()
                }
                result.add(
                    DreamShaderFormattingBlock(
                        node = child,
                        settings = settings,
                        indent = childIndent
                    )
                )
                if (child.elementType == DreamShaderTokenTypes.LBRACE) {
                    braceDepth++
                } else if (child.elementType == DreamShaderTokenTypes.RBRACE && braceDepth > 0) {
                    braceDepth--
                }
            }
            child = child.treeNext
        }
        return result
    }

    override fun getIndent(): Indent? = indent

    override fun getSpacing(child1: Block?, child2: Block): Spacing? {
        val left = (child1 as? AbstractBlock)?.node
        val right = (child2 as? AbstractBlock)?.node ?: return null
        if (left == null) return null

        val leftType = left.elementType
        val rightType = right.elementType
        val rightText = right.text

        if (leftType == TokenType.WHITE_SPACE || rightType == TokenType.WHITE_SPACE) return null

        if (rightType == DreamShaderTokenTypes.RBRACE) {
            return lineBreak()
        }

        if (leftType == DreamShaderElementTypes.SECTION && rightType == DreamShaderElementTypes.SECTION) {
            return lineBreak()
        }

        if (leftType == DreamShaderElementTypes.SECTION && rightType == DreamShaderTokenTypes.RBRACE) {
            return lineBreak()
        }

        if (leftType == DreamShaderTokenTypes.LBRACE && rightType == DreamShaderElementTypes.SECTION) {
            return lineBreak()
        }

        if (leftType == DreamShaderTokenTypes.LBRACE) {
            return lineBreak()
        }

        if (leftType == DreamShaderTokenTypes.RBRACE) {
            return lineBreak()
        }

        if (leftType == DreamShaderTokenTypes.OPERATOR && left.text == ";") {
            return lineBreak()
        }

        if (rightType == DreamShaderTokenTypes.OPERATOR && rightText == ";") {
            return spaces(0)
        }

        if (rightType == DreamShaderTokenTypes.OPERATOR && rightText == ",") {
            return spaces(if (common.SPACE_BEFORE_COMMA) 1 else 0)
        }

        if (leftType == DreamShaderTokenTypes.OPERATOR && left.text == ",") {
            return spaces(if (common.SPACE_AFTER_COMMA) 1 else 0)
        }

        if (leftType == DreamShaderTokenTypes.OPERATOR && left.text == ":") {
            return spaces(if (common.SPACE_AFTER_COLON) 1 else 0)
        }

        if (rightType == DreamShaderTokenTypes.OPERATOR && rightText == ":") {
            return spaces(if (common.SPACE_BEFORE_COLON) 1 else 0)
        }

        if (rightType == DreamShaderTokenTypes.LPAREN) {
            return spaces(if (spaceBeforeLeftParen(left)) 1 else 0)
        }

        if (leftType == DreamShaderTokenTypes.LPAREN && rightType == DreamShaderTokenTypes.RPAREN) {
            return spaces(if (common.SPACE_WITHIN_PARENTHESES) 1 else 0)
        }

        if (leftType == DreamShaderTokenTypes.LPAREN) {
            return spaces(if (common.SPACE_WITHIN_PARENTHESES) 1 else 0)
        }

        if (rightType == DreamShaderTokenTypes.RPAREN) {
            return spaces(if (common.SPACE_WITHIN_PARENTHESES) 1 else 0)
        }

        if (leftType == DreamShaderTokenTypes.OPERATOR && left.text == ".") {
            return spaces(0)
        }

        if (rightType == DreamShaderTokenTypes.OPERATOR && rightText == ".") {
            return spaces(0)
        }

        if (leftType == DreamShaderTokenTypes.OPERATOR && isAssignmentOperator(left.text)) {
            return spaces(if (common.SPACE_AROUND_ASSIGNMENT_OPERATORS) 1 else 0)
        }

        if (rightType == DreamShaderTokenTypes.OPERATOR && isAssignmentOperator(rightText)) {
            return spaces(if (common.SPACE_AROUND_ASSIGNMENT_OPERATORS) 1 else 0)
        }

        if (leftType == DreamShaderTokenTypes.OPERATOR && isBinaryOperator(left.text)) {
            return spaces(if (spaceAroundBinaryOperator(left.text)) 1 else 0)
        }

        if (rightType == DreamShaderTokenTypes.OPERATOR && isBinaryOperator(rightText)) {
            return spaces(if (spaceAroundBinaryOperator(rightText)) 1 else 0)
        }

        return spaces(1)
    }

    override fun getChildAttributes(newChildIndex: Int): ChildAttributes {
        val indent = if (myNode.elementType == DreamShaderTokenTypes.LBRACE) {
            Indent.getNormalIndent()
        } else {
            Indent.getNoneIndent()
        }
        return ChildAttributes(indent, null)
    }

    override fun isLeaf(): Boolean = myNode.firstChildNode == null

    private fun spaces(count: Int): Spacing {
        return Spacing.createSpacing(
            count,
            count,
            0,
            common.KEEP_LINE_BREAKS,
            common.KEEP_BLANK_LINES_IN_CODE
        )
    }

    private fun lineBreak(): Spacing {
        return Spacing.createSpacing(0, 0, 1, common.KEEP_LINE_BREAKS, common.KEEP_BLANK_LINES_IN_CODE)
    }

    private fun spaceBeforeLeftParen(left: ASTNode): Boolean {
        return when (left.elementType) {
            DreamShaderTokenTypes.KEYWORD -> {
                when (left.text.lowercase(Locale.ROOT)) {
                    "if" -> common.SPACE_BEFORE_IF_PARENTHESES
                    "for" -> common.SPACE_BEFORE_FOR_PARENTHESES
                    "while" -> common.SPACE_BEFORE_WHILE_PARENTHESES
                    "switch" -> common.SPACE_BEFORE_SWITCH_PARENTHESES
                    else -> common.SPACE_BEFORE_METHOD_CALL_PARENTHESES
                }
            }
            DreamShaderTokenTypes.IDENTIFIER,
            DreamShaderTokenTypes.TYPE -> common.SPACE_BEFORE_METHOD_CALL_PARENTHESES
            else -> false
        }
    }

    private fun isAssignmentOperator(text: String): Boolean {
        return text == "="
    }

    private fun isBinaryOperator(text: String): Boolean {
        return when (text) {
            "+", "-" -> true
            "*", "/" -> true
            ">", "<", ">=", "<=" -> true
            "==", "!=" -> true
            "&&", "||" -> true
            else -> false
        }
    }

    private fun spaceAroundBinaryOperator(text: String): Boolean {
        return when (text) {
            "+", "-" -> common.SPACE_AROUND_ADDITIVE_OPERATORS
            "*", "/" -> common.SPACE_AROUND_MULTIPLICATIVE_OPERATORS
            ">", "<", ">=", "<=" -> common.SPACE_AROUND_RELATIONAL_OPERATORS
            "==", "!=" -> common.SPACE_AROUND_EQUALITY_OPERATORS
            "&&", "||" -> common.SPACE_AROUND_LOGICAL_OPERATORS
            else -> true
        }
    }
}
