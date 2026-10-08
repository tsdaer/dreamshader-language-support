package com.github.tsdaer.dreamshaderlanguagesupport.language.parser

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Permissive parser for the DreamShader 2.0 dialect.
 *
 * Design constraints (plan A2, upstream `Docs/language-v2/index.md`):
 * - **The plugin is not the compiler.** Semantic correctness is the engine bridge's business, so
 *   this parser emits no error elements; malformed input still yields a usable tree, mirroring the
 *   1.x lenient philosophy.
 * - **`/// @custom` bodies are opaque.** Once a function's doc block carries `@custom`, the body is
 *   captured brace-balanced without interpretation, so `$`, `@`, mid-line `#`, `'` and braces
 *   inside strings or comments cannot break the capture (upstream slices the text verbatim).
 * - **Legacy blocks stay parseable.** A mixed file routes here whenever any V2 marker exists; 1.x
 *   blocks (`Shader(...) { ... }`, `Namespace Tools { ... }`) are captured as opaque LEGACY_BLOCKs.
 * - Expressions use a flat postfix shape: calls, named arguments, member/index chains,
 *   constructors, casts and initializer lists get their own nodes; `?:`, binary operators and
 *   assignments stay flat inside an EXPRESSION node. Precedence-tree fidelity is a later
 *   milestone (C2).
 */
class DreamShader2PsiParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val rootMarker = builder.mark()
        while (!builder.eof()) {
            parseTopLevel(builder)
        }
        rootMarker.done(root)
        return builder.treeBuilt
    }

    // ------------------------------------------------------------------
    // top level
    // ------------------------------------------------------------------

    private fun parseTopLevel(builder: PsiBuilder) {
        when (builder.tokenType) {
            DreamShaderTokenTypes.DOC_COMMENT -> parseDocumentedDeclaration(builder)
            DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE -> parseDirective(builder)
            DreamShaderTokenTypes.KEYWORD -> parseKeywordDeclaration(builder, customDoc = false)
            DreamShaderTokenTypes.TYPE, DreamShaderTokenTypes.IDENTIFIER ->
                parseGlobalOrFunction(builder, customDoc = false)
            DreamShaderTokenTypes.SEMICOLON -> {
                val marker = builder.mark()
                builder.advanceLexer()
                marker.done(DreamShader2ElementTypes.EMPTY_STATEMENT)
            }
            else -> builder.advanceLexer()
        }
    }

    /**
     * `///` doc runs plus the declaration they document. Blank lines between `///` lines do not
     * end the block upstream, so consecutive runs are kept as sibling DOC_BLOCKs inside the same
     * declaration node. A block with nothing under it (an orphan, upstream DSH3221 territory) is
     * closed as standalone DOC_BLOCK nodes.
     */
    private fun parseDocumentedDeclaration(builder: PsiBuilder) {
        val outer = builder.mark()
        var customDoc = false
        while (builder.tokenType == DreamShaderTokenTypes.DOC_COMMENT) {
            customDoc = customDoc or parseDocRun(builder)
        }
        when (builder.tokenType) {
            DreamShaderTokenTypes.KEYWORD -> parseKeywordDeclaration(builder, customDoc, outer)
            DreamShaderTokenTypes.TYPE, DreamShaderTokenTypes.IDENTIFIER ->
                parseGlobalOrFunction(builder, customDoc, outer)
            else -> outer.done(DreamShader2ElementTypes.DOC_BLOCK)
        }
    }

    /** Consumes one run of consecutive `///` lines into a DOC_BLOCK node; returns `@custom` presence. */
    private fun parseDocRun(builder: PsiBuilder): Boolean {
        var custom = false
        val marker = builder.mark()
        while (builder.tokenType == DreamShaderTokenTypes.DOC_COMMENT) {
            if (builder.tokenText?.contains(CUSTOM_DIRECTIVE) == true) {
                custom = true
            }
            builder.advanceLexer()
        }
        marker.done(DreamShader2ElementTypes.DOC_BLOCK)
        return custom
    }

    private fun parseKeywordDeclaration(
        builder: PsiBuilder,
        customDoc: Boolean,
        enclosing: PsiBuilder.Marker? = null,
    ) {
        val text = builder.tokenText?.toString() ?: run {
            enclosing?.done(DreamShader2ElementTypes.DOC_BLOCK)
            builder.advanceLexer()
            return
        }
        when (text) {
            "uniform" -> parseUniform(builder, enclosing)
            "static", "const" -> parseGlobalOrFunction(builder, customDoc, enclosing, leadingModifier = text)
            "struct" -> parseStruct(builder, enclosing)
            "export", "extern" -> parseFunction(builder, customDoc, enclosing, modifier = text)
            "import" -> parseInclude(builder, enclosing)
            else -> {
                enclosing?.done(DreamShader2ElementTypes.DOC_BLOCK)
                parseLegacyBlockOrSkip(builder)
            }
        }
    }

    private fun parseGlobalOrFunction(
        builder: PsiBuilder,
        customDoc: Boolean,
        enclosing: PsiBuilder.Marker? = null,
        leadingModifier: String? = null,
    ) {
        if (looksLikeFunctionSignature(builder, leadingModifier)) {
            parseFunction(builder, customDoc, enclosing, modifier = null, leadingModifier = leadingModifier)
        } else {
            parseGlobalVariable(builder, enclosing, leadingModifier)
        }
    }

    /**
     * Decides function-vs-global by scanning ahead from the current token:
     * `[static|const] (Type|UserType) Name (` is a function signature.
     */
    private fun looksLikeFunctionSignature(builder: PsiBuilder, leadingModifier: String?): Boolean {
        var offset = if (leadingModifier != null) 1 else 0
        var sawType = false
        var sawName = false
        while (true) {
            val type = builder.rawLookup(offset) ?: return false
            when (type) {
                DreamShaderTokenTypes.WHITE_SPACE,
                DreamShaderTokenTypes.LINE_COMMENT,
                DreamShaderTokenTypes.BLOCK_COMMENT,
                DreamShaderTokenTypes.DOC_COMMENT,
                -> offset++
                DreamShaderTokenTypes.KEYWORD -> {
                    val text = rawTokenTextAt(builder, offset)
                    if (text == "static" || text == "const") {
                        offset++
                    } else {
                        return false
                    }
                }
                DreamShaderTokenTypes.TYPE, DreamShaderTokenTypes.IDENTIFIER -> {
                    if (sawType) {
                        if (sawName) return false
                        sawName = true
                    } else {
                        sawType = true
                    }
                    offset++
                }
                DreamShaderTokenTypes.LPAREN -> return sawType && sawName
                else -> return false
            }
        }
    }

    private fun parseUniform(builder: PsiBuilder, enclosing: PsiBuilder.Marker?) {
        val marker = enclosing ?: builder.mark()
        expect(builder, DreamShaderTokenTypes.KEYWORD) // uniform
        if (builder.tokenType == DreamShaderTokenTypes.TYPE || builder.tokenType == DreamShaderTokenTypes.IDENTIFIER) {
            builder.advanceLexer() // declared type (user struct spelling included)
        }
        parseDeclaratorListTail(builder, localNames = true)
        marker.done(DreamShader2ElementTypes.UNIFORM_DECLARATION)
    }

    private fun parseGlobalVariable(
        builder: PsiBuilder,
        enclosing: PsiBuilder.Marker?,
        leadingModifier: String?,
    ) {
        val marker = enclosing ?: builder.mark()
        if (leadingModifier != null) {
            expect(builder, DreamShaderTokenTypes.KEYWORD) // the modifier the caller saw
        }
        while (builder.tokenType == DreamShaderTokenTypes.KEYWORD &&
            (builder.tokenText == "static" || builder.tokenText == "const")
        ) {
            builder.advanceLexer()
        }
        if (builder.tokenType == DreamShaderTokenTypes.TYPE || builder.tokenType == DreamShaderTokenTypes.IDENTIFIER) {
            builder.advanceLexer() // type (user struct spelling included)
        }
        parseDeclaratorListTail(builder, localNames = true)
        marker.done(DreamShader2ElementTypes.GLOBAL_DECLARATION)
    }

    /** `Name (= expr)? (, Name (= expr)?)* ;` starting at the first declarator name. */
    private fun parseDeclaratorListTail(builder: PsiBuilder, localNames: Boolean = false) {
        var first = true
        while (!builder.eof()) {
            skipTrivia(builder)
            if (builder.tokenType == DreamShaderTokenTypes.IDENTIFIER ||
                builder.tokenType == DreamShaderTokenTypes.KEYWORD
            ) {
                val name = if (localNames && !first) builder.mark() else null
                builder.advanceLexer() // declarator name (a keyword used as a name stays permissive)
                name?.done(DreamShader2ElementTypes.LOCAL_DECLARATOR_NAME)
                first = false
            }
            parseArrayDimensions(builder)
            skipTrivia(builder)
            if (builder.tokenType == DreamShaderTokenTypes.OPERATOR && builder.tokenText == "=") {
                builder.advanceLexer()
                parseInitializerExpression(builder)
                skipTrivia(builder)
            }
            when {
                builder.tokenType == DreamShaderTokenTypes.COMMA -> builder.advanceLexer()
                builder.tokenType == DreamShaderTokenTypes.SEMICOLON -> {
                    builder.advanceLexer()
                    return
                }
                else -> return
            }
        }
    }

    private fun parseArrayDimensions(builder: PsiBuilder) {
        while (builder.tokenType == DreamShaderTokenTypes.LBRACKET) {
            builder.advanceLexer()
            if (builder.tokenType != DreamShaderTokenTypes.RBRACKET) {
                parseExpression(builder)
            }
            if (builder.tokenType == DreamShaderTokenTypes.RBRACKET) {
                builder.advanceLexer()
            } else {
                return
            }
        }
    }

    private fun parseStruct(builder: PsiBuilder, enclosing: PsiBuilder.Marker?) {
        val marker = enclosing ?: builder.mark()
        expect(builder, DreamShaderTokenTypes.KEYWORD) // struct
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.IDENTIFIER) {
            builder.advanceLexer() // struct name
        }
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.LBRACE) {
            builder.advanceLexer()
            while (!builder.eof() && builder.tokenType != DreamShaderTokenTypes.RBRACE) {
                parseStructField(builder)
            }
            expect(builder, DreamShaderTokenTypes.RBRACE)
        }
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.SEMICOLON) {
            builder.advanceLexer()
        }
        marker.done(DreamShader2ElementTypes.STRUCT_DECLARATION)
    }

    private fun parseStructField(builder: PsiBuilder) {
        val marker = builder.mark()
        var customDoc = false
        var sawDoc = false
        while (builder.tokenType == DreamShaderTokenTypes.DOC_COMMENT) {
            sawDoc = true
            customDoc = customDoc or parseDocRun(builder)
        }
        if (sawDoc && builder.tokenType != DreamShaderTokenTypes.TYPE &&
            builder.tokenType != DreamShaderTokenTypes.IDENTIFIER
        ) {
            marker.done(DreamShader2ElementTypes.DOC_BLOCK)
            return
        }
        if (builder.tokenType == DreamShaderTokenTypes.TYPE || builder.tokenType == DreamShaderTokenTypes.IDENTIFIER) {
            builder.advanceLexer() // field type
            skipTrivia(builder)
            if (builder.tokenType == DreamShaderTokenTypes.IDENTIFIER ||
                builder.tokenType == DreamShaderTokenTypes.KEYWORD
            ) {
                builder.advanceLexer() // field name
            }
            parseArrayDimensions(builder)
            skipTrivia(builder)
            if (builder.tokenType == DreamShaderTokenTypes.OPERATOR && builder.tokenText == "=") {
                builder.advanceLexer()
                parseInitializerExpression(builder)
                skipTrivia(builder)
            }
            if (builder.tokenType == DreamShaderTokenTypes.SEMICOLON) {
                builder.advanceLexer()
            }
            marker.done(DreamShader2ElementTypes.STRUCT_FIELD)
        } else {
            // Stray token inside a struct: consume it so the field loop makes progress.
            builder.advanceLexer()
            marker.drop()
        }
    }

    private fun parseFunction(
        builder: PsiBuilder,
        customDoc: Boolean,
        enclosing: PsiBuilder.Marker?,
        modifier: String?,
        leadingModifier: String? = null,
    ) {
        val marker = enclosing ?: builder.mark()
        if (leadingModifier != null) {
            expect(builder, DreamShaderTokenTypes.KEYWORD) // the modifier the caller saw
        }
        while (builder.tokenType == DreamShaderTokenTypes.KEYWORD &&
            (builder.tokenText == "static" || builder.tokenText == "const")
        ) {
            builder.advanceLexer()
        }
        if (modifier != null && builder.tokenType == DreamShaderTokenTypes.KEYWORD &&
            builder.tokenText == modifier
        ) {
            builder.advanceLexer() // export / extern
        }
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.TYPE || builder.tokenType == DreamShaderTokenTypes.IDENTIFIER) {
            builder.advanceLexer() // return type
        }
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.IDENTIFIER ||
            builder.tokenType == DreamShaderTokenTypes.KEYWORD
        ) {
            builder.advanceLexer() // function name
        }
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.LPAREN) {
            parseParameterList(builder)
        }
        skipTrivia(builder)
        when (builder.tokenType) {
            DreamShaderTokenTypes.LBRACE -> {
                if (customDoc) {
                    parseCustomBody(builder)
                } else {
                    parseBlock(builder)
                }
            }
            DreamShaderTokenTypes.SEMICOLON -> builder.advanceLexer()
            else -> {
                // Broken header or bodyless prototype: leave the rest to the enclosing loop
                // instead of swallowing the file.
            }
        }
        marker.done(DreamShader2ElementTypes.FUNCTION_DECLARATION)
    }

    private fun parseParameterList(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.LPAREN)
        while (!builder.eof() && builder.tokenType != DreamShaderTokenTypes.RPAREN) {
            val parameter = builder.mark()
            while (builder.tokenType == DreamShaderTokenTypes.KEYWORD &&
                builder.tokenText in PARAMETER_MODIFIERS
            ) {
                builder.advanceLexer()
            }
            if (builder.tokenType == DreamShaderTokenTypes.TYPE ||
                builder.tokenType == DreamShaderTokenTypes.IDENTIFIER
            ) {
                builder.advanceLexer() // parameter type
            }
            skipTrivia(builder)
            if (builder.tokenType == DreamShaderTokenTypes.IDENTIFIER ||
                builder.tokenType == DreamShaderTokenTypes.KEYWORD
            ) {
                builder.advanceLexer() // parameter name
            }
            parseArrayDimensions(builder)
            skipTrivia(builder)
            if (builder.tokenType == DreamShaderTokenTypes.OPERATOR && builder.tokenText == "=") {
                builder.advanceLexer()
                parseExpression(builder)
            }
            parameter.done(DreamShader2ElementTypes.PARAMETER)
            skipTrivia(builder)
            when (builder.tokenType) {
                DreamShaderTokenTypes.COMMA -> builder.advanceLexer()
                DreamShaderTokenTypes.RPAREN -> Unit
                else -> break
            }
        }
        expect(builder, DreamShaderTokenTypes.RPAREN)
        marker.done(DreamShader2ElementTypes.PARAMETER_LIST)
    }

    // ------------------------------------------------------------------
    // directives
    // ------------------------------------------------------------------

    private fun parseDirective(builder: PsiBuilder) {
        val directiveText = builder.tokenText?.toString() ?: return
        val marker = builder.mark()
        builder.advanceLexer() // #pragma / #include / #if ...
        when {
            directiveText == "#pragma" -> parseRestOfLine(builder, marker, DreamShader2ElementTypes.PRAGMA_DIRECTIVE)
            directiveText == "#include" -> {
                skipTrivia(builder)
                if (builder.tokenType == DreamShaderTokenTypes.STRING) {
                    builder.advanceLexer()
                }
                marker.done(DreamShader2ElementTypes.INCLUDE_DIRECTIVE)
            }
            else -> parseRestOfLine(builder, marker, DreamShader2ElementTypes.PREPROCESSOR_DIRECTIVE)
        }
    }

    private fun parseInclude(builder: PsiBuilder, enclosing: PsiBuilder.Marker?) {
        val marker = enclosing ?: builder.mark()
        expect(builder, DreamShaderTokenTypes.KEYWORD) // import
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.STRING) {
            builder.advanceLexer()
        }
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.SEMICOLON) {
            builder.advanceLexer()
        }
        marker.done(DreamShader2ElementTypes.INCLUDE_DIRECTIVE)
    }

    /**
     * Consumes the rest of the directive's line into the open marker. `#pragma material(...)`
     * keeps its argument tokens as node children so later milestones can mine keys and values.
     *
     * PsiBuilder never surfaces WHITE_SPACE as the current token (whitespace is attached
     * transparently), so the end of the directive line is detected from the *raw* token before
     * the current one.
     */
    private fun parseRestOfLine(builder: PsiBuilder, marker: PsiBuilder.Marker, nodeType: IElementType) {
        while (!builder.eof()) {
            val type = builder.tokenType
            if (type == DreamShaderTokenTypes.LINE_COMMENT || type == DreamShaderTokenTypes.BLOCK_COMMENT) {
                break
            }
            if (type == DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE) {
                break
            }
            if (isPrecededByLineBreak(builder)) {
                break
            }
            builder.advanceLexer()
        }
        marker.done(nodeType)
    }

    /** True when a line break sits between the previous raw token and the current one. */
    private fun isPrecededByLineBreak(builder: PsiBuilder): Boolean {
        val previousStart = builder.rawTokenTypeStart(-1)
        val currentStart = builder.rawTokenTypeStart(0)
        if (previousStart < 0 || currentStart <= previousStart) {
            return false
        }
        val between = builder.originalText.subSequence(previousStart, currentStart)
        for (i in between.indices) {
            val c = between[i]
            if (c == '\n' || c == '\r') {
                return true
            }
        }
        return false
    }

    // ------------------------------------------------------------------
    // opaque captures
    // ------------------------------------------------------------------

    /**
     * 1.x block in a mixed file: `Keyword (args)? [Name] { balanced }` - captured without
     * interpretation so a mixed `.dsh` parses cleanly under the 2.0 front end.
     */
    private fun parseLegacyBlockOrSkip(builder: PsiBuilder) {
        val marker = builder.mark()
        builder.advanceLexer() // block keyword
        var depth = 0
        var sawBrace = false
        while (!builder.eof()) {
            val type = builder.tokenType
            when (type) {
                DreamShaderTokenTypes.LBRACE -> {
                    depth++
                    sawBrace = true
                    builder.advanceLexer()
                }
                DreamShaderTokenTypes.RBRACE -> {
                    depth--
                    builder.advanceLexer()
                    if (depth <= 0 && sawBrace) {
                        marker.done(DreamShader2ElementTypes.LEGACY_BLOCK)
                        return
                    }
                }
                DreamShaderTokenTypes.SEMICOLON -> {
                    if (!sawBrace) {
                        builder.advanceLexer()
                        marker.done(DreamShader2ElementTypes.LEGACY_BLOCK)
                        return
                    }
                    builder.advanceLexer()
                }
                DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE -> {
                    if (depth == 0) {
                        marker.done(DreamShader2ElementTypes.LEGACY_BLOCK)
                        return
                    }
                    builder.advanceLexer()
                }
                else -> builder.advanceLexer()
            }
        }
        marker.done(DreamShader2ElementTypes.LEGACY_BLOCK)
    }

    /**
     * `/// @custom` body: brace-balanced verbatim capture. String, comment and directive tokens
     * carry their braces inside a single token, so token-level counting is capture-safe.
     */
    private fun parseCustomBody(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.LBRACE)
        var depth = 1
        while (!builder.eof() && depth > 0) {
            when (builder.tokenType) {
                DreamShaderTokenTypes.LBRACE -> depth++
                DreamShaderTokenTypes.RBRACE -> depth--
            }
            builder.advanceLexer()
        }
        marker.done(DreamShader2ElementTypes.CUSTOM_BODY)
    }

    // ------------------------------------------------------------------
    // statements
    // ------------------------------------------------------------------

    private fun parseBlock(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.LBRACE)
        while (!builder.eof() && builder.tokenType != DreamShaderTokenTypes.RBRACE) {
            parseStatement(builder)
        }
        expect(builder, DreamShaderTokenTypes.RBRACE)
        marker.done(DreamShader2ElementTypes.BLOCK)
    }

    private fun parseStatement(builder: PsiBuilder) {
        when (builder.tokenType) {
            DreamShaderTokenTypes.LBRACE -> parseBlock(builder)
            DreamShaderTokenTypes.SEMICOLON -> {
                val marker = builder.mark()
                builder.advanceLexer()
                marker.done(DreamShader2ElementTypes.EMPTY_STATEMENT)
            }
            DreamShaderTokenTypes.PREPROCESSOR_DIRECTIVE -> parseDirective(builder)
            DreamShaderTokenTypes.DOC_COMMENT -> {
                val marker = builder.mark()
                while (builder.tokenType == DreamShaderTokenTypes.DOC_COMMENT) {
                    parseDocRun(builder)
                }
                marker.done(DreamShader2ElementTypes.DOC_BLOCK)
            }
            DreamShaderTokenTypes.KEYWORD -> parseKeywordStatement(builder)
            DreamShaderTokenTypes.TYPE, DreamShaderTokenTypes.IDENTIFIER ->
                parseVariableDeclarationOrExpressionStatement(builder)
            else -> {
                if (!parseExpressionStatement(builder)) {
                    // Guarantee progress on garbage.
                    builder.advanceLexer()
                }
            }
        }
    }

    private fun parseKeywordStatement(builder: PsiBuilder) {
        val text = builder.tokenText?.toString() ?: run {
            builder.advanceLexer()
            return
        }
        when (text) {
            "if" -> parseIfStatement(builder)
            "for" -> parseForStatement(builder)
            "while" -> parseWhileStatement(builder)
            "do" -> parseDoWhileStatement(builder)
            "return" -> {
                val marker = builder.mark()
                builder.advanceLexer()
                if (!isStatementTerminator(builder.tokenType)) {
                    parseExpression(builder)
                    skipTrivia(builder)
                }
                expectSemicolon(builder)
                marker.done(DreamShader2ElementTypes.RETURN_STATEMENT)
            }
            "break", "continue", "discard" -> {
                val marker = builder.mark()
                builder.advanceLexer()
                skipTrivia(builder)
                expectSemicolon(builder)
                marker.done(DreamShader2ElementTypes.JUMP_STATEMENT)
            }
            "static", "const" -> parseVariableDeclarationOrExpressionStatement(builder)
            else -> {
                if (!parseExpressionStatement(builder)) {
                    builder.advanceLexer()
                }
            }
        }
    }

    private fun parseIfStatement(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.KEYWORD) // if
        skipTrivia(builder)
        expect(builder, DreamShaderTokenTypes.LPAREN)
        parseExpression(builder)
        expect(builder, DreamShaderTokenTypes.RPAREN)
        skipTrivia(builder)
        parseStatement(builder)
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.KEYWORD && builder.tokenText == "else") {
            builder.advanceLexer()
            skipTrivia(builder)
            parseStatement(builder)
        }
        marker.done(DreamShader2ElementTypes.IF_STATEMENT)
    }

    private fun parseForStatement(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.KEYWORD) // for
        skipTrivia(builder)
        expect(builder, DreamShaderTokenTypes.LPAREN)
        // init: declaration, expression, or empty
        if (builder.tokenType == DreamShaderTokenTypes.SEMICOLON) {
            builder.advanceLexer()
        } else if (builder.tokenType == DreamShaderTokenTypes.TYPE ||
            (builder.tokenType == DreamShaderTokenTypes.KEYWORD &&
                (builder.tokenText == "static" || builder.tokenText == "const"))
        ) {
            parseVariableDeclarationStatement(builder)
        } else {
            parseExpression(builder)
            skipTrivia(builder)
            if (builder.tokenType == DreamShaderTokenTypes.SEMICOLON) {
                builder.advanceLexer()
            }
        }
        // condition: optional
        if (builder.tokenType != DreamShaderTokenTypes.SEMICOLON) {
            parseExpression(builder)
        }
        expectSemicolon(builder)
        // update: optional
        if (builder.tokenType != DreamShaderTokenTypes.RPAREN) {
            parseExpression(builder)
        }
        expect(builder, DreamShaderTokenTypes.RPAREN)
        skipTrivia(builder)
        parseStatement(builder)
        marker.done(DreamShader2ElementTypes.FOR_STATEMENT)
    }

    private fun parseWhileStatement(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.KEYWORD) // while
        skipTrivia(builder)
        expect(builder, DreamShaderTokenTypes.LPAREN)
        parseExpression(builder)
        expect(builder, DreamShaderTokenTypes.RPAREN)
        skipTrivia(builder)
        parseStatement(builder)
        marker.done(DreamShader2ElementTypes.WHILE_STATEMENT)
    }

    private fun parseDoWhileStatement(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.KEYWORD) // do
        skipTrivia(builder)
        parseStatement(builder)
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.KEYWORD && builder.tokenText == "while") {
            builder.advanceLexer()
            skipTrivia(builder)
            expect(builder, DreamShaderTokenTypes.LPAREN)
            parseExpression(builder)
            expect(builder, DreamShaderTokenTypes.RPAREN)
        }
        expectSemicolon(builder)
        marker.done(DreamShader2ElementTypes.DO_WHILE_STATEMENT)
    }

    private fun parseVariableDeclarationOrExpressionStatement(builder: PsiBuilder) {
        if (isVariableDeclarationStart(builder)) {
            parseVariableDeclarationStatement(builder)
        } else {
            if (!parseExpressionStatement(builder)) {
                builder.advanceLexer()
            }
        }
    }

    /** `Type Name ...` / `static ...` / `UserType Name ...` starts a declaration statement. */
    private fun isVariableDeclarationStart(builder: PsiBuilder): Boolean {
        val first = builder.tokenType ?: return false
        if (first == DreamShaderTokenTypes.TYPE) return true
        if (first == DreamShaderTokenTypes.KEYWORD &&
            (builder.tokenText == "static" || builder.tokenText == "const")
        ) {
            return true
        }
        if (first == DreamShaderTokenTypes.IDENTIFIER) {
            return rawMeaningfulLookup(builder, 1) == DreamShaderTokenTypes.IDENTIFIER
        }
        return false
    }

    /** Raw lookup that skips whitespace and comment tokens; null at end of input. */
    private fun rawMeaningfulLookup(builder: PsiBuilder, offset: Int): IElementType? {
        var i = offset
        while (true) {
            val type = builder.rawLookup(i) ?: return null
            if (type == DreamShaderTokenTypes.WHITE_SPACE ||
                type == DreamShaderTokenTypes.LINE_COMMENT ||
                type == DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                i++
                continue
            }
            return type
        }
    }

    private fun parseVariableDeclarationStatement(builder: PsiBuilder) {
        val marker = builder.mark()
        while (builder.tokenType == DreamShaderTokenTypes.KEYWORD &&
            (builder.tokenText == "static" || builder.tokenText == "const")
        ) {
            builder.advanceLexer()
        }
        if (builder.tokenType == DreamShaderTokenTypes.TYPE || builder.tokenType == DreamShaderTokenTypes.IDENTIFIER) {
            builder.advanceLexer() // type
        }
        parseDeclaratorListTail(builder, localNames = true)
        marker.done(DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT)
    }

    /** Returns true when at least one token was consumed as part of the statement. */
    private fun parseExpressionStatement(builder: PsiBuilder): Boolean {
        val start = builder.currentOffset
        val marker = builder.mark()
        parseExpression(builder)
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.SEMICOLON) {
            builder.advanceLexer()
        }
        marker.done(DreamShader2ElementTypes.EXPRESSION_STATEMENT)
        return builder.currentOffset > start
    }

    // ------------------------------------------------------------------
    // expressions (flat postfix shape)
    // ------------------------------------------------------------------

    /**
     * Parses an expression: a primary (REFERENCE/LITERAL/PARENTHESIZED/CAST/CONSTRUCTOR)
     * extended by call / member / index postfixes, with `?:`, binary operators and assignments
     * kept flat inside one EXPRESSION node.
     */
    private fun parseExpression(builder: PsiBuilder) {
        val outer = builder.mark()
        parsePostfixExpression(builder)
        while (!builder.eof()) {
            val type = builder.tokenType
            if (type == DreamShaderTokenTypes.OPERATOR && builder.tokenText in BINARY_OR_ASSIGNMENT_OPS) {
                builder.advanceLexer()
                parsePostfixExpression(builder)
            } else {
                break
            }
        }
        outer.done(DreamShader2ElementTypes.EXPRESSION)
    }

    /** Initializer position: `{ ... }` lists are allowed here (upstream: initializer only). */
    private fun parseInitializerExpression(builder: PsiBuilder) {
        if (builder.tokenType == DreamShaderTokenTypes.LBRACE) {
            parseInitializerList(builder)
        } else {
            parseExpression(builder)
        }
    }

    private fun parseInitializerList(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.LBRACE)
        while (!builder.eof() && builder.tokenType != DreamShaderTokenTypes.RBRACE) {
            parseInitializerExpression(builder)
            skipTrivia(builder)
            if (builder.tokenType == DreamShaderTokenTypes.COMMA) {
                builder.advanceLexer()
            } else {
                break
            }
        }
        expect(builder, DreamShaderTokenTypes.RBRACE)
        marker.done(DreamShader2ElementTypes.INITIALIZER_LIST)
    }

    private fun parsePostfixExpression(builder: PsiBuilder) {
        var outer = builder.mark()
        var kind = parsePrimary(builder)
        while (!builder.eof()) {
            val suffix = builder.tokenType in setOf(DreamShaderTokenTypes.LPAREN, DreamShaderTokenTypes.DOT, DreamShaderTokenTypes.LBRACKET)
            if (suffix && kind != PostfixKind.PLAIN) {
                outer.done(kind.nodeType)
                outer = outer.precede()
            }
            when (builder.tokenType) {
                DreamShaderTokenTypes.LPAREN -> {
                    parseArgumentList(builder)
                    kind = PostfixKind.CALL
                }
                DreamShaderTokenTypes.DOT -> {
                    builder.advanceLexer()
                    skipTrivia(builder)
                    if (builder.tokenType == DreamShaderTokenTypes.IDENTIFIER ||
                        builder.tokenType == DreamShaderTokenTypes.KEYWORD ||
                        builder.tokenType == DreamShaderTokenTypes.TYPE
                    ) {
                        builder.advanceLexer()
                    }
                    kind = PostfixKind.MEMBER
                }
                DreamShaderTokenTypes.LBRACKET -> {
                    builder.advanceLexer()
                    if (builder.tokenType != DreamShaderTokenTypes.RBRACKET) {
                        parseExpression(builder)
                    }
                    expect(builder, DreamShaderTokenTypes.RBRACKET)
                    kind = PostfixKind.INDEX
                }
                DreamShaderTokenTypes.OPERATOR -> {
                    val text = builder.tokenText?.toString() ?: break
                    if (text == "++" || text == "--") {
                        builder.advanceLexer()
                    } else {
                        break
                    }
                }
                else -> break
            }
        }
        outer.done(kind.nodeType)
    }

    private enum class PostfixKind(val nodeType: IElementType) {
        PLAIN(DreamShader2ElementTypes.EXPRESSION),
        CALL(DreamShader2ElementTypes.CALL_EXPRESSION),
        MEMBER(DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION),
        INDEX(DreamShader2ElementTypes.INDEX_EXPRESSION),
    }

    private fun parsePrimary(builder: PsiBuilder): PostfixKind {
        skipTrivia(builder)
        return when (builder.tokenType) {
            DreamShaderTokenTypes.NUMBER, DreamShaderTokenTypes.STRING -> {
                val marker = builder.mark()
                builder.advanceLexer()
                marker.done(DreamShader2ElementTypes.LITERAL_EXPRESSION)
                PostfixKind.PLAIN
            }
            DreamShaderTokenTypes.KEYWORD, DreamShaderTokenTypes.IDENTIFIER -> {
                val marker = builder.mark()
                builder.advanceLexer()
                marker.done(DreamShader2ElementTypes.REFERENCE_EXPRESSION)
                PostfixKind.PLAIN
            }
            DreamShaderTokenTypes.TYPE -> {
                val marker = builder.mark()
                builder.advanceLexer()
                skipTrivia(builder)
                if (builder.tokenType == DreamShaderTokenTypes.LPAREN) {
                    parseArgumentList(builder)
                    marker.done(DreamShader2ElementTypes.CONSTRUCTOR_EXPRESSION)
                } else {
                    marker.drop()
                }
                PostfixKind.PLAIN
            }
            DreamShaderTokenTypes.LPAREN -> {
                if (isCastPrefix(builder)) {
                    val marker = builder.mark()
                    builder.advanceLexer() // (
                    builder.advanceLexer() // type
                    expect(builder, DreamShaderTokenTypes.RPAREN)
                    parsePostfixExpression(builder)
                    marker.done(DreamShader2ElementTypes.CAST_EXPRESSION)
                } else {
                    val marker = builder.mark()
                    builder.advanceLexer() // (
                    parseExpression(builder)
                    expect(builder, DreamShaderTokenTypes.RPAREN)
                    marker.done(DreamShader2ElementTypes.PARENTHESIZED_EXPRESSION)
                }
                PostfixKind.PLAIN
            }
            DreamShaderTokenTypes.LBRACE -> {
                parseInitializerList(builder)
                PostfixKind.PLAIN
            }
            DreamShaderTokenTypes.OPERATOR -> {
                val text = builder.tokenText?.toString() ?: run {
                    val marker = builder.mark()
                    builder.advanceLexer()
                    marker.done(DreamShader2ElementTypes.EXPRESSION)
                    return PostfixKind.PLAIN
                }
                if (text in PREFIX_OPERATORS) {
                    val marker = builder.mark()
                    builder.advanceLexer()
                    parsePostfixExpression(builder)
                    marker.done(DreamShader2ElementTypes.EXPRESSION)
                } else {
                    val marker = builder.mark()
                    builder.advanceLexer()
                    marker.done(DreamShader2ElementTypes.EXPRESSION)
                }
                PostfixKind.PLAIN
            }
            else -> {
                val marker = builder.mark()
                builder.advanceLexer()
                marker.done(DreamShader2ElementTypes.EXPRESSION)
                PostfixKind.PLAIN
            }
        }
    }

    /** `( Type )` prefix - only built-in type spellings count, mirroring the upstream parser. */
    private fun isCastPrefix(builder: PsiBuilder): Boolean {
        if (builder.rawLookup(1) != DreamShaderTokenTypes.TYPE) return false
        var offset = 2
        while (true) {
            val type = builder.rawLookup(offset) ?: return false
            when (type) {
                DreamShaderTokenTypes.WHITE_SPACE,
                DreamShaderTokenTypes.LINE_COMMENT,
                DreamShaderTokenTypes.BLOCK_COMMENT,
                -> offset++
                DreamShaderTokenTypes.RPAREN -> return true
                else -> return false
            }
        }
    }

    private fun parseArgumentList(builder: PsiBuilder) {
        val marker = builder.mark()
        expect(builder, DreamShaderTokenTypes.LPAREN)
        while (!builder.eof() && builder.tokenType != DreamShaderTokenTypes.RPAREN) {
            parseArgument(builder)
            skipTrivia(builder)
            when (builder.tokenType) {
                DreamShaderTokenTypes.COMMA -> builder.advanceLexer()
                DreamShaderTokenTypes.RPAREN -> Unit
                else -> break
            }
        }
        expect(builder, DreamShaderTokenTypes.RPAREN)
        marker.done(DreamShader2ElementTypes.ARGUMENT_LIST)
    }

    /**
     * `Name = expr` (also `Pin[0] = expr`) becomes a NAMED_ARGUMENT; anything else is a positional
     * argument. Rejecting a positional argument after a named one is the upstream's business, not
     * ours (red line: the plugin is not the compiler).
     */
    private fun parseArgument(builder: PsiBuilder) {
        if (isNamedArgumentStart(builder)) {
            val marker = builder.mark()
            builder.advanceLexer() // name
            if (builder.tokenType == DreamShaderTokenTypes.LBRACKET) {
                builder.advanceLexer()
                if (builder.tokenType != DreamShaderTokenTypes.RBRACKET) {
                    parseExpression(builder)
                }
                expect(builder, DreamShaderTokenTypes.RBRACKET)
            }
            expect(builder, DreamShaderTokenTypes.OPERATOR) // =
            parseInitializerExpression(builder)
            marker.done(DreamShader2ElementTypes.NAMED_ARGUMENT)
        } else {
            val marker = builder.mark()
            parseInitializerExpression(builder)
            marker.done(DreamShader2ElementTypes.ARGUMENT)
        }
    }

    private fun isNamedArgumentStart(builder: PsiBuilder): Boolean {
        if (builder.tokenType != DreamShaderTokenTypes.IDENTIFIER) return false
        var offset = 1
        while (true) {
            val type = builder.rawLookup(offset) ?: return false
            when (type) {
                DreamShaderTokenTypes.WHITE_SPACE,
                DreamShaderTokenTypes.LINE_COMMENT,
                DreamShaderTokenTypes.BLOCK_COMMENT,
                -> offset++
                DreamShaderTokenTypes.LBRACKET,
                DreamShaderTokenTypes.RBRACKET,
                DreamShaderTokenTypes.NUMBER,
                -> offset++
                DreamShaderTokenTypes.OPERATOR -> return rawTokenTextAt(builder, offset) == "="
                else -> return false
            }
        }
    }

    // ------------------------------------------------------------------
    // builder helpers
    // ------------------------------------------------------------------

    private fun expect(builder: PsiBuilder, type: IElementType) {
        if (builder.tokenType == type) {
            builder.advanceLexer()
        }
    }

    private fun expectSemicolon(builder: PsiBuilder) {
        skipTrivia(builder)
        if (builder.tokenType == DreamShaderTokenTypes.SEMICOLON) {
            builder.advanceLexer()
        }
    }

    private fun skipTrivia(builder: PsiBuilder) {
        while (builder.tokenType == DreamShaderTokenTypes.WHITE_SPACE ||
            builder.tokenType == DreamShaderTokenTypes.LINE_COMMENT ||
            builder.tokenType == DreamShaderTokenTypes.BLOCK_COMMENT
        ) {
            builder.advanceLexer()
        }
    }

    private fun rawTokenTextAt(builder: PsiBuilder, offset: Int): String? {
        val start = builder.rawTokenTypeStart(offset)
        if (start < 0) return null
        // No rawTokenTypeEnd in this platform API level: the next raw token's start delimits us.
        val next = builder.rawTokenTypeStart(offset + 1)
        val end = if (next > start) next else start + 1
        return builder.originalText.subSequence(start, end).toString()
    }

    private fun isStatementTerminator(type: IElementType?): Boolean =
        type == DreamShaderTokenTypes.SEMICOLON || type == DreamShaderTokenTypes.RBRACE

    private companion object {
        const val CUSTOM_DIRECTIVE = "@custom"
        val PARAMETER_MODIFIERS = setOf("in", "out", "inout")
        val PREFIX_OPERATORS = setOf("++", "--", "!", "~", "+", "-")
        val BINARY_OR_ASSIGNMENT_OPS = setOf(
            "=", "+=", "-=", "*=", "/=", "%=", "<<=", ">>=", "&=", "|=", "^=",
            "||", "&&", "|", "^", "&", "==", "!=", "<", ">", "<=", ">=", "<<", ">>",
            "+", "-", "*", "/", "%", "?", ":",
        )
    }
}
