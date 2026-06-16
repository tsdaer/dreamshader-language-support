package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange

/**
 * Singleton for DreamShaderSyntaxDiagnosticsPass.
 */
internal object DreamShaderSyntaxDiagnosticsPass {
    fun annotate(
        sourceText: String,
        tokens: List<DreamShaderLexedToken>,
        holder: AnnotationHolder
    ) {
        annotateUnclosedLiteralDiagnostics(tokens, holder)
        annotateUnmatchedBraceDiagnostics(tokens, sourceText.length, holder)
    }

    private fun annotateUnclosedLiteralDiagnostics(tokens: List<DreamShaderLexedToken>, holder: AnnotationHolder) {
        tokens.forEach { token ->
            if (token.type == DreamShaderTokenTypes.STRING && !token.text.endsWith("\"")) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.unclosedStringLiteral")
                )
                    .range(token.range)
                    .create()
            }
            if (token.type == DreamShaderTokenTypes.BLOCK_COMMENT && !token.text.endsWith("*/")) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.unclosedBlockComment")
                )
                    .range(token.range)
                    .create()
            }
        }
    }

    private fun annotateUnmatchedBraceDiagnostics(
        tokens: List<DreamShaderLexedToken>,
        fileLength: Int,
        holder: AnnotationHolder
    ) {
        val openingBraces = mutableListOf<DreamShaderLexedToken>()
        tokens.forEach { token ->
            when (token.type) {
                DreamShaderTokenTypes.LBRACE -> openingBraces.add(token)
                DreamShaderTokenTypes.RBRACE -> {
                    if (openingBraces.isEmpty()) {
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            DreamShaderBundle.message("diagnostic.unmatchedBrace")
                        )
                            .range(token.range)
                            .create()
                    } else {
                        openingBraces.removeAt(openingBraces.lastIndex)
                    }
                }
            }
        }

        if (openingBraces.isNotEmpty()) {
            val unmatched = openingBraces.last()
            val markerStart = unmatched.range.startOffset.coerceAtLeast(0)
            val markerEnd = (markerStart + 1).coerceAtMost(fileLength)
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.unmatchedBrace")
            )
                .range(TextRange(markerStart, markerEnd))
                .create()
        }
    }

}

