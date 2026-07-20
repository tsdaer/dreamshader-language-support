package com.github.tsdaer.dreamshaderlanguagesupport.language.parser
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
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
        val lowered = keywordText.lowercase()
        if (!DreamShaderLanguageKeywords.DECLARATION_KEYWORDS.contains(lowered)) return false

        val marker = builder.mark()
        builder.advanceLexer() // declaration keyword
        val recoveryMode = validateDeclarationHeader(builder, lowered)

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
            if (recoveryMode == HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY &&
                (tokenType == DreamShaderTokenTypes.KEYWORD ||
                    tokenType == DreamShaderTokenTypes.SECTION ||
                    tokenType == DreamShaderTokenTypes.RBRACE)
            ) {
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
        val recoveryMode = validateSectionHeader(builder)

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
            if (recoveryMode == HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY &&
                (tokenType == DreamShaderTokenTypes.KEYWORD ||
                    tokenType == DreamShaderTokenTypes.SECTION ||
                    tokenType == DreamShaderTokenTypes.RBRACE)
            ) {
                break
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

    private fun validateDeclarationHeader(builder: PsiBuilder, keywordText: String): HeaderRecoveryMode {
        skipTrivia(builder)
        val tokenType = builder.tokenType
        return when (tokenType) {
            null -> {
                builder.error(DreamShaderBundle.message("diagnostic.malformedDeclarationExpectedNameOrArgs"))
                HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY
            }
            DreamShaderTokenTypes.IDENTIFIER,
            DreamShaderTokenTypes.LPAREN -> HeaderRecoveryMode.NONE
            DreamShaderTokenTypes.TYPE -> {
                if (keywordText in DreamShaderLanguageKeywords.FUNCTION_LIKE_DECLARATION_KEYWORDS) {
                    HeaderRecoveryMode.NONE
                } else {
                    builder.error(DreamShaderBundle.message("diagnostic.malformedDeclarationExpectedNameOrArgs"))
                    HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY
                }
            }
            DreamShaderTokenTypes.LBRACE -> {
                builder.error(DreamShaderBundle.message("diagnostic.malformedDeclarationExpectedNameOrArgs"))
                HeaderRecoveryMode.NONE
            }
            else -> {
                builder.error(DreamShaderBundle.message("diagnostic.malformedDeclarationExpectedNameOrArgs"))
                HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY
            }
        }
    }

    private fun validateSectionHeader(builder: PsiBuilder): HeaderRecoveryMode {
        skipTrivia(builder)
        val tokenType = builder.tokenType
        return when {
            tokenType == null -> {
                builder.error(DreamShaderBundle.message("diagnostic.malformedSectionExpectedLBrace"))
                HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY
            }
            tokenType == DreamShaderTokenTypes.LBRACE -> HeaderRecoveryMode.NONE
            tokenType == DreamShaderTokenTypes.OPERATOR && builder.tokenText == ";" -> HeaderRecoveryMode.NONE
            tokenType == DreamShaderTokenTypes.KEYWORD && builder.tokenText?.lowercase() in setOf("group", "propgroup") -> {
                builder.advanceLexer() // group keyword
                skipTrivia(builder)
                if (builder.tokenType == DreamShaderTokenTypes.LPAREN) {
                    builder.advanceLexer() // (
                    skipTrivia(builder)
                    if (builder.tokenType == DreamShaderTokenTypes.STRING) {
                        builder.advanceLexer() // "Name"
                        skipTrivia(builder)
                    }
                    if (builder.tokenType == DreamShaderTokenTypes.RPAREN) {
                        builder.advanceLexer() // )
                    }
                }
                skipTrivia(builder)
                if (builder.tokenType == DreamShaderTokenTypes.LBRACE) {
                    HeaderRecoveryMode.NONE
                } else {
                    builder.error(DreamShaderBundle.message("diagnostic.malformedSectionExpectedLBrace"))
                    HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY
                }
            }
            tokenType == DreamShaderTokenTypes.OPERATOR && builder.tokenText == "=" -> {
                builder.advanceLexer()
                skipTrivia(builder)
                if (builder.tokenType == DreamShaderTokenTypes.LBRACE) {
                    HeaderRecoveryMode.NONE
                } else {
                    builder.error(DreamShaderBundle.message("diagnostic.malformedSectionExpectedLBrace"))
                    HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY
                }
            }
            else -> {
                builder.error(DreamShaderBundle.message("diagnostic.malformedSectionExpectedLBrace"))
                HeaderRecoveryMode.STOP_AT_DECLARATION_BOUNDARY
            }
        }
    }

    private fun skipTrivia(builder: PsiBuilder) {
        while (isTrivia(builder.tokenType)) {
            builder.advanceLexer()
        }
    }

    private fun isTrivia(tokenType: IElementType?): Boolean {
        return tokenType == DreamShaderTokenTypes.WHITE_SPACE ||
            tokenType == DreamShaderTokenTypes.LINE_COMMENT ||
            tokenType == DreamShaderTokenTypes.BLOCK_COMMENT
    }

    private enum class HeaderRecoveryMode {
        NONE,
        STOP_AT_DECLARATION_BOUNDARY
    }
}
