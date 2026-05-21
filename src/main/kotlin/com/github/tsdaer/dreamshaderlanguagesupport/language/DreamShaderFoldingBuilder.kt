package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import java.util.Locale

class DreamShaderFoldingBuilder : FoldingBuilderEx(), DumbAware {
    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val astRoot = root.node ?: return emptyArray()
        return buildDescriptors(astRoot, astRoot.text)
    }

    override fun buildFoldRegions(node: ASTNode, document: Document): Array<FoldingDescriptor> {
        return buildDescriptors(node, node.text)
    }

    private fun buildDescriptors(root: ASTNode, text: String): Array<FoldingDescriptor> {
        if (text.isEmpty()) return emptyArray()

        val descriptors = mutableListOf<FoldingDescriptor>()
        collectBraceFoldRegions(root, text, descriptors)
        collectRegionFoldRegions(root, text, descriptors)
        return descriptors.toTypedArray()
    }

    override fun getPlaceholderText(node: ASTNode): String = "..."

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    private fun collectBraceFoldRegions(
        root: ASTNode,
        text: String,
        out: MutableList<FoldingDescriptor>
    ) {
        val stack = ArrayDeque<Int>()
        val lexer = DreamShaderLexer()
        lexer.start(text, 0, text.length, 0)
        while (lexer.tokenType != null) {
            when (lexer.tokenType) {
                DreamShaderTokenTypes.LBRACE -> stack.addLast(lexer.tokenStart)
                DreamShaderTokenTypes.RBRACE -> {
                    val left = stack.removeLastOrNull()
                    if (left != null) {
                        val rightExclusive = lexer.tokenEnd
                        val range = TextRange(left, rightExclusive)
                        if (isMultiline(text, range)) {
                            out.add(FoldingDescriptor(root, range, null, "{...}"))
                        }
                    }
                }
            }
            lexer.advance()
        }
    }

    private fun collectRegionFoldRegions(
        root: ASTNode,
        text: String,
        out: MutableList<FoldingDescriptor>
    ) {
        data class RegionStart(val offset: Int, val placeholder: String)

        val starts = ArrayDeque<RegionStart>()
        var lineStart = 0
        while (lineStart < text.length) {
            val lineEnd = findLineEnd(text, lineStart)
            val rawLine = text.substring(lineStart, lineEnd)
            val trimmed = rawLine.trimStart()
            val lowered = trimmed.lowercase(Locale.ROOT)

            if (lowered.startsWith("// region")) {
                val suffix = trimmed.substring("// region".length).trim()
                val placeholder = if (suffix.isEmpty()) "// region ..." else "// region $suffix"
                starts.addLast(RegionStart(lineStart, placeholder))
            } else if (lowered.startsWith("// endregion")) {
                val start = starts.removeLastOrNull()
                if (start != null) {
                    val rangeEnd = includeLineBreak(text, lineEnd)
                    val range = TextRange(start.offset, rangeEnd)
                    if (isMultiline(text, range)) {
                        out.add(FoldingDescriptor(root, range, null, start.placeholder))
                    }
                }
            }

            lineStart = nextLineStart(text, lineEnd)
        }
    }

    private fun findLineEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && text[i] != '\n' && text[i] != '\r') {
            i++
        }
        return i
    }

    private fun includeLineBreak(text: String, lineEnd: Int): Int {
        var i = lineEnd
        if (i < text.length && text[i] == '\r') i++
        if (i < text.length && text[i] == '\n') i++
        return i
    }

    private fun nextLineStart(text: String, lineEnd: Int): Int {
        if (lineEnd >= text.length) return text.length
        if (text[lineEnd] == '\r') {
            return if (lineEnd + 1 < text.length && text[lineEnd + 1] == '\n') lineEnd + 2 else lineEnd + 1
        }
        return lineEnd + 1
    }

    private fun isMultiline(text: String, range: TextRange): Boolean {
        if (range.length < 2) return false
        val fragment = text.substring(range.startOffset, range.endOffset)
        return fragment.contains('\n') || fragment.contains('\r')
    }
}
