package com.github.tsdaer.dreamshaderlanguagesupport.language.parser
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLanguageKeywords
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Permissive staged parser for DreamShaderLang.
 *
 * Current strategy: reliably mark top-level declarations and direct sections,
 * while allowing partially invalid code to still produce a usable PSI tree for
 * completion, navigation and diagnostics.
 */
class DreamShaderPsiParser : PsiParser {
    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        parseFile(builder)
        while (!builder.eof()) {
            builder.advanceLexer()
        }
        rootMarker.done(root)
        return builder.treeBuilt
    }

    private fun parseFile(builder: PsiBuilder) {
        while (!builder.eof()) {
            if (tryParseDeclaration(builder)) continue
            if (tryParseSection(builder)) continue
            builder.advanceLexer()
        }
    }

    private fun tryParseDeclaration(builder: PsiBuilder): Boolean {
        if (builder.tokenType != DreamShaderTokenTypes.KEYWORD) return false
        val keywordText = builder.tokenText ?: return false
        if (!DreamShaderLanguageKeywords.DECLARATION_KEYWORDS.contains(keywordText.lowercase())) return false

        val marker = builder.mark()
        builder.advanceLexer() // declaration keyword

        var foundBody = false
        while (!builder.eof()) {
            val tokenType = builder.tokenType
            if (tokenType == DreamShaderTokenTypes.LBRACE) {
                parseDeclarationBody(builder)
                foundBody = true
                break
            }
            if (tokenType == DreamShaderTokenTypes.OPERATOR && builder.tokenText == ";") {
                builder.advanceLexer()
                break
            }
            builder.advanceLexer()
        }

        if (!foundBody) {
            // Keep permissive parse in phase-1 and still emit a declaration node.
        }
        marker.done(DreamShaderElementTypes.DECLARATION)
        return true
    }

    private fun parseDeclarationBody(builder: PsiBuilder) {
        if (builder.tokenType != DreamShaderTokenTypes.LBRACE) return
        var depth = 0
        while (!builder.eof()) {
            val tokenType = builder.tokenType
            when (tokenType) {
                DreamShaderTokenTypes.LBRACE -> {
                    depth++
                    builder.advanceLexer()
                }

                DreamShaderTokenTypes.RBRACE -> {
                    depth--
                    builder.advanceLexer()
                    if (depth <= 0) {
                        break
                    }
                }

                DreamShaderTokenTypes.SECTION -> {
                    if (depth == 1) {
                        if (tryParseSection(builder)) {
                            continue
                        }
                    }
                    builder.advanceLexer()
                }

                DreamShaderTokenTypes.KEYWORD -> {
                    if (depth == 1 && tryParseDeclaration(builder)) {
                        continue
                    }
                    builder.advanceLexer()
                }

                else -> builder.advanceLexer()
            }
        }
    }

    private fun tryParseSection(builder: PsiBuilder): Boolean {
        if (builder.tokenType != DreamShaderTokenTypes.SECTION) return false
        val marker = builder.mark()
        builder.advanceLexer() // section keyword

        while (!builder.eof()) {
            val tokenType = builder.tokenType
            if (tokenType == DreamShaderTokenTypes.LBRACE) {
                parseBalancedBraces(builder)
                marker.done(DreamShaderElementTypes.SECTION)
                return true
            }
            if (tokenType == DreamShaderTokenTypes.OPERATOR && builder.tokenText == ";") {
                builder.advanceLexer()
                marker.done(DreamShaderElementTypes.SECTION)
                return true
            }
            if (tokenType == DreamShaderTokenTypes.KEYWORD || tokenType == DreamShaderTokenTypes.SECTION) {
                break
            }
            builder.advanceLexer()
        }

        marker.done(DreamShaderElementTypes.SECTION)
        return true
    }

    private fun parseBalancedBraces(builder: PsiBuilder) {
        var depth = 0
        while (!builder.eof()) {
            val tokenType = builder.tokenType
            if (tokenType == DreamShaderTokenTypes.LBRACE) depth++
            if (tokenType == DreamShaderTokenTypes.RBRACE) {
                depth--
                builder.advanceLexer()
                if (depth <= 0) break
                continue
            }
            builder.advanceLexer()
        }
    }
}
