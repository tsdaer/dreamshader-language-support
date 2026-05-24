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
        val attribute = DreamShaderSemanticTokenClassifier.classify(element) ?: return
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(attribute)
            .create()
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

    // Bridge diagnostics.
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

    // Section-shape diagnostics.
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
                .filter { declaration -> declaration.keywordText() !in DSF_ALLOWED_TOP_LEVEL_DECLARATIONS }
                .forEach { declaration ->
                    val declarationKeyword = DISPLAY_DECLARATION_KEYWORDS[declaration.keywordText()] ?: declaration.keywordText().orEmpty()
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message(
                            "diagnostic.topLevelDeclarationNotAllowedInDsf",
                            declarationKeyword
                        )
                    ).range(declaration.nameIdentifier ?: declaration).create()
                }

            tokens.filter { token ->
                token.depthBefore == 0 &&
                    token.type == DreamShaderTokenTypes.KEYWORD &&
                    token.text.equals("namespace", ignoreCase = true)
            }.forEach { token ->
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.topLevelNamespaceNotAllowedInDsf")
                ).range(token.range).create()
            }
        }
        if (extension == "dsm") {
            topLevelDeclarations
                .filter { declaration -> declaration.keywordText() in DSM_DISALLOWED_TOP_LEVEL_DECLARATIONS }
                .forEach { declaration ->
                    val declarationKeyword = DISPLAY_DECLARATION_KEYWORDS[declaration.keywordText()] ?: declaration.keywordText().orEmpty()
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message(
                            "diagnostic.topLevelDeclarationNotAllowedInDsm",
                            declarationKeyword
                        )
                    ).range(declaration.nameIdentifier ?: declaration).create()
                }
        }
        if (extension == "dsh") {
            topLevelDeclarations
                .filter { declaration -> declaration.keywordText() in DSH_DISALLOWED_TOP_LEVEL_DECLARATIONS }
                .forEach { declaration ->
                    val declarationKeyword = DISPLAY_DECLARATION_KEYWORDS[declaration.keywordText()] ?: declaration.keywordText().orEmpty()
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message(
                            "diagnostic.topLevelDeclarationNotAllowedInDsh",
                            declarationKeyword
                        )
                    ).range(declaration.nameIdentifier ?: declaration).create()
                }
        }

        topLevelDeclarations.forEach { declaration ->
            when (declaration.keywordText()) {
                "virtualfunction" -> annotateVirtualFunctionRules(sourceText, declaration, holder)
                "shaderlayer", "shaderlayerblend" -> annotateLayerRules(declaration, holder)
            }
            annotateDeclarationSectionSchemaRules(declaration, holder)
        }

        annotateNamespaceRules(tokens, holder)
    }

    // Semantic diagnostics.
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
        annotateUnsupportedGraphLoopDiagnostics(sourceText, topLevelDeclarations, holder)
        annotateUnsupportedGraphSwitchDiagnostics(sourceText, topLevelDeclarations, holder)
        annotateUnsupportedGraphBreakContinueDiagnostics(sourceText, topLevelDeclarations, holder)
        annotateUnsupportedGraphReturnDiagnostics(sourceText, topLevelDeclarations, holder)
        annotateMissingOutArgumentDiagnostics(sourceText, tokens, topLevelDeclarations, holder)
        annotateAssetRootPathDiagnostics(topLevelDeclarations, holder)
        annotateUnresolvedImportDiagnostics(file, tokens, holder)
    }

    // Syntax diagnostics: literals/braces/declaration-shape.
    private fun annotateUnclosedLiteralDiagnostics(tokens: List<LexedToken>, holder: AnnotationHolder) {
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
                    DreamShaderBundle.message("diagnostic.malformedDeclarationExpectedNameOrArgs")
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
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.malformedSectionExpectedLBrace")
                )
                    .range(token.range)
                    .create()
            }
        }
    }

    // Section-shape diagnostics: declaration-level constraints.
    private fun annotateVirtualFunctionRules(
        sourceText: String,
        declaration: DreamShaderDeclaration,
        holder: AnnotationHolder
    ) {
        val sections = directSectionsOf(declaration)
        val graphSection = sections.firstOrNull { canonicalSectionName(it.sectionName()) == "graph" }
        val codeSectionRange = topLevelSectionHeaderRange(sourceText, declaration.bodyTextRange(), "code")
        if (graphSection != null || codeSectionRange != null) {
            val annotation = holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.virtualFunctionDisallowsGraphOrCode")
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
        val outputsSection = sections.firstOrNull { canonicalSectionName(it.sectionName()) == "outputs" }
        val outputDeclarations = topLevelTypedDeclarations(outputsSection)
        val hasSingleMaterialAttributesOutput =
            outputDeclarations.size == 1 && outputDeclarations.single().equals("materialattributes", ignoreCase = true)
        if (!hasSingleMaterialAttributesOutput) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.layerRequiresSingleMaterialAttributesOutput")
            ).range(outputsSection ?: declaration).create()
        }

        if (declaration.keywordText() == "shaderlayerblend") {
            val inputsSection = sections.firstOrNull { canonicalSectionName(it.sectionName()) == "inputs" }
            val inputsMaterialAttributesCount = topLevelTypedDeclarations(inputsSection)
                .count { it.equals("materialattributes", ignoreCase = true) }
            if (inputsMaterialAttributesCount < 2) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.layerBlendRequiresTwoMaterialAttributesInputs")
                ).range(inputsSection ?: declaration).create()
            }
        }
    }

    private fun annotateDeclarationSectionSchemaRules(
        declaration: DreamShaderDeclaration,
        holder: AnnotationHolder
    ) {
        val keyword = declaration.keywordText() ?: return
        val allowedSections = DECLARATION_ALLOWED_SECTIONS[keyword] ?: return
        val requiredSections = DECLARATION_REQUIRED_SECTIONS[keyword].orEmpty()
        val sections = directSectionsOf(declaration)

        val groupedByName = sections
            .mapNotNull { section -> canonicalSectionNameForDeclaration(keyword, section.sectionName())?.let { name -> name to section } }
            .groupBy({ it.first }, { it.second })

        groupedByName.forEach { (sectionName, entries) ->
            if (entries.size <= 1) return@forEach
            entries.drop(1).forEach { section ->
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message(
                        "diagnostic.duplicateSectionInDeclaration",
                        displaySectionName(sectionName),
                        displayDeclarationKeyword(keyword)
                    )
                ).range(section).create()
            }
        }

        sections.forEach { section ->
            val sectionName = canonicalSectionNameForDeclaration(keyword, section.sectionName()) ?: return@forEach
            if (sectionName !in allowedSections) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message(
                        "diagnostic.sectionNotAllowedInDeclarations",
                        displaySectionName(sectionName),
                        displayDeclarationKeyword(keyword)
                    )
                    ).range(section).create()
                }
            }

        requiredSections.forEach { requiredSection ->
            if (groupedByName.containsKey(requiredSection)) return@forEach
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message(
                    "diagnostic.declarationRequiresSection",
                    displayDeclarationKeyword(keyword),
                    displaySectionName(requiredSection)
                )
            ).range(declaration.nameIdentifier ?: declaration).create()
        }

        if (keyword == "virtualfunction") {
            annotateVirtualFunctionAssetOptionRules(
                optionSections = groupedByName["options"].orEmpty(),
                settingsAliasSections = groupedByName["settings"].orEmpty(),
                holder = holder
            )
        }
    }

    private fun annotateVirtualFunctionAssetOptionRules(
        optionSections: List<DreamShaderSection>,
        settingsAliasSections: List<DreamShaderSection>,
        holder: AnnotationHolder
    ) {
        var hasAsset = false
        val candidateSections = optionSections + settingsAliasSections
        candidateSections.forEach { section ->
            val body = sectionBody(section) ?: return@forEach
            val matcher = VIRTUAL_FUNCTION_ASSET_ASSIGNMENT_PATTERN.matcher(body.text)
            while (matcher.find()) {
                hasAsset = true
                val value = matcher.group(1) ?: continue
                val trimmed = value.trim()
                val validationError = validateVirtualFunctionAssetPath(trimmed)
                if (validationError == null) continue
                val valueRange = TextRange(body.startOffset + matcher.start(1), body.startOffset + matcher.end(1))
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    validationError
                ).range(valueRange).create()
            }
        }
        if (!hasAsset && candidateSections.isNotEmpty()) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetRequired")
            ).range(candidateSections.first()).create()
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
                        DreamShaderBundle.message("diagnostic.namespaceAllowsOnlyFunctionOrGraphFunction")
                    ).range(token.range).create()
                }
            }
            k++
        }
        return namespaceKeywordIndex
    }

    // Semantic diagnostics: settings/material outputs/types.
    private fun annotateSettingsDiagnostics(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            val declarationKeyword = declaration.keywordText()
            directSectionsOf(declaration)
                .filter {
                    val sectionName = canonicalSectionName(it.sectionName())
                    when {
                        sectionName == "options" -> false
                        declarationKeyword == "virtualfunction" && sectionName == "settings" -> false
                        else -> sectionName == "settings"
                    }
                }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val matcher = SETTINGS_ASSIGNMENT_PATTERN.matcher(body.text)
                    while (matcher.find()) {
                        val key = matcher.group(1) ?: continue
                        val keyLower = key.lowercase(Locale.ROOT)
                        val keyRange = TextRange(body.startOffset + matcher.start(1), body.startOffset + matcher.end(1))
                        if (keyLower !in SETTINGS_KEYS) {
                            val suggestion = suggestSettingsKey(key)
                            val message = if (suggestion != null) {
                                DreamShaderBundle.message(
                                    "diagnostic.unknownSettingsKeyWithSuggestion",
                                    key,
                                    suggestion
                                )
                            } else {
                                DreamShaderBundle.message("diagnostic.unknownSettingsKey", key)
                            }
                            holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                message
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
                                DreamShaderBundle.message("diagnostic.invalidSettingValue", value, key)
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
                .filter {
                    val sectionName = canonicalSectionName(it.sectionName())
                    sectionName == "outputs" || sectionName == "graph"
                }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val matcher = BASE_MEMBER_PATTERN.matcher(body.text)
                    while (matcher.find()) {
                        val member = matcher.group(1) ?: continue
                        if (member.lowercase(Locale.ROOT) in BASE_OUTPUT_MEMBERS) continue
                        val range = TextRange(body.startOffset + matcher.start(), body.startOffset + matcher.end())
                        val suggestion = suggestBaseOutputMember(member)
                        val message = if (suggestion != null) {
                            DreamShaderBundle.message(
                                "diagnostic.unknownBaseOutputMemberWithSuggestion",
                                member,
                                suggestion
                            )
                        } else {
                            DreamShaderBundle.message("diagnostic.unknownBaseOutputMember", member)
                        }
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            message
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
                .filter {
                    val sectionName = canonicalSectionName(it.sectionName())
                    sectionName == "inputs" || sectionName == "outputs" || sectionName == "properties"
                }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val matcher = TYPED_DECLARATION_PATTERN.matcher(body.text)
                    while (matcher.find()) {
                        val type = matcher.group(1) ?: continue
                        val lower = type.lowercase(Locale.ROOT)
                        if (lower in KNOWN_TYPES || lower in TYPE_QUALIFIERS) continue
                        val range = TextRange(body.startOffset + matcher.start(1), body.startOffset + matcher.end(1))
                        val suggestion = suggestTypeName(type)
                        val message = if (suggestion != null) {
                            DreamShaderBundle.message("diagnostic.unknownTypeWithSuggestion", type, suggestion)
                        } else {
                            DreamShaderBundle.message("diagnostic.unknownType", type)
                        }
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            message
                        ).range(range).create()
                    }
                }
        }
    }

    private fun annotateUnsupportedGraphLoopDiagnostics(
        sourceText: String,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            directSectionsOf(declaration)
                .filter { canonicalSectionName(it.sectionName()) == "graph" }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val tokens = lexTokens(sourceText, body.startOffset, body.startOffset + body.text.length)
                    tokens.forEach { token ->
                        if (token.type != DreamShaderTokenTypes.KEYWORD) return@forEach
                        if (token.text !in UNSUPPORTED_GRAPH_LOOP_KEYWORDS) return@forEach
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            DreamShaderBundle.message("diagnostic.graphDisallowsLoopStatement", token.text)
                        ).range(token.range).create()
                    }
                }
        }
    }

    private fun annotateUnsupportedGraphSwitchDiagnostics(
        sourceText: String,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            directSectionsOf(declaration)
                .filter { canonicalSectionName(it.sectionName()) == "graph" }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val tokens = lexTokens(sourceText, body.startOffset, body.startOffset + body.text.length)
                    tokens.forEach { token ->
                        if (token.type != DreamShaderTokenTypes.KEYWORD) return@forEach
                        if (token.text !in UNSUPPORTED_GRAPH_SWITCH_KEYWORDS) return@forEach
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            DreamShaderBundle.message("diagnostic.graphDisallowsSwitchStatement", token.text)
                        ).range(token.range).create()
                    }
                }
        }
    }

    private fun annotateUnsupportedGraphBreakContinueDiagnostics(
        sourceText: String,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            directSectionsOf(declaration)
                .filter { canonicalSectionName(it.sectionName()) == "graph" }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val tokens = lexTokens(sourceText, body.startOffset, body.startOffset + body.text.length)
                    tokens.forEach { token ->
                        if (token.type != DreamShaderTokenTypes.KEYWORD) return@forEach
                        if (token.text !in UNSUPPORTED_GRAPH_FLOW_KEYWORDS) return@forEach
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            DreamShaderBundle.message("diagnostic.graphDisallowsControlStatement", token.text)
                        ).range(token.range).create()
                    }
                }
        }
    }

    private fun annotateUnsupportedGraphReturnDiagnostics(
        sourceText: String,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            directSectionsOf(declaration)
                .filter { canonicalSectionName(it.sectionName()) == "graph" }
                .forEach { section ->
                    val body = sectionBody(section) ?: return@forEach
                    val tokens = lexTokens(sourceText, body.startOffset, body.startOffset + body.text.length)
                    tokens.forEach { token ->
                        if (token.type != DreamShaderTokenTypes.KEYWORD) return@forEach
                        if (token.text != UNSUPPORTED_GRAPH_RETURN_KEYWORD) return@forEach
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            DreamShaderBundle.message("diagnostic.graphDisallowsReturnStatement")
                        ).range(token.range).create()
                    }
                }
        }
    }

    // Semantic diagnostics: call/import validations.
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
                DreamShaderBundle.message("diagnostic.missingOutArgumentForParameter", missingOutParam.name)
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
                DreamShaderBundle.message("diagnostic.cannotResolveImport", importPath)
            ).range(token.range).create()
        }
    }

    private fun annotateAssetRootPathDiagnostics(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            val keyword = declaration.keywordText() ?: return@forEach
            if (keyword !in ASSET_DECLARATION_KEYWORDS) return@forEach
            val header = declarationHeaderText(declaration.text) ?: return@forEach
            val rootAssignment = findNamedAssignmentValue(header, "root") ?: return@forEach
            val rootValue = rootAssignment.value.trim().trim('"')
            if (rootValue.isBlank()) return@forEach
            if (isAllowedAssetRootValue(rootValue)) return@forEach

            val range = TextRange(
                declaration.textRange.startOffset + rootAssignment.startOffset,
                declaration.textRange.startOffset + rootAssignment.endOffset
            )
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message(
                    "diagnostic.assetRootPathRootNotAllowed",
                    displayDeclarationKeyword(keyword),
                    rootValue
                )
            ).range(range).create()
        }
    }

    // Call-signature utilities.
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

    private fun declarationHeaderText(declarationText: String): String? {
        val headerEnd = declarationText.indexOf('{').let { if (it >= 0) it else declarationText.length }
        val header = declarationText.substring(0, headerEnd).trim()
        return if (header.isBlank()) null else header
    }

    private fun findNamedAssignmentValue(headerText: String, key: String): NamedAssignmentMatch? {
        val match = NAMED_ASSIGNMENT_PATTERN.matcher(headerText)
        while (match.find()) {
            val currentKey = match.group(1) ?: continue
            if (!currentKey.equals(key, ignoreCase = true)) continue
            val valueStart = match.start(2)
            val valueEnd = match.end(2)
            if (valueStart < 0 || valueEnd <= valueStart) continue
            return NamedAssignmentMatch(
                key = currentKey,
                value = headerText.substring(valueStart, valueEnd),
                startOffset = valueStart,
                endOffset = valueEnd
            )
        }
        return null
    }

    private fun isAllowedAssetRootValue(root: String): Boolean {
        if (root.equals("game", ignoreCase = true)) return true
        if (root.startsWith("plugin.", ignoreCase = true) && root.length > "plugin.".length) return true
        if (root.startsWith("plugins.", ignoreCase = true) && root.length > "plugins.".length) return true
        return false
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

    // PSI/token traversal helpers.
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

    private fun displaySectionName(sectionName: String): String {
        return DISPLAY_SECTION_NAMES[sectionName] ?: sectionName.replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

    private fun canonicalSectionName(sectionName: String?): String? {
        if (sectionName == null) return null
        return SECTION_NAME_ALIASES[sectionName] ?: sectionName
    }

    private fun canonicalSectionNameForDeclaration(declarationKeyword: String, sectionName: String?): String? {
        val name = canonicalSectionName(sectionName) ?: return null
        if (sectionName == "results" && declarationKeyword !in DECLARATIONS_ALLOW_RESULTS_ALIAS) {
            return sectionName
        }
        return name
    }

    private fun displayDeclarationKeyword(keyword: String): String {
        return DISPLAY_DECLARATION_KEYWORDS[keyword] ?: keyword.replaceFirstChar { it.uppercase(Locale.ROOT) }
    }

    private fun suggestSettingsKey(rawKey: String): String? {
        return findClosestCandidate(rawKey, SETTINGS_KEY_CANONICAL, maxDistance = 2)
    }

    private fun suggestTypeName(rawType: String): String? {
        return findClosestCandidate(rawType, KNOWN_TYPES_CANONICAL, maxDistance = 2)
    }

    private fun suggestBaseOutputMember(rawMember: String): String? {
        val normalized = rawMember.lowercase(Locale.ROOT)
        if (normalized.isBlank()) return null

        var bestDistance = Int.MAX_VALUE
        var bestCandidate: String? = null
        BASE_OUTPUT_MEMBERS_CANONICAL.forEach { candidate ->
            val candidateLower = candidate.lowercase(Locale.ROOT)
            val directDistance = levenshteinDistance(normalized, candidateLower)
            val strippedDistance = if (candidateLower.startsWith("base") && candidateLower.length > 4) {
                levenshteinDistance(normalized, candidateLower.removePrefix("base"))
            } else {
                Int.MAX_VALUE
            }
            val score = minOf(directDistance, strippedDistance)
            if (score < bestDistance) {
                bestDistance = score
                bestCandidate = candidate
            }
        }
        if (bestCandidate == null) return null
        return if (bestDistance <= 3) bestCandidate else null
    }

    private fun validateVirtualFunctionAssetPath(value: String): String? {
        if (value.isBlank()) {
            return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetRequiresPath")
        }

        val pathRoot = extractPathRoot(value)
        if (pathRoot == null) {
            return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetRequiresPath")
        }
        if (!isAllowedVirtualFunctionAssetRoot(pathRoot)) {
            return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetPathRootNotAllowed", pathRoot)
        }
        return null
    }

    private fun extractPathRoot(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 3) {
            val unquoted = trimmed.substring(1, trimmed.length - 1).trim()
            if (unquoted.isEmpty()) return null
            return when {
                unquoted.startsWith("/") -> "Game"
                unquoted.startsWith("Game/") -> "Game"
                unquoted.startsWith("Engine/") -> "Engine"
                unquoted.startsWith("Plugin.") -> unquoted.substringBefore('/')
                unquoted.startsWith("Plugins.") -> unquoted.substringBefore('/')
                else -> null
            }
        }

        val pathMatch = PATH_CALL_PATTERN.matcher(trimmed)
        if (pathMatch.matches()) {
            val inside = trimmed.substringAfter('(').substringBeforeLast(')').trim()
            if (inside.isBlank()) return null
            val firstArg = splitTopLevel(inside, ',').firstOrNull()?.trim().orEmpty()
            if (firstArg.isBlank()) return null
            return firstArg.removePrefix("\"").removeSuffix("\"").trim()
        }
        return null
    }

    private fun isAllowedVirtualFunctionAssetRoot(root: String): Boolean {
        if (root.equals("game", ignoreCase = true)) return true
        if (root.equals("engine", ignoreCase = true)) return true
        if (root.startsWith("plugin.", ignoreCase = true) && root.length > "plugin.".length) return true
        if (root.startsWith("plugins.", ignoreCase = true) && root.length > "plugins.".length) return true
        return false
    }

    private fun findClosestCandidate(rawInput: String, candidates: Collection<String>, maxDistance: Int): String? {
        val normalized = rawInput.lowercase(Locale.ROOT)
        if (normalized.isBlank()) return null
        var bestDistance = Int.MAX_VALUE
        var bestCandidate: String? = null
        candidates.forEach { candidate ->
            val distance = levenshteinDistance(normalized, candidate.lowercase(Locale.ROOT))
            if (distance < bestDistance) {
                bestDistance = distance
                bestCandidate = candidate
            }
        }
        if (bestCandidate == null) return null
        return if (bestDistance <= maxDistance) bestCandidate else null
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in a.indices) {
            current[0] = i + 1
            for (j in b.indices) {
                val substitutionCost = if (a[i] == b[j]) 0 else 1
                current[j + 1] = minOf(
                    current[j] + 1,
                    previous[j + 1] + 1,
                    previous[j] + substitutionCost
                )
            }
            val tmp = previous
            previous = current
            current = tmp
        }
        return previous[b.length]
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

    private data class NamedAssignmentMatch(
        val key: String,
        val value: String,
        val startOffset: Int,
        val endOffset: Int
    )

    companion object {
        // Semantic validation data.
        private val SETTINGS_KEYS = setOf(
            "materialdomain", "domain", "shadingmodel", "blendmode", "rendertype", "translucencylightingmode",
            "lightingmode", "twosided", "wireframe", "ditheredlodtransition", "ditheropacitymask",
            "allownegativeemissivecolor", "castdynamicshadowasmasked", "responsiveaa", "screenspacereflections",
            "contactshadows", "disabledepthtest", "outputtranslucentvelocity", "tangentspacenormal", "fullyrough",
            "issky", "thinsurface", "numcustomizeduvs", "refractionmethod", "refractionmode"
        )
        private val SETTINGS_KEY_CANONICAL = listOf(
            "MaterialDomain", "Domain", "ShadingModel", "BlendMode", "RenderType", "TranslucencyLightingMode",
            "LightingMode", "TwoSided", "Wireframe", "DitheredLODTransition", "DitherOpacityMask",
            "AllowNegativeEmissiveColor", "CastDynamicShadowAsMasked", "ResponsiveAA", "ScreenSpaceReflections",
            "ContactShadows", "DisableDepthTest", "OutputTranslucentVelocity", "TangentSpaceNormal", "FullyRough",
            "IsSky", "ThinSurface", "NumCustomizedUVs", "RefractionMethod", "RefractionMode"
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
        private val BASE_OUTPUT_MEMBERS_CANONICAL = listOf(
            "MaterialAttributes", "Attributes", "BaseColor", "EmissiveColor", "Emissive", "Opacity", "OpacityMask",
            "Metallic", "Specular", "Roughness", "Normal", "AmbientOcclusion", "AO", "Refraction", "WorldPositionOffset",
            "WPO", "PixelDepthOffset", "PDO", "SubsurfaceColor", "ClearCoat", "ClearCoatRoughness", "CustomData0",
            "CustomData1", "DiffuseColor", "SpecularColor", "SurfaceThickness", "Displacement", "CustomizedUV0",
            "CustomizedUV1", "CustomizedUV2", "CustomizedUV3", "CustomizedUV4", "CustomizedUV5", "CustomizedUV6",
            "CustomizedUV7", "CustomizedUVs0", "CustomizedUVs1", "CustomizedUVs2", "CustomizedUVs3", "CustomizedUVs4",
            "CustomizedUVs5", "CustomizedUVs6", "CustomizedUVs7", "MOOAEncodedAttribute0", "MOOAEncodedAttribute1",
            "MOOAEncodedAttribute2", "MOOAEncodedAttribute3", "MOOAEncodedAttribute4", "Anisotropy", "Tangent"
        )

        // Type validation data.
        private val KNOWN_TYPES = DreamShaderLexer.TYPES.map { it.lowercase(Locale.ROOT) }.toSet()
        private val KNOWN_TYPES_CANONICAL = DreamShaderLexer.TYPES.toList()
        private val TYPE_QUALIFIERS = setOf("in", "out", "inout", "const", "static", "opt")

        // Regex: settings/output/type diagnostics.
        private val SETTINGS_ASSIGNMENT_PATTERN: Pattern = Pattern.compile(
            "(?m)\\b([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[A-Za-z_][A-Za-z0-9_.]*)"
        )
        private val NAMED_ASSIGNMENT_PATTERN: Pattern = Pattern.compile(
            "(?is)\\b([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[A-Za-z_][A-Za-z0-9_./-]*)"
        )
        private val VIRTUAL_FUNCTION_ASSET_ASSIGNMENT_PATTERN: Pattern = Pattern.compile(
            "(?mis)\\bAsset\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|Path\\s*\\([^\\n;{}]*\\)|[A-Za-z_][A-Za-z0-9_.]*)"
        )
        private val PATH_CALL_PATTERN: Pattern = Pattern.compile("(?is)Path\\s*\\(.*\\)")
        private val BASE_MEMBER_PATTERN: Pattern = Pattern.compile("\\bBase\\.([A-Za-z_][A-Za-z0-9_]*)")
        private val TYPED_DECLARATION_PATTERN: Pattern = Pattern.compile(
            "(?m)(?:^|;)\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|;|\\n)"
        )

        // Regex: call-signature diagnostics.
        private val PARAM_NAME_PATTERN: Pattern = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*$")
        private val OUT_QUALIFIER_PATTERN: Pattern = Pattern.compile("\\bout\\b")

        private val DSH_DISALLOWED_TOP_LEVEL_DECLARATIONS = setOf(
            "shader",
            "shaderfunction",
            "shaderlayer",
            "shaderlayerblend"
        )
        private val ASSET_DECLARATION_KEYWORDS = setOf(
            "shader",
            "shaderfunction",
            "shaderlayer",
            "shaderlayerblend"
        )
        private val DSF_ALLOWED_TOP_LEVEL_DECLARATIONS = setOf(
            "shaderfunction",
            "shaderlayer",
            "shaderlayerblend",
            "virtualfunction",
            "function",
            "graphfunction"
        )
        private val DSM_DISALLOWED_TOP_LEVEL_DECLARATIONS = setOf(
            "shaderfunction",
            "shaderlayer",
            "shaderlayerblend"
        )

        private val DISPLAY_DECLARATION_KEYWORDS = mapOf(
            "shader" to "Shader",
            "shaderfunction" to "ShaderFunction",
            "shaderlayer" to "ShaderLayer",
            "shaderlayerblend" to "ShaderLayerBlend",
            "virtualfunction" to "VirtualFunction",
            "function" to "Function",
            "graphfunction" to "GraphFunction",
            "namespace" to "Namespace"
        )
        private val DISPLAY_SECTION_NAMES = mapOf(
            "properties" to "Properties",
            "inputs" to "Inputs",
            "outputs" to "Outputs",
            "results" to "Results",
            "settings" to "Settings",
            "options" to "Options",
            "graph" to "Graph"
        )

        private val SECTION_NAME_ALIASES = mapOf(
            "results" to "outputs"
        )

        private val DECLARATIONS_ALLOW_RESULTS_ALIAS = setOf(
            "shaderfunction",
            "virtualfunction"
        )

        private val DECLARATION_ALLOWED_SECTIONS = mapOf(
            "shader" to setOf("properties", "outputs", "settings", "graph"),
            "shaderfunction" to setOf("properties", "inputs", "outputs", "settings", "graph"),
            "shaderlayer" to setOf("properties", "inputs", "outputs", "settings", "graph"),
            "shaderlayerblend" to setOf("properties", "inputs", "outputs", "settings", "graph"),
            "virtualfunction" to setOf("properties", "inputs", "outputs", "settings", "options"),
            "function" to emptySet(),
            "graphfunction" to emptySet()
        )

        private val DECLARATION_REQUIRED_SECTIONS = mapOf(
            "shader" to setOf("graph"),
            "shaderfunction" to setOf("graph"),
            "shaderlayer" to setOf("outputs"),
            "shaderlayerblend" to setOf("inputs", "outputs")
        )

        private val UNSUPPORTED_GRAPH_LOOP_KEYWORDS = setOf("for", "while", "do")
        private val UNSUPPORTED_GRAPH_SWITCH_KEYWORDS = setOf("switch", "case", "default")
        private val UNSUPPORTED_GRAPH_FLOW_KEYWORDS = setOf("break", "continue")
        private const val UNSUPPORTED_GRAPH_RETURN_KEYWORD = "return"
    }
}
