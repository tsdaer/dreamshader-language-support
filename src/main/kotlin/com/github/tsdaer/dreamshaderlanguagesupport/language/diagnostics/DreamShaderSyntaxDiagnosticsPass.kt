package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLanguageKeywords
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import java.util.*

/**
 * $name 单例对象。
 */
internal object DreamShaderSyntaxDiagnosticsPass {
    fun annotate(
        sourceText: String,
        tokens: List<DreamShaderLexedToken>,
        holder: AnnotationHolder
    ) {
        annotateUnclosedLiteralDiagnostics(tokens, holder)
        annotateUnmatchedBraceDiagnostics(tokens, sourceText.length, holder)
        annotateMalformedTopLevelDeclarationDiagnostics(tokens, holder)
        annotateMalformedSectionDiagnostics(tokens, holder)
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

    private fun annotateMalformedTopLevelDeclarationDiagnostics(tokens: List<DreamShaderLexedToken>, holder: AnnotationHolder) {
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.depthBefore != 0) return@forEach
            if (token.type != DreamShaderTokenTypes.KEYWORD) return@forEach
            val keyword = token.text.lowercase(Locale.ROOT)
            if (keyword !in DreamShaderLanguageKeywords.DECLARATION_KEYWORDS) return@forEach

            val next = nextSignificantToken(tokens, index)
            val malformed = next == null ||
                next.type == DreamShaderTokenTypes.LBRACE ||
                (next.type != DreamShaderTokenTypes.IDENTIFIER && next.type != DreamShaderTokenTypes.LPAREN)
            if (malformed) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.malformedDeclarationExpectedNameOrArgs")
                ).range(token.range).create()
            }
        }
    }

    private fun annotateMalformedSectionDiagnostics(tokens: List<DreamShaderLexedToken>, holder: AnnotationHolder) {
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.depthBefore != 1) return@forEach
            if (token.type != DreamShaderTokenTypes.SECTION) return@forEach

            val next = nextSignificantToken(tokens, index)
            val valid = when {
                next == null -> false
                next.type == DreamShaderTokenTypes.LBRACE -> true
                next.type == DreamShaderTokenTypes.OPERATOR && next.text == ";" -> true
                next.type == DreamShaderTokenTypes.OPERATOR && next.text == "=" -> {
                    val equalsIndex = nextSignificantTokenIndex(tokens, index)
                    val afterEquals = if (equalsIndex != null) nextSignificantToken(tokens, equalsIndex) else null
                    afterEquals?.type == DreamShaderTokenTypes.LBRACE
                }
                else -> false
            }
            if (!valid) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.malformedSectionExpectedLBrace")
                )
                    .range(token.range)
                    .create()
            }
        }
    }

    private fun nextSignificantTokenIndex(tokens: List<DreamShaderLexedToken>, index: Int): Int? {
        var i = index + 1
        while (i < tokens.size) {
            if (!tokens[i].isTrivia) return i
            i++
        }
        return null
    }

    private fun nextSignificantToken(tokens: List<DreamShaderLexedToken>, index: Int): DreamShaderLexedToken? {
        var i = index + 1
        while (i < tokens.size) {
            val token = tokens[i]
            if (!token.isTrivia) return token
            i++
        }
        return null
    }
}

