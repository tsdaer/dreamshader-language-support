package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeDiagnosticsRepository
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import java.util.Locale
import java.util.regex.Pattern

/**
 * Central diagnostics and semantic-highlighting annotator.
 *
 * This class intentionally combines multiple diagnostic layers so one PSI walk
 * can produce all user-facing results:
 * - syntax-level checks (unclosed literals, brace balance, malformed shells)
 * - section-shape rules (file-role/declaration-shape constraints)
 * - semantic checks (settings/base outputs/types/imports/call out-args)
 * - external Bridge diagnostics mapping
 */
class DreamShaderSemanticAnnotator : Annotator {
    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element is DreamShaderPsiFile) {
            annotateFileDiagnostics(element, holder)
            return
        }

        if (element.text.isBlank()) return

        when (element.node.elementType) {
            DreamShaderTokenTypes.KEYWORD -> {
                if (isDeclarationKeywordElement(element)) {
                    holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                        .range(element)
                        .textAttributes(DreamShaderTextAttributes.KEYWORD)
                        .create()
                }
                return
            }

            DreamShaderTokenTypes.SECTION -> {
                holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                    .range(element)
                    .textAttributes(DreamShaderTextAttributes.SECTION)
                    .create()
                return
            }

            DreamShaderTokenTypes.IDENTIFIER -> annotateIdentifierSemanticToken(element, holder)
        }
    }

    private fun annotateIdentifierSemanticToken(
        element: PsiElement,
        holder: AnnotationHolder
    ) {
        if (element.text.isBlank()) return

        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        if (declaration != null && element == declaration.nameIdentifier) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(DreamShaderTextAttributes.DECLARATION_NAME)
                .create()
            return
        }

        if (isUeNamespaceIdentifier(element)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(DreamShaderTextAttributes.BUILTIN_NAMESPACE)
                .create()
            return
        }

        if (isMaterialOutputMemberIdentifier(element)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(DreamShaderTextAttributes.MATERIAL_OUTPUT_MEMBER)
                .create()
            return
        }

        if (looksLikeLocalSymbol(element)) {
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(element)
                .textAttributes(DreamShaderTextAttributes.LOCAL_SYMBOL)
                .create()
            return
        }

        val text = element.text
        if (!looksLikeCallableReference(text, element)) return
        if (isDeclarationHeadIdentifier(element)) return
        if (!isInsideDeclarationBody(element)) return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(DreamShaderTextAttributes.CALLABLE_REFERENCE)
            .create()
    }

    private fun isDeclarationKeywordElement(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val firstKeyword = declaration.node.getChildren(null).firstOrNull { it.elementType == DreamShaderTokenTypes.KEYWORD }
        return firstKeyword?.psi == element
    }

    private fun isUeNamespaceIdentifier(element: PsiElement): Boolean {
        if (!element.text.equals("UE", ignoreCase = true)) return false
        if (!isInsideDeclarationBody(element)) return false
        return skipWhitespaceForward(element) == "."
    }

    private fun isMaterialOutputMemberIdentifier(element: PsiElement): Boolean {
        if (!isInsideDeclarationBody(element)) return false
        val prev = previousSignificantElement(element) ?: return false
        if (prev.node.elementType != DreamShaderTokenTypes.OPERATOR || prev.text != ".") return false
        val base = previousSignificantElement(prev) ?: return false
        if (base.node.elementType != DreamShaderTokenTypes.IDENTIFIER || !base.text.equals("Base", ignoreCase = true)) {
            return false
        }
        return element.text.lowercase(Locale.ROOT) in BASE_OUTPUT_MEMBERS
    }

    private fun looksLikeLocalSymbol(element: PsiElement): Boolean {
        if (!isInsideDeclarationBody(element)) return false
        if (isDeclarationHeadIdentifier(element)) return false
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        val parentSection = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false)
        val sectionName = parentSection?.sectionName()
        val allowedBySection = sectionName == "graph" || sectionName == "outputs" || sectionName == "inputs"
        val allowedByFunctionBody = declaration?.isFunctionLike() == true
        if (!allowedBySection && !allowedByFunctionBody) return false
        if (looksLikeCallableReference(element.text, element)) return false
        if (isNamespaceQualifier(element)) return false
        if (isLikelyMemberAccessTarget(element)) return false
        return true
    }

    private fun isNamespaceQualifier(element: PsiElement): Boolean {
        val next = skipWhitespaceForward(element)
        if (next != ":") return false
        val text = element.containingFile.text
        var i = element.textRange.endOffset
        while (i < text.length && text[i].isWhitespace()) i++
        if (i + 1 >= text.length) return false
        return text[i] == ':' && text[i + 1] == ':'
    }

    private fun isLikelyMemberAccessTarget(element: PsiElement): Boolean {
        val next = skipWhitespaceForward(element) ?: return false
        return next == "." || next == "["
    }

    private fun previousSignificantElement(element: PsiElement): PsiElement? {
        var current = element.prevSibling
        while (current != null) {
            if (!current.text.isNullOrBlank()) {
                val type = current.node?.elementType
                if (type != DreamShaderTokenTypes.WHITE_SPACE &&
                    type != DreamShaderTokenTypes.LINE_COMMENT &&
                    type != DreamShaderTokenTypes.BLOCK_COMMENT
                ) {
                    return current
                }
            }
            current = current.prevSibling
        }

        var leaf = PsiTreeUtil.prevVisibleLeaf(element)
        while (leaf != null) {
            val type = leaf.node?.elementType
            if (type != DreamShaderTokenTypes.WHITE_SPACE &&
                type != DreamShaderTokenTypes.LINE_COMMENT &&
                type != DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                return leaf
            }
            leaf = PsiTreeUtil.prevVisibleLeaf(leaf)
        }
        return null
    }

    private fun looksLikeCallableReference(text: String, element: PsiElement): Boolean {
        if (text.equals("ue", ignoreCase = true)) return false
        val next = skipWhitespaceForward(element)
        return next == "("
    }

    private fun skipWhitespaceForward(element: PsiElement): String? {
        val text = element.containingFile.text
        val start = element.textRange.endOffset
        var i = start
        while (i < text.length && text[i].isWhitespace()) i++
        if (i >= text.length) return null
        return text[i].toString()
    }

    private fun isDeclarationHeadIdentifier(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        if (element == declaration.nameIdentifier) return true

        val declarationRange = declaration.textRange
        val bodyRange = declaration.bodyTextRange() ?: return false
        val elementRange = element.textRange
        return declarationRange.contains(elementRange) && !bodyRange.contains(elementRange)
    }

    private fun isInsideDeclarationBody(element: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val bodyRange = declaration.bodyTextRange() ?: return false
        if (!bodyRange.contains(element.textRange)) return false

        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false)
        if (section != null) {
            val sectionName = section.sectionName()?.lowercase(Locale.ROOT)
            if (sectionName == "settings" || sectionName == "options" || sectionName == "properties") {
                return false
            }
        }

        return true
    }

    private fun annotateFileDiagnostics(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        val sourceText = file.text
        val tokens = lexTokens(sourceText, 0, file.textLength)
        val topLevelDeclarations = topLevelDeclarations(file)

        annotateSyntaxDiagnostics(sourceText, tokens, holder)
        annotateSectionShapeDiagnostics(file, sourceText, tokens, topLevelDeclarations, holder)
        annotateSemanticDiagnostics(file, sourceText, tokens, topLevelDeclarations, holder)
        annotateBridgeDiagnostics(file, holder)
    }

    private fun annotateBridgeDiagnostics(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        val repository = file.project.getService(DreamShaderBridgeDiagnosticsRepository::class.java) ?: return
        repository.refresh(file.virtualFile)
        val diagnostics = file.virtualFile?.let { repository.diagnosticsForFile(it) }.orEmpty()
        diagnostics.forEach { diagnostic ->
            val lineIndex = (diagnostic.line - 1).coerceAtLeast(0)
            val columnIndex = (diagnostic.column - 1).coerceAtLeast(0)
            val lineStartOffset = lineStartOffset(file.text, lineIndex)
            val start = (lineStartOffset + columnIndex).coerceIn(0, file.textLength)
            val end = (start + 1).coerceAtMost(file.textLength)
            if (start >= end) return@forEach

            val severity = if (diagnostic.severity == "warning" || diagnostic.severity == "warn") {
                HighlightSeverity.WARNING
            } else {
                HighlightSeverity.ERROR
            }
            holder.newAnnotation(severity, diagnostic.message)
                .range(TextRange(start, end))
                .create()
        }
    }

    private fun annotateSyntaxDiagnostics(
        sourceText: String,
        tokens: List<LexedToken>,
        holder: AnnotationHolder
    ) {
        annotateUnclosedLiteralDiagnostics(tokens, holder)
        annotateUnmatchedBraceDiagnostics(tokens, sourceText.length, holder)
        annotateMalformedTopLevelDeclarationDiagnostics(tokens, holder)
        annotateMalformedSectionDiagnostics(tokens, holder)
    }

    private fun annotateSectionShapeDiagnostics(
        file: DreamShaderPsiFile,
        sourceText: String,
        tokens: List<LexedToken>,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        val extension = file.virtualFile?.extension?.lowercase(Locale.ROOT)
        if (extension == "dsf") {
            topLevelDeclarations
                .filter { it.keywordText() == "shader" }
                .forEach { declaration ->
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "Top-level Shader declaration is not allowed in .dsf files"
                    ).range(declaration.nameIdentifier ?: declaration).create()
                }
        }

        topLevelDeclarations.forEach { declaration ->
            when (declaration.keywordText()) {
                "virtualfunction" -> annotateVirtualFunctionRules(sourceText, declaration, holder)
                "shaderlayer", "shaderlayerblend" -> annotateLayerRules(declaration, holder)
            }
        }

        annotateNamespaceRules(tokens, holder)
    }

    private fun annotateSemanticDiagnostics(
        file: DreamShaderPsiFile,
        sourceText: String,
        tokens: List<LexedToken>,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        annotateSettingsDiagnostics(topLevelDeclarations, holder)
        annotateBaseOutputMemberDiagnostics(topLevelDeclarations, holder)
        annotateUnknownTypeDiagnostics(topLevelDeclarations, holder)
        annotateMissingOutArgumentDiagnostics(sourceText, tokens, topLevelDeclarations, holder)
        annotateUnresolvedImportDiagnostics(file, tokens, holder)
    }

    private fun annotateUnclosedLiteralDiagnostics(tokens: List<LexedToken>, holder: AnnotationHolder) {
        tokens.forEach { token ->
            if (token.type == DreamShaderTokenTypes.STRING && !token.text.endsWith("\"")) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Unclosed string literal")
                    .range(token.range)
                    .create()
            }
            if (token.type == DreamShaderTokenTypes.BLOCK_COMMENT && !token.text.endsWith("*/")) {
                holder.newAnnotation(HighlightSeverity.ERROR, "Unclosed block comment")
                    .range(token.range)
                    .create()
            }
        }
    }

    private fun annotateUnmatchedBraceDiagnostics(
        tokens: List<LexedToken>,
        fileLength: Int,
        holder: AnnotationHolder
    ) {
        val openingBraces = mutableListOf<LexedToken>()
        tokens.forEach { token ->
            when (token.type) {
                DreamShaderTokenTypes.LBRACE -> openingBraces.add(token)
                DreamShaderTokenTypes.RBRACE -> {
                    if (openingBraces.isEmpty()) {
                        holder.newAnnotation(HighlightSeverity.ERROR, "Unmatched brace")
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
            holder.newAnnotation(HighlightSeverity.ERROR, "Unmatched brace")
                .range(TextRange(markerStart, markerEnd))
                .create()
        }
    }

    private fun annotateMalformedTopLevelDeclarationDiagnostics(tokens: List<LexedToken>, holder: AnnotationHolder) {
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
                    "Malformed declaration: expected declaration name or argument list"
                ).range(token.range).create()
            }
        }
    }

    private fun annotateMalformedSectionDiagnostics(tokens: List<LexedToken>, holder: AnnotationHolder) {
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
                holder.newAnnotation(HighlightSeverity.ERROR, "Malformed section: expected '{'")
                    .range(token.range)
                    .create()
            }
        }
    }

    private fun annotateVirtualFunctionRules(
        sourceText: String,
        declaration: DreamShaderDeclaration,
        holder: AnnotationHolder
    ) {
        val sections = directSectionsOf(declaration)
        val graphSection = sections.firstOrNull { it.sectionName() == "graph" }
        val codeSectionRange = topLevelSectionHeaderRange(sourceText, declaration.bodyTextRange(), "code")
        if (graphSection != null || codeSectionRange != null) {
            val annotation = holder.newAnnotation(
                HighlightSeverity.ERROR,
                "VirtualFunction does not support Graph/Code sections"
            )
            if (graphSection != null) {
                annotation.range(graphSection)
            } else if (codeSectionRange != null) {
                annotation.range(codeSectionRange)
            } else {
                annotation.range(declaration)
            }
            annotation.create()
        }
    }

    private fun annotateLayerRules(declaration: DreamShaderDeclaration, holder: AnnotationHolder) {
        val sections = directSectionsOf(declaration)
        val outputsSection = sections.firstOrNull { it.sectionName() == "outputs" }
        val outputDeclarations = topLevelTypedDeclarations(outputsSection)
        val hasSingleMaterialAttributesOutput =
            outputDeclarations.size == 1 && outputDeclarations.single().equals("materialattributes", ignoreCase = true)
        if (!hasSingleMaterialAttributesOutput) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "ShaderLayer/ShaderLayerBlend must declare exactly one MaterialAttributes output"
            ).range(outputsSection ?: declaration).create()
        }

        if (declaration.keywordText() == "shaderlayerblend") {
            val inputsSection = sections.firstOrNull { it.sectionName() == "inputs" }
            val inputsMaterialAttributesCount = topLevelTypedDeclarations(inputsSection)
                .count { it.equals("materialattributes", ignoreCase = true) }
            if (inputsMaterialAttributesCount < 2) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    "ShaderLayerBlend requires at least two MaterialAttributes inputs"
                ).range(inputsSection ?: declaration).create()
            }
        }
    }

    private fun annotateNamespaceRules(tokens: List<LexedToken>, holder: AnnotationHolder) {
        val allowed = setOf("function", "graphfunction")
        var i = 0
        while (i < tokens.size) {
            val token = tokens[i]
            if (
                token.depthBefore == 0 &&
                token.type == DreamShaderTokenTypes.KEYWORD &&
                token.text.equals("namespace", ignoreCase = true)
            ) {
                val namespaceEnd = annotateNamespaceBodyDisallowedDeclarations(tokens, i, allowed, holder)
                if (namespaceEnd > i) i = namespaceEnd
            }
            i++
        }
    }

    private fun annotateNamespaceBodyDisallowedDeclarations(
        tokens: List<LexedToken>,
        namespaceKeywordIndex: Int,
        allowedDeclarationKeywords: Set<String>,
        holder: AnnotationHolder
    ): Int {
        var i = namespaceKeywordIndex + 1
        while (i < tokens.size) {
            val token = tokens[i]
            if (token.depthBefore != 0) return i
            if (token.type == DreamShaderTokenTypes.OPERATOR && token.text == ";") return i
            if (token.type == DreamShaderTokenTypes.LBRACE) break
            i++
        }
        if (i >= tokens.size || tokens[i].type != DreamShaderTokenTypes.LBRACE) return namespaceKeywordIndex

        var k = i + 1
        while (k < tokens.size) {
            val token = tokens[k]
            if (token.type == DreamShaderTokenTypes.RBRACE && token.depthBefore == 1) return k
            if (token.depthBefore == 1 && token.type == DreamShaderTokenTypes.KEYWORD) {
                val keyword = token.text.lowercase(Locale.ROOT)
                if (keyword in DreamShaderLanguageKeywords.DECLARATION_KEYWORDS && keyword !in allowedDeclarationKeywords) {
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        "Namespace can only contain Function or GraphFunction declarations"
                    ).range(token.range).create()
                }
            }
            k++
        }
        return namespaceKeywordIndex
    }

    private fun annotateSettingsDiagnostics(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            directSectionsOf(declaration)
                .filter { it.sectionName() == "settings" || it.sectionName() == "options" }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val matcher = SETTINGS_ASSIGNMENT_PATTERN.matcher(body.text)
                    while (matcher.find()) {
                        val key = matcher.group(1) ?: continue
                        val keyLower = key.lowercase(Locale.ROOT)
                        val keyRange = TextRange(body.startOffset + matcher.start(1), body.startOffset + matcher.end(1))
                        if (keyLower !in SETTINGS_KEYS) {
                            holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                "Unknown settings key '$key'"
                            ).range(keyRange).create()
                            continue
                        }

                        val validValues = SETTING_VALUE_MAPPINGS[keyLower] ?: continue
                        val rawValue = matcher.group(2) ?: continue
                        val value = rawValue.trim().trim('"')
                        if (value !in validValues) {
                            val valueRange = TextRange(body.startOffset + matcher.start(2), body.startOffset + matcher.end(2))
                            holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                "Invalid value '$value' for setting '$key'"
                            ).range(valueRange).create()
                        }
                    }
                }
        }
    }

    private fun annotateBaseOutputMemberDiagnostics(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            directSectionsOf(declaration)
                .filter { it.sectionName() == "outputs" || it.sectionName() == "graph" }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val matcher = BASE_MEMBER_PATTERN.matcher(body.text)
                    while (matcher.find()) {
                        val member = matcher.group(1) ?: continue
                        if (member.lowercase(Locale.ROOT) in BASE_OUTPUT_MEMBERS) continue
                        val range = TextRange(body.startOffset + matcher.start(), body.startOffset + matcher.end())
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "Unknown material output member 'Base.$member'"
                        ).range(range).create()
                    }
                }
        }
    }

    private fun annotateUnknownTypeDiagnostics(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            directSectionsOf(declaration)
                .filter { it.sectionName() == "inputs" || it.sectionName() == "outputs" || it.sectionName() == "properties" }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val matcher = TYPED_DECLARATION_PATTERN.matcher(body.text)
                    while (matcher.find()) {
                        val type = matcher.group(1) ?: continue
                        val lower = type.lowercase(Locale.ROOT)
                        if (lower in KNOWN_TYPES || lower in TYPE_QUALIFIERS) continue
                        val range = TextRange(body.startOffset + matcher.start(1), body.startOffset + matcher.end(1))
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            "Unknown type '$type'"
                        ).range(range).create()
                    }
                }
        }
    }

    private fun annotateMissingOutArgumentDiagnostics(
        sourceText: String,
        tokens: List<LexedToken>,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        val signatureByFunction = topLevelDeclarations.mapNotNull { declaration ->
            val keyword = declaration.keywordText() ?: return@mapNotNull null
            if (keyword != "function" && keyword != "graphfunction") return@mapNotNull null
            val name = declaration.declarationName() ?: return@mapNotNull null
            val signature = parseDeclarationParameters(declaration.text) ?: return@mapNotNull null
            if (signature.params.none { it.isOut }) return@mapNotNull null
            name to signature
        }.toMap()
        if (signatureByFunction.isEmpty()) return

        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.type != DreamShaderTokenTypes.IDENTIFIER) return@forEach
            val signature = signatureByFunction[token.text] ?: return@forEach
            val next = nextSignificantToken(tokens, index) ?: return@forEach
            if (next.type != DreamShaderTokenTypes.LPAREN) return@forEach

            val prev = previousSignificantToken(tokens, index)
            if (prev?.type == DreamShaderTokenTypes.OPERATOR && prev.text == ".") return@forEach
            if (prev?.type == DreamShaderTokenTypes.KEYWORD && prev.text.lowercase(Locale.ROOT) in DreamShaderLanguageKeywords.DECLARATION_KEYWORDS) return@forEach

            val call = parseCallArgumentInfo(sourceText, next.range.startOffset) ?: return@forEach
            val missingOutParam = signature.params.withIndex()
                .firstOrNull { (paramIndex, param) -> param.isOut && paramIndex >= call.argumentCount }
                ?.value
                ?: return@forEach

            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "Missing out argument for parameter '${missingOutParam.name}'"
            ).range(token.range).create()
        }
    }

    private fun annotateUnresolvedImportDiagnostics(
        file: DreamShaderPsiFile,
        tokens: List<LexedToken>,
        holder: AnnotationHolder
    ) {
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.type != DreamShaderTokenTypes.STRING) return@forEach
            val previous = previousSignificantToken(tokens, index)
            if (previous?.type != DreamShaderTokenTypes.KEYWORD || !previous.text.equals("import", ignoreCase = true)) return@forEach
            if (token.text.length < 2 || !token.text.startsWith('"') || !token.text.endsWith('"')) return@forEach
            val importPath = token.text.substring(1, token.text.length - 1).trim()
            if (importPath.isBlank()) return@forEach
            if (DreamShaderImportResolver.resolveImport(file, importPath) != null) return@forEach

            holder.newAnnotation(
                HighlightSeverity.ERROR,
                "Cannot resolve import '$importPath'"
            ).range(token.range).create()
        }
    }

    private fun parseDeclarationParameters(declarationText: String): ParsedSignature? {
        val headerEnd = declarationText.indexOf('{').let { if (it >= 0) it else declarationText.length }
        val header = declarationText.substring(0, headerEnd)
        val leftParen = header.indexOf('(')
        val rightParen = header.lastIndexOf(')')
        if (leftParen < 0 || rightParen <= leftParen) return null
        val rawParams = header.substring(leftParen + 1, rightParen)
        val params = splitTopLevel(rawParams, ',').mapNotNull { rawParam ->
            val trimmed = rawParam.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val nameMatch = PARAM_NAME_PATTERN.matcher(trimmed)
            if (!nameMatch.find()) return@mapNotNull null
            val name = nameMatch.group(1) ?: return@mapNotNull null
            ParsedParam(
                name = name,
                isOut = OUT_QUALIFIER_PATTERN.matcher(trimmed).find()
            )
        }
        return ParsedSignature(params)
    }

    private fun parseCallArgumentInfo(sourceText: String, leftParenOffset: Int): CallArgumentInfo? {
        if (leftParenOffset < 0 || leftParenOffset >= sourceText.length || sourceText[leftParenOffset] != '(') return null
        var index = leftParenOffset + 1
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false
        var commaCount = 0
        var sawArgumentToken = false

        while (index < sourceText.length) {
            val ch = sourceText[index]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                } else if (!ch.isWhitespace()) {
                    sawArgumentToken = true
                }
                index++
                continue
            }

            when (ch) {
                '"' -> {
                    inString = true
                    sawArgumentToken = true
                }
                '(' -> {
                    parenDepth++
                    sawArgumentToken = true
                }
                ')' -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        val argumentCount = if (!sawArgumentToken) 0 else commaCount + 1
                        return CallArgumentInfo(argumentCount)
                    }
                    if (parenDepth > 0) parenDepth--
                }
                '[' -> {
                    bracketDepth++
                    sawArgumentToken = true
                }
                ']' -> if (bracketDepth > 0) bracketDepth--
                '{' -> {
                    braceDepth++
                    sawArgumentToken = true
                }
                '}' -> if (braceDepth > 0) braceDepth--
                ',' -> if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) commaCount++
                else -> if (!ch.isWhitespace()) sawArgumentToken = true
            }
            index++
        }
        return null
    }

    private fun splitTopLevel(input: String, delimiter: Char): List<String> {
        val result = mutableListOf<String>()
        var start = 0
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false
        var i = 0
        while (i < input.length) {
            val ch = input[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                i++
                continue
            }
            when (ch) {
                '"' -> inString = true
                '(' -> parenDepth++
                ')' -> if (parenDepth > 0) parenDepth--
                '[' -> bracketDepth++
                ']' -> if (bracketDepth > 0) bracketDepth--
                '{' -> braceDepth++
                '}' -> if (braceDepth > 0) braceDepth--
                delimiter -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        result.add(input.substring(start, i))
                        start = i + 1
                    }
                }
            }
            i++
        }
        result.add(input.substring(start))
        return result
    }

    private fun topLevelDeclarations(file: DreamShaderPsiFile): List<DreamShaderDeclaration> {
        return PsiTreeUtil.findChildrenOfType(file, DreamShaderDeclaration::class.java)
            .filter { PsiTreeUtil.getParentOfType(it, DreamShaderDeclaration::class.java, true) == null }
    }

    private fun directSectionsOf(declaration: DreamShaderDeclaration): List<DreamShaderSection> {
        return PsiTreeUtil.findChildrenOfType(declaration, DreamShaderSection::class.java)
            .filter { section ->
                PsiTreeUtil.getParentOfType(section, DreamShaderSection::class.java, true) == null &&
                    PsiTreeUtil.getParentOfType(section, DreamShaderDeclaration::class.java, true) == declaration
            }
            .toList()
    }

    private fun topLevelTypedDeclarations(section: DreamShaderSection?): List<String> {
        if (section == null) return emptyList()
        val body = sectionBodyText(section.text) ?: return emptyList()
        val result = mutableListOf<String>()
        val matcher = TYPED_DECLARATION_PATTERN.matcher(body)
        while (matcher.find()) {
            val typeName = matcher.group(1)
            if (!typeName.isNullOrBlank()) result.add(typeName)
        }
        return result
    }

    private fun topLevelSectionHeaderRange(
        sourceText: String,
        bodyRange: TextRange?,
        sectionName: String
    ): TextRange? {
        if (bodyRange == null) return null
        val tokens = lexTokens(sourceText, bodyRange.startOffset, bodyRange.endOffset)
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token.depthBefore != 1) continue
            if (!token.text.equals(sectionName, ignoreCase = true)) continue
            if (
                token.type != DreamShaderTokenTypes.IDENTIFIER &&
                token.type != DreamShaderTokenTypes.SECTION &&
                token.type != DreamShaderTokenTypes.KEYWORD
            ) {
                continue
            }

            val next = nextSignificantToken(tokens, index) ?: continue
            if (next.type == DreamShaderTokenTypes.LBRACE) return token.range
            if (next.type == DreamShaderTokenTypes.OPERATOR && next.text == "=") {
                val afterEquals = nextSignificantToken(tokens, tokens.indexOf(next))
                if (afterEquals?.type == DreamShaderTokenTypes.LBRACE) return token.range
            }
        }
        return null
    }

    private fun sectionBody(section: DreamShaderSection): SectionBody? {
        val text = section.text
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return SectionBody(
            text = text.substring(start + 1, end),
            startOffset = section.textRange.startOffset + start + 1
        )
    }

    private fun sectionBodyText(sectionText: String): String? {
        val start = sectionText.indexOf('{')
        val end = sectionText.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        return sectionText.substring(start + 1, end)
    }

    private fun nextSignificantTokenIndex(tokens: List<LexedToken>, index: Int): Int? {
        var i = index + 1
        while (i < tokens.size) {
            if (!tokens[i].isTrivia) return i
            i++
        }
        return null
    }

    private fun nextSignificantToken(tokens: List<LexedToken>, index: Int): LexedToken? {
        var i = index + 1
        while (i < tokens.size) {
            val token = tokens[i]
            if (!token.isTrivia) return token
            i++
        }
        return null
    }

    private fun previousSignificantToken(tokens: List<LexedToken>, index: Int): LexedToken? {
        var i = index - 1
        while (i >= 0) {
            val token = tokens[i]
            if (!token.isTrivia) return token
            i--
        }
        return null
    }

    private fun lexTokens(sourceText: String, startOffset: Int, endOffset: Int): List<LexedToken> {
        val lexer = DreamShaderLexer()
        lexer.start(sourceText, startOffset, endOffset, 0)
        val result = mutableListOf<LexedToken>()
        var depth = 0
        while (lexer.tokenType != null) {
            val type = lexer.tokenType ?: break
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            val token = LexedToken(
                type = type,
                text = sourceText.substring(start, end),
                range = TextRange(start, end),
                depthBefore = depth
            )
            result.add(token)
            when (type) {
                DreamShaderTokenTypes.LBRACE -> depth++
                DreamShaderTokenTypes.RBRACE -> depth = (depth - 1).coerceAtLeast(0)
            }
            lexer.advance()
        }
        return result
    }

    private fun lineStartOffset(text: String, lineIndex: Int): Int {
        if (lineIndex <= 0) return 0
        var currentLine = 0
        var offset = 0
        while (offset < text.length && currentLine < lineIndex) {
            if (text[offset] == '\n') currentLine++
            offset++
        }
        return offset.coerceIn(0, text.length)
    }

    private data class LexedToken(
        val type: com.intellij.psi.tree.IElementType,
        val text: String,
        val range: TextRange,
        val depthBefore: Int
    ) {
        val isTrivia: Boolean
            get() = type == DreamShaderTokenTypes.WHITE_SPACE ||
                type == DreamShaderTokenTypes.LINE_COMMENT ||
                type == DreamShaderTokenTypes.BLOCK_COMMENT
    }

    private data class ParsedParam(
        val name: String,
        val isOut: Boolean
    )

    private data class ParsedSignature(
        val params: List<ParsedParam>
    )

    private data class CallArgumentInfo(
        val argumentCount: Int
    )

    private data class SectionBody(
        val text: String,
        val startOffset: Int
    )

    companion object {
        private val SETTINGS_KEYS = setOf(
            "materialdomain", "domain", "shadingmodel", "blendmode", "rendertype", "translucencylightingmode",
            "lightingmode", "twosided", "wireframe", "ditheredlodtransition", "ditheropacitymask",
            "allownegativeemissivecolor", "castdynamicshadowasmasked", "responsiveaa", "screenspacereflections",
            "contactshadows", "disabledepthtest", "outputtranslucentvelocity", "tangentspacenormal", "fullyrough",
            "issky", "thinsurface", "numcustomizeduvs", "refractionmethod", "refractionmode"
        )

        private val SETTING_VALUE_MAPPINGS = mapOf(
            "materialdomain" to setOf("Surface", "DeferredDecal", "LightFunction", "PostProcess", "UserInterface", "UI", "VirtualTexture"),
            "domain" to setOf("Surface", "DeferredDecal", "LightFunction", "PostProcess", "UserInterface", "UI", "VirtualTexture"),
            "shadingmodel" to setOf("DefaultLit", "Lit", "Unlit", "Subsurface", "PreintegratedSkin", "ClearCoat", "SubsurfaceProfile", "TwoSidedFoliage", "Hair", "Cloth", "Eye", "SingleLayerWater", "ThinTranslucent", "Substrate", "Strata"),
            "blendmode" to setOf("Opaque", "Masked", "Cutout", "Translucent", "Transparent", "Additive", "Modulate", "AlphaComposite", "AlphaHoldout"),
            "rendertype" to setOf("Opaque", "Masked", "Cutout", "Translucent", "Transparent", "Additive", "Modulate", "AlphaComposite", "AlphaHoldout")
        )

        private val BASE_OUTPUT_MEMBERS = setOf(
            "materialattributes", "attributes", "basecolor", "emissivecolor", "emissive", "opacity", "opacitymask",
            "metallic", "specular", "roughness", "normal", "ambientocclusion", "ao", "refraction", "worldpositionoffset",
            "wpo", "pixeldepthoffset", "pdo", "subsurfacecolor", "clearcoat", "clearcoatroughness", "customdata0",
            "customdata1", "diffusecolor", "specularcolor", "surfacethickness", "displacement", "customizeduv0",
            "customizeduv1", "customizeduv2", "customizeduv3", "customizeduv4", "customizeduv5", "customizeduv6",
            "customizeduv7", "customizeduvs0", "customizeduvs1", "customizeduvs2", "customizeduvs3", "customizeduvs4",
            "customizeduvs5", "customizeduvs6", "customizeduvs7", "mooaencodedattribute0", "mooaencodedattribute1",
            "mooaencodedattribute2", "mooaencodedattribute3", "mooaencodedattribute4", "anisotropy", "tangent"
        )

        private val KNOWN_TYPES = DreamShaderLexer.TYPES.map { it.lowercase(Locale.ROOT) }.toSet()
        private val TYPE_QUALIFIERS = setOf("in", "out", "inout", "const", "static", "opt")
        private val SETTINGS_ASSIGNMENT_PATTERN: Pattern = Pattern.compile(
            "(?m)\\b([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[A-Za-z_][A-Za-z0-9_.]*)"
        )
        private val BASE_MEMBER_PATTERN: Pattern = Pattern.compile("\\bBase\\.([A-Za-z_][A-Za-z0-9_]*)")
        private val TYPED_DECLARATION_PATTERN: Pattern = Pattern.compile(
            "(?m)(?:^|;)\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|;|\\n)"
        )
        private val PARAM_NAME_PATTERN: Pattern = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*$")
        private val OUT_QUALIFIER_PATTERN: Pattern = Pattern.compile("\\bout\\b")
    }
}
