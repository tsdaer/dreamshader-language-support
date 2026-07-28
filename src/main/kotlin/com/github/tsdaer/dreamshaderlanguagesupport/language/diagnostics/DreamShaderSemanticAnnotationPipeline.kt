package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeDiagnosticsPass
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeSettingsRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguageRules
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionManifest
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.DreamShaderSemanticTokenClassifier
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLanguageKeywords
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderPackageRootImportAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiUtil
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.github.tsdaer.dreamshaderlanguagesupport.language.templates.DreamShaderTemplateService
import com.intellij.codeInsight.FileModificationService
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.*
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
import java.util.regex.Pattern
import kotlin.io.path.invariantSeparatorsPathString

/**
 * DreamShader 诊断与语义高亮的中心流水线。
 *
 * 执行顺序与职责：
 * 1. 语法诊断（字符串/注释未闭合、结构错误、花括号平衡）
 * 2. Section 形状诊断（文件角色与声明结构约束）
 * 3. 语义诊断（设置项、输出成员、类型、调用签名、导入解析）
 * 4. Bridge 诊断映射
 * 5. 对非文件级 PSI 元素追加语义 token 标注
 *
 * 本流水线也负责相关 quick fix，包括导入目标创建、
 * 包根导入恢复，以及语义拼写建议修复。
 */
internal class DreamShaderSemanticAnnotationPipeline {
    fun annotate(element: PsiElement, holder: AnnotationHolder) {
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

    fun annotateFileDiagnostics(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        val inputs = cachedFileDiagnosticInputs(file)
        val sourceText = inputs.sourceText
        val tokens = inputs.tokens
        val topLevelDeclarations = inputs.topLevelDeclarations
        val declarationContexts = inputs.declarationContexts

        DreamShaderSyntaxDiagnosticsPass.annotate(sourceText, tokens, holder)
        annotateSectionShapeDiagnostics(file, sourceText, tokens, topLevelDeclarations, declarationContexts, holder)
        annotateSemanticDiagnostics(file, sourceText, tokens, topLevelDeclarations, declarationContexts, holder)
        DreamShaderBridgeDiagnosticsPass.annotate(file, holder)
    }

    private fun cachedFileDiagnosticInputs(file: DreamShaderPsiFile): FileDiagnosticInputs {
        return CachedValuesManager.getManager(file.project).getCachedValue(
            file,
            FILE_DIAGNOSTIC_INPUTS_KEY,
            {
                CachedValueProvider.Result.create(
                    computeFileDiagnosticInputsStatic(file),
                    file
                )
            },
            false
        )
    }

    // Section 形状诊断：文件角色规则与声明级结构规则。
    private fun annotateSectionShapeDiagnostics(
        file: DreamShaderPsiFile,
        sourceText: String,
        tokens: List<DreamShaderLexedToken>,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        val extension = file.virtualFile?.extension?.lowercase(Locale.ROOT)
        if (extension == "dsf") {
            topLevelDeclarations
                .filter { declaration -> declaration.keywordText() !in DreamShaderLanguageRules.dsfAllowedTopLevelDeclarations }
                .forEach { declaration ->
                    val declarationKeyword = DreamShaderLanguageRules.displayDeclarationKeywords[declaration.keywordText()] ?: declaration.keywordText().orEmpty()
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message(
                            "diagnostic.topLevelDeclarationNotAllowedInDsf",
                            declarationKeyword
                        )
                    ).range(declaration.nameIdentifier ?: declaration).create()
                }
        }
        if (extension == "dsm") {
            topLevelDeclarations
                .filter { declaration -> declaration.keywordText() in DreamShaderLanguageRules.dsmDisallowedTopLevelDeclarations }
                .forEach { declaration ->
                    val declarationKeyword = DreamShaderLanguageRules.displayDeclarationKeywords[declaration.keywordText()] ?: declaration.keywordText().orEmpty()
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
                .filter { declaration -> declaration.keywordText() in DreamShaderLanguageRules.dshDisallowedTopLevelDeclarations }
                .forEach { declaration ->
                    val declarationKeyword = DreamShaderLanguageRules.displayDeclarationKeywords[declaration.keywordText()] ?: declaration.keywordText().orEmpty()
                    holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message(
                            "diagnostic.topLevelDeclarationNotAllowedInDsh",
                            declarationKeyword
                        )
                    ).range(declaration.nameIdentifier ?: declaration).create()
                }
        }

        declarationContexts.forEach { context ->
            when (context.keyword) {
                "virtualfunction" -> annotateVirtualFunctionRules(sourceText, context, holder)
                "shaderlayer", "shaderlayerblend" -> annotateLayerRules(context, holder)
            }
            annotateDeclarationSectionSchemaRules(context, holder)
        }

        annotateNamespaceRules(tokens, holder)
    }

    // 语义诊断：设置项、类型、输出、表达式、调用与导入校验。
    private fun annotateSemanticDiagnostics(
        file: DreamShaderPsiFile,
        sourceText: String,
        tokens: List<DreamShaderLexedToken>,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        annotateDuplicateDeclarationNameDiagnostics(topLevelDeclarations, holder)
        annotateSettingsDiagnostics(topLevelDeclarations, declarationContexts, holder)
        annotateBaseOutputMemberDiagnostics(declarationContexts, holder)
        annotateSubstrateBindingExclusivityDiagnostics(declarationContexts, holder)
        annotateUnknownTypeDiagnostics(declarationContexts, holder)
        annotateUnknownDeclarationParameterTypeDiagnostics(declarationContexts, holder)
        annotateUnknownBodyLocalTypeDiagnostics(sourceText, declarationContexts, holder)
        annotateConstTextureDefaultAssetDiagnostics(declarationContexts, holder)
        annotateGeneralResourcePathDiagnostics(declarationContexts, holder)
        annotateOptionalInputDefaultDiagnostics(declarationContexts, holder)
        annotateUnknownExpressionClassDiagnostics(file, sourceText, topLevelDeclarations, holder)
        annotateSubstrateExpressionDiagnostics(declarationContexts, holder)
        annotateUnsupportedGraphLoopDiagnostics(declarationContexts, holder)
        annotateUnsupportedGraphSwitchDiagnostics(declarationContexts, holder)
        annotateUnsupportedGraphBreakContinueDiagnostics(declarationContexts, holder)
        annotateUnsupportedGraphReturnDiagnostics(declarationContexts, holder)
        annotateMissingOutArgumentDiagnostics(file, sourceText, tokens, topLevelDeclarations, holder)
        annotateAssetRootPathDiagnostics(topLevelDeclarations, holder)
        annotateUnresolvedImportDiagnostics(file, tokens, holder)
    }

    private fun annotateDuplicateDeclarationNameDiagnostics(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        annotateDuplicateDeclarationNameDiagnosticsInScope(topLevelDeclarations, holder)
    }

    private fun annotateDuplicateDeclarationNameDiagnosticsInScope(
        declarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        if (declarations.isEmpty()) return

        declarations
            .mapNotNull { declaration ->
                val name = declaration.declarationName()?.trim().orEmpty()
                if (name.isBlank()) return@mapNotNull null
                declaration to name
            }
            .groupBy { (_, name) -> name }
            .forEach { (duplicateName, entries) ->
                if (entries.size <= 1) return@forEach
                val occupiedNames = entries.mapNotNull { (_, name) ->
                    name.trim().takeIf { it.isNotBlank() }
                }.toMutableSet()
                entries.drop(1).forEach { (declaration, _) ->
                    val annotation = holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        DreamShaderBundle.message("diagnostic.duplicateDeclarationName", duplicateName)
                    ).range(declaration.nameIdentifier ?: declaration)
                    suggestUniqueDeclarationName(duplicateName, occupiedNames)?.let { replacement ->
                        annotation.withFix(createRenameDuplicateDeclarationQuickFix(declaration, replacement))
                        occupiedNames.add(replacement)
                    }
                    annotation.create()
                }
            }

        declarations.forEach { declaration ->
            if (declaration.keywordText() != "namespace") return@forEach
            val children = directChildDeclarations(declaration)
            annotateDuplicateDeclarationNameDiagnosticsInScope(children, holder)
        }
    }

    private fun createRenameDuplicateDeclarationQuickFix(
        declaration: DreamShaderDeclaration,
        replacement: String
    ): IntentionAction {
        val declarationPointer: SmartPsiElementPointer<DreamShaderDeclaration> = SmartPointerManager.createPointer(declaration)
        return object : IntentionAction {
            override fun getText(): String =
                DreamShaderBundle.message("quickfix.duplicateDeclarationRenameTo", replacement)

            override fun getFamilyName(): String =
                DreamShaderBundle.message("quickfix.family.semantic")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                val target = declarationPointer.element ?: return false
                return target.isValid && replacement.isNotBlank() && target.declarationName() != replacement
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val target = declarationPointer.element ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(target)) return
                target.setName(replacement)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    // Section 形状诊断：声明作用域下的 section 兼容性与必需项检查。
    private fun annotateVirtualFunctionRules(sourceText: String, context: DeclarationContext, holder: AnnotationHolder) {
        val graphSection = context.firstSection("graph")
        val codeSectionRange = context.topLevelSectionHeaderRange("code")
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
                annotation.range(context.declaration)
            }
            annotation.create()
        }
    }

    private fun annotateLayerRules(context: DeclarationContext, holder: AnnotationHolder) {
        val outputsSection = context.firstSection("outputs")
        val outputDeclarations = context.topLevelTypedDeclarations(outputsSection)
        val hasSingleMaterialAttributesOutput =
            outputDeclarations.size == 1 && outputDeclarations.single().equals("materialattributes", ignoreCase = true)
        if (!hasSingleMaterialAttributesOutput) {
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.layerRequiresSingleMaterialAttributesOutput")
            ).range(outputsSection ?: context.declaration).create()
        }

        if (context.keyword == "shaderlayerblend") {
            val inputsSection = context.firstSection("inputs")
            val inputDeclarations = context.topLevelTypedDeclarations(inputsSection)
            val materialAttributesInputs = inputDeclarations
                .count { it.equals("materialattributes", ignoreCase = true) }
            // 上游收紧：ShaderLayerBlend 必须刚好两个输入，且都必须是 MaterialAttributes。
            if (inputDeclarations.size != 2 || materialAttributesInputs != 2) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.layerBlendRequiresTwoMaterialAttributesInputs")
                ).range(inputsSection ?: context.declaration).create()
            }
        } else {
            val inputsSection = context.firstSection("inputs")
            val inputDeclarations = context.topLevelTypedDeclarations(inputsSection)
            // 上游收紧：ShaderLayer 最多一个输入，且若存在必须是 MaterialAttributes。
            val violatesInputShape = inputDeclarations.size > 1 ||
                (inputDeclarations.size == 1 && !inputDeclarations.single().equals("materialattributes", ignoreCase = true))
            if (violatesInputShape) {
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.layerRequiresMaterialAttributesInput")
                ).range(inputsSection ?: context.declaration).create()
            }
        }
    }

    private fun annotateDeclarationSectionSchemaRules(context: DeclarationContext, holder: AnnotationHolder) {
        val declaration = context.declaration
        val keyword = context.keyword ?: return
        val allowedSections = DreamShaderLanguageRules.declarationAllowedSections[keyword] ?: return
        val requiredSections = DreamShaderLanguageRules.declarationRequiredSections[keyword].orEmpty()
        val sections = context.directSections
        val groupedByName = context.sectionsByCanonicalNameForDeclaration

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
            val sectionName = context.canonicalSectionNameForDeclaration(section) ?: return@forEach
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
                declaration = declaration,
                optionSections = groupedByName["options"].orEmpty(),
                settingsAliasSections = groupedByName["settings"].orEmpty(),
                holder = holder
            )
            annotateVirtualFunctionDescriptionOptionRules(
                declaration = declaration,
                optionSections = groupedByName["options"].orEmpty(),
                settingsAliasSections = groupedByName["settings"].orEmpty(),
                holder = holder
            )
        }
    }

    private fun annotateVirtualFunctionAssetOptionRules(
        declaration: DreamShaderDeclaration,
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
                val valueRange = TextRange(body.startOffset + matcher.start(1), body.startOffset + matcher.end(1))
                val validationError = validateVirtualFunctionAssetPath(trimmed)
                if (validationError != null) {
                    val pathRoot = extractPathRoot(trimmed)
                    val isUnknownRoot = pathRoot != null && !isAllowedVirtualFunctionAssetRoot(pathRoot)
                    val annotation = holder.newAnnotation(
                        HighlightSeverity.ERROR,
                        validationError
                    ).range(valueRange)
                    if (isUnknownRoot) {
                        annotation.withFix(createReplaceVirtualFunctionAssetRootWithGameQuickFix(section, valueRange, trimmed))
                    }
                    if (isPathCallMissingObjectSegment(trimmed)) {
                        annotation.withFix(
                            createCompleteAssetPathObjectSegmentQuickFix(
                                replacementRange = valueRange,
                                rawValue = trimmed
                            )
                        )
                    }
                    annotation.create()
                }
                val invalidObjectSuffix = validateAssetObjectSegmentSuffix(trimmed)
                if (invalidObjectSuffix != null) {
                    holder.newAnnotation(
                        HighlightSeverity.WARNING,
                        invalidObjectSuffix
                    ).range(valueRange).create()
                }
            }
        }
        if (!hasAsset) {
            val rangeTarget = candidateSections.firstOrNull() ?: declaration.nameIdentifier ?: declaration
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetRequired")
            ).range(rangeTarget).create()
        }
    }

    private fun annotateVirtualFunctionDescriptionOptionRules(
        declaration: DreamShaderDeclaration,
        optionSections: List<DreamShaderSection>,
        settingsAliasSections: List<DreamShaderSection>,
        holder: AnnotationHolder
    ) {
        val candidateSections = optionSections + settingsAliasSections
        var hasDescription = false
        candidateSections.forEach { section ->
            val body = sectionBody(section) ?: return@forEach
            val matcher = VIRTUAL_FUNCTION_DESCRIPTION_ASSIGNMENT_PATTERN.matcher(body.text)
            while (matcher.find()) {
                hasDescription = true
                val value = matcher.group(1) ?: continue
                val trimmed = value.trim()
                val valueRange = TextRange(body.startOffset + matcher.start(1), body.startOffset + matcher.end(1))
                if (!isQuotedStringLiteral(trimmed)) {
                    holder.newAnnotation(
                        HighlightSeverity.WARNING,
                        DreamShaderBundle.message("diagnostic.virtualFunctionOptionDescriptionMustBeQuoted")
                    ).range(valueRange)
                        .withFix(createQuoteVirtualFunctionDescriptionQuickFix(section, valueRange, trimmed))
                        .create()
                    continue
                }

                val description = trimmed.substring(1, trimmed.length - 1).trim()
                if (description.isNotEmpty()) continue
                holder.newAnnotation(
                    HighlightSeverity.WARNING,
                    DreamShaderBundle.message("diagnostic.virtualFunctionOptionDescriptionEmpty")
                ).range(valueRange)
                    .withFix(createFillVirtualFunctionDescriptionQuickFix(section, valueRange))
                    .create()
            }
        }
        if (!hasDescription) {
            val fallbackSection = candidateSections.firstOrNull()
            val annotationTarget = fallbackSection ?: declaration.nameIdentifier ?: declaration
            val annotation = holder.newAnnotation(
                HighlightSeverity.WARNING,
                DreamShaderBundle.message("diagnostic.virtualFunctionOptionDescriptionRecommended")
            ).range(annotationTarget)
            if (fallbackSection != null) {
                annotation.withFix(createAddVirtualFunctionDescriptionQuickFix(fallbackSection))
            }
            annotation.create()
        }
    }

    private fun createAddVirtualFunctionDescriptionQuickFix(section: DreamShaderSection): IntentionAction {
        val pointer: SmartPsiElementPointer<DreamShaderSection> = SmartPointerManager.createPointer(section)
        return object : IntentionAction {
            override fun getText(): String =
                DreamShaderBundle.message("quickfix.virtualFunctionOptionDescriptionAdd")

            override fun getFamilyName(): String =
                DreamShaderBundle.message("quickfix.family.virtualFunction")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return pointer.element?.isValid == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetSection = pointer.element ?: return
                val targetFile = targetSection.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetSection)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val body = sectionBody(targetSection) ?: return
                val insertionOffset = descriptionInsertionOffset(body)
                val insertionText = buildDescriptionInsertionText(targetSection, body, targetFile.text)
                document.insertString(insertionOffset, insertionText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createQuoteVirtualFunctionDescriptionQuickFix(
        section: DreamShaderSection,
        valueRange: TextRange,
        rawValue: String
    ): IntentionAction {
        val sectionPointer: SmartPsiElementPointer<DreamShaderSection> = SmartPointerManager.createPointer(section)
        val relativeRange = TextRange(
            valueRange.startOffset - section.textRange.startOffset,
            valueRange.endOffset - section.textRange.startOffset
        )
        return object : IntentionAction {
            override fun getText(): String =
                DreamShaderBundle.message("quickfix.virtualFunctionOptionDescriptionQuote")

            override fun getFamilyName(): String =
                DreamShaderBundle.message("quickfix.family.virtualFunction")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return sectionPointer.element?.isValid == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetSection = sectionPointer.element ?: return
                val targetFile = targetSection.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetSection)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val replacement = "\"" + rawValue.trim().trim('"') + "\""
                replaceSectionRelativeRange(document, targetSection, relativeRange, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createReplaceVirtualFunctionAssetRootWithGameQuickFix(
        section: DreamShaderSection,
        valueRange: TextRange,
        rawValue: String
    ): IntentionAction {
        val sectionPointer: SmartPsiElementPointer<DreamShaderSection> = SmartPointerManager.createPointer(section)
        val relativeRange = TextRange(
            valueRange.startOffset - section.textRange.startOffset,
            valueRange.endOffset - section.textRange.startOffset
        )
        return object : IntentionAction {
            override fun getText(): String =
                DreamShaderBundle.message("quickfix.virtualFunctionOptionAssetReplaceRootWithGame")

            override fun getFamilyName(): String =
                DreamShaderBundle.message("quickfix.family.virtualFunction")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return sectionPointer.element?.isValid == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetSection = sectionPointer.element ?: return
                val targetFile = targetSection.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetSection)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val replacement = replaceVirtualFunctionAssetRootWithGame(rawValue) ?: return
                replaceSectionRelativeRange(document, targetSection, relativeRange, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun replaceVirtualFunctionAssetRootWithGame(rawValue: String): String? {
        val trimmed = rawValue.trim()
        if (isQuotedStringLiteral(trimmed)) {
            val unquoted = trimmed.substring(1, trimmed.length - 1).trim()
            val replacementUnquoted = replaceQuotedAssetRootWithGame(unquoted) ?: return null
            return "\"$replacementUnquoted\""
        }
        val matcher = PATH_CALL_WITH_CAPTURE_PATTERN.matcher(trimmed)
        if (!matcher.matches()) return null
        val prefix = matcher.group(1) ?: return null
        val inside = matcher.group(2) ?: return null
        val suffix = matcher.group(3) ?: ")"
        val args = splitTopLevel(inside, ',').map { it.trim() }.toMutableList()
        if (args.isEmpty()) return null
        val firstArg = args[0]
        args[0] = if (firstArg.startsWith("\"") && firstArg.endsWith("\"")) "\"Game\"" else "Game"
        return prefix + args.joinToString(", ") + suffix
    }

    private fun replaceQuotedAssetRootWithGame(unquotedPath: String): String? {
        val normalized = unquotedPath.trim().removePrefix("/")
        if (normalized.isBlank()) return null
        if (!normalized.contains('/')) return null
        val segments = normalized.split('/', limit = 2)
        if (segments.size < 2) return null
        val tail = segments[1].trim()
        if (tail.isBlank()) return null
        return "Game/$tail"
    }

    private fun isPathCallMissingObjectSegment(rawValue: String): Boolean {
        val trimmed = rawValue.trim()
        val matcher = PATH_CALL_WITH_CAPTURE_PATTERN.matcher(trimmed)
        if (!matcher.matches()) return false
        val inside = matcher.group(2)?.trim().orEmpty()
        if (inside.isBlank()) return false
        val args = splitTopLevel(inside, ',').map { it.trim() }
        return args.size == 1 && args[0].isNotBlank()
    }

    private fun completePathCallWithObjectSegment(rawValue: String): String? {
        val trimmed = rawValue.trim()
        val matcher = PATH_CALL_WITH_CAPTURE_PATTERN.matcher(trimmed)
        if (!matcher.matches()) return null
        val prefix = matcher.group(1) ?: return null
        val inside = matcher.group(2)?.trim().orEmpty()
        val suffix = matcher.group(3) ?: ")"
        if (inside.isBlank()) return null
        val args = splitTopLevel(inside, ',').map { it.trim() }.toMutableList()
        if (args.size != 1 || args[0].isBlank()) return null
        args.add("Textures/T_AutoAsset")
        return prefix + args.joinToString(", ") + suffix
    }

    private fun createFillVirtualFunctionDescriptionQuickFix(
        section: DreamShaderSection,
        valueRange: TextRange
    ): IntentionAction {
        val sectionPointer: SmartPsiElementPointer<DreamShaderSection> = SmartPointerManager.createPointer(section)
        val relativeRange = TextRange(
            valueRange.startOffset - section.textRange.startOffset,
            valueRange.endOffset - section.textRange.startOffset
        )
        return object : IntentionAction {
            override fun getText(): String =
                DreamShaderBundle.message("quickfix.virtualFunctionOptionDescriptionFillDefault")

            override fun getFamilyName(): String =
                DreamShaderBundle.message("quickfix.family.virtualFunction")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return sectionPointer.element?.isValid == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetSection = sectionPointer.element ?: return
                val targetFile = targetSection.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetSection)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                replaceSectionRelativeRange(
                    document = document,
                    section = targetSection,
                    relativeRange = relativeRange,
                    replacement = "\"Bridge-compatible virtual function\""
                )
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun replaceSectionRelativeRange(
        document: com.intellij.openapi.editor.Document,
        section: DreamShaderSection,
        relativeRange: TextRange,
        replacement: String
    ) {
        val sectionStart = section.textRange.startOffset
        val start = (sectionStart + relativeRange.startOffset).coerceIn(0, document.textLength)
        val end = (sectionStart + relativeRange.endOffset).coerceIn(start, document.textLength)
        document.replaceString(start, end, replacement)
    }

    private fun descriptionInsertionOffset(body: SectionBody): Int {
        val trailingTriviaLength = body.text
            .takeLastWhile { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
            .length
        return body.startOffset + body.text.length - trailingTriviaLength
    }

    private fun buildDescriptionInsertionText(
        section: DreamShaderSection,
        body: SectionBody,
        fileText: String
    ): String {
        val bodyText = body.text
        val trailingTriviaLength = bodyText
            .takeLastWhile { it == ' ' || it == '\t' || it == '\r' || it == '\n' }
            .length
        val bodyContent = bodyText.dropLast(trailingTriviaLength)
        val sectionIndent = lineIndentAt(fileText, section.textRange.startOffset)
        val entryIndent = firstBodyEntryIndent(bodyText) ?: "$sectionIndent    "
        val needsLeadingNewline = bodyContent.isNotEmpty() && !bodyContent.endsWith('\n') && !bodyContent.endsWith('\r')
        return buildString {
            if (needsLeadingNewline) append('\n')
            append(entryIndent)
            append("Description = \"Bridge-compatible virtual function\";")
        }
    }

    private fun firstBodyEntryIndent(bodyText: String): String? {
        val lines = bodyText.lineSequence()
        for (line in lines) {
            if (line.isBlank()) continue
            return line.takeWhile { it == ' ' || it == '\t' }
        }
        return null
    }

    private fun lineIndentAt(text: String, offset: Int): String {
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOf('\n', (safeOffset - 1).coerceAtLeast(0)).let { if (it < 0) 0 else it + 1 }
        return text.substring(lineStart, safeOffset).takeWhile { it == ' ' || it == '\t' }
    }

    private fun annotateNamespaceRules(tokens: List<DreamShaderLexedToken>, holder: AnnotationHolder) {
        val allowed = setOf("function", "graphfunction", "namespace")
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
        tokens: List<DreamShaderLexedToken>,
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

    /**
     * 解析 Bridge `settings.json` 提供的枚举别名，按诊断使用的小写键展开
     * （含同义键 materialdomain/domain、blendmode/rendertype）。
     * 缺失或为空时返回空表，由调用方回退到硬编码校验器。
     */
    private fun resolveBridgeSettingValueOverrides(
        topLevelDeclarations: List<DreamShaderDeclaration>
    ): Map<String, Set<String>> {
        val project = topLevelDeclarations.firstOrNull()?.project ?: return emptyMap()
        val repository = project.getService(DreamShaderBridgeSettingsRepository::class.java) ?: return emptyMap()
        val result = mutableMapOf<String, Set<String>>()
        fun put(targetKeys: List<String>, bridgeKey: String) {
            val aliases = repository.allowedAliasesForKey(bridgeKey)
            if (aliases.isNotEmpty()) targetKeys.forEach { result[it] = aliases }
        }
        put(listOf("materialdomain", "domain"), "materialdomain")
        put(listOf("shadingmodel"), "shadingmodel")
        put(listOf("blendmode", "rendertype"), "blendmode")
        return result
    }

    // 语义诊断：设置项、Base 输出成员、类型名与表达式类检查。
    private fun annotateSettingsDiagnostics(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        val bridgeValueOverrides = resolveBridgeSettingValueOverrides(topLevelDeclarations)
        declarationContexts.forEach { context ->
            val declarationKeyword = context.keyword
            context.directSections
                .filter {
                    val sectionName = context.canonicalSectionName(it)
                    when {
                        sectionName == "options" -> false
                        declarationKeyword == "virtualfunction" && sectionName == "settings" -> false
                        else -> sectionName == "settings"
                    }
                }
                .forEach { section ->
                    val body = context.sectionBody(section) ?: return@forEach
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
                            val annotation = holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                message
                            ).range(keyRange)
                            if (suggestion != null) {
                                annotation.withFix(
                                    createReplaceSettingsKeyQuickFix(
                                        section = section,
                                        keyRange = keyRange,
                                        replacement = suggestion
                                    )
                                )
                            }
                            annotation.create()
                            continue
                        }

                        val rawValue = matcher.group(2) ?: continue
                        val value = rawValue.trim().trim('"')
                        val bridgeValues = bridgeValueOverrides[keyLower]
                        val isValid = if (bridgeValues != null && bridgeValues.isNotEmpty()) {
                            bridgeValues.any { it.equals(value, ignoreCase = true) }
                        } else {
                            val validator = SETTING_VALUE_VALIDATORS[keyLower] ?: continue
                            validator(value)
                        }
                        if (!isValid) {
                            val valueRange = TextRange(body.startOffset + matcher.start(2), body.startOffset + matcher.end(2))
                            val annotation = holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                DreamShaderBundle.message("diagnostic.invalidSettingValue", value, key)
                            ).range(valueRange)
                            when (keyLower) {
                                "twosided" -> annotation.withFix(
                                    createReplaceSettingsValueQuickFix(
                                        section = section,
                                        valueRange = valueRange,
                                        replacement = "true",
                                        messageKey = "quickfix.settingsReplaceWithTrue"
                                    )
                                )
                                "numcustomizeduvs" -> annotation.withFix(
                                    createReplaceSettingsValueQuickFix(
                                        section = section,
                                        valueRange = valueRange,
                                        replacement = "0",
                                        messageKey = "quickfix.settingsReplaceWithZero"
                                    )
                                )
                            }
                            annotation.create()
                        }
                    }
                }
        }
    }

    private fun createReplaceSettingsKeyQuickFix(
        section: DreamShaderSection,
        keyRange: TextRange,
        replacement: String
    ): IntentionAction {
        val sectionPointer: SmartPsiElementPointer<DreamShaderSection> = SmartPointerManager.createPointer(section)
        val relativeRange = TextRange(
            keyRange.startOffset - section.textRange.startOffset,
            keyRange.endOffset - section.textRange.startOffset
        )
        return object : IntentionAction {
            override fun getText(): String = DreamShaderBundle.message("quickfix.settingsReplaceWithSuggestion", replacement)

            override fun getFamilyName(): String = DreamShaderBundle.message("quickfix.family.settings")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return sectionPointer.element?.isValid == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetSection = sectionPointer.element ?: return
                val targetFile = targetSection.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetSection)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                replaceSectionRelativeRange(document, targetSection, relativeRange, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createReplaceSettingsValueQuickFix(
        section: DreamShaderSection,
        valueRange: TextRange,
        replacement: String,
        messageKey: String
    ): IntentionAction {
        val sectionPointer: SmartPsiElementPointer<DreamShaderSection> = SmartPointerManager.createPointer(section)
        val relativeRange = TextRange(
            valueRange.startOffset - section.textRange.startOffset,
            valueRange.endOffset - section.textRange.startOffset
        )
        return object : IntentionAction {
            override fun getText(): String = DreamShaderBundle.message(messageKey)

            override fun getFamilyName(): String = DreamShaderBundle.message("quickfix.family.settings")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return sectionPointer.element?.isValid == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetSection = sectionPointer.element ?: return
                val targetFile = targetSection.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetSection)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                replaceSectionRelativeRange(document, targetSection, relativeRange, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun annotateBaseOutputMemberDiagnostics(
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        declarationContexts.forEach { context ->
            context.directSections
                .filter {
                    val sectionName = context.canonicalSectionName(it)
                    sectionName == "outputs" || sectionName == "graph"
                }
                .forEach { section ->
                    val body = context.sectionBody(section) ?: return@forEach
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
                        val annotation = holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            message
                        ).range(range)
                        if (suggestion != null) {
                            annotation.withFix(
                                createReplaceSectionRangeQuickFix(
                                    section = section,
                                    targetRange = range,
                                    replacement = "Base.$suggestion",
                                    text = DreamShaderBundle.message("quickfix.baseOutputReplaceWithSuggestion", suggestion),
                                    family = DreamShaderBundle.message("quickfix.family.semantic")
                                )
                            )
                        }
                        annotation.create()
                    }
                }
        }
    }

    private fun annotateSubstrateBindingExclusivityDiagnostics(
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        declarationContexts.forEach { context ->
            val frontMaterialRanges = mutableListOf<TextRange>()
            val materialAttributesRanges = mutableListOf<TextRange>()
            context.directSections
                .filter {
                    val sectionName = context.canonicalSectionName(it)
                    sectionName == "outputs" || sectionName == "graph"
                }
                .forEach { section ->
                    val body = context.sectionBody(section) ?: return@forEach
                    val matcher = BASE_BINDING_TARGET_PATTERN.matcher(body.text)
                    while (matcher.find()) {
                        val member = matcher.group(1)?.lowercase(Locale.ROOT) ?: continue
                        val range = TextRange(body.startOffset + matcher.start(), body.startOffset + matcher.end(1))
                        when (member) {
                            "frontmaterial" -> frontMaterialRanges.add(range)
                            "materialattributes" -> materialAttributesRanges.add(range)
                        }
                    }
                }
            if (frontMaterialRanges.isEmpty() || materialAttributesRanges.isEmpty()) return@forEach
            // Base.FrontMaterial 绑定到 Substrate，与 Base.MaterialAttributes 互斥；在冲突的目标上各报一次。
            (frontMaterialRanges + materialAttributesRanges).forEach { range ->
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.frontMaterialMaterialAttributesExclusive")
                ).range(range).create()
            }
        }
    }

    private fun annotateUnknownTypeDiagnostics(
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        declarationContexts.forEach { context ->
            context.directSections
                .filter {
                    val sectionName = context.canonicalSectionName(it)
                    sectionName == "inputs" || sectionName == "outputs" || sectionName == "properties"
                }
                .forEach { section ->
                    val body = context.sectionBody(section) ?: return@forEach
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
                        val annotation = holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            message
                        ).range(range)
                        if (suggestion != null) {
                            annotation.withFix(
                                createReplaceSectionRangeQuickFix(
                                    section = section,
                                    targetRange = range,
                                    replacement = suggestion,
                                    text = DreamShaderBundle.message("quickfix.typeReplaceWithSuggestion", suggestion),
                                    family = DreamShaderBundle.message("quickfix.family.semantic")
                                )
                            )
                        }
                        annotation.create()
                    }
                }
        }
    }

    private fun annotateUnknownDeclarationParameterTypeDiagnostics(
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        declarationContexts.forEach { context ->
            val declaration = context.declaration
            val keyword = context.keyword
            if (keyword != "function" && keyword != "graphfunction") return@forEach
            val signature = context.parsedSignature ?: return@forEach
            signature.params.forEach { param ->
                val typeName = param.typeName ?: return@forEach
                val typeRange = param.typeRangeInDeclaration ?: return@forEach
                val lower = typeName.lowercase(Locale.ROOT)
                if (lower in KNOWN_TYPES || lower in TYPE_QUALIFIERS) return@forEach
                val range = TextRange(
                    declaration.textRange.startOffset + typeRange.startOffset,
                    declaration.textRange.startOffset + typeRange.endOffset
                )
                val suggestion = suggestTypeName(typeName)
                val message = if (suggestion != null) {
                    DreamShaderBundle.message("diagnostic.unknownTypeWithSuggestion", typeName, suggestion)
                } else {
                    DreamShaderBundle.message("diagnostic.unknownType", typeName)
                }
                val annotation = holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    message
                ).range(range)
                if (suggestion != null) {
                    annotation.withFix(
                        createReplaceDeclarationRangeQuickFix(
                            declaration = declaration,
                            targetRangeInDeclaration = typeRange,
                            replacement = suggestion,
                            text = DreamShaderBundle.message("quickfix.typeReplaceWithSuggestion", suggestion),
                            family = DreamShaderBundle.message("quickfix.family.semantic")
                        )
                    )
                }
                annotation.create()
            }
        }
    }

    private fun annotateUnknownBodyLocalTypeDiagnostics(
        sourceText: String,
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        graphConstrainedBodyRanges(declarationContexts).forEach { bodyRange ->
            val bodyText = sourceText.substring(bodyRange.startOffset, bodyRange.endOffset)
            val matcher = TYPED_DECLARATION_PATTERN.matcher(bodyText)
            while (matcher.find()) {
                val type = matcher.group(1) ?: continue
                val lower = type.lowercase(Locale.ROOT)
                if (lower in KNOWN_TYPES || lower in TYPE_QUALIFIERS) continue
                val range = TextRange(bodyRange.startOffset + matcher.start(1), bodyRange.startOffset + matcher.end(1))
                val suggestion = suggestTypeName(type)
                val message = if (suggestion != null) {
                    DreamShaderBundle.message("diagnostic.unknownTypeWithSuggestion", type, suggestion)
                } else {
                    DreamShaderBundle.message("diagnostic.unknownType", type)
                }
                val annotation = holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    message
                ).range(range)
                if (suggestion != null) {
                    annotation.withFix(
                        createReplaceFileRangeQuickFix(
                            replacementRange = range,
                            replacement = suggestion,
                            text = DreamShaderBundle.message("quickfix.typeReplaceWithSuggestion", suggestion),
                            family = DreamShaderBundle.message("quickfix.family.semantic")
                        )
                    )
                }
                annotation.create()
            }
        }
    }

    private fun annotateConstTextureDefaultAssetDiagnostics(
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        declarationContexts.forEach { context ->
            context.directSections
                .filter { context.canonicalSectionName(it) == "properties" }
                .forEach { section ->
                    val body = context.sectionBody(section) ?: return@forEach
                    val statements = splitTopLevel(body.text, ';')
                    var statementStart = 0
                    statements.forEach { statement ->
                        val statementEnd = statementStart + statement.length
                        val trimmedStartInStatement = statement.indexOfFirst { !it.isWhitespace() }
                        val trimmed = statement.trim()
                        if (trimmedStartInStatement >= 0 && trimmed.isNotEmpty()) {
                            val matcher = CONST_TEXTURE_DECLARATION_PATTERN.matcher(trimmed)
                            if (matcher.find()) {
                                val type = matcher.group(1) ?: ""
                                if (type.lowercase(Locale.ROOT) in CONST_TEXTURE_TYPES_REQUIRING_EXPLICIT_DEFAULT_ASSET) {
                                    val initializerRaw = matcher.group(2)?.trim().orEmpty()
                                    val typeStartInTrimmed = matcher.start(1)
                                    val typeEndInTrimmed = matcher.end(1)
                                    val range = TextRange(
                                        body.startOffset + statementStart + trimmedStartInStatement + typeStartInTrimmed,
                                        body.startOffset + statementStart + trimmedStartInStatement + typeEndInTrimmed
                                    )
                                    if (initializerRaw.isBlank()) {
                                        holder.newAnnotation(
                                            HighlightSeverity.ERROR,
                                            DreamShaderBundle.message("diagnostic.constTextureRequiresExplicitDefaultAsset", type)
                                        ).range(range).create()
                                    } else {
                                        val initializerStartInTrimmed = matcher.start(2)
                                        val initializerEndInTrimmed = matcher.end(2)
                                        val initializerRange = TextRange(
                                            body.startOffset + statementStart + trimmedStartInStatement + initializerStartInTrimmed,
                                            body.startOffset + statementStart + trimmedStartInStatement + initializerEndInTrimmed
                                        )
                                        val error = validateConstTextureDefaultAssetValue(type, initializerRaw)
                                        if (error != null) {
                                            val annotation = holder.newAnnotation(
                                                HighlightSeverity.ERROR,
                                                error
                                            ).range(initializerRange)
                                            val pathRoot = extractPathRoot(initializerRaw)
                                            val isUnknownRoot = pathRoot != null && !isAllowedVirtualFunctionAssetRoot(pathRoot)
                                            if (isUnknownRoot) {
                                                annotation.withFix(
                                                    createReplaceFileRangeAssetRootWithGameQuickFix(
                                                        valueRange = initializerRange,
                                                        rawValue = initializerRaw
                                                    )
                                                )
                                            }
                                            if (isPathCallMissingObjectSegment(initializerRaw)) {
                                                annotation.withFix(
                                                    createCompleteAssetPathObjectSegmentQuickFix(
                                                        replacementRange = initializerRange,
                                                        rawValue = initializerRaw
                                                    )
                                                )
                                            }
                                            annotation.create()
                                        }
                                        val invalidObjectSuffix = validateAssetObjectSegmentSuffix(initializerRaw)
                                        if (invalidObjectSuffix != null) {
                                            holder.newAnnotation(
                                                HighlightSeverity.WARNING,
                                                invalidObjectSuffix
                                            ).range(initializerRange).create()
                                        }
                                    }
                                }
                            }
                        }
                        statementStart = (statementEnd + 1).coerceAtMost(body.text.length)
                    }
                }
        }
    }

    private fun annotateGeneralResourcePathDiagnostics(
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        declarationContexts.forEach { context ->
            context.directSections
                .filter {
                    when (context.canonicalSectionName(it)) {
                        "properties", "inputs", "outputs", "graph", "results" -> true
                        else -> false
                    }
                }
                .forEach { section ->
                    val body = context.sectionBody(section) ?: return@forEach
                    splitTopLevelWithOffsets(body.text, ';').forEach { segment ->
                        val candidate = parseResourceInitializerCandidate(segment.text) ?: return@forEach
                        if (!candidate.typeName.isResourceLikeType()) return@forEach
                        val value = candidate.initializerText ?: return@forEach
                        if (!looksLikePathInitializer(value)) return@forEach

                        val initializerRange = TextRange(
                            body.startOffset + segment.startOffset + candidate.initializerStartInSegment,
                            body.startOffset + segment.startOffset + candidate.initializerEndInSegment
                        )
                        val error = validateGeneralResourcePathValue(value)
                        if (error != null) {
                            val annotation = holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                error
                            ).range(initializerRange)
                            val pathRoot = extractPathRoot(value)
                            val isUnknownRoot = pathRoot != null && !isAllowedVirtualFunctionAssetRoot(pathRoot)
                            if (isUnknownRoot) {
                                annotation.withFix(
                                    createReplaceFileRangeAssetRootWithGameQuickFix(
                                        valueRange = initializerRange,
                                        rawValue = value
                                    )
                                )
                            }
                            if (isPathCallMissingObjectSegment(value)) {
                                annotation.withFix(
                                    createCompleteAssetPathObjectSegmentQuickFix(
                                        replacementRange = initializerRange,
                                        rawValue = value
                                    )
                                )
                            }
                            annotation.create()
                        }

                        val invalidObjectSuffix = validateAssetObjectSegmentSuffix(value)
                        if (invalidObjectSuffix != null) {
                            holder.newAnnotation(
                                HighlightSeverity.WARNING,
                                invalidObjectSuffix
                            ).range(initializerRange).create()
                        }
                    }
                }
        }
    }

    private fun annotateOptionalInputDefaultDiagnostics(
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder
    ) {
        declarationContexts.forEach { context ->
            val declaration = context.declaration
            context.directSections
                .filter { context.canonicalSectionName(it) == "inputs" }
                .forEach { section ->
                    val body = context.sectionBody(section) ?: return@forEach
                    splitTopLevelWithOffsets(body.text, ';').forEach { segment ->
                        val optionalInput = parseOptionalInputDeclaration(segment.text) ?: return@forEach
                        val range = TextRange(
                            body.startOffset + segment.startOffset + optionalInput.highlightStartInSegment,
                            body.startOffset + segment.startOffset + optionalInput.highlightEndInSegment
                        )
                        annotateOptionalInputDefault(
                            name = optionalInput.name,
                            defaultValue = optionalInput.defaultValue,
                            range = range,
                            holder = holder
                        )
                    }
                }

            val keyword = context.keyword
            if (keyword != "function" && keyword != "graphfunction") return@forEach
            val signature = context.parsedSignature ?: return@forEach
            signature.params
                .filter { it.isOptional }
                .forEach { param ->
                    val rangeInDeclaration = param.parameterRangeInDeclaration ?: param.typeRangeInDeclaration ?: return@forEach
                    val range = TextRange(
                        declaration.textRange.startOffset + rangeInDeclaration.startOffset,
                        declaration.textRange.startOffset + rangeInDeclaration.endOffset
                    )
                    annotateOptionalInputDefault(
                        name = param.name,
                        defaultValue = param.defaultValue,
                        range = range,
                        holder = holder
                    )
                }
        }
    }

    private fun annotateOptionalInputDefault(
        name: String,
        defaultValue: String?,
        range: TextRange,
        holder: AnnotationHolder
    ) {
        if (defaultValue == null) {
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                DreamShaderBundle.message("diagnostic.optionalInputRequiresDefault", name)
            ).range(range).create()
            return
        }

        if (isMalformedDefaultValue(defaultValue)) {
            holder.newAnnotation(
                HighlightSeverity.WARNING,
                DreamShaderBundle.message("diagnostic.inputDefaultValueMalformed", name)
            ).range(range).create()
        }
    }

    private fun parseOptionalInputDeclaration(segmentText: String): OptionalInputDeclaration? {
        val trimmedStart = segmentText.indexOfFirst { !it.isWhitespace() }
        if (trimmedStart < 0) return null
        val text = segmentText.substring(trimmedStart).trimEnd()
        if (!OPTIONAL_INPUT_DECLARATION_PATTERN.matcher(text).find()) return null
        val nameMatch = PARAM_NAME_BEFORE_DEFAULT_PATTERN.matcher(text)
        if (!nameMatch.find()) return null
        val name = nameMatch.group(1) ?: return null
        val equalsIndex = text.indexOf('=')
        val defaultValue = if (equalsIndex >= 0) text.substring(equalsIndex + 1).trim() else null
        return OptionalInputDeclaration(
            name = name,
            defaultValue = defaultValue,
            highlightStartInSegment = trimmedStart,
            highlightEndInSegment = trimmedStart + text.length
        )
    }

    private fun isMalformedDefaultValue(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return true
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var inString = false
        var escaped = false
        trimmed.forEach { ch ->
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                return@forEach
            }
            when (ch) {
                '"' -> inString = true
                '(' -> parenDepth++
                ')' -> parenDepth--
                '[' -> bracketDepth++
                ']' -> bracketDepth--
                '{' -> braceDepth++
                '}' -> braceDepth--
            }
        }
        return inString || parenDepth != 0 || bracketDepth != 0 || braceDepth != 0
    }

    private fun annotateUnknownExpressionClassDiagnostics(
        file: DreamShaderPsiFile,
        sourceText: String,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        val settings = file.project.getService(DreamShaderProjectSettings::class.java)
        val classCandidates = DreamShaderMaterialExpressionManifest.expressionClassNames(
            file.project,
            settings?.state?.materialExpressionManifestPath
        )
        val knownClassByLower = linkedMapOf<String, String>()
        classCandidates
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { className ->
                knownClassByLower.putIfAbsent(className.lowercase(Locale.ROOT), className)
            }

        allDeclarationContexts(topLevelDeclarations).forEach { context ->
            context.graphLikeSections()
                .forEach { section ->
                    val tokens = context.sectionTokens(section).orEmpty()
                    tokens.indices.forEach { index ->
                        val token = tokens[index]
                        if (!token.text.equals("UE", ignoreCase = true)) return@forEach
                        val dotIndex = nextSignificantTokenIndex(tokens, index) ?: return@forEach
                        val dotToken = tokens[dotIndex]
                        if (dotToken.type != DreamShaderTokenTypes.OPERATOR || dotToken.text != ".") return@forEach
                        val expressionIndex = nextSignificantTokenIndex(tokens, dotIndex) ?: return@forEach
                        val expressionToken = tokens[expressionIndex]
                        if (!expressionToken.text.equals("Expression", ignoreCase = true)) return@forEach
                        val leftParenIndex = nextSignificantTokenIndex(tokens, expressionIndex) ?: return@forEach
                        val leftParenToken = tokens[leftParenIndex]
                        if (leftParenToken.type != DreamShaderTokenTypes.LPAREN) return@forEach

                        val callArgumentInfo = parseCallArgumentInfo(sourceText, leftParenToken.range.startOffset) ?: return@forEach
                        val classArgument = findExpressionNamedArgument(
                            sourceText = sourceText,
                            argumentStartOffset = leftParenToken.range.startOffset + 1,
                            argumentEndOffset = callArgumentInfo.rightParenOffset,
                            key = "class"
                        )
                        if (classArgument == null || classArgument.value.isBlank()) {
                            val defaultClass = knownClassByLower.values.firstOrNull() ?: "Sine"
                            holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                DreamShaderBundle.message("diagnostic.ueExpressionClassRequired")
                            ).range(expressionToken.range).withFix(
                                createInsertUeExpressionNamedArgumentQuickFix(
                                    section = section,
                                    leftParenOffset = leftParenToken.range.startOffset,
                                    rightParenOffset = callArgumentInfo.rightParenOffset,
                                    assignmentText = """Class="$defaultClass"""",
                                    insertAtBeginning = true,
                                    quickFixText = DreamShaderBundle.message("quickfix.ueExpressionAddClass")
                                )
                            ).create()
                        } else if (knownClassByLower.isNotEmpty() && classArgument.value.lowercase(Locale.ROOT) !in knownClassByLower) {
                            val suggestion = suggestExpressionClassName(classArgument.value, knownClassByLower.values)
                            val message = if (suggestion != null) {
                                DreamShaderBundle.message(
                                    "diagnostic.unknownExpressionClassWithSuggestion",
                                    classArgument.value,
                                    suggestion
                                )
                            } else {
                                DreamShaderBundle.message("diagnostic.unknownExpressionClass", classArgument.value)
                            }
                            val annotation = holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                message
                            ).range(classArgument.range)
                            if (suggestion != null) {
                                annotation.withFix(
                                    createReplaceSectionRangeQuickFix(
                                        section = section,
                                        targetRange = classArgument.range,
                                        replacement = if (classArgument.quoted) "\"$suggestion\"" else suggestion,
                                        text = DreamShaderBundle.message("quickfix.typeReplaceWithSuggestion", suggestion),
                                        family = DreamShaderBundle.message("quickfix.family.semantic")
                                    )
                                )
                            }
                            annotation.create()
                        }

                        val outputTypeArgument = findExpressionNamedArgument(
                            sourceText = sourceText,
                            argumentStartOffset = leftParenToken.range.startOffset + 1,
                            argumentEndOffset = callArgumentInfo.rightParenOffset,
                            key = "outputtype"
                        )
                        if (outputTypeArgument == null || outputTypeArgument.value.isBlank()) {
                            holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                DreamShaderBundle.message("diagnostic.ueExpressionOutputTypeRequired")
                            ).range(expressionToken.range).withFix(
                                createInsertUeExpressionNamedArgumentQuickFix(
                                    section = section,
                                    leftParenOffset = leftParenToken.range.startOffset,
                                    rightParenOffset = callArgumentInfo.rightParenOffset,
                                    assignmentText = """OutputType="float1"""",
                                    insertAtBeginning = false,
                                    quickFixText = DreamShaderBundle.message("quickfix.ueExpressionAddOutputType")
                                )
                            ).create()
                        } else if (outputTypeArgument.value.lowercase(Locale.ROOT) !in UE_EXPRESSION_OUTPUT_TYPES) {
                            val suggestion = suggestExpressionOutputType(outputTypeArgument.value)
                            val message = if (suggestion != null) {
                                DreamShaderBundle.message(
                                    "diagnostic.invalidExpressionOutputTypeWithSuggestion",
                                    outputTypeArgument.value,
                                    suggestion
                                )
                            } else {
                                DreamShaderBundle.message("diagnostic.invalidExpressionOutputType", outputTypeArgument.value)
                            }
                            val annotation = holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                message
                            ).range(outputTypeArgument.range)
                            if (suggestion != null) {
                                annotation.withFix(
                                    createReplaceSectionRangeQuickFix(
                                        section = section,
                                        targetRange = outputTypeArgument.range,
                                        replacement = if (outputTypeArgument.quoted) "\"$suggestion\"" else suggestion,
                                        text = DreamShaderBundle.message("quickfix.typeReplaceWithSuggestion", suggestion),
                                        family = DreamShaderBundle.message("quickfix.family.semantic")
                                    )
                                )
                            }
                            annotation.create()
                        } else if (
                            outputTypeArgument.value.equals("Substrate", ignoreCase = true) &&
                            classArgument != null &&
                            isCustomExpressionClass(classArgument.value)
                        ) {
                            // 上游约束：UMaterialExpressionCustom 不支持 OutputType="Substrate"。
                            holder.newAnnotation(
                                HighlightSeverity.ERROR,
                                DreamShaderBundle.message("diagnostic.customExpressionDisallowsSubstrateOutput")
                            ).range(outputTypeArgument.range).create()
                        }
                    }
                }
        }
    }

    private fun findExpressionNamedArgument(
        sourceText: String,
        argumentStartOffset: Int,
        argumentEndOffset: Int,
        key: String
    ): ExpressionClassArgument? {
        if (argumentStartOffset !in 0..<argumentEndOffset || argumentEndOffset > sourceText.length) {
            return null
        }
        val argumentsText = sourceText.substring(argumentStartOffset, argumentEndOffset)
        val matcher = NAMED_ASSIGNMENT_PATTERN.matcher(argumentsText)
        var rawValue: String? = null
        var valueStart = -1
        var valueEnd = -1
        while (matcher.find()) {
            val currentKey = matcher.group(1) ?: continue
            if (!currentKey.equals(key, ignoreCase = true)) continue
            rawValue = matcher.group(2)?.trim()
            valueStart = argumentStartOffset + matcher.start(2)
            valueEnd = argumentStartOffset + matcher.end(2)
            break
        }
        if (rawValue.isNullOrBlank()) return null
        if (valueStart !in 0..<valueEnd || valueEnd > sourceText.length) return null

        val quoted = isQuotedStringLiteral(rawValue)
        val normalized = if (quoted) rawValue.substring(1, rawValue.length - 1).trim() else rawValue
        return ExpressionClassArgument(
            value = normalized,
            range = TextRange(valueStart, valueEnd),
            quoted = quoted
        )
    }

    private fun suggestExpressionClassName(rawClass: String, candidates: Collection<String>): String? {
        return findClosestCandidate(rawClass, candidates, maxDistance = 3)
    }

    private fun suggestExpressionOutputType(rawType: String): String? {
        return findClosestCandidate(rawType, UE_EXPRESSION_OUTPUT_TYPES_CANONICAL, maxDistance = 2)
    }

    private fun isCustomExpressionClass(rawClass: String): Boolean {
        val normalized = rawClass.trim()
            .removePrefix("UMaterialExpression")
            .removePrefix("MaterialExpression")
        return normalized.equals("Custom", ignoreCase = true)
    }

    private fun createInsertUeExpressionNamedArgumentQuickFix(
        section: DreamShaderSection,
        leftParenOffset: Int,
        rightParenOffset: Int,
        assignmentText: String,
        insertAtBeginning: Boolean,
        quickFixText: String
    ): IntentionAction {
        val sectionPointer: SmartPsiElementPointer<DreamShaderSection> = SmartPointerManager.createPointer(section)
        val relativeLeftParenOffset = leftParenOffset - section.textRange.startOffset
        val relativeRightParenOffset = rightParenOffset - section.textRange.startOffset
        return object : IntentionAction {
            override fun getText(): String = quickFixText

            override fun getFamilyName(): String = DreamShaderBundle.message("quickfix.family.semantic")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                val targetSection = sectionPointer.element ?: return false
                val length = targetSection.textLength
                return relativeLeftParenOffset in 0 until length && relativeRightParenOffset in 0 until length
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetSection = sectionPointer.element ?: return
                val targetFile = targetSection.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetSection)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return

                val sectionStart = targetSection.textRange.startOffset
                val absoluteLeftParenOffset = (sectionStart + relativeLeftParenOffset).coerceIn(0, document.textLength)
                val absoluteRightParenOffset = (sectionStart + relativeRightParenOffset).coerceIn(0, document.textLength)
                if (absoluteRightParenOffset <= absoluteLeftParenOffset) return

                val argsStart = (absoluteLeftParenOffset + 1).coerceIn(0, document.textLength)
                val argsEnd = absoluteRightParenOffset.coerceIn(argsStart, document.textLength)
                val argsText = document.text.substring(argsStart, argsEnd)
                val insertion = when {
                    argsText.trim().isBlank() -> assignmentText
                    insertAtBeginning -> "$assignmentText, "
                    else -> ", $assignmentText"
                }
                val insertOffset = when {
                    argsText.trim().isBlank() -> argsStart
                    insertAtBeginning -> argsStart
                    else -> argsEnd
                }
                document.insertString(insertOffset, insertion)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createReplaceSectionRangeQuickFix(
        section: DreamShaderSection,
        targetRange: TextRange,
        replacement: String,
        text: String,
        family: String
    ): IntentionAction {
        val sectionPointer: SmartPsiElementPointer<DreamShaderSection> = SmartPointerManager.createPointer(section)
        val relativeRange = TextRange(
            targetRange.startOffset - section.textRange.startOffset,
            targetRange.endOffset - section.textRange.startOffset
        )
        return object : IntentionAction {
            override fun getText(): String = text

            override fun getFamilyName(): String = family

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return sectionPointer.element?.isValid == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetSection = sectionPointer.element ?: return
                val targetFile = targetSection.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetSection)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                replaceSectionRelativeRange(document, targetSection, relativeRange, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createReplaceDeclarationRangeQuickFix(
        declaration: DreamShaderDeclaration,
        targetRangeInDeclaration: TextRange,
        replacement: String,
        text: String,
        family: String
    ): IntentionAction {
        val declarationPointer: SmartPsiElementPointer<DreamShaderDeclaration> = SmartPointerManager.createPointer(declaration)
        return object : IntentionAction {
            override fun getText(): String = text

            override fun getFamilyName(): String = family

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                return declarationPointer.element?.isValid == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetDeclaration = declarationPointer.element ?: return
                val targetFile = targetDeclaration.containingFile ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetDeclaration)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val declarationStart = targetDeclaration.textRange.startOffset
                val start = (declarationStart + targetRangeInDeclaration.startOffset).coerceIn(0, document.textLength)
                val end = (declarationStart + targetRangeInDeclaration.endOffset).coerceIn(start, document.textLength)
                document.replaceString(start, end, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createReplaceFileRangeQuickFix(
        replacementRange: TextRange,
        replacement: String,
        text: String,
        family: String
    ): IntentionAction {
        return object : IntentionAction {
            override fun getText(): String = text

            override fun getFamilyName(): String = family

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                if (file == null || !file.isValid) return false
                return replacementRange.startOffset >= 0 &&
                    replacementRange.endOffset >= replacementRange.startOffset &&
                    replacementRange.endOffset <= file.textLength
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetFile = file ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val start = replacementRange.startOffset.coerceIn(0, document.textLength)
                val end = replacementRange.endOffset.coerceIn(start, document.textLength)
                document.replaceString(start, end, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createReplaceFileRangeAssetRootWithGameQuickFix(
        valueRange: TextRange,
        rawValue: String
    ): IntentionAction {
        return object : IntentionAction {
            override fun getText(): String =
                DreamShaderBundle.message("quickfix.virtualFunctionOptionAssetReplaceRootWithGame")

            override fun getFamilyName(): String =
                DreamShaderBundle.message("quickfix.family.semantic")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                if (file == null || !file.isValid) return false
                return valueRange.startOffset >= 0 &&
                    valueRange.endOffset >= valueRange.startOffset &&
                    valueRange.endOffset <= file.textLength &&
                    replaceVirtualFunctionAssetRootWithGame(rawValue) != null
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetFile = file ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val replacement = replaceVirtualFunctionAssetRootWithGame(rawValue) ?: return
                val start = valueRange.startOffset.coerceIn(0, document.textLength)
                val end = valueRange.endOffset.coerceIn(start, document.textLength)
                document.replaceString(start, end, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createCompleteAssetPathObjectSegmentQuickFix(
        replacementRange: TextRange,
        rawValue: String
    ): IntentionAction {
        return object : IntentionAction {
            override fun getText(): String =
                DreamShaderBundle.message("quickfix.assetPathAddObjectSegment")

            override fun getFamilyName(): String =
                DreamShaderBundle.message("quickfix.family.semantic")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                if (file == null || !file.isValid) return false
                return replacementRange.startOffset >= 0 &&
                    replacementRange.endOffset >= replacementRange.startOffset &&
                    replacementRange.endOffset <= file.textLength &&
                    completePathCallWithObjectSegment(rawValue) != null
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetFile = file ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val replacement = completePathCallWithObjectSegment(rawValue) ?: return
                val start = replacementRange.startOffset.coerceIn(0, document.textLength)
                val end = replacementRange.endOffset.coerceIn(start, document.textLength)
                document.replaceString(start, end, replacement)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun annotateSubstrateExpressionDiagnostics(declarationContexts: List<DeclarationContext>, holder: AnnotationHolder) {
        graphConstrainedBodyTokens(declarationContexts).forEach { tokens ->
            val substrateSymbols = collectSubstrateLocalSymbols(tokens)
            if (substrateSymbols.isEmpty()) return@forEach

            annotateSubstrateArithmeticDiagnostics(tokens, substrateSymbols, holder)
            annotateSubstrateSwizzleDiagnostics(tokens, substrateSymbols, holder)
            annotateSubstrateVectorConstructorDiagnostics(tokens, substrateSymbols, holder)
            annotateSubstrateTernaryDiagnostics(tokens, substrateSymbols, holder)
        }
    }

    private fun collectSubstrateLocalSymbols(tokens: List<DreamShaderLexedToken>): Set<String> {
        val result = mutableSetOf<String>()
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.type != DreamShaderTokenTypes.TYPE || !token.text.equals("Substrate", ignoreCase = true)) return@forEach
            val next = nextSignificantToken(tokens, index) ?: return@forEach
            if (next.type == DreamShaderTokenTypes.IDENTIFIER) {
                result.add(next.text)
            }
        }
        return result
    }

    private fun annotateSubstrateArithmeticDiagnostics(
        tokens: List<DreamShaderLexedToken>,
        substrateSymbols: Set<String>,
        holder: AnnotationHolder
    ) {
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.type != DreamShaderTokenTypes.OPERATOR || token.text !in SUBSTRATE_ARITHMETIC_OPERATORS) return@forEach
            val previous = previousSignificantToken(tokens, index)
            val next = nextSignificantToken(tokens, index)
            if (!isSubstrateSymbolToken(previous, substrateSymbols) && !isSubstrateSymbolToken(next, substrateSymbols)) return@forEach
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.substrateArithmeticNotSupported")
            ).range(token.range).create()
        }
    }

    private fun annotateSubstrateSwizzleDiagnostics(
        tokens: List<DreamShaderLexedToken>,
        substrateSymbols: Set<String>,
        holder: AnnotationHolder
    ) {
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (!isSubstrateSymbolToken(token, substrateSymbols)) return@forEach
            val dotIndex = nextSignificantTokenIndex(tokens, index) ?: return@forEach
            val dot = tokens[dotIndex]
            if (dot.type != DreamShaderTokenTypes.OPERATOR || dot.text != ".") return@forEach
            val member = nextSignificantToken(tokens, dotIndex) ?: return@forEach
            if (member.type != DreamShaderTokenTypes.IDENTIFIER || !isSwizzleMember(member.text)) return@forEach
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.substrateSwizzleNotSupported")
            ).range(member.range).create()
        }
    }

    private fun annotateSubstrateVectorConstructorDiagnostics(
        tokens: List<DreamShaderLexedToken>,
        substrateSymbols: Set<String>,
        holder: AnnotationHolder
    ) {
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.type != DreamShaderTokenTypes.TYPE) return@forEach
            if (token.text.lowercase(Locale.ROOT) !in SUBSTRATE_VECTOR_CONSTRUCTOR_TYPES) return@forEach
            val leftParenIndex = nextSignificantTokenIndex(tokens, index) ?: return@forEach
            if (tokens[leftParenIndex].type != DreamShaderTokenTypes.LPAREN) return@forEach
            val rightParenIndex = findMatchingParenIndex(tokens, leftParenIndex) ?: return@forEach
            if (!containsSubstrateSymbol(tokens, leftParenIndex + 1, rightParenIndex, substrateSymbols)) return@forEach
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.substrateVectorConstructorNotSupported")
            ).range(token.range).create()
        }
    }

    private fun annotateSubstrateTernaryDiagnostics(
        tokens: List<DreamShaderLexedToken>,
        substrateSymbols: Set<String>,
        holder: AnnotationHolder
    ) {
        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.text != "?") return@forEach
            val colonIndex = findTernaryColonIndex(tokens, index) ?: return@forEach
            val expressionEndIndex = findTernaryExpressionEndIndex(tokens, colonIndex)
            val hasSubstrateBranch = containsSubstrateSymbol(tokens, index + 1, colonIndex, substrateSymbols) ||
                containsSubstrateSymbol(tokens, colonIndex + 1, expressionEndIndex + 1, substrateSymbols)
            if (!hasSubstrateBranch) return@forEach
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.substrateBranchMergeNotSupported")
            ).range(token.range).create()
        }
    }

    private fun isSubstrateSymbolToken(token: DreamShaderLexedToken?, substrateSymbols: Set<String>): Boolean {
        return token?.type == DreamShaderTokenTypes.IDENTIFIER && token.text in substrateSymbols
    }

    private fun containsSubstrateSymbol(
        tokens: List<DreamShaderLexedToken>,
        startIndex: Int,
        endIndexExclusive: Int,
        substrateSymbols: Set<String>
    ): Boolean {
        val boundedStart = startIndex.coerceAtLeast(0)
        val boundedEnd = endIndexExclusive.coerceAtMost(tokens.size)
        if (boundedStart >= boundedEnd) return false
        for (i in boundedStart until boundedEnd) {
            if (isSubstrateSymbolToken(tokens[i], substrateSymbols)) return true
        }
        return false
    }

    private fun isSwizzleMember(text: String): Boolean {
        if (text.isEmpty() || text.length > 4) return false
        return text.all { it.lowercaseChar() in SUBSTRATE_SWIZZLE_CHARS }
    }

    private fun findMatchingParenIndex(tokens: List<DreamShaderLexedToken>, leftParenIndex: Int): Int? {
        if (leftParenIndex !in tokens.indices || tokens[leftParenIndex].type != DreamShaderTokenTypes.LPAREN) return null
        var depth = 0
        for (i in leftParenIndex until tokens.size) {
            when (tokens[i].type) {
                DreamShaderTokenTypes.LPAREN -> depth++
                DreamShaderTokenTypes.RPAREN -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
        return null
    }

    private fun findTernaryColonIndex(tokens: List<DreamShaderLexedToken>, questionIndex: Int): Int? {
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var nestedTernaryDepth = 0
        var i = questionIndex + 1
        while (i < tokens.size) {
            val token = tokens[i]
            when (token.type) {
                DreamShaderTokenTypes.LPAREN -> parenDepth++
                DreamShaderTokenTypes.RPAREN -> if (parenDepth > 0) parenDepth-- else return null
                DreamShaderTokenTypes.LBRACKET -> bracketDepth++
                DreamShaderTokenTypes.RBRACKET -> if (bracketDepth > 0) bracketDepth-- else return null
                DreamShaderTokenTypes.LBRACE -> braceDepth++
                DreamShaderTokenTypes.RBRACE -> if (braceDepth > 0) braceDepth-- else return null
                DreamShaderTokenTypes.OPERATOR -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                        when (token.text) {
                            "?" -> nestedTernaryDepth++
                            ":" -> {
                                if (nestedTernaryDepth == 0) return i
                                nestedTernaryDepth--
                            }
                            ";" -> return null
                        }
                    }
                }
            }
            i++
        }
        return null
    }

    private fun findTernaryExpressionEndIndex(tokens: List<DreamShaderLexedToken>, colonIndex: Int): Int {
        var parenDepth = 0
        var bracketDepth = 0
        var braceDepth = 0
        var i = colonIndex + 1
        while (i < tokens.size) {
            val token = tokens[i]
            when (token.type) {
                DreamShaderTokenTypes.LPAREN -> parenDepth++
                DreamShaderTokenTypes.RPAREN -> if (parenDepth > 0) parenDepth-- else return i - 1
                DreamShaderTokenTypes.LBRACKET -> bracketDepth++
                DreamShaderTokenTypes.RBRACKET -> if (bracketDepth > 0) bracketDepth-- else return i - 1
                DreamShaderTokenTypes.LBRACE -> braceDepth++
                DreamShaderTokenTypes.RBRACE -> if (braceDepth > 0) braceDepth-- else return i - 1
                DreamShaderTokenTypes.OPERATOR -> {
                    if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0 && token.text in TERNARY_EXPRESSION_TERMINATORS) {
                        return i - 1
                    }
                }
            }
            i++
        }
        return tokens.lastIndex
    }

    private fun annotateUnsupportedGraphLoopDiagnostics(declarationContexts: List<DeclarationContext>, holder: AnnotationHolder) {
        annotateUnsupportedGraphKeywordDiagnostics(
            declarationContexts = declarationContexts,
            holder = holder,
            keywords = UNSUPPORTED_GRAPH_LOOP_KEYWORDS
        ) { keyword ->
            DreamShaderBundle.message("diagnostic.graphDisallowsLoopStatement", keyword)
        }
    }

    private fun annotateUnsupportedGraphSwitchDiagnostics(declarationContexts: List<DeclarationContext>, holder: AnnotationHolder) {
        annotateUnsupportedGraphKeywordDiagnostics(
            declarationContexts = declarationContexts,
            holder = holder,
            keywords = UNSUPPORTED_GRAPH_SWITCH_KEYWORDS
        ) { keyword ->
            DreamShaderBundle.message("diagnostic.graphDisallowsSwitchStatement", keyword)
        }
    }

    private fun annotateUnsupportedGraphBreakContinueDiagnostics(declarationContexts: List<DeclarationContext>, holder: AnnotationHolder) {
        annotateUnsupportedGraphKeywordDiagnostics(
            declarationContexts = declarationContexts,
            holder = holder,
            keywords = UNSUPPORTED_GRAPH_FLOW_KEYWORDS
        ) { keyword ->
            DreamShaderBundle.message("diagnostic.graphDisallowsControlStatement", keyword)
        }
    }

    private fun annotateUnsupportedGraphReturnDiagnostics(declarationContexts: List<DeclarationContext>, holder: AnnotationHolder) {
        val tokenLists = mutableListOf<List<DreamShaderLexedToken>>()
        declarationContexts.forEach { context ->
            context.directSections
                .filter { context.canonicalSectionName(it) == "graph" }
                .forEach { section ->
                    context.sectionTokens(section)?.takeIf { it.isNotEmpty() }?.let(tokenLists::add)
                }

            val keyword = context.keyword
            if (keyword == "function" || keyword == "graphfunction") {
                if (context.declaration.returnType() == null) {
                    context.declarationBodyTokens.takeIf { it.isNotEmpty() }?.let(tokenLists::add)
                }
            }
        }

        val normalizedKeyword = UNSUPPORTED_GRAPH_RETURN_KEYWORD.lowercase(Locale.ROOT)
        val visitedKeywordOffsets = mutableSetOf<Int>()
        tokenLists.forEach { tokens ->
            tokens.forEach { token ->
                if (token.type != DreamShaderTokenTypes.KEYWORD) return@forEach
                if (token.text.lowercase(Locale.ROOT) != normalizedKeyword) return@forEach
                if (!visitedKeywordOffsets.add(token.range.startOffset)) return@forEach
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.graphDisallowsReturnStatement")
                ).range(token.range).create()
            }
        }
    }

    private fun annotateUnsupportedGraphKeywordDiagnostics(
        declarationContexts: List<DeclarationContext>,
        holder: AnnotationHolder,
        keywords: Set<String>,
        message: (String) -> String
    ) {
        val normalizedKeywords = keywords.map { it.lowercase(Locale.ROOT) }.toSet()
        if (normalizedKeywords.isEmpty()) return

        val visitedKeywordOffsets = mutableSetOf<Int>()
        graphConstrainedBodyTokens(declarationContexts).forEach { tokens ->
            tokens.forEach { token ->
                if (token.type != DreamShaderTokenTypes.KEYWORD) return@forEach
                val normalized = token.text.lowercase(Locale.ROOT)
                if (normalized !in normalizedKeywords) return@forEach
                if (!visitedKeywordOffsets.add(token.range.startOffset)) return@forEach
                holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    message(normalized)
                ).range(token.range).create()
            }
        }
    }

    private fun graphConstrainedBodyRanges(declarationContexts: List<DeclarationContext>): List<TextRange> {
        return graphConstrainedBodyTokens(declarationContexts).mapNotNull { tokens ->
            val first = tokens.firstOrNull() ?: return@mapNotNull null
            val last = tokens.lastOrNull() ?: return@mapNotNull null
            TextRange(first.range.startOffset, last.range.endOffset)
        }
    }

    private fun graphConstrainedBodyTokens(declarationContexts: List<DeclarationContext>): List<List<DreamShaderLexedToken>> {
        val tokenLists = mutableListOf<List<DreamShaderLexedToken>>()
        declarationContexts.forEach { context ->
            context.directSections
                .filter { context.canonicalSectionName(it) == "graph" }
                .forEach { section ->
                    context.sectionTokens(section)?.takeIf { it.isNotEmpty() }?.let(tokenLists::add)
                }

            val keyword = context.keyword
            if (keyword == "function" || keyword == "graphfunction") {
                context.declarationBodyTokens.takeIf { it.isNotEmpty() }?.let(tokenLists::add)
            }
        }
        return tokenLists
    }

    private fun allDeclarations(topLevelDeclarations: List<DreamShaderDeclaration>): List<DreamShaderDeclaration> {
        return allDeclarationContexts(topLevelDeclarations).map { it.declaration }
    }

    private fun allDeclarationContexts(topLevelDeclarations: List<DreamShaderDeclaration>): List<DeclarationContext> {
        if (topLevelDeclarations.isEmpty()) return emptyList()
        val ordered = mutableListOf<DeclarationContext>()
        val queue = ArrayDeque<DeclarationContext>()
        topLevelDeclarations.forEach { queue.addLast(buildDeclarationContext(it)) }
        while (queue.isNotEmpty()) {
            val context = queue.removeFirst()
            ordered.add(context)
            context.directChildDeclarations.forEach { child ->
                queue.addLast(buildDeclarationContext(child))
            }
        }
        return ordered
    }

    private fun buildDeclarationContext(declaration: DreamShaderDeclaration): DeclarationContext {
        val keyword = declaration.keywordText()
        val directSections = directSectionsOf(declaration)
        val sectionBodies = directSections.mapNotNull { section ->
            sectionBody(section)?.let { body -> section to body }
        }.toMap()
        val sectionTokensBySection = directSections.mapNotNull { section ->
            val body = sectionBodies[section] ?: return@mapNotNull null
            section to lexTokens(declaration.containingFile.text, body.startOffset, body.startOffset + body.text.length)
        }.toMap()
        val sectionsByCanonicalNameForDeclaration =
            if (keyword == null) {
                emptyMap()
            } else {
                directSections
                    .mapNotNull { section ->
                        DreamShaderLanguageRules.canonicalSectionNameForDeclaration(keyword, section.sectionName())
                            ?.let { name -> name to section }
                    }
                    .groupBy({ it.first }, { it.second })
            }
        val typedDeclarationsBySection = directSections.associateWith { section ->
            sectionBodies[section]?.text?.let(::extractTopLevelTypedDeclarations).orEmpty()
        }
        return DeclarationContext(
            declaration = declaration,
            keyword = keyword,
            directSections = directSections,
            directChildDeclarations = directChildDeclarations(declaration),
            sectionBodies = sectionBodies,
            sectionTokensBySection = sectionTokensBySection,
            sectionsByCanonicalNameForDeclaration = sectionsByCanonicalNameForDeclaration,
            typedDeclarationsBySection = typedDeclarationsBySection,
            parsedSignature = parseDeclarationParameters(declaration.text),
            declarationBodyTokens = declaration.bodyTextRange()
                ?.let { range -> lexTokens(declaration.containingFile.text, range.startOffset, range.endOffset) }
                .orEmpty(),
            topLevelBodyTokens = declaration.bodyTextRange()
                ?.let { range -> lexTokens(declaration.containingFile.text, range.startOffset, range.endOffset) }
                .orEmpty()
        )
    }

    private fun extractTopLevelTypedDeclarations(sectionBodyText: String): List<String> {
        val result = mutableListOf<String>()
        val matcher = TYPED_DECLARATION_PATTERN.matcher(sectionBodyText)
        while (matcher.find()) {
            val typeName = matcher.group(1)
            if (!typeName.isNullOrBlank()) result.add(typeName)
        }
        return result
    }

    // 语义诊断：调用签名检查、导入解析与导入 quick fix。
    private fun annotateMissingOutArgumentDiagnostics(
        file: DreamShaderPsiFile,
        sourceText: String,
        tokens: List<DreamShaderLexedToken>,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        val signatureCandidates = collectCallableSignatureCandidates(topLevelDeclarations)
        if (signatureCandidates.isEmpty()) return

        tokens.indices.forEach { index ->
            val token = tokens[index]
            if (token.type != DreamShaderTokenTypes.IDENTIFIER) return@forEach
            val next = nextSignificantToken(tokens, index) ?: return@forEach
            if (next.type != DreamShaderTokenTypes.LPAREN) return@forEach

            val prev = previousSignificantToken(tokens, index)
            if (prev?.type == DreamShaderTokenTypes.OPERATOR && prev.text == ".") return@forEach
            if (prev?.type == DreamShaderTokenTypes.KEYWORD && prev.text.lowercase(Locale.ROOT) in DreamShaderLanguageKeywords.DECLARATION_KEYWORDS) return@forEach

            val signature = resolveCallableSignatureForCall(
                sourceText = sourceText,
                callName = token.text,
                callNameStartOffset = token.range.startOffset,
                signatureCandidates = signatureCandidates,
                topLevelDeclarations = topLevelDeclarations
            ) ?: return@forEach

            val call = parseCallArgumentInfo(sourceText, next.range.startOffset) ?: return@forEach
            val missingOutParam = signature.params.withIndex()
                .firstOrNull { (paramIndex, param) -> param.isOut && paramIndex >= call.argumentCount }
                ?.value
                ?: return@forEach

            val annotation = holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.missingOutArgumentForParameter", missingOutParam.name)
            ).range(token.range)
            annotation.withFix(
                createAddMissingOutArgumentsQuickFix(
                    file = file,
                    call = call,
                    signature = signature
                )
            )
            annotation.create()
        }
    }

    private fun collectCallableSignatureCandidates(
        topLevelDeclarations: List<DreamShaderDeclaration>
    ): List<CallableSignatureCandidate> {
        val result = mutableListOf<CallableSignatureCandidate>()
        fun visit(declaration: DreamShaderDeclaration, namespacePath: List<String>) {
            val keyword = declaration.keywordText()
            val nextNamespacePath = if (keyword == "namespace") {
                val namespaceName = declaration.declarationName().orEmpty().trim()
                if (namespaceName.isNotBlank()) namespacePath + namespaceName else namespacePath
            } else {
                namespacePath
            }

            if (keyword == "function" || keyword == "graphfunction") {
                val name = declaration.declarationName().orEmpty().trim()
                val signature = parseDeclarationParameters(declaration.text)
                if (name.isNotBlank() && signature != null) {
                    result.add(
                        CallableSignatureCandidate(
                            name = name,
                            namespacePath = nextNamespacePath,
                            signature = signature
                        )
                    )
                }
            }

            directChildDeclarations(declaration).forEach { child ->
                visit(child, nextNamespacePath)
            }
        }
        topLevelDeclarations.forEach { declaration ->
            visit(declaration, emptyList())
        }
        return result
    }

    private fun resolveCallableSignatureForCall(
        sourceText: String,
        callName: String,
        callNameStartOffset: Int,
        signatureCandidates: List<CallableSignatureCandidate>,
        topLevelDeclarations: List<DreamShaderDeclaration>
    ): ParsedSignature? {
        val candidatesByName = signatureCandidates.filter { it.name == callName }
        if (candidatesByName.isEmpty()) return null

        val qualifierChainBefore = readQualifierChainBeforeIdentifier(sourceText, callNameStartOffset)
        if (qualifierChainBefore.isNotEmpty()) {
            return candidatesByName.firstOrNull { it.namespacePath == qualifierChainBefore }?.signature
        }

        val enclosingNamespacePath = enclosingNamespacePathAtOffset(topLevelDeclarations, callNameStartOffset)
        for (depth in enclosingNamespacePath.size downTo 0) {
            val scopePath = enclosingNamespacePath.take(depth)
            val candidate = candidatesByName.firstOrNull { it.namespacePath == scopePath }
            if (candidate != null) return candidate.signature
        }
        return null
    }

    private fun readQualifierChainBeforeIdentifier(text: String, anchorOffset: Int): List<String> {
        val qualifiers = mutableListOf<String>()
        var i = anchorOffset - 1
        while (true) {
            while (i >= 0 && text[i].isWhitespace()) i--
            if (i < 1 || text[i] != ':' || text[i - 1] != ':') break

            i -= 2
            while (i >= 0 && text[i].isWhitespace()) i--
            if (i < 0 || !isIdentifierChar(text[i])) return emptyList()

            val end = i
            while (i >= 0 && isIdentifierChar(text[i])) i--
            val start = i + 1
            if (start > end) return emptyList()
            qualifiers.add(text.substring(start, end + 1))
        }
        qualifiers.reverse()
        return qualifiers
    }

    private fun enclosingNamespacePathAtOffset(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        offset: Int
    ): List<String> {
        fun visit(
            declaration: DreamShaderDeclaration,
            namespacePath: List<String>,
            targetOffset: Int
        ): List<String>? {
            val declarationRange = declaration.textRange ?: return null
            if (!declarationRange.containsOffset(targetOffset)) return null

            val keyword = declaration.keywordText()
            val nextNamespacePath = if (keyword == "namespace") {
                val namespaceName = declaration.declarationName().orEmpty().trim()
                if (namespaceName.isNotBlank()) namespacePath + namespaceName else namespacePath
            } else {
                namespacePath
            }

            directChildDeclarations(declaration).forEach { child ->
                val childPath = visit(child, nextNamespacePath, targetOffset)
                if (childPath != null) return childPath
            }

            return nextNamespacePath
        }

        topLevelDeclarations.forEach { declaration ->
            val path = visit(declaration, emptyList(), offset)
            if (path != null) return path
        }
        return emptyList()
    }

    private fun isIdentifierChar(ch: Char): Boolean = ch == '_' || ch.isLetterOrDigit()

    private fun createAddMissingOutArgumentsQuickFix(
        file: DreamShaderPsiFile,
        call: CallArgumentInfo,
        signature: ParsedSignature
    ): IntentionAction {
        val filePointer: SmartPsiElementPointer<DreamShaderPsiFile> = SmartPointerManager.createPointer(file)
        val missingOutParams = signature.params.withIndex()
            .filter { (index, param) -> param.isOut && index >= call.argumentCount }
            .map { it.value.name }
        return object : IntentionAction {
            override fun getText(): String =
                DreamShaderBundle.message("quickfix.callAddMissingOutArguments")

            override fun getFamilyName(): String = DreamShaderBundle.message("quickfix.family.call")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                val targetFile = filePointer.element ?: return false
                return missingOutParams.isNotEmpty() && call.rightParenOffset in 0 until targetFile.textLength
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetFile = filePointer.element ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val insertionOffset = call.rightParenOffset.coerceIn(0, document.textLength)
                val fileText = document.text
                val occupied = collectIdentifierNames(fileText).toMutableSet()
                val generated = mutableSetOf<String>()
                val suffix = normalizedOutArgumentPlaceholderSuffix(project)
                val suggestedTargets = missingOutParams.map { paramName ->
                    generateUniqueOutTargetName("$paramName$suffix", occupied, generated)
                }
                val insertionText = when {
                    suggestedTargets.isEmpty() -> ""
                    call.argumentCount > 0 -> ", ${suggestedTargets.joinToString(", ")}"
                    else -> suggestedTargets.joinToString(", ")
                }
                if (insertionText.isBlank()) return
                document.insertString(insertionOffset, insertionText)
                PsiDocumentManager.getInstance(project).commitDocument(document)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun annotateUnresolvedImportDiagnostics(
        file: DreamShaderPsiFile,
        tokens: List<DreamShaderLexedToken>,
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
            detectUnsupportedImportExtension(importPath)?.let { unsupportedExtension ->
                val annotation = holder.newAnnotation(
                    HighlightSeverity.ERROR,
                    DreamShaderBundle.message("diagnostic.importUnsupportedExtension", unsupportedExtension)
                ).range(token.range)
                suggestedImportExtensionReplacements(file, importPath).forEach { suggestion ->
                    createReplaceImportExtensionQuickFix(file, token.range, importPath, suggestion)
                        ?.let { annotation.withFix(it) }
                }
                annotation.create()
                return@forEach
            }
            if (DreamShaderImportResolver.resolveImport(file, importPath) != null) return@forEach

            val packageRootAnalysis = file.project.basePath?.let { basePath ->
                DreamShaderImportResolver.analyzePackageRootImport(basePath, importPath)
            }
            val diagnosticMessage = packageRootAnalysis?.let { analysis ->
                when {
                    analysis.resolvedEntryRelativePath == null && !analysis.manifestEntryValid ->
                        DreamShaderBundle.message("diagnostic.packageRootImportInvalidEntry", importPath, analysis.manifestEntryRaw.orEmpty())
                    analysis.resolvedEntryRelativePath == null ->
                        DreamShaderBundle.message("diagnostic.packageRootImportEntryMissing", importPath, analysis.suggestedEntryRelativePath)
                    else -> DreamShaderBundle.message("diagnostic.cannotResolveImport", importPath)
                }
            } ?: DreamShaderBundle.message("diagnostic.cannotResolveImport", importPath)

            val annotation = holder.newAnnotation(HighlightSeverity.ERROR, diagnosticMessage).range(token.range)
            createCreateMissingImportFileQuickFix(file, importPath, packageRootAnalysis)?.let { annotation.withFix(it) }
            annotation.create()
        }
    }

    private fun createReplaceImportExtensionQuickFix(
        file: DreamShaderPsiFile,
        importStringRange: TextRange,
        importPath: String,
        suggestion: ImportExtensionSuggestion
    ): IntentionAction? {
        val replacementExtension = suggestion.extension
        if (!replacementExtension.startsWith(".")) return null
        val fixedImportPath = replaceImportExtension(importPath, replacementExtension) ?: return null
        val filePointer: SmartPsiElementPointer<DreamShaderPsiFile> = SmartPointerManager.createPointer(file)
        val willUpdatePreferredDefault = willPersistPreferredImportExtension(file.project, replacementExtension)
        return object : IntentionAction {
            override fun getText(): String {
                return formatImportExtensionQuickFixText(
                    replacementExtension = replacementExtension,
                    resolvesExistingTarget = suggestion.resolvesExistingTarget,
                    preferredByUserDefault = suggestion.preferredByUserDefault,
                    willUpdatePreferredDefault = willUpdatePreferredDefault
                )
            }

            override fun getFamilyName(): String = DreamShaderBundle.message("quickfix.family.import")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                val targetFile = filePointer.element ?: return false
                return importStringRange.startOffset >= 0 &&
                    importStringRange.endOffset <= targetFile.textLength &&
                    importStringRange.length > 1
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetFile = filePointer.element ?: return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) return
                val document = PsiDocumentManager.getInstance(project).getDocument(targetFile) ?: return
                val start = importStringRange.startOffset.coerceIn(0, document.textLength)
                val end = importStringRange.endOffset.coerceIn(start, document.textLength)
                if (end - start < 2) return
                document.replaceString(start, end, "\"$fixedImportPath\"")
                PsiDocumentManager.getInstance(project).commitDocument(document)
                maybePersistPreferredImportExtension(project, replacementExtension)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun createCreateMissingImportFileQuickFix(
        file: DreamShaderPsiFile,
        importPath: String,
        packageRootAnalysis: DreamShaderPackageRootImportAnalysis?
    ): IntentionAction? {
        val filePointer: SmartPsiElementPointer<DreamShaderPsiFile> = SmartPointerManager.createPointer(file)
        val creationPlan = buildImportCreationPlan(importPath, packageRootAnalysis) ?: return null
        return object : IntentionAction {
            override fun getText(): String = DreamShaderBundle.message("quickfix.importCreateMissingFile", creationPlan.relativePath)

            override fun getFamilyName(): String = DreamShaderBundle.message("quickfix.family.import")

            override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean {
                val targetFile = filePointer.element ?: return false
                return resolveImportCreationTarget(targetFile, creationPlan)?.let { !Files.exists(it) } == true
            }

            override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
                val targetFile = filePointer.element ?: return
                val targetPath = resolveImportCreationTarget(targetFile, creationPlan) ?: return
                if (Files.exists(targetPath)) return
                if (!FileModificationService.getInstance().preparePsiElementForWrite(targetFile)) return

                val templateService = DreamShaderTemplateService(project)
                val result = when (targetPath.fileName.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)) {
                    "dsm" -> templateService.createMaterialTemplate(targetPath.invariantSeparatorsPathString)
                    "dsf" -> templateService.createFunctionTemplate(targetPath.invariantSeparatorsPathString)
                    else -> templateService.createHeaderTemplate(targetPath.invariantSeparatorsPathString)
                }
                if (!result.success) return
                val createdPath = result.targetPath ?: return
                val created = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(createdPath) ?: return
                OpenFileDescriptor(project, created, 0, 0).navigate(true)
            }

            override fun startInWriteAction(): Boolean = true
        }
    }

    private fun buildImportCreationPlan(importPath: String): ImportCreationPlan? {
        val normalized = importPath.trim().replace('\\', '/')
        if (normalized.isBlank()) return null
        if (normalized.contains("://")) return null
        if (isAbsolutePath(normalized)) return null
        val isScoped = normalized.startsWith("@")
        val parts = normalized.split('/').filter { it.isNotBlank() }
        if (parts.isEmpty()) return null
        if (parts.any { it == "." || it == ".." }) return null
        if (isScoped && parts.size < 2) return null
        if (parts.any { !isImportCreationPathSegmentSafe(it) }) return null
        val last = parts.last()
        if (last.contains('.')) {
            val extension = last.substringAfterLast('.', "").lowercase(Locale.ROOT)
            if (extension !in IMPORT_FILE_EXTENSIONS) return null
        }
        val pathWithExtension = if (parts.last().contains('.')) parts.joinToString("/") else "${parts.joinToString("/")}.dsh"
        return ImportCreationPlan(
            relativePath = pathWithExtension,
            isScopedPackageImport = isScoped
        )
    }

    private fun buildImportCreationPlan(
        importPath: String,
        packageRootAnalysis: DreamShaderPackageRootImportAnalysis?
    ): ImportCreationPlan? {
        val packageRootCandidate = packageRootAnalysis?.takeIf {
            it.resolvedEntryRelativePath == null
        }?.let { analysis ->
            val relativePath = "${analysis.packageImportPath}/${analysis.suggestedEntryRelativePath}"
            if (!isImportCreationRelativePathSafe(relativePath)) return@let null
            ImportCreationPlan(
                relativePath = relativePath,
                isScopedPackageImport = true
            )
        }
        if (packageRootCandidate != null) return packageRootCandidate
        return buildImportCreationPlan(importPath)
    }

    private fun resolveImportCreationTarget(file: DreamShaderPsiFile, creationPlan: ImportCreationPlan): Path? {
        val containingDirectory = file.virtualFile?.parent ?: return null
        val projectBasePath = file.project.basePath ?: return null
        val projectRoot = runCatching { Paths.get(projectBasePath).normalize().toAbsolutePath() }.getOrNull() ?: return null
        val containingPath = runCatching { Paths.get(containingDirectory.path).normalize().toAbsolutePath() }.getOrNull()
        val basePath = if (creationPlan.isScopedPackageImport) {
            projectRoot.resolve("DShader").resolve("Packages")
        } else {
            containingPath?.takeIf { it.startsWith(projectRoot) } ?: projectRoot
        }
        val normalizedTarget = runCatching {
            var target = basePath
            creationPlan.relativePath.split('/').forEach { segment ->
                if (segment.isBlank()) return@forEach
                target = target.resolve(segment)
            }
            target.normalize().toAbsolutePath()
        }.getOrNull() ?: return null
        return if (normalizedTarget.startsWith(projectRoot)) normalizedTarget else null
    }

    private fun isImportCreationRelativePathSafe(relativePath: String): Boolean {
        val parts = relativePath.replace('\\', '/').split('/').filter { it.isNotBlank() }
        return parts.isNotEmpty() &&
            parts.none { it == "." || it == ".." } &&
            parts.all(::isImportCreationPathSegmentSafe)
    }

    private fun isImportCreationPathSegmentSafe(segment: String): Boolean {
        if (segment.isBlank()) return false
        if (segment.any { it.code < 32 }) return false
        return segment.none { it in WINDOWS_INVALID_PATH_SEGMENT_CHARS }
    }

    private fun isAbsolutePath(path: String): Boolean {
        if (path.startsWith("/") || path.startsWith("\\")) return true
        return runCatching { Paths.get(path).isAbsolute }.getOrDefault(false)
    }

    private fun detectUnsupportedImportExtension(importPath: String): String? {
        val normalized = importPath.trim().replace('\\', '/')
        val lastSegment = normalized.substringAfterLast('/', "")
        if (lastSegment.isBlank()) return null
        val dotIndex = lastSegment.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex >= lastSegment.length - 1) return null
        val extension = lastSegment.substring(dotIndex + 1).lowercase(Locale.ROOT)
        return if (extension in IMPORT_FILE_EXTENSIONS) null else ".${extension}"
    }

    private fun replaceImportExtension(importPath: String, replacementExtension: String): String? {
        val normalized = importPath.trim().replace('\\', '/')
        if (normalized.isBlank()) return null
        val slashIndex = normalized.lastIndexOf('/')
        val lastSegment = if (slashIndex >= 0) normalized.substring(slashIndex + 1) else normalized
        val dotIndex = lastSegment.lastIndexOf('.')
        if (dotIndex <= 0) return null
        val prefix = if (slashIndex >= 0) normalized.substring(0, slashIndex + 1) else ""
        val stem = lastSegment.substring(0, dotIndex)
        if (stem.isBlank()) return null
        return "$prefix$stem$replacementExtension"
    }

    private fun suggestedImportExtensionReplacements(
        file: DreamShaderPsiFile,
        importPath: String
    ): List<ImportExtensionSuggestion> {
        val preferredExtension = normalizedPreferredImportExtension(file.project)
        val prioritized = IMPORT_FILE_EXTENSIONS_ORDERED.map { extension ->
            val replacement = ".$extension"
            val candidatePath = replaceImportExtension(importPath, replacement)
            val resolvesExisting = candidatePath != null && DreamShaderImportResolver.resolveImport(file, candidatePath) != null
            ImportExtensionSuggestion(
                extension = replacement,
                resolvesExistingTarget = resolvesExisting,
                preferredByUserDefault = extension == preferredExtension,
                orderIndex = IMPORT_FILE_EXTENSIONS_ORDERED.indexOf(extension)
            )
        }
        return prioritized.sortedWith(
            compareByDescending<ImportExtensionSuggestion> { it.resolvesExistingTarget }
                .thenByDescending { it.preferredByUserDefault }
                .thenBy { it.orderIndex }
        )
    }

    private fun normalizedPreferredImportExtension(project: Project): String {
        val raw = project.getService(DreamShaderProjectSettings::class.java).state.preferredImportExtension
        val normalized = raw.trim().removePrefix(".").lowercase(Locale.ROOT)
        return if (normalized in IMPORT_FILE_EXTENSIONS) normalized else "dsh"
    }

    private fun maybePersistPreferredImportExtension(project: Project, replacementExtension: String) {
        val settingsState = project.getService(DreamShaderProjectSettings::class.java).state
        if (!settingsState.autoUpdatePreferredImportExtension) return
        val normalized = replacementExtension.trim().removePrefix(".").lowercase(Locale.ROOT)
        if (normalized !in IMPORT_FILE_EXTENSIONS) return
        settingsState.preferredImportExtension = normalized
    }

    private fun willPersistPreferredImportExtension(project: Project, replacementExtension: String): Boolean {
        val settingsState = project.getService(DreamShaderProjectSettings::class.java).state
        if (!settingsState.autoUpdatePreferredImportExtension) return false
        val normalized = replacementExtension.trim().removePrefix(".").lowercase(Locale.ROOT)
        if (normalized !in IMPORT_FILE_EXTENSIONS) return false
        val currentPreferred = settingsState.preferredImportExtension.trim().removePrefix(".").lowercase(Locale.ROOT)
        return normalized != currentPreferred
    }

    private fun formatImportExtensionQuickFixText(
        replacementExtension: String,
        resolvesExistingTarget: Boolean,
        preferredByUserDefault: Boolean,
        willUpdatePreferredDefault: Boolean
    ): String {
        val baseText = DreamShaderBundle.message("quickfix.importChangeExtension", replacementExtension)
        val suffixes = listOfNotNull(
            DreamShaderBundle.message("quickfix.importChangeExtensionHintResolvesExisting").takeIf { resolvesExistingTarget },
            DreamShaderBundle.message("quickfix.importChangeExtensionHintPreferredDefault").takeIf { preferredByUserDefault },
            DreamShaderBundle.message("quickfix.importChangeExtensionHintWillUpdatePreferredDefault").takeIf { willUpdatePreferredDefault }
        )
        return baseText + suffixes.joinToString(separator = "")
    }

    private fun collectIdentifierNames(text: String): Set<String> {
        val matcher = IDENTIFIER_PATTERN.matcher(text)
        val result = linkedSetOf<String>()
        while (matcher.find()) {
            matcher.group()?.let { result.add(it) }
        }
        return result
    }

    private fun generateUniqueOutTargetName(
        base: String,
        occupied: MutableSet<String>,
        generated: MutableSet<String>
    ): String {
        if (base !in occupied && base !in generated) {
            generated.add(base)
            occupied.add(base)
            return base
        }
        var suffix = 2
        while (true) {
            val candidate = "$base$suffix"
            if (candidate !in occupied && candidate !in generated) {
                generated.add(candidate)
                occupied.add(candidate)
                return candidate
            }
            suffix++
        }
    }

    private fun normalizedOutArgumentPlaceholderSuffix(project: Project): String {
        val raw = project.getService(DreamShaderProjectSettings::class.java).state.outArgumentPlaceholderSuffix
        val trimmed = raw.trim()
        if (trimmed.isBlank()) return "Out"
        val cleaned = trimmed.replace(Regex("[^A-Za-z0-9_]"), "_")
        if (cleaned.isBlank()) return "Out"
        return if (cleaned.first().isDigit()) "_$cleaned" else cleaned
    }

    private fun annotateAssetRootPathDiagnostics(
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        topLevelDeclarations.forEach { declaration ->
            val keyword = declaration.keywordText() ?: return@forEach
            if (keyword !in DreamShaderLanguageRules.assetDeclarationKeywords) return@forEach
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

    // 工具函数：函数签名提取与调用参数分析。
    private fun parseDeclarationParameters(declarationText: String): ParsedSignature? {
        val headerEnd = declarationText.indexOf('{').let { if (it >= 0) it else declarationText.length }
        val header = declarationText.substring(0, headerEnd)
        val leftParen = header.indexOf('(')
        val rightParen = header.lastIndexOf(')')
        if (leftParen !in 0..<rightParen) return null
        val rawParams = header.substring(leftParen + 1, rightParen)
        val paramsStartOffset = leftParen + 1
        val params = splitTopLevelWithOffsets(rawParams, ',').mapNotNull { segment ->
            val rawParam = segment.text
            val trimmed = rawParam.trim()
            if (trimmed.isBlank()) return@mapNotNull null
            val defaultSeparatorIndex = rawParam.indexOf('=')
            val rawParamBeforeDefault = if (defaultSeparatorIndex >= 0) rawParam.substring(0, defaultSeparatorIndex) else rawParam
            val trimmedBeforeDefault = rawParamBeforeDefault.trim()
            val nameMatch = PARAM_NAME_PATTERN.matcher(trimmedBeforeDefault)
            if (!nameMatch.find()) return@mapNotNull null
            val name = nameMatch.group(1) ?: return@mapNotNull null
            val nameStartInRaw = rawParamBeforeDefault.lastIndexOf(name).takeIf { it >= 0 } ?: rawParamBeforeDefault.length
            val parameterType = parseDeclarationParameterType(rawParam, nameStartInRaw)
            val typeRangeInDeclaration = parameterType?.rangeInParameter?.let { rangeInParameter ->
                TextRange(
                    paramsStartOffset + segment.startOffset + rangeInParameter.startOffset,
                    paramsStartOffset + segment.startOffset + rangeInParameter.endOffset
                )
            }
            val parameterStart = rawParam.indexOfFirst { !it.isWhitespace() }.let { if (it >= 0) it else 0 }
            val parameterEnd = rawParam.indexOfLast { !it.isWhitespace() }.let { if (it >= 0) it + 1 else rawParam.length }
            ParsedParam(
                name = name,
                isOut = OUT_QUALIFIER_PATTERN.matcher(trimmedBeforeDefault).find(),
                isOptional = OPT_QUALIFIER_PATTERN.matcher(trimmedBeforeDefault).find(),
                defaultValue = if (defaultSeparatorIndex >= 0) rawParam.substring(defaultSeparatorIndex + 1).trim() else null,
                typeName = parameterType?.typeName,
                typeRangeInDeclaration = typeRangeInDeclaration,
                parameterRangeInDeclaration = TextRange(
                    paramsStartOffset + segment.startOffset + parameterStart,
                    paramsStartOffset + segment.startOffset + parameterEnd
                )
            )
        }
        return ParsedSignature(params)
    }

    private fun declarationHeaderText(declarationText: String): String? {
        val headerEnd = declarationText.indexOf('{').let { if (it >= 0) it else declarationText.length }
        val header = declarationText.substring(0, headerEnd).trim()
        return header.ifBlank { null }
    }

    private fun findNamedAssignmentValue(headerText: String, key: String): NamedAssignmentMatch? {
        val match = NAMED_ASSIGNMENT_PATTERN.matcher(headerText)
        while (match.find()) {
            val currentKey = match.group(1) ?: continue
            if (!currentKey.equals(key, ignoreCase = true)) continue
            val valueStart = match.start(2)
            val valueEnd = match.end(2)
            if (valueStart !in 0..<valueEnd) continue
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
                        return CallArgumentInfo(
                            argumentCount = argumentCount,
                            rightParenOffset = index
                        )
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
        return splitTopLevelWithOffsets(input, delimiter).map { it.text }
    }

    private fun splitTopLevelWithOffsets(input: String, delimiter: Char): List<SplitSegment> {
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
        return result.mapIndexed { index, segmentText ->
            val segmentStart = if (index == 0) {
                0
            } else {
                val priorLength = result.take(index).sumOf { it.length }
                priorLength + index
            }
            SplitSegment(text = segmentText, startOffset = segmentStart)
        }
    }

    private fun parseDeclarationParameterType(rawParameter: String, nameStartInParameter: Int): ParsedParameterType? {
        val typeSearchEnd = nameStartInParameter.coerceIn(0, rawParameter.length)
        if (typeSearchEnd <= 0) return null
        val leadingPart = rawParameter.substring(0, typeSearchEnd)
        val matcher = IDENTIFIER_PATTERN.matcher(leadingPart)
        while (matcher.find()) {
            val candidate = matcher.group() ?: continue
            if (candidate.lowercase(Locale.ROOT) in TYPE_QUALIFIERS) continue
            return ParsedParameterType(
                typeName = candidate,
                rangeInParameter = TextRange(matcher.start(), matcher.end())
            )
        }
        return null
    }

    // 工具函数：PSI/token 遍历与文本范围辅助处理。
    private fun topLevelDeclarations(file: DreamShaderPsiFile): List<DreamShaderDeclaration> {
        return DreamShaderPsiUtil.topLevelDeclarations(file)
    }

    private fun directSectionsOf(declaration: DreamShaderDeclaration): List<DreamShaderSection> {
        return DreamShaderPsiUtil.directSectionsOf(declaration)
    }

    private fun directChildDeclarations(declaration: DreamShaderDeclaration): List<DreamShaderDeclaration> {
        return DreamShaderPsiUtil.directChildDeclarations(declaration)
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
        tokens: List<DreamShaderLexedToken>,
        sectionName: String
    ): TextRange? {
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
        if (start !in 0..<end) return null
        return SectionBody(
            text = text.substring(start + 1, end),
            startOffset = section.textRange.startOffset + start + 1
        )
    }

    private fun sectionBodyText(sectionText: String): String? {
        val start = sectionText.indexOf('{')
        val end = sectionText.lastIndexOf('}')
        if (start !in 0..<end) return null
        return sectionText.substring(start + 1, end)
    }

    private fun displaySectionName(sectionName: String): String {
        return DreamShaderLanguageRules.displaySectionName(sectionName)
    }

    private fun canonicalSectionName(sectionName: String?): String? {
        return DreamShaderLanguageRules.canonicalSectionName(sectionName)
    }

    private fun canonicalSectionNameForDeclaration(declarationKeyword: String, sectionName: String?): String? {
        return DreamShaderLanguageRules.canonicalSectionNameForDeclaration(declarationKeyword, sectionName)
    }

    private fun displayDeclarationKeyword(keyword: String): String {
        return DreamShaderLanguageRules.displayDeclarationKeyword(keyword)
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

    private fun suggestUniqueDeclarationName(baseName: String, occupiedNames: Set<String>): String? {
        val trimmed = baseName.trim()
        if (trimmed.isBlank()) return null
        if (trimmed !in occupiedNames) return trimmed
        for (index in 2..9999) {
            val candidate = "$trimmed$index"
            if (candidate !in occupiedNames) return candidate
        }
        return null
    }

    private fun validateVirtualFunctionAssetPath(value: String): String? {
        if (value.isBlank()) {
            return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetRequiresPath")
        }

        val pathRoot = extractPathRoot(value)
            ?: return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetRequiresPath")
        if (!isAllowedVirtualFunctionAssetRoot(pathRoot)) {
            return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetPathRootNotAllowed", pathRoot)
        }
        return null
    }

    private fun validateConstTextureDefaultAssetValue(type: String, value: String): String? {
        if (value.isBlank()) {
            return DreamShaderBundle.message("diagnostic.constTextureRequiresExplicitDefaultAsset", type)
        }
        val pathRoot = extractPathRoot(value)
            ?: return DreamShaderBundle.message("diagnostic.constTextureDefaultAssetRequiresPath", type)
        if (!isAllowedVirtualFunctionAssetRoot(pathRoot)) {
            return DreamShaderBundle.message("diagnostic.constTextureDefaultAssetPathRootNotAllowed", type, pathRoot)
        }
        return null
    }

    private fun validateGeneralResourcePathValue(value: String): String? {
        if (value.isBlank()) {
            return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetRequiresPath")
        }
        val pathRoot = extractPathRoot(value)
            ?: return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetRequiresPath")
        if (!isAllowedVirtualFunctionAssetRoot(pathRoot)) {
            return DreamShaderBundle.message("diagnostic.virtualFunctionOptionAssetPathRootNotAllowed", pathRoot)
        }
        return null
    }

    private fun validateAssetObjectSegmentSuffix(value: String): String? {
        val objectSegment = extractAssetObjectSegment(value) ?: return null
        val invalidSuffix = INVALID_ASSET_OBJECT_SEGMENT_SUFFIXES.firstOrNull { suffix ->
            objectSegment.endsWith(suffix, ignoreCase = true)
        } ?: return null
        return DreamShaderBundle.message("diagnostic.assetPathObjectSegmentInvalidSuffix", invalidSuffix)
    }

    private fun isQuotedStringLiteral(value: String): Boolean {
        if (value.length < 2) return false
        return value.startsWith("\"") && value.endsWith("\"")
    }

    private fun extractPathRoot(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 3) {
            val unquoted = trimmed.substring(1, trimmed.length - 1).trim()
            if (unquoted.isEmpty()) return null
            if (unquoted.startsWith("/")) {
                val normalized = unquoted.removePrefix("/")
                if (normalized.isBlank()) return null
                val root = normalized.substringBefore('/').trim()
                return if (root.isBlank()) null else root
            }
            return when {
                unquoted.startsWith("Game/") -> "Game"
                unquoted.startsWith("Engine/") -> "Engine"
                unquoted.startsWith("Plugin.") -> unquoted.substringBefore('/')
                unquoted.startsWith("Plugins.") -> unquoted.substringBefore('/')
                unquoted.contains('/') -> unquoted.substringBefore('/')
                else -> null
            }
        }

        val pathMatch = PATH_CALL_PATTERN.matcher(trimmed)
        if (pathMatch.matches()) {
            val inside = trimmed.substringAfter('(').substringBeforeLast(')').trim()
            if (inside.isBlank()) return null
            val args = splitTopLevel(inside, ',').map { it.trim() }
            if (args.size == 1) {
                val onlyArg = args.first().removePrefix("\"").removeSuffix("\"").trim()
                if (onlyArg.startsWith("/")) {
                    val normalized = onlyArg.removePrefix("/")
                    if (normalized.isBlank()) return null
                    return normalized.substringBefore('/').trim().ifBlank { null }
                }
                return null
            }
            if (args.size < 2) return null
            val firstArg = args.firstOrNull().orEmpty()
            if (firstArg.isBlank()) return null
            val secondArg = args[1].removePrefix("\"").removeSuffix("\"").trim()
            if (secondArg.isBlank()) return null
            return firstArg.removePrefix("\"").removeSuffix("\"").trim()
        }
        return null
    }

    private fun extractAssetObjectSegment(value: String): String? {
        val trimmed = value.trim()
        if (trimmed.startsWith("\"") && trimmed.endsWith("\"") && trimmed.length >= 3) {
            val unquoted = trimmed.substring(1, trimmed.length - 1).trim().removePrefix("/")
            val objectSegment = unquoted.substringAfterLast('/').trim()
            return objectSegment.ifBlank { null }
        }

        val pathMatch = PATH_CALL_PATTERN.matcher(trimmed)
        if (pathMatch.matches()) {
            val inside = trimmed.substringAfter('(').substringBeforeLast(')').trim()
            if (inside.isBlank()) return null
            val args = splitTopLevel(inside, ',').map { it.trim() }
            val objectPath = when {
                args.size >= 2 -> args[1].removePrefix("\"").removeSuffix("\"").trim().removePrefix("/")
                args.size == 1 -> args[0].removePrefix("\"").removeSuffix("\"").trim().removePrefix("/").substringAfter('/', "")
                else -> return null
            }
            val objectSegment = objectPath.substringAfterLast('/').trim()
            return objectSegment.ifBlank { null }
        }
        return null
    }

    private fun looksLikePathInitializer(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        if (trimmed.startsWith("\"")) return true
        return PATH_CALL_PATTERN.matcher(trimmed).matches()
    }

    private fun String.isResourceLikeType(): Boolean {
        return lowercase(Locale.ROOT) in RESOURCE_LIKE_TYPES
    }

    private fun parseResourceInitializerCandidate(statement: String): ResourceInitializerCandidate? {
        val trimmedStart = statement.indexOfFirst { !it.isWhitespace() }
        if (trimmedStart < 0) return null
        val trimmed = statement.trim()
        val matcher = RESOURCE_INITIALIZER_PATTERN.matcher(trimmed)
        if (!matcher.find()) return null
        val typeName = matcher.group(1) ?: return null
        val initializer = matcher.group(3)?.trim()
        val initializerStart = matcher.start(3)
        val initializerEnd = matcher.end(3)
        return ResourceInitializerCandidate(
            typeName = typeName,
            initializerText = initializer,
            initializerStartInSegment = trimmedStart + initializerStart,
            initializerEndInSegment = trimmedStart + initializerEnd
        )
    }

    private data class ResourceInitializerCandidate(
        val typeName: String,
        val initializerText: String?,
        val initializerStartInSegment: Int,
        val initializerEndInSegment: Int
    )

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

    private fun previousSignificantToken(tokens: List<DreamShaderLexedToken>, index: Int): DreamShaderLexedToken? {
        var i = index - 1
        while (i >= 0) {
            val token = tokens[i]
            if (!token.isTrivia) return token
            i--
        }
        return null
    }

    private fun lexTokens(sourceText: String, startOffset: Int, endOffset: Int): List<DreamShaderLexedToken> {
        val lexer = DreamShaderLexer()
        lexer.start(sourceText, startOffset, endOffset, 0)
        val result = mutableListOf<DreamShaderLexedToken>()
        var depth = 0
        while (lexer.tokenType != null) {
            val type = lexer.tokenType ?: break
            val start = lexer.tokenStart
            val end = lexer.tokenEnd
            val token = DreamShaderLexedToken(
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

    private data class SplitSegment(
        val text: String,
        val startOffset: Int
    )

    private data class FileDiagnosticInputs(
        val sourceText: String,
        val tokens: List<DreamShaderLexedToken>,
        val topLevelDeclarations: List<DreamShaderDeclaration>,
        val declarationContexts: List<DeclarationContext>
    )

    private data class DeclarationContext(
        val declaration: DreamShaderDeclaration,
        val keyword: String?,
        val directSections: List<DreamShaderSection>,
        val directChildDeclarations: List<DreamShaderDeclaration>,
        val sectionBodies: Map<DreamShaderSection, SectionBody>,
        val sectionTokensBySection: Map<DreamShaderSection, List<DreamShaderLexedToken>>,
        val sectionsByCanonicalNameForDeclaration: Map<String, List<DreamShaderSection>>,
        val typedDeclarationsBySection: Map<DreamShaderSection, List<String>>,
        val parsedSignature: ParsedSignature?,
        val declarationBodyTokens: List<DreamShaderLexedToken>,
        val topLevelBodyTokens: List<DreamShaderLexedToken>
    ) {
        fun canonicalSectionName(section: DreamShaderSection): String? =
            DreamShaderLanguageRules.canonicalSectionName(section.sectionName())

        fun canonicalSectionNameForDeclaration(section: DreamShaderSection): String? {
            val currentKeyword = keyword ?: return null
            return DreamShaderLanguageRules.canonicalSectionNameForDeclaration(currentKeyword, section.sectionName())
        }

        fun firstSection(canonicalName: String): DreamShaderSection? =
            directSections.firstOrNull { canonicalSectionName(it) == canonicalName }

        fun sectionBody(section: DreamShaderSection): SectionBody? = sectionBodies[section]

        fun sectionTokens(section: DreamShaderSection): List<DreamShaderLexedToken>? = sectionTokensBySection[section]

        fun topLevelTypedDeclarations(section: DreamShaderSection?): List<String> =
            if (section == null) emptyList() else typedDeclarationsBySection[section].orEmpty()

        fun topLevelSectionHeaderRange(sectionName: String): TextRange? =
            topLevelSectionHeaderRangeStatic(topLevelBodyTokens, sectionName)

        fun graphLikeSections(): List<DreamShaderSection> =
            directSections.filter {
                val sectionName = canonicalSectionName(it)
                sectionName == "graph" || sectionName == "outputs"
            }
    }

    /**
     * Data model for ParsedParameterType.
     */
    private data class ParsedParameterType(
        val typeName: String,
        val rangeInParameter: TextRange
    )

    /**
     * Data model for ParsedParam.
     */
    private data class ParsedParam(
        val name: String,
        val isOut: Boolean,
        val isOptional: Boolean,
        val defaultValue: String?,
        val typeName: String?,
        val typeRangeInDeclaration: TextRange?,
        val parameterRangeInDeclaration: TextRange?
    )

    /**
     * Data model for ParsedSignature.
     */
    private data class ParsedSignature(
        val params: List<ParsedParam>
    )

    /**
     * Data model for CallArgumentInfo.
     */
    private data class CallArgumentInfo(
        val argumentCount: Int,
        val rightParenOffset: Int
    )

    /**
     * Data model for CallableSignatureCandidate.
     */
    private data class CallableSignatureCandidate(
        val name: String,
        val namespacePath: List<String>,
        val signature: ParsedSignature
    )

    /**
     * Data model for SectionBody.
     */
    private data class SectionBody(
        val text: String,
        val startOffset: Int
    )

    private data class OptionalInputDeclaration(
        val name: String,
        val defaultValue: String?,
        val highlightStartInSegment: Int,
        val highlightEndInSegment: Int
    )

    /**
     * Data model for NamedAssignmentMatch.
     */
    private data class NamedAssignmentMatch(
        val key: String,
        val value: String,
        val startOffset: Int,
        val endOffset: Int
    )

    /**
     * Data model for ExpressionClassArgument.
     */
    private data class ExpressionClassArgument(
        val value: String,
        val range: TextRange,
        val quoted: Boolean
    )

    /**
     * Data model for ImportCreationPlan.
     */
    private data class ImportCreationPlan(
        val relativePath: String,
        val isScopedPackageImport: Boolean
    )

    /**
     * Candidate extension replacement for unsupported import-extension quick fixes.
     */
    private data class ImportExtensionSuggestion(
        val extension: String,
        val resolvesExistingTarget: Boolean,
        val preferredByUserDefault: Boolean,
        val orderIndex: Int
    )

    companion object {
        private val FILE_DIAGNOSTIC_INPUTS_KEY: Key<CachedValue<FileDiagnosticInputs>> =
            Key.create("dreamshader.semantic.file.diagnostic.inputs")

        private fun computeFileDiagnosticInputsStatic(file: DreamShaderPsiFile): FileDiagnosticInputs {
            val sourceText = file.text
            val topLevelDeclarations = DreamShaderPsiUtil.topLevelDeclarations(file)
            return FileDiagnosticInputs(
                sourceText = sourceText,
                tokens = lexFileTokens(sourceText, 0, file.textLength),
                topLevelDeclarations = topLevelDeclarations,
                declarationContexts = buildAllDeclarationContexts(topLevelDeclarations)
            )
        }

        private fun lexFileTokens(sourceText: String, startOffset: Int, endOffset: Int): List<DreamShaderLexedToken> {
            val lexer = DreamShaderLexer()
            lexer.start(sourceText, startOffset, endOffset, 0)
            val result = mutableListOf<DreamShaderLexedToken>()
            var depth = 0
            while (lexer.tokenType != null) {
                val type = lexer.tokenType
                val text = lexer.tokenText
                val range = TextRange(lexer.tokenStart, lexer.tokenEnd)
                val depthBefore = depth
                if (text == "{") depth++
                if (text == "}") depth = (depth - 1).coerceAtLeast(0)
                if (type != null) {
                    result.add(DreamShaderLexedToken(type, text, range, depthBefore))
                }
                lexer.advance()
            }
            return result
        }

        private fun buildAllDeclarationContexts(topLevelDeclarations: List<DreamShaderDeclaration>): List<DeclarationContext> {
            if (topLevelDeclarations.isEmpty()) return emptyList()
            val ordered = mutableListOf<DeclarationContext>()
            val queue = ArrayDeque<DeclarationContext>()
            topLevelDeclarations.forEach { queue.addLast(buildDeclarationContextStatic(it)) }
            while (queue.isNotEmpty()) {
                val context = queue.removeFirst()
                ordered.add(context)
                context.directChildDeclarations.forEach { child ->
                    val childKeyword = child.keywordText()
                    if (childKeyword != "group" && childKeyword != "propgroup") {
                        queue.addLast(buildDeclarationContextStatic(child))
                    }
                }
            }
            return ordered
        }

        private fun buildDeclarationContextStatic(declaration: DreamShaderDeclaration): DeclarationContext {
            val keyword = declaration.keywordText()
            val directSections = directSectionsOfStatic(declaration)
            val directChildDecls = directChildDeclarationsStatic(declaration)
            val groupFlattenedSections = directChildDecls
                .filter { it.keywordText() == "group" || it.keywordText() == "propgroup" }
                .flatMap { DreamShaderPsiUtil.directSectionsOf(it) }
            val allDirectSections = directSections + groupFlattenedSections
            val sectionBodies = allDirectSections.mapNotNull { section ->
                sectionBodyStatic(section)?.let { body -> section to body }
            }.toMap()
            val fileText = declaration.containingFile.text
            val sectionTokensBySection = allDirectSections.mapNotNull { section ->
                val body = sectionBodies[section] ?: return@mapNotNull null
                section to lexFileTokens(fileText, body.startOffset, body.startOffset + body.text.length)
            }.toMap()
            val sectionsByCanonicalNameForDeclaration =
                if (keyword == null) {
                    emptyMap()
                } else {
                    allDirectSections
                        .mapNotNull { section ->
                            DreamShaderLanguageRules.canonicalSectionNameForDeclaration(keyword, section.sectionName())
                                ?.let { name -> name to section }
                        }
                        .groupBy({ it.first }, { it.second })
                }
            val typedDeclarationsBySection = allDirectSections.associateWith { section ->
                sectionBodies[section]?.text?.let(::extractTopLevelTypedDeclarationsStatic).orEmpty()
            }
            return DeclarationContext(
                declaration = declaration,
                keyword = keyword,
                directSections = allDirectSections,
                directChildDeclarations = directChildDecls,
                sectionBodies = sectionBodies,
                sectionTokensBySection = sectionTokensBySection,
                sectionsByCanonicalNameForDeclaration = sectionsByCanonicalNameForDeclaration,
                typedDeclarationsBySection = typedDeclarationsBySection,
                parsedSignature = parseDeclarationParametersStatic(declaration.text),
                declarationBodyTokens = declaration.bodyTextRange()
                    ?.let { range -> lexFileTokens(fileText, range.startOffset, range.endOffset) }
                    .orEmpty(),
                topLevelBodyTokens = declaration.bodyTextRange()
                    ?.let { range -> lexFileTokens(fileText, range.startOffset, range.endOffset) }
                    .orEmpty()
            )
        }

        private fun directSectionsOfStatic(declaration: DreamShaderDeclaration): List<DreamShaderSection> {
            return DreamShaderPsiUtil.directSectionsOf(declaration)
        }

        private fun directChildDeclarationsStatic(declaration: DreamShaderDeclaration): List<DreamShaderDeclaration> {
            return DreamShaderPsiUtil.directChildDeclarations(declaration)
        }

        private fun sectionBodyStatic(section: DreamShaderSection): SectionBody? {
            val text = section.text
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            if (start !in 0..<end) return null
            return SectionBody(
                text = text.substring(start + 1, end),
                startOffset = section.textRange.startOffset + start + 1
            )
        }

        private fun extractTopLevelTypedDeclarationsStatic(sectionBodyText: String): List<String> {
            val result = mutableListOf<String>()
            val matcher = TYPED_DECLARATION_PATTERN.matcher(sectionBodyText)
            while (matcher.find()) {
                val typeName = matcher.group(1)
                if (!typeName.isNullOrBlank()) result.add(typeName)
            }
            return result
        }

        private fun topLevelSectionHeaderRangeStatic(
            tokens: List<DreamShaderLexedToken>,
            sectionName: String
        ): TextRange? {
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

                val next = nextSignificantTokenStatic(tokens, index) ?: continue
                if (next.type == DreamShaderTokenTypes.LBRACE) return token.range
                if (next.type == DreamShaderTokenTypes.OPERATOR && next.text == "=") {
                    val afterEquals = nextSignificantTokenStatic(tokens, tokens.indexOf(next))
                    if (afterEquals?.type == DreamShaderTokenTypes.LBRACE) return token.range
                }
            }
            return null
        }

        private fun nextSignificantTokenStatic(
            tokens: List<DreamShaderLexedToken>,
            index: Int
        ): DreamShaderLexedToken? {
            var i = index + 1
            while (i < tokens.size) {
                val token = tokens[i]
                if (!token.isTrivia) return token
                i++
            }
            return null
        }

        private fun parseDeclarationParametersStatic(declarationText: String): ParsedSignature? {
            val headerEnd = declarationText.indexOf('{').let { if (it >= 0) it else declarationText.length }
            val header = declarationText.substring(0, headerEnd)
            val leftParen = header.indexOf('(')
            val rightParen = header.lastIndexOf(')')
            if (leftParen !in 0..<rightParen) return null
            val rawParams = header.substring(leftParen + 1, rightParen)
            val paramsStartOffset = leftParen + 1
            val params = splitTopLevelWithOffsetsStatic(rawParams, ',').mapNotNull { segment ->
                val rawParam = segment.text
                val trimmed = rawParam.trim()
                if (trimmed.isBlank()) return@mapNotNull null
                val defaultSeparatorIndex = rawParam.indexOf('=')
                val rawParamBeforeDefault = if (defaultSeparatorIndex >= 0) rawParam.substring(0, defaultSeparatorIndex) else rawParam
                val trimmedBeforeDefault = rawParamBeforeDefault.trim()
                val nameMatch = PARAM_NAME_PATTERN.matcher(trimmedBeforeDefault)
                if (!nameMatch.find()) return@mapNotNull null
                val name = nameMatch.group(1) ?: return@mapNotNull null
                val nameStartInRaw = rawParamBeforeDefault.lastIndexOf(name).takeIf { it >= 0 } ?: rawParamBeforeDefault.length
                val parameterType = parseDeclarationParameterTypeStatic(rawParam, nameStartInRaw)
                val typeRangeInDeclaration = parameterType?.rangeInParameter?.let { rangeInParameter ->
                    TextRange(
                        paramsStartOffset + segment.startOffset + rangeInParameter.startOffset,
                        paramsStartOffset + segment.startOffset + rangeInParameter.endOffset
                    )
                }
                val parameterStart = rawParam.indexOfFirst { !it.isWhitespace() }.let { if (it >= 0) it else 0 }
                val parameterEnd = rawParam.indexOfLast { !it.isWhitespace() }.let { if (it >= 0) it + 1 else rawParam.length }
                ParsedParam(
                    name = name,
                    isOut = OUT_QUALIFIER_PATTERN.matcher(trimmedBeforeDefault).find(),
                    isOptional = OPT_QUALIFIER_PATTERN.matcher(trimmedBeforeDefault).find(),
                    defaultValue = if (defaultSeparatorIndex >= 0) rawParam.substring(defaultSeparatorIndex + 1).trim() else null,
                    typeName = parameterType?.typeName,
                    typeRangeInDeclaration = typeRangeInDeclaration,
                    parameterRangeInDeclaration = TextRange(
                        paramsStartOffset + segment.startOffset + parameterStart,
                        paramsStartOffset + segment.startOffset + parameterEnd
                    )
                )
            }
            return ParsedSignature(params)
        }

        private fun parseDeclarationParameterTypeStatic(rawParameter: String, nameStartInParameter: Int): ParsedParameterType? {
            val typeSearchEnd = nameStartInParameter.coerceIn(0, rawParameter.length)
            if (typeSearchEnd <= 0) return null
            val leadingPart = rawParameter.substring(0, typeSearchEnd)
            val matcher = IDENTIFIER_PATTERN.matcher(leadingPart)
            while (matcher.find()) {
                val candidate = matcher.group() ?: continue
                if (candidate.lowercase(Locale.ROOT) in TYPE_QUALIFIERS) continue
                return ParsedParameterType(
                    typeName = candidate,
                    rangeInParameter = TextRange(matcher.start(), matcher.end())
                )
            }
            return null
        }

        private fun splitTopLevelWithOffsetsStatic(input: String, delimiter: Char): List<SplitSegment> {
            if (input.isEmpty()) return listOf(SplitSegment("", 0))
            val result = mutableListOf<String>()
            var start = 0
            var i = 0
            var parenDepth = 0
            var bracketDepth = 0
            var braceDepth = 0
            var inString = false
            var escaped = false
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
                } else {
                    when (ch) {
                        '"' -> inString = true
                        '(' -> parenDepth++
                        ')' -> parenDepth = (parenDepth - 1).coerceAtLeast(0)
                        '[' -> bracketDepth++
                        ']' -> bracketDepth = (bracketDepth - 1).coerceAtLeast(0)
                        '{' -> braceDepth++
                        '}' -> braceDepth = (braceDepth - 1).coerceAtLeast(0)
                        delimiter -> {
                            if (parenDepth == 0 && bracketDepth == 0 && braceDepth == 0) {
                                result.add(input.substring(start, i))
                                start = i + 1
                            }
                        }
                    }
                }
                i++
            }
            result.add(input.substring(start))
            return result.mapIndexed { index, segmentText ->
                val segmentStart = if (index == 0) {
                    0
                } else {
                    val priorLength = result.take(index).sumOf { it.length }
                    priorLength + index
                }
                SplitSegment(text = segmentText, startOffset = segmentStart)
            }
        }

        // 语义校验字典与建议修复的规范候选集。
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
        private val BOOLEAN_SETTING_KEYS = setOf(
            "twosided",
            "wireframe",
            "ditheredlodtransition",
            "ditheropacitymask",
            "allownegativeemissivecolor",
            "castdynamicshadowasmasked",
            "responsiveaa",
            "screenspacereflections",
            "contactshadows",
            "disabledepthtest",
            "outputtranslucentvelocity",
            "tangentspacenormal",
            "fullyrough",
            "issky",
            "thinsurface"
        )
        private val SETTING_VALUE_VALIDATORS: Map<String, (String) -> Boolean> = buildMap {
            SETTING_VALUE_MAPPINGS.forEach { (key, values) ->
                put(key) { value -> value in values }
            }
            BOOLEAN_SETTING_KEYS.forEach { key ->
                put(key) { value -> value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true) }
            }
            put("numcustomizeduvs") { value ->
                value.toIntOrNull()?.let { it in 0..8 } == true
            }
        }

        private val BASE_OUTPUT_MEMBERS = setOf(
            "frontmaterial",
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
            "FrontMaterial",
            "MaterialAttributes", "Attributes", "BaseColor", "EmissiveColor", "Emissive", "Opacity", "OpacityMask",
            "Metallic", "Specular", "Roughness", "Normal", "AmbientOcclusion", "AO", "Refraction", "WorldPositionOffset",
            "WPO", "PixelDepthOffset", "PDO", "SubsurfaceColor", "ClearCoat", "ClearCoatRoughness", "CustomData0",
            "CustomData1", "DiffuseColor", "SpecularColor", "SurfaceThickness", "Displacement", "CustomizedUV0",
            "CustomizedUV1", "CustomizedUV2", "CustomizedUV3", "CustomizedUV4", "CustomizedUV5", "CustomizedUV6",
            "CustomizedUV7", "CustomizedUVs0", "CustomizedUVs1", "CustomizedUVs2", "CustomizedUVs3", "CustomizedUVs4",
            "CustomizedUVs5", "CustomizedUVs6", "CustomizedUVs7", "MOOAEncodedAttribute0", "MOOAEncodedAttribute1",
            "MOOAEncodedAttribute2", "MOOAEncodedAttribute3", "MOOAEncodedAttribute4", "Anisotropy", "Tangent"
        )

        // 类型校验集合：声明类型与 UE.Expression 的 OutputType。
        private val KNOWN_TYPES = DreamShaderLexer.TYPES.map { it.lowercase(Locale.ROOT) }.toSet()
        private val KNOWN_TYPES_CANONICAL = DreamShaderLexer.TYPES.toList()
        private val UE_EXPRESSION_OUTPUT_TYPES = KNOWN_TYPES
        private val UE_EXPRESSION_OUTPUT_TYPES_CANONICAL = KNOWN_TYPES_CANONICAL
        private val TYPE_QUALIFIERS = setOf("in", "out", "inout", "const", "static", "opt")

        // 正则模式：设置项/输出/类型/导入相关诊断。
        private val SETTINGS_ASSIGNMENT_PATTERN: Pattern = Pattern.compile(
            "(?m)\\b([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|-?\\d+(?:\\.\\d+)?|[A-Za-z_][A-Za-z0-9_.]*)"
        )
        private val NAMED_ASSIGNMENT_PATTERN: Pattern = Pattern.compile(
            "(?is)\\b([A-Za-z_][A-Za-z0-9_.]*)\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[A-Za-z_][A-Za-z0-9_./-]*)"
        )
        private val VIRTUAL_FUNCTION_ASSET_ASSIGNMENT_PATTERN: Pattern = Pattern.compile(
            "(?mis)\\bAsset\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|Path\\s*\\([^\\n;{}]*\\)|[A-Za-z_][A-Za-z0-9_.]*)"
        )
        private val VIRTUAL_FUNCTION_DESCRIPTION_ASSIGNMENT_PATTERN: Pattern = Pattern.compile(
            "(?mis)\\bDescription\\s*=\\s*(\"(?:[^\"\\\\]|\\\\.)*\"|[^;\\n{}]+)"
        )
        private val PATH_CALL_WITH_CAPTURE_PATTERN: Pattern = Pattern.compile(
            "(?is)^(\\s*Path\\s*\\()(.*)(\\)\\s*)$"
        )
        private val IDENTIFIER_PATTERN: Pattern = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b")
        private val PATH_CALL_PATTERN: Pattern = Pattern.compile("(?is)Path\\s*\\(.*\\)")
        private val IMPORT_FILE_EXTENSIONS_ORDERED = listOf("dsh", "dsf", "dsm")
        private val IMPORT_FILE_EXTENSIONS = IMPORT_FILE_EXTENSIONS_ORDERED.toSet()
        private val WINDOWS_INVALID_PATH_SEGMENT_CHARS = setOf('<', '>', ':', '"', '|', '?', '*')
        private val BASE_MEMBER_PATTERN: Pattern = Pattern.compile("\\bBase\\.([A-Za-z_][A-Za-z0-9_]*)")
        private val BASE_BINDING_TARGET_PATTERN: Pattern = Pattern.compile(
            "(?i)\\bBase\\.(FrontMaterial|MaterialAttributes)\\b\\s*="
        )
        private val TYPED_DECLARATION_PATTERN: Pattern = Pattern.compile(
            "(?m)(?:^|;)\\s*([A-Za-z_][A-Za-z0-9_]*)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:=|;|\\n)"
        )
        private val CONST_TEXTURE_DECLARATION_PATTERN: Pattern = Pattern.compile(
            "(?is)^const\\s+(TextureCube|Texture2DArray|Texture3D|VolumeTexture)\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:=\\s*(.+))?$"
        )
        private val RESOURCE_INITIALIZER_PATTERN: Pattern = Pattern.compile(
            "(?is)^(?:const\\s+)?([A-Za-z_][A-Za-z0-9_]*)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*=\\s*(.+)$"
        )
        private val CONST_TEXTURE_TYPES_REQUIRING_EXPLICIT_DEFAULT_ASSET = setOf(
            "texturecube",
            "texture2darray",
            "texture3d",
            "volumetexture"
        )
        private val RESOURCE_LIKE_TYPES = setOf(
            "texture2d", "texturecube", "texture2darray", "texture3d", "volumetexture", "samplerstate"
        )
        private val INVALID_ASSET_OBJECT_SEGMENT_SUFFIXES = listOf(
            ".uasset", ".umap",
            ".png", ".jpg", ".jpeg", ".bmp", ".tga", ".tif", ".tiff", ".exr", ".hdr", ".dds", ".gif", ".webp"
        )
        private val SUBSTRATE_ARITHMETIC_OPERATORS = setOf("+", "-", "*", "/")
        private val SUBSTRATE_SWIZZLE_CHARS = setOf('x', 'y', 'z', 'w', 'r', 'g', 'b', 'a')
        private val SUBSTRATE_VECTOR_CONSTRUCTOR_TYPES = setOf(
            "float2", "float3", "float4",
            "half2", "half3", "half4",
            "int2", "int3", "int4",
            "uint2", "uint3", "uint4",
            "vec2", "vec3", "vec4",
            "ivec2", "ivec3", "ivec4",
            "uvec2", "uvec3", "uvec4",
            "bvec2", "bvec3", "bvec4"
        )
        private val TERNARY_EXPRESSION_TERMINATORS = setOf(";", ",")

        // 正则模式：调用签名相关诊断。
        private val PARAM_NAME_PATTERN: Pattern = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*$")
        private val PARAM_NAME_BEFORE_DEFAULT_PATTERN: Pattern = Pattern.compile(
            "\\b([A-Za-z_][A-Za-z0-9_]*)\\s*(?:=|$)"
        )
        private val OUT_QUALIFIER_PATTERN: Pattern = Pattern.compile("\\bout\\b")
        private val OPT_QUALIFIER_PATTERN: Pattern = Pattern.compile("\\bopt\\b")
        private val OPTIONAL_INPUT_DECLARATION_PATTERN: Pattern = Pattern.compile(
            "(?i)^(?=.*\\bopt\\b)(?:\\b(?:in|out|inout|const|static|opt)\\b\\s+)*[A-Za-z_][A-Za-z0-9_<>,]*\\s+[A-Za-z_][A-Za-z0-9_]*\\s*(?:=.*)?$"
        )

        private val UNSUPPORTED_GRAPH_LOOP_KEYWORDS = setOf("for", "while", "do")
        private val UNSUPPORTED_GRAPH_SWITCH_KEYWORDS = setOf("switch", "case", "default")
        private val UNSUPPORTED_GRAPH_FLOW_KEYWORDS = setOf("break", "continue")
        private const val UNSUPPORTED_GRAPH_RETURN_KEYWORD = "return"
    }
}


