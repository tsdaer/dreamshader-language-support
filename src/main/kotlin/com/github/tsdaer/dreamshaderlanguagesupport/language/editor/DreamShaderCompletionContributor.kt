package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeSettingsRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguageRules
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLanguageKeywords
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderParserDefinition
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderPsiParser
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.codeInsight.AutoPopupController
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.editorActions.TypedHandlerDelegate
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.codeInsight.template.TemplateManager
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import java.nio.charset.StandardCharsets
import java.util.*
import javax.swing.Icon

private val TYPE_KEYWORDS = DreamShaderLexer.TYPES.sorted()
private val PARSER_DEFINITION = DreamShaderParserDefinition()
private val PSI_PARSER = DreamShaderPsiParser()

private const val NAMESPACE_KEYWORD = "namespace"
private const val SETTINGS_SECTION = "settings"
private const val OPTIONS_SECTION = "options"
private const val GRAPH_SECTION = "graph"
private const val OUTPUTS_SECTION = "outputs"
private const val RESULTS_SECTION = "results"
private const val INPUTS_SECTION = "inputs"

private enum class BlockKind {
    DECLARATION,
    NAMESPACE,
    SECTION,
    OTHER
}

/**
 * Data model for BlockContext.
 */
private data class BlockContext(
    val kind: BlockKind,
    val name: String? = null
)

/**
 * Data model for DreamShaderCompletionContext.
 */
internal data class DreamShaderCompletionContext(
    val isTopLevel: Boolean,
    val isInDeclarationBody: Boolean,
    val isInCommentOrString: Boolean,
    val isTypeCompletionContext: Boolean,
    val declarationKeyword: String? = null,
    val currentSectionName: String? = null,
    val isInSectionBody: Boolean = false,
    val isInSettingsOrOptionsSection: Boolean = false,
    val isFunctionLikeDeclaration: Boolean = false
)

/**
 * Computes completion context with a resilient fallback chain:
 * PSI -> parser-backed text analysis -> lexer-only fallback.
 */
internal object DreamShaderCompletionContextAnalyzer {
    fun analyze(text: String, offset: Int): DreamShaderCompletionContext {
        if (ApplicationManager.getApplication() == null) {
            return analyzeWithLexer(text, offset)
        }
        val parsedContext = analyzeWithParsedText(text, offset)
        if (parsedContext != null) {
            return parsedContext
        }
        return analyzeWithLexer(text, offset)
    }

    fun analyze(file: PsiFile, offset: Int): DreamShaderCompletionContext {
        val fromPsi = analyzeWithPsi(file, offset)
        if (fromPsi != null) {
            return fromPsi
        }
        return analyzeWithLexer(file.text, offset)
    }

    private fun analyzeWithParsedText(text: String, offset: Int): DreamShaderCompletionContext? {
        if (text.isEmpty()) return null
        return try {
            val builder = PsiBuilderFactoryImpl().createBuilder(PARSER_DEFINITION, DreamShaderLexer(), text)
            val root = PSI_PARSER.parse(DreamShaderElementTypes.FILE, builder)
            analyzeFromParsedRoot(root, offset.coerceIn(0, text.length), text)
        } catch (_: Throwable) {
            null
        }
    }

    private fun analyzeWithPsi(file: PsiFile, offset: Int): DreamShaderCompletionContext? {
        if (file.node == null) return null

        val safeOffset = offset.coerceIn(0, file.textLength)
        var element = file.findElementAt(safeOffset)
        if (element == null && safeOffset > 0) {
            element = file.findElementAt(safeOffset - 1)
        }
        while (element is PsiWhiteSpace && element.prevSibling != null) {
            element = element.prevSibling
        }
        if (element == null) return null

        val declaration = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        val section = PsiTreeUtil.getParentOfType(element, DreamShaderSection::class.java, false)
        val isInCommentOrString = isInCommentOrStringPsi(element)
        val isTopLevel = declaration == null
        val inDeclarationBodyRange = declaration?.bodyTextRange()?.containsOffsetForCompletion(safeOffset) == true
        val inSectionBodyRange = section?.bodyTextRange()?.containsOffsetForCompletion(safeOffset) == true
        val isInDeclarationBody = declaration != null && inDeclarationBodyRange && !inSectionBodyRange
        val sectionName = section?.sectionName()
        val isSettingsOrOptionsSection = (sectionName == SETTINGS_SECTION || sectionName == OPTIONS_SECTION) && inSectionBodyRange
        val isFunctionLikeDeclaration = declaration?.isFunctionLike() == true
        val isTypeCompletionContext = declaration != null && inDeclarationBodyRange &&
            !isSettingsOrOptionsSection &&
            (!isInDeclarationBody || isFunctionLikeDeclaration)

        return DreamShaderCompletionContext(
            isTopLevel = isTopLevel,
            isInDeclarationBody = isInDeclarationBody,
            isInCommentOrString = isInCommentOrString,
            isTypeCompletionContext = isTypeCompletionContext,
            declarationKeyword = declaration?.keywordText(),
            currentSectionName = sectionName,
            isInSectionBody = inSectionBodyRange,
            isInSettingsOrOptionsSection = isSettingsOrOptionsSection,
            isFunctionLikeDeclaration = isFunctionLikeDeclaration
        )
    }

    private fun isInCommentOrStringPsi(element: com.intellij.psi.PsiElement): Boolean {
        var current: com.intellij.psi.PsiElement? = element
        while (current != null) {
            val type = current.node?.elementType
            if (type == DreamShaderTokenTypes.STRING ||
                type == DreamShaderTokenTypes.LINE_COMMENT ||
                type == DreamShaderTokenTypes.BLOCK_COMMENT
            ) {
                return true
            }
            current = current.parent
        }
        return false
    }

    private fun com.intellij.openapi.util.TextRange.containsOffsetForCompletion(offset: Int): Boolean {
        return offset in startOffset..endOffset
    }

    private fun analyzeWithLexer(text: String, offset: Int): DreamShaderCompletionContext {
        val safeOffset = offset.coerceIn(0, text.length)
        val lexer = DreamShaderLexer()
        lexer.start(text, 0, safeOffset, 0)

        val blockStack = mutableListOf<BlockContext>()
        var pendingBlockContext: BlockContext? = null
        var inCommentOrString = false

        while (lexer.tokenType != null) {
            val tokenType = lexer.tokenType!!
            val tokenStart = lexer.tokenStart
            val tokenEnd = lexer.tokenEnd
            val tokenText = text.substring(tokenStart, tokenEnd)

            if (tokenEnd == safeOffset && (
                    tokenType == DreamShaderTokenTypes.STRING ||
                        tokenType == DreamShaderTokenTypes.LINE_COMMENT ||
                        tokenType == DreamShaderTokenTypes.BLOCK_COMMENT
                    )
            ) {
                inCommentOrString = true
            }

            when (tokenType) {
                DreamShaderTokenTypes.KEYWORD -> {
                    val lowered = tokenText.lowercase(Locale.ROOT)
                    pendingBlockContext = when (lowered) {
                        in DreamShaderLanguageKeywords.DECLARATION_KEYWORDS -> BlockContext(BlockKind.DECLARATION, lowered)
                        NAMESPACE_KEYWORD -> BlockContext(BlockKind.NAMESPACE, lowered)
                        else -> pendingBlockContext
                    }
                }

                DreamShaderTokenTypes.SECTION -> {
                    pendingBlockContext = BlockContext(BlockKind.SECTION, tokenText.lowercase(Locale.ROOT))
                }

                DreamShaderTokenTypes.LBRACE -> {
                    blockStack.add(pendingBlockContext ?: BlockContext(BlockKind.OTHER))
                    pendingBlockContext = null
                }

                DreamShaderTokenTypes.RBRACE -> {
                    if (blockStack.isNotEmpty()) {
                        blockStack.removeAt(blockStack.lastIndex)
                    }
                    pendingBlockContext = null
                }

                DreamShaderTokenTypes.OPERATOR -> {
                    if (tokenText.contains(';')) {
                        pendingBlockContext = null
                    }
                }
            }
            lexer.advance()
        }

        val isTopLevel = blockStack.isEmpty()
        val lastDeclarationIndex = blockStack.indexOfLast { it.kind == BlockKind.DECLARATION }
        val isInDeclarationBody = lastDeclarationIndex >= 0 && lastDeclarationIndex == blockStack.lastIndex
        val currentSection = blockStack.lastOrNull { it.kind == BlockKind.SECTION }?.name
        val isInSectionBody = blockStack.lastOrNull()?.kind == BlockKind.SECTION
        val isSettingsOrOptionsSection = currentSection == SETTINGS_SECTION || currentSection == OPTIONS_SECTION
        val currentDeclarationKeyword = blockStack.lastOrNull { it.kind == BlockKind.DECLARATION }?.name
        val isFunctionLikeDeclaration = currentDeclarationKeyword in DreamShaderLanguageKeywords.FUNCTION_LIKE_DECLARATION_KEYWORDS
        val isInsideDeclarationTree = lastDeclarationIndex >= 0
        val isTypeCompletionContext = isInsideDeclarationTree &&
            !isSettingsOrOptionsSection &&
            (!isInDeclarationBody || isFunctionLikeDeclaration)

        return DreamShaderCompletionContext(
            isTopLevel = isTopLevel,
            isInDeclarationBody = isInDeclarationBody,
            isInCommentOrString = inCommentOrString,
            isTypeCompletionContext = isTypeCompletionContext,
            declarationKeyword = currentDeclarationKeyword,
            currentSectionName = currentSection,
            isInSectionBody = isInSectionBody,
            isInSettingsOrOptionsSection = isSettingsOrOptionsSection && isInSectionBody,
            isFunctionLikeDeclaration = isFunctionLikeDeclaration
        )
    }

    private fun analyzeFromParsedRoot(
        root: com.intellij.lang.ASTNode,
        offset: Int,
        text: String
    ): DreamShaderCompletionContext? {
        val targetNode = findInnermostNodeAtOffset(root, offset) ?: return null
        val declarationNode = findAncestorNodeOfType(targetNode, DreamShaderElementTypes.DECLARATION)
        val sectionNode = findAncestorNodeOfType(targetNode, DreamShaderElementTypes.SECTION)
        val isInCommentOrString = targetNode.elementType == DreamShaderTokenTypes.STRING ||
            targetNode.elementType == DreamShaderTokenTypes.LINE_COMMENT ||
            targetNode.elementType == DreamShaderTokenTypes.BLOCK_COMMENT
        val isTopLevel = declarationNode == null

        val declarationPsi = declarationNode?.let { PARSER_DEFINITION.createElement(it) as? DreamShaderDeclaration }
        val sectionPsi = sectionNode?.let { PARSER_DEFINITION.createElement(it) as? DreamShaderSection }

        val inDeclarationBodyRange = declarationPsi?.bodyTextRange()?.containsOffsetForCompletion(offset) == true
        val inSectionBodyRange = sectionPsi?.bodyTextRange()?.containsOffsetForCompletion(offset) == true
        val isInDeclarationBody = declarationPsi != null && inDeclarationBodyRange && !inSectionBodyRange
        val sectionName = sectionPsi?.sectionName()
        val isSettingsOrOptionsSection = (sectionName == SETTINGS_SECTION || sectionName == OPTIONS_SECTION) && inSectionBodyRange
        val isFunctionLikeDeclaration = declarationPsi?.isFunctionLike() == true
        val isTypeCompletionContext = declarationPsi != null && inDeclarationBodyRange &&
            !isSettingsOrOptionsSection &&
            (!isInDeclarationBody || isFunctionLikeDeclaration)

        return DreamShaderCompletionContext(
            isTopLevel = isTopLevel,
            isInDeclarationBody = isInDeclarationBody,
            isInCommentOrString = isInCommentOrString,
            isTypeCompletionContext = isTypeCompletionContext,
            declarationKeyword = declarationPsi?.keywordText(),
            currentSectionName = sectionName,
            isInSectionBody = inSectionBodyRange,
            isInSettingsOrOptionsSection = isSettingsOrOptionsSection,
            isFunctionLikeDeclaration = isFunctionLikeDeclaration
        )
    }

    private fun findInnermostNodeAtOffset(
        root: com.intellij.lang.ASTNode,
        offset: Int
    ): com.intellij.lang.ASTNode? {
        fun walk(node: com.intellij.lang.ASTNode): com.intellij.lang.ASTNode? {
            if (!node.textRange.containsOffsetForCompletion(offset)) return null
            var child = node.firstChildNode
            while (child != null) {
                val found = walk(child)
                if (found != null) return found
                child = child.treeNext
            }
            return node
        }
        return walk(root)
    }

    private fun findAncestorNodeOfType(
        node: com.intellij.lang.ASTNode,
        elementType: com.intellij.psi.tree.IElementType
    ): com.intellij.lang.ASTNode? {
        var current: com.intellij.lang.ASTNode? = node
        while (current != null) {
            if (current.elementType == elementType) {
                return current
            }
            current = current.treeParent
        }
        return null
    }
}

/**
 * Data model for DreamShaderCompletionItem.
 */
internal data class DreamShaderCompletionItem(
    val label: String,
    val insertText: String = label,
    val detail: String? = null,
    val tailText: String? = null,
    val typeText: String? = detail,
    val icon: Icon? = null,
    val priority: Double = 0.0,
    val caretOffset: Int? = null,
    val snippet: String? = null,
    val replacementStartOffset: Int? = null
)

/**
 * Data model for DreamShaderSettingValueContext.
 */
private data class DreamShaderSettingValueContext(
    val settingKey: String,
    val valuePrefix: String,
    val isQuotedValueContext: Boolean
)

/**
 * Data model for DreamShaderExpressionClassValueContext.
 */
private data class DreamShaderExpressionClassValueContext(
    val prefix: String
)

internal data class DreamShaderImportCompletionPrefix(
    val prefix: String,
    val startOffset: Int
)

/**
 * Data model for namespace-qualified callable completion.
 */
internal data class DreamShaderNamespaceCallableCandidate(
    val namespacePath: List<String>,
    val item: DreamShaderCompletionItem
)

/**
 * Data model for declaration-head named-argument completion.
 */
private data class DreamShaderDeclarationHeadCompletionContext(
    val declarationKeyword: String,
    val keyPrefix: String,
    val valueKey: String? = null,
    val valuePrefix: String = ""
)

/**
 * Singleton for DreamShaderCompletionData.
 */
private object DreamShaderCompletionData {
    val settingsKeys = listOf(
        "MaterialDomain",
        "Domain",
        "ShadingModel",
        "BlendMode",
        "RenderType",
        "TranslucencyLightingMode",
        "LightingMode",
        "TwoSided",
        "Wireframe",
        "DitheredLODTransition",
        "DitherOpacityMask",
        "AllowNegativeEmissiveColor",
        "CastDynamicShadowAsMasked",
        "ResponsiveAA",
        "ScreenSpaceReflections",
        "ContactShadows",
        "DisableDepthTest",
        "OutputTranslucentVelocity",
        "TangentSpaceNormal",
        "FullyRough",
        "IsSky",
        "ThinSurface",
        "NumCustomizedUVs",
        "RefractionMethod",
        "RefractionMode"
    )
    val virtualFunctionOptionsKeys = listOf(
        "Asset",
        "Description"
    )
    val virtualFunctionAssetPathTemplates = listOf(
        "Path(Game, Materials/M_VFAsset)",
        "Path(Engine, Materials/M_VFAsset)",
        "Path(Plugin.MyPlugin, MaterialFunctions/MF_VFAsset)",
        "Path(Plugins.MyPlugin, MaterialFunctions/MF_VFAsset)"
    )
    val virtualFunctionAssetObjectPathTemplates = listOf(
        "Game/MaterialFunctions/MF_VFAsset",
        "Engine/Functions/Engine_MF",
        "Plugin.MyPlugin/MaterialFunctions/MF_VFAsset",
        "Plugins.MyPlugin/MaterialFunctions/MF_VFAsset"
    )
    val virtualFunctionDescriptionTemplates = listOf(
        "Existing material function asset",
        "Bridge-compatible virtual function"
    )

    val settingValueMappings = buildMap {
        put(
            "materialdomain", listOf(
                "Surface",
                "DeferredDecal",
                "LightFunction",
                "PostProcess",
                "UserInterface",
                "UI",
                "VirtualTexture"
            )
        )
        put(
            "domain", listOf(
                "Surface",
                "DeferredDecal",
                "LightFunction",
                "PostProcess",
                "UserInterface",
                "UI",
                "VirtualTexture"
            )
        )
        put(
            "shadingmodel", listOf(
                "DefaultLit",
                "Lit",
                "Unlit",
                "Subsurface",
                "PreintegratedSkin",
                "ClearCoat",
                "SubsurfaceProfile",
                "TwoSidedFoliage",
                "Hair",
                "Cloth",
                "Eye",
                "SingleLayerWater",
                "ThinTranslucent",
                "Substrate",
                "Strata"
            )
        )
        put(
            "blendmode", listOf(
                "Opaque",
                "Masked",
                "Cutout",
                "Translucent",
                "Transparent",
                "Additive",
                "Modulate",
                "AlphaComposite",
                "AlphaHoldout"
            )
        )
        put(
            "rendertype", listOf(
                "Opaque",
                "Masked",
                "Cutout",
                "Translucent",
                "Transparent",
                "Additive",
                "Modulate",
                "AlphaComposite",
                "AlphaHoldout"
            )
        )

        val booleanValues = listOf("true", "false")
        listOf(
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
        ).forEach { key ->
            put(key, booleanValues)
        }
        put("numcustomizeduvs", (0..8).map { it.toString() })
    }

    val baseOutputMembers = listOf(
        "MaterialAttributes",
        "Attributes",
        "BaseColor",
        "EmissiveColor",
        "Emissive",
        "Opacity",
        "OpacityMask",
        "Metallic",
        "Specular",
        "Roughness",
        "Normal",
        "AmbientOcclusion",
        "AO",
        "Refraction",
        "WorldPositionOffset",
        "WPO",
        "PixelDepthOffset",
        "PDO",
        "SubsurfaceColor",
        "ClearCoat",
        "ClearCoatRoughness",
        "CustomData0",
        "CustomData1",
        "DiffuseColor",
        "SpecularColor",
        "SurfaceThickness",
        "Displacement",
        "CustomizedUV0",
        "CustomizedUV1",
        "CustomizedUV2",
        "CustomizedUV3",
        "CustomizedUV4",
        "CustomizedUV5",
        "CustomizedUV6",
        "CustomizedUV7",
        "CustomizedUVs0",
        "CustomizedUVs1",
        "CustomizedUVs2",
        "CustomizedUVs3",
        "CustomizedUVs4",
        "CustomizedUVs5",
        "CustomizedUVs6",
        "CustomizedUVs7",
        "MooaEncodedAttribute0",
        "MooaEncodedAttribute1",
        "MooaEncodedAttribute2",
        "MooaEncodedAttribute3",
        "MooaEncodedAttribute4",
        "Anisotropy",
        "Tangent"
    )

    val ueBuiltins = listOf(
        DreamShaderCompletionItem(
            label = "TexCoord",
            insertText = "TexCoord(Index=0)",
            detail = "UE.TexCoord"
        ),
        DreamShaderCompletionItem(
            label = "Time",
            insertText = "Time(Period=4.0)",
            detail = "UE.Time"
        ),
        DreamShaderCompletionItem(
            label = "Panner",
            insertText = "Panner(Coordinate=UV, Time=UE.Time(), Speed=float2(0.1, 0.0))",
            detail = "UE.Panner"
        ),
        DreamShaderCompletionItem(
            label = "WorldPosition",
            insertText = "WorldPosition()",
            detail = "UE.WorldPosition"
        ),
        DreamShaderCompletionItem(
            label = "ObjectPositionWS",
            insertText = "ObjectPositionWS()",
            detail = "UE.ObjectPositionWS"
        ),
        DreamShaderCompletionItem(
            label = "CameraVectorWS",
            insertText = "CameraVectorWS()",
            detail = "UE.CameraVectorWS"
        ),
        DreamShaderCompletionItem(
            label = "ScreenPosition",
            insertText = "ScreenPosition()",
            detail = "UE.ScreenPosition"
        ),
        DreamShaderCompletionItem(
            label = "VertexColor",
            insertText = "VertexColor()",
            detail = "UE.VertexColor"
        ),
        DreamShaderCompletionItem(
            label = "TransformVector",
            insertText = "TransformVector(Input=NormalTS, Source=\"Tangent\", Destination=\"World\")",
            detail = "UE.TransformVector"
        ),
        DreamShaderCompletionItem(
            label = "TransformPosition",
            insertText = "TransformPosition(Input=WorldPos, Source=\"Local\", Destination=\"World\")",
            detail = "UE.TransformPosition"
        ),
        DreamShaderCompletionItem(
            label = "Expression",
            insertText = "Expression(Class=\"Sine\", OutputType=\"float1\", Input=UE.Time())",
            detail = "UE.Expression",
            caretOffset = "Expression(Class=\"".length
        ),
        DreamShaderCompletionItem(
            label = "CollectionParam",
            insertText = "CollectionParam(Collection=Path(Game, MaterialParameterCollections/MPC_Global), Parameter=\"Value\")",
            detail = "UE.CollectionParam"
        ),
        DreamShaderCompletionItem(
            label = "StaticSwitchParameter",
            insertText = "StaticSwitchParameter(Name=\"UseDetail\", Default=true, True=Detail, False=Base)",
            detail = "UE.StaticSwitchParameter"
        )
    )

    val hlslIntrinsics = listOf(
        "abs",
        "acos",
        "asin",
        "atan",
        "atan2",
        "ceil",
        "clamp",
        "cos",
        "cross",
        "ddx",
        "ddy",
        "distance",
        "dot",
        "exp",
        "exp2",
        "floor",
        "frac",
        "fmod",
        "length",
        "lerp",
        "log",
        "log2",
        "max",
        "min",
        "mul",
        "normalize",
        "pow",
        "reflect",
        "rsqrt",
        "saturate",
        "sin",
        "smoothstep",
        "sqrt",
        "step",
        "tan"
    )

    val defaultExpressionClasses = listOf(
        "Sine",
        "Cosine",
        "Multiply",
        "Add",
        "Subtract",
        "TextureSample"
    )
}

/**
 * Singleton for DreamShaderCompletionSuggester.
 */
internal object DreamShaderCompletionSuggester {
    /**
     * Produces context-aware suggestions for declarations, sections, settings,
     * import paths, UE builtins, HLSL intrinsics, and expression classes.
     */
    fun suggest(
        text: String,
        offset: Int,
        importCandidates: List<String> = emptyList(),
        expressionClassCandidates: List<String> = emptyList(),
        callableCandidates: List<DreamShaderCompletionItem> = emptyList(),
        namespaceCallableCandidates: List<DreamShaderNamespaceCallableCandidate> = emptyList(),
        materialExpressionCatalogEntries: List<DreamShaderMaterialExpressionInfo> = emptyList(),
        bridgeSettingValueOverrides: Map<String, List<String>> = emptyMap(),
        bridgeSettingValueDisplayNames: Map<String, Map<String, String>> = emptyMap()
    ): List<DreamShaderCompletionItem> {
        val context = DreamShaderCompletionContextAnalyzer.analyze(text, offset)
        val suggestions = linkedMapOf<String, DreamShaderCompletionItem>()
        fun add(item: DreamShaderCompletionItem) {
            val key = item.label.lowercase(Locale.ROOT)
            val existing = suggestions[key]
            if (existing == null || item.priority > existing.priority) {
                suggestions[key] = item
            }
        }
        fun addAll(items: Iterable<DreamShaderCompletionItem>) = items.forEach(::add)

        val importPrefix = importCompletionPrefix(text, offset)
        if (importPrefix != null) {
            importCandidates
                .filter { it.lowercase(Locale.ROOT).startsWith(importPrefix.prefix.lowercase(Locale.ROOT)) }
                .sorted()
                .forEach { path ->
                    add(
                        DreamShaderCompletionItem(
                            label = path,
                            insertText = path,
                            detail = "DreamShader import",
                            replacementStartOffset = importPrefix.startOffset
                        )
                    )
                }
            return suggestions.values.toList()
        }

        val linePrefix = linePrefix(text, offset)
        val declarationHeadContext = extractDeclarationHeadCompletionContext(text, offset)
        if (declarationHeadContext != null) {
            addAll(declarationHeadCompletionItems(declarationHeadContext))
            if (suggestions.isNotEmpty()) return suggestions.values.toList()
        }

        val expressionClassContext = extractExpressionClassValueContext(text, offset)
        if (expressionClassContext != null) {
            val catalogClassCandidates = DreamShaderMaterialExpressionManifest.expressionClassNames(materialExpressionCatalogEntries)
            val candidates = (expressionClassCandidates + catalogClassCandidates).distinct().ifEmpty {
                DreamShaderCompletionData.defaultExpressionClasses
            }
            candidates
                .filter { it.lowercase(Locale.ROOT).startsWith(expressionClassContext.prefix.lowercase(Locale.ROOT)) }
                .sorted()
                .forEach { className ->
                    add(DreamShaderCompletionItem(label = className, insertText = className, detail = "UE.Expression Class"))
                }
            return suggestions.values.toList()
        }

        val settingValueContext = extractSettingValueContext(linePrefix, text, offset)
        if (settingValueContext != null) {
            val key = settingValueContext.settingKey.lowercase(Locale.ROOT)
            val valuePrefix = settingValueContext.valuePrefix.lowercase(Locale.ROOT)
            val isVirtualFunctionOptionsAliasContext =
                context.declarationKeyword == "virtualfunction" &&
                    (context.currentSectionName == OPTIONS_SECTION || context.currentSectionName == SETTINGS_SECTION)
            val values = when {
                isVirtualFunctionOptionsAliasContext &&
                    key == "asset" &&
                    settingValueContext.isQuotedValueContext -> DreamShaderCompletionData.virtualFunctionAssetObjectPathTemplates
                isVirtualFunctionOptionsAliasContext &&
                    key == "asset" -> DreamShaderCompletionData.virtualFunctionAssetPathTemplates
                isVirtualFunctionOptionsAliasContext &&
                    key == "description" &&
                    settingValueContext.isQuotedValueContext -> DreamShaderCompletionData.virtualFunctionDescriptionTemplates
                else -> bridgeSettingValueOverrides[key]
                    ?.takeIf { it.isNotEmpty() }
                    ?: DreamShaderCompletionData.settingValueMappings[key].orEmpty()
            }
            values
                .filter { value -> value.lowercase(Locale.ROOT).startsWith(valuePrefix) }
                .forEach { value ->
                    val displayName = bridgeSettingValueDisplayNames[key]?.get(value.lowercase(Locale.ROOT))
                    add(
                        DreamShaderCompletionItem(
                            label = value,
                            insertText = value,
                            detail = displayName?.takeIf { it.isNotBlank() } ?: "$key value"
                        )
                    )
                }
            if (suggestions.isNotEmpty()) {
                return suggestions.values.toList()
            }
        }

        if (context.isInCommentOrString) return emptyList()

        if (context.isInSettingsOrOptionsSection) {
            if (isSettingsKeyContext(linePrefix)) {
                val keys = when {
                    (context.currentSectionName == OPTIONS_SECTION || context.currentSectionName == SETTINGS_SECTION) &&
                        context.declarationKeyword == "virtualfunction" -> DreamShaderCompletionData.virtualFunctionOptionsKeys
                    context.currentSectionName == SETTINGS_SECTION -> DreamShaderCompletionData.settingsKeys
                    else -> emptyList()
                }
                keys.forEach { key ->
                    add(DreamShaderCompletionItem(label = key))
                }
                if (suggestions.isNotEmpty()) return suggestions.values.toList()
            }
        }

        if (isOutputsSection(context) && isAfterBaseAccessor(linePrefix)) {
            DreamShaderCompletionData.baseOutputMembers.forEach { member ->
                add(
                    DreamShaderCompletionItem(
                        label = member,
                        insertText = "$member = ;",
                        detail = "Base.$member",
                        typeText = "output",
                        icon = DreamShaderIcons.SECTION_OUTPUTS,
                        priority = 90.0
                    )
                )
            }
            return suggestions.values.toList()
        }

        val namespaceQualifiedPrefix = namespaceQualifiedMemberPrefix(linePrefix)
        if (namespaceQualifiedPrefix != null && isGraphLikeContext(context)) {
            val (namespacePath, memberPrefix) = namespaceQualifiedPrefix
            namespaceCallableCandidates
                .filter { candidate ->
                    candidate.namespacePath.equalsPath(namespacePath) &&
                        candidate.item.label.startsWith(memberPrefix, ignoreCase = true)
                }
                .forEach { candidate ->
                    add(
                        candidate.item.copy(
                            insertText = "${candidate.item.label}()",
                            caretOffset = candidate.item.label.length + 1,
                            priority = candidate.item.priority.coerceAtLeast(72.0)
                        )
                    )
                }
            if (suggestions.isNotEmpty()) return suggestions.values.toList()
        }

        val namespaceMemberPrefix = namespaceMemberPrefix(linePrefix)
        if (namespaceMemberPrefix != null && isGraphLikeContext(context)) {
            val (namespace, memberPrefix) = namespaceMemberPrefix
            catalogCompletionItems(materialExpressionCatalogEntries, namespace)
                .filter { it.label.lowercase(Locale.ROOT).startsWith(memberPrefix.lowercase(Locale.ROOT)) }
                .forEach(::add)
            if (namespace.equals("UE", ignoreCase = true)) {
                DreamShaderCompletionData.ueBuiltins
                    .filter { it.label.lowercase(Locale.ROOT).startsWith(memberPrefix.lowercase(Locale.ROOT)) }
                    .forEach(::add)
            }
            if (namespace.equals("UE", ignoreCase = true) || suggestions.isNotEmpty()) {
                return suggestions.values.toList()
            }
        }

        val catalogNamespaces = materialExpressionCatalogEntries
            .map { it.namespace }
            .filter { it.isNotBlank() && !it.equals("UE", ignoreCase = true) }
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedBy { it.lowercase(Locale.ROOT) }

        if (isGraphLikeContext(context)) {
            callableCandidates
                .filter { it.label.isNotBlank() }
                .forEach(::add)
            localSymbolCompletionItems(text, offset, context).forEach(::add)
            catalogNamespaces
                .map { namespace ->
                    DreamShaderCompletionItem(
                        label = namespace,
                        insertText = "$namespace.",
                        detail = "$namespace material graph namespace",
                        typeText = "namespace",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = 70.0
                    )
                }
                .forEach(::add)
        }

        if (context.isTopLevel) {
            DreamShaderLanguageKeywords.TOP_LEVEL_KEYWORDS.forEach { keyword ->
                add(
                    DreamShaderCompletionItem(
                        label = keyword,
                        typeText = "declaration",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = 40.0
                    )
                )
            }
        }

        if (context.isInDeclarationBody) {
            DreamShaderLanguageRules.completionSectionsForDeclaration(context.declarationKeyword).forEach { section ->
                add(
                    DreamShaderCompletionItem(
                        label = section,
                        typeText = "section",
                        icon = DreamShaderIcons.SECTION,
                        priority = 45.0
                    )
                )
            }
            val declKeyword = context.declarationKeyword?.lowercase(Locale.ROOT)
            val groupAllowed = declKeyword != null && declKeyword !in DreamShaderLanguageKeywords.FUNCTION_LIKE_DECLARATION_KEYWORDS && declKeyword != "namespace"
            if (groupAllowed) {
                add(
                    DreamShaderCompletionItem(
                        label = "Group",
                        insertText = "Group(\"Name\")",
                        detail = "Property grouping scope",
                        typeText = "declaration",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = 44.0,
                        snippet = "Group(\"${'$'}{1:Name}\") {${'$'}{2}}"
                    )
                )
                add(
                    DreamShaderCompletionItem(
                        label = "PropGroup",
                        insertText = "PropGroup(\"Name\")",
                        detail = "Property grouping scope (legacy alias)",
                        typeText = "declaration",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = 42.0,
                        snippet = "PropGroup(\"${'$'}{1:Name}\") {${'$'}{2}}"
                    )
                )
            }
        }

        if (context.isInDeclarationBody && isAfterSectionKeyword(linePrefix)) {
            add(
                DreamShaderCompletionItem(
                    label = "Group",
                    insertText = "Group(\"Name\")",
                    detail = "Section group modifier",
                    typeText = "modifier",
                    icon = DreamShaderIcons.SECTION,
                    priority = 46.0,
                    snippet = "Group(\"${'$'}{1:Name}\") {${'$'}{2}}"
                )
            )
        }

        if (context.isTypeCompletionContext) {
            qualifierCompletionItems(context, linePrefix).forEach(::add)
            TYPE_KEYWORDS.forEach { type ->
                add(
                    DreamShaderCompletionItem(
                        label = type,
                        insertText = type,
                        typeText = "type",
                        priority = 35.0
                    )
                )
            }
        }

        if (isOutputsSection(context) && !linePrefix.contains("Base.")) {
            add(
                DreamShaderCompletionItem(
                    label = "Base",
                    insertText = "Base.",
                    detail = "Root material output namespace",
                    typeText = "output",
                    icon = DreamShaderIcons.SECTION_OUTPUTS,
                    priority = 85.0
                )
            )
        }

        if (isGraphLikeContext(context)) {
            add(
                DreamShaderCompletionItem(
                    label = "UE",
                    insertText = "UE.",
                    detail = "Unreal material graph namespace",
                    typeText = "namespace",
                    icon = DreamShaderIcons.DECLARATION,
                    priority = 80.0
                )
            )
            DreamShaderCompletionData.hlslIntrinsics.forEach { intrinsic ->
                add(
                    DreamShaderCompletionItem(
                        label = intrinsic,
                        insertText = "$intrinsic()",
                        detail = "HLSL intrinsic",
                        tailText = "()",
                        typeText = "HLSL",
                        icon = DreamShaderIcons.FUNCTION,
                        priority = 30.0
                    )
                )
            }
            TYPE_KEYWORDS.forEach { constructor ->
                add(
                    DreamShaderCompletionItem(
                        label = constructor,
                        insertText = "$constructor()",
                        detail = "DreamShader type constructor",
                        tailText = "()",
                        typeText = "constructor",
                        priority = 25.0
                    )
                )
            }
        }

        return suggestions.values.toList()
    }

    private fun isOutputsSection(context: DreamShaderCompletionContext): Boolean {
        return context.isInSectionBody && context.currentSectionName == OUTPUTS_SECTION
    }

    private fun isGraphLikeContext(context: DreamShaderCompletionContext): Boolean {
        if (context.isInDeclarationBody && context.isFunctionLikeDeclaration) return true
        if (!context.isInSectionBody) return false
        return context.currentSectionName == GRAPH_SECTION ||
            context.currentSectionName == OUTPUTS_SECTION ||
            context.currentSectionName == INPUTS_SECTION ||
            context.currentSectionName == RESULTS_SECTION
    }

    private fun isAfterSectionKeyword(linePrefix: String): Boolean {
        val trimmed = linePrefix.trimEnd()
        return DreamShaderLanguageKeywords.SECTION_KEYWORDS.any { section ->
            trimmed.endsWith(section, ignoreCase = true) &&
                (trimmed.length == section.length || trimmed[trimmed.length - section.length - 1].isWhitespace())
        }
    }

    private fun localSymbolCompletionItems(
        text: String,
        offset: Int,
        context: DreamShaderCompletionContext
    ): List<DreamShaderCompletionItem> {
        val safeOffset = offset.coerceIn(0, text.length)
        val scopeStart = currentGraphLikeScopeStart(text, safeOffset, context)
        val prefix = text.substring(scopeStart, safeOffset)
        val results = linkedMapOf<String, DreamShaderCompletionItem>()
        val declarationPattern = Regex("""\b(?:const\s+)?([A-Za-z_][A-Za-z0-9_<>,]*)\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?==|;|,|\))""")
        declarationPattern.findAll(prefix).forEach { match ->
            val type = match.groupValues[1]
            val name = match.groupValues[2]
            val normalized = name.lowercase(Locale.ROOT)
            if (normalized in DreamShaderLanguageKeywords.DECLARATION_KEYWORDS) return@forEach
            if (normalized in DreamShaderLanguageKeywords.SECTION_KEYWORDS.map { it.lowercase(Locale.ROOT) }) return@forEach
            if (name.isBlank() || type.equals("import", ignoreCase = true)) return@forEach
            results.putIfAbsent(
                normalized,
                DreamShaderCompletionItem(
                    label = name,
                    detail = type,
                    typeText = "local",
                    priority = 20.0
                )
            )
        }
        return results.values.toList()
    }

    private fun currentGraphLikeScopeStart(
        text: String,
        offset: Int,
        context: DreamShaderCompletionContext
    ): Int {
        val safeOffset = offset.coerceIn(0, text.length)
        if (!context.isInSectionBody && !context.isFunctionLikeDeclaration) return 0
        val sectionHeader = when (context.currentSectionName) {
            GRAPH_SECTION -> "Graph"
            INPUTS_SECTION -> "Inputs"
            OUTPUTS_SECTION -> "Outputs"
            RESULTS_SECTION -> "Results"
            else -> null
        }
        if (sectionHeader != null) {
            val pattern = Regex("""(?i)\b$sectionHeader\s*(?:=\s*)?\{""")
            pattern.findAll(text.substring(0, safeOffset)).lastOrNull()?.let { match ->
                return match.range.last + 1
            }
        }
        return text.lastIndexOf('{', (safeOffset - 1).coerceAtLeast(0)).let { if (it >= 0) it + 1 else 0 }
    }

    private fun linePrefix(text: String, offset: Int): String {
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOfAny(charArrayOf('\n', '\r'), safeOffset - 1).let { if (it < 0) 0 else it + 1 }
        return text.substring(lineStart, safeOffset)
    }

    internal fun importCompletionPrefix(text: String, offset: Int): DreamShaderImportCompletionPrefix? {
        val safeOffset = offset.coerceIn(0, text.length)
        val prefix = text.substring(0, safeOffset)
        val lineStart = prefix.lastIndexOfAny(charArrayOf('\n', '\r')).let { if (it < 0) 0 else it + 1 }
        val line = prefix.substring(lineStart)
        val match = Regex("""^\s*import\s+"([^"]*)$""").find(line) ?: return null
        val prefixRange = match.groups[1]?.range ?: return null
        return DreamShaderImportCompletionPrefix(
            prefix = match.groupValues[1],
            startOffset = lineStart + prefixRange.first
        )
    }

    private fun extractSettingValueContext(
        linePrefix: String,
        text: String,
        offset: Int
    ): DreamShaderSettingValueContext? {
        val eqIndex = linePrefix.lastIndexOf('=')
        if (eqIndex <= 0) return null
        val lineStart = offset - linePrefix.length
        val valueSegmentStart = (lineStart + eqIndex + 1).coerceIn(0, text.length)
        val rawValuePrefix = text.substring(valueSegmentStart, offset.coerceIn(valueSegmentStart, text.length))
        val trimmedValuePrefix = rawValuePrefix.trimStart()
        val isQuotedValueContext = trimmedValuePrefix.startsWith('"')
        if (isQuotedValueContext) {
            val quoteCount = rawValuePrefix.count { it == '"' }
            if (quoteCount % 2 == 0) return null
        } else if (trimmedValuePrefix.contains(';')) {
            return null
        }

        val keyPart = linePrefix.substring(0, eqIndex)
        val keyMatch = Regex("""([A-Za-z_][A-Za-z0-9_.]*)\s*$""").find(keyPart) ?: return null
        val normalizedValuePrefix = when {
            trimmedValuePrefix.startsWith('"') -> trimmedValuePrefix.removePrefix("\"")
            else -> trimmedValuePrefix
        }.trim()
        return DreamShaderSettingValueContext(
            settingKey = keyMatch.groupValues[1],
            valuePrefix = normalizedValuePrefix,
            isQuotedValueContext = isQuotedValueContext
        )
    }

    private fun isSettingsKeyContext(linePrefix: String): Boolean {
        if (linePrefix.contains('=')) return false
        return Regex("""^\s*[A-Za-z_][A-Za-z0-9_.]*$|^\s*$""").matches(linePrefix)
    }

    private fun isAfterBaseAccessor(linePrefix: String): Boolean {
        return Regex("""\bBase\.([A-Za-z0-9_]*)$""").containsMatchIn(linePrefix)
    }

    private fun namespaceMemberPrefix(linePrefix: String): Pair<String, String>? {
        val match = Regex("""\b([A-Za-z_][A-Za-z0-9_]*)\.([A-Za-z0-9_]*)$""").find(linePrefix) ?: return null
        return match.groupValues[1] to match.groupValues[2]
    }

    private fun namespaceQualifiedMemberPrefix(linePrefix: String): Pair<List<String>, String>? {
        val match = Regex("""\b([A-Za-z_][A-Za-z0-9_]*(?:::[A-Za-z_][A-Za-z0-9_]*)*)::([A-Za-z0-9_]*)$""")
            .find(linePrefix) ?: return null
        return match.groupValues[1].split("::").filter { it.isNotBlank() } to match.groupValues[2]
    }

    private fun List<String>.equalsPath(other: List<String>): Boolean {
        if (size != other.size) return false
        return indices.all { this[it].equals(other[it], ignoreCase = true) }
    }

    private fun qualifierCompletionItems(
        context: DreamShaderCompletionContext,
        linePrefix: String
    ): List<DreamShaderCompletionItem> {
        val trimmed = linePrefix.trimStart()
        if (trimmed.contains('=')) return emptyList()
        if (trimmed.contains(Regex("""\b[A-Za-z_][A-Za-z0-9_]*\s+[A-Za-z_][A-Za-z0-9_]*"""))) return emptyList()
        val inInputLikeSection = context.currentSectionName == INPUTS_SECTION || context.isFunctionLikeDeclaration
        if (!inInputLikeSection) return emptyList()
        return listOf("in", "out", "inout", "opt", "const")
            .filter { it.startsWith(trimmed, ignoreCase = true) || trimmed.isBlank() }
            .map {
                DreamShaderCompletionItem(
                    label = it,
                    insertText = "$it ",
                    typeText = "qualifier",
                    priority = 38.0
                )
            }
    }

    private fun extractDeclarationHeadCompletionContext(
        text: String,
        offset: Int
    ): DreamShaderDeclarationHeadCompletionContext? {
        val safeOffset = offset.coerceIn(0, text.length)
        if (isOffsetInCommentLight(text, safeOffset)) return null
        val prefix = text.substring(0, safeOffset)
        val headStartMatch = Regex(
            """(?is)\b(Shader|ShaderFunction|ShaderLayer|ShaderLayerBlend|VirtualFunction)\s*\([^{};]*$"""
        ).find(prefix.takeLast(500)) ?: return null
        val fragment = headStartMatch.value
        val keyword = headStartMatch.groupValues[1].lowercase(Locale.ROOT)
        val afterParen = fragment.substringAfter('(', missingDelimiterValue = "")
        val assignment = Regex("""(?is)\b(Name|Root)\s*=\s*"?([A-Za-z0-9_./-]*)$""").find(afterParen)
        if (assignment != null) {
            return DreamShaderDeclarationHeadCompletionContext(
                declarationKeyword = keyword,
                keyPrefix = "",
                valueKey = assignment.groupValues[1].lowercase(Locale.ROOT),
                valuePrefix = assignment.groupValues[2]
            )
        }
        val keyPrefix = Regex("""([A-Za-z_]*)$""").find(afterParen)?.groupValues?.getOrNull(1).orEmpty()
        return DreamShaderDeclarationHeadCompletionContext(
            declarationKeyword = keyword,
            keyPrefix = keyPrefix
        )
    }

    private fun declarationHeadCompletionItems(
        context: DreamShaderDeclarationHeadCompletionContext
    ): List<DreamShaderCompletionItem> {
        val valueKey = context.valueKey
        if (valueKey == "root") {
            return listOf("Game", "Plugin.MyPlugin", "Plugins.MyPlugin")
                .filter { it.startsWith(context.valuePrefix, ignoreCase = true) }
                .map {
                    DreamShaderCompletionItem(
                        label = it,
                        insertText = it,
                        detail = "DreamShader asset root",
                        typeText = "root",
                        priority = 82.0
                    )
                }
        }
        if (valueKey == "name") {
            val templates = when (context.declarationKeyword) {
                "shader" -> listOf("Materials/M_Material")
                "shaderfunction", "virtualfunction" -> listOf("Functions/F_Function")
                "shaderlayer" -> listOf("Layers/SL_Layer")
                "shaderlayerblend" -> listOf("Layers/SLB_Blend")
                else -> emptyList()
            }
            return templates
                .filter { it.startsWith(context.valuePrefix, ignoreCase = true) }
                .map {
                    DreamShaderCompletionItem(
                        label = it,
                        insertText = it,
                        detail = "DreamShader asset name",
                        typeText = "name",
                        priority = 81.0
                    )
                }
        }
        val keys = when (context.declarationKeyword) {
            "shader", "shaderfunction", "shaderlayer", "shaderlayerblend" -> listOf("Name", "Root")
            "virtualfunction" -> listOf("Name")
            else -> emptyList()
        }
        return keys
            .filter { it.startsWith(context.keyPrefix, ignoreCase = true) }
            .map { key ->
                DreamShaderCompletionItem(
                    label = key,
                    insertText = "$key=\"\"",
                    detail = "declaration argument",
                    typeText = "argument",
                    priority = 83.0,
                    caretOffset = key.length + 2
                )
            }
    }

    private fun extractExpressionClassValueContext(
        text: String,
        offset: Int
    ): DreamShaderExpressionClassValueContext? {
        val safeOffset = offset.coerceIn(0, text.length)
        val prefixText = text.substring(0, safeOffset).takeLast(600)
        val pattern = Regex(
            """(?is)UE\s*\.\s*Expression\s*\([^)]*?\bClass\s*=\s*"([A-Za-z0-9_]*)$"""
        )
        val match = pattern.find(prefixText) ?: return null
        return DreamShaderExpressionClassValueContext(prefix = match.groupValues[1])
    }

    private fun isOffsetInCommentLight(text: String, offset: Int): Boolean {
        var i = 0
        var inString = false
        var escaped = false
        var inLineComment = false
        var inBlockComment = false
        val safeOffset = offset.coerceIn(0, text.length)
        while (i < safeOffset) {
            val ch = text[i]
            val next = text.getOrNull(i + 1)
            when {
                inLineComment -> if (ch == '\n' || ch == '\r') inLineComment = false
                inBlockComment -> if (ch == '*' && next == '/') {
                    inBlockComment = false
                    i++
                }
                inString -> {
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '"') {
                        inString = false
                    }
                }
                ch == '/' && next == '/' -> {
                    inLineComment = true
                    i++
                }
                ch == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                ch == '"' -> inString = true
            }
            i++
        }
        return inLineComment || inBlockComment
    }

    private fun catalogCompletionItems(
        entries: List<DreamShaderMaterialExpressionInfo>,
        namespace: String
    ): List<DreamShaderCompletionItem> {
        return entries
            .asSequence()
            .filter { it.namespace.equals(namespace, ignoreCase = true) }
            .map { entry ->
                DreamShaderCompletionItem(
                    label = entry.ueName,
                    insertText = catalogInsertText(entry),
                    detail = buildCatalogDetail(entry),
                    tailText = entry.signature
                        ?.substringAfter(entry.ueName, missingDelimiterValue = "")
                        ?.takeIf { it.startsWith("(") },
                    typeText = entry.outputType?.takeIf { it.isNotBlank() } ?: entry.namespace,
                    icon = DreamShaderIcons.FUNCTION,
                    priority = if (entry.namespace.equals("UE", ignoreCase = true)) 75.0 else 78.0,
                    snippet = catalogSnippet(entry)
                )
            }
            .distinctBy { it.label.lowercase(Locale.ROOT) }
            .toList()
    }

    /**
     * 补全弹窗 detail：限定名加可选输出类型（`Substrate.Slab → Substrate`）。
     * 保持简洁，完整描述/参数列表留给悬浮文档。
     */
    private fun buildCatalogDetail(entry: DreamShaderMaterialExpressionInfo): String {
        val outputType = entry.outputType?.takeIf { it.isNotBlank() }
        return if (outputType != null) "${entry.qualifiedName} → $outputType" else entry.qualifiedName
    }

    /**
     * catalog 补全在 `Namespace.` 已输入后触发，故 snippet 需去掉 `Namespace.` 前缀，
     * 避免插入出现重复命名空间（如 `UE.UE.MaterialXScreen(...)`）。前缀不匹配时返回原样。
     */
    private fun catalogSnippet(entry: DreamShaderMaterialExpressionInfo): String? {
        val snippet = entry.snippet?.takeIf { it.isNotBlank() } ?: return null
        val qualifiedPrefix = "${entry.namespace}.${entry.ueName}"
        return if (snippet.startsWith(qualifiedPrefix)) {
            snippet.removePrefix("${entry.namespace}.")
        } else {
            snippet
        }
    }

    private fun catalogInsertText(entry: DreamShaderMaterialExpressionInfo): String {
        val signature = entry.signature?.trim().orEmpty()
        val qualifiedPrefix = "${entry.namespace}.${entry.ueName}"
        if (signature.startsWith(qualifiedPrefix)) {
            return signature.removePrefix("${entry.namespace}.")
        }
        if (signature.startsWith("${entry.ueName}(")) {
            return signature
        }
        return "${entry.ueName}(...)"
    }
}

/**
 * Implementation of DreamShaderInsertTextHandler.
 */
private class DreamShaderInsertTextHandler(
    private val insertText: String,
    private val caretOffset: Int?,
    private val replacementStartOffset: Int? = null
) : InsertHandler<com.intellij.codeInsight.lookup.LookupElement> {
    override fun handleInsert(
        insertionContext: InsertionContext,
        item: com.intellij.codeInsight.lookup.LookupElement
    ) {
        val start = replacementStartOffset
            ?.coerceIn(0, insertionContext.document.textLength)
            ?.takeIf { it <= insertionContext.tailOffset }
            ?: insertionContext.startOffset
        val end = insertionContext.tailOffset
        insertionContext.document.replaceString(start, end, insertText)
        val finalCaret = start + (caretOffset ?: insertText.length)
        insertionContext.editor.caretModel.moveToOffset(finalCaret.coerceIn(start, start + insertText.length))
    }
}

/**
 * 将带 `${N:default}` 占位的 snippet 作为 IntelliJ 活动模板插入，使用户可 Tab 跳转占位符。
 * 先删除 lookup 已插入的 label 文本，再从 [DreamShaderSnippetParser] 装配并启动模板。
 */
private class DreamShaderTemplateInsertHandler(
    private val snippet: String
) : InsertHandler<com.intellij.codeInsight.lookup.LookupElement> {
    override fun handleInsert(
        insertionContext: InsertionContext,
        item: com.intellij.codeInsight.lookup.LookupElement
    ) {
        val editor = insertionContext.editor
        val project = insertionContext.project
        insertionContext.document.deleteString(insertionContext.startOffset, insertionContext.tailOffset)
        editor.caretModel.moveToOffset(insertionContext.startOffset)
        PsiDocumentManager.getInstance(project).commitDocument(insertionContext.document)

        val templateManager = TemplateManager.getInstance(project)
        val template = templateManager.createTemplate("", "")
        template.isToReformat = false
        DreamShaderSnippetParser.fill(template, snippet)
        templateManager.startTemplate(editor, template)
    }
}

private fun collectProjectImportCandidates(file: PsiFile): List<String> {
    val project = file.project
    val scope = GlobalSearchScope.projectScope(project)
    val files = linkedMapOf<String, VirtualFile>()
    fun collectByExtension(extension: String) {
        FilenameIndex.getAllFilesByExt(project, extension, scope).forEach { vf ->
            files.putIfAbsent(vf.path, vf)
        }
    }
    collectByExtension("dsh")
    collectByExtension("dsf")
    collectByExtension("dsm")

    val basePath = project.basePath?.replace('\\', '/')
    val pathCandidates = files.values
        .asSequence()
        .filter { it != file.virtualFile }
        .flatMap { vf ->
            val normalizedPath = vf.path.replace('\\', '/')
            if (basePath != null && normalizedPath.startsWith("$basePath/")) {
                normalizeProjectRelativeImportCandidates(normalizedPath.removePrefix("$basePath/")).asSequence()
            } else {
                sequenceOf(vf.name)
            }
        }
        .distinct()
        .sorted()
        .toList()

    val packageRootCandidates = collectPackageRootImportCandidates(file)
    return (pathCandidates + packageRootCandidates)
        .distinct()
        .sorted()
}

internal fun normalizeProjectRelativeImportCandidates(projectRelativePath: String): List<String> {
    val normalized = projectRelativePath.replace('\\', '/').trimStart('/')
    if (normalized.isBlank()) return emptyList()

    val packagePrefix = "DShader/Packages/"
    if (normalized.startsWith(packagePrefix)) {
        val packageImportPath = normalized.removePrefix(packagePrefix).trimStart('/')
        return if (packageImportPath.isNotBlank()) listOf(packageImportPath) else emptyList()
    }

    val dshaderPrefix = "DShader/"
    if (normalized.startsWith(dshaderPrefix)) {
        val fromDShaderRoot = normalized.removePrefix(dshaderPrefix).trimStart('/')
        return listOfNotNull(
            fromDShaderRoot.takeIf { it.isNotBlank() },
            normalized
        ).distinct()
    }

    return listOf(normalized)
}

private fun collectPackageRootImportCandidates(file: PsiFile): List<String> {
    val projectBase = file.project.basePath ?: return emptyList()
    val packagesRoot = LocalFileSystem.getInstance()
        .findFileByPath("${projectBase.replace('\\', '/')}/DShader/Packages")
        ?: return emptyList()
    if (!packagesRoot.isDirectory) return emptyList()

    val results = linkedSetOf<String>()
    val scopedRoots = packagesRoot.children.filter { it.isDirectory && it.name.startsWith("@") }
    scopedRoots.forEach { scopeDir ->
        scopeDir.children
            .filter { it.isDirectory }
            .forEach { packageDir ->
                results.add("${scopeDir.name}/${packageDir.name}")
            }
    }

    packagesRoot.children
        .filter { it.isDirectory && !it.name.startsWith("@") }
        .forEach { packageDir ->
            results.add(packageDir.name)
        }

    // Prefer manifest name when available to respect canonical package naming.
    (scopedRoots + packagesRoot.children.filter { it.isDirectory && !it.name.startsWith("@") }).forEach { dir ->
        val packageDirs = if (dir.name.startsWith("@")) dir.children.filter { it.isDirectory } else listOf(dir)
        packageDirs.forEach { packageDir ->
            readPackageManifestName(packageDir)?.let { results.add(it) }
        }
    }
    return results.toList()
}

private fun readPackageManifestName(packageDir: VirtualFile): String? {
    val metadata = packageDir.findChild("dreamshader.package.json") ?: return null
    if (!metadata.isValid || metadata.isDirectory) return null
    val content = runCatching { String(metadata.contentsToByteArray(), StandardCharsets.UTF_8) }.getOrNull() ?: return null
    val match = Regex(""""name"\s*:\s*"((?:[^"\\]|\\.)*)"""", setOf(RegexOption.IGNORE_CASE)).find(content) ?: return null
    return match.groupValues[1].trim().takeIf { it.isNotBlank() }
}

private fun collectMaterialExpressionCatalogEntries(file: PsiFile): List<DreamShaderMaterialExpressionInfo> {
    val settings = file.project.getService(DreamShaderProjectSettings::class.java)
    return DreamShaderMaterialExpressionManifest.catalogEntries(
        project = file.project,
        explicitManifestPath = settings?.state?.materialExpressionManifestPath
    )
}

private fun collectCallableCompletionCandidates(file: PsiFile): List<DreamShaderCompletionItem> {
    val importedSourceTexts = DreamShaderImportClosureResolver.resolveImportClosure(file)
        .drop(1)
        .map { it.text }
    return DreamShaderSignatureHelpAnalyzer.collectDeclaredCallables(
        sourceText = file.text,
        additionalSourceTexts = importedSourceTexts
    ).map { callable ->
        DreamShaderCompletionItem(
            label = callable.name,
            insertText = "${callable.name}()",
            detail = callable.signature.presentableText,
            tailText = callable.signature.presentableText.substringAfter(callable.name, missingDelimiterValue = ""),
            typeText = "callable",
            icon = DreamShaderIcons.FUNCTION,
            priority = 65.0,
            caretOffset = callable.name.length + 1
        )
    }
}

private fun collectNamespaceCallableCompletionCandidates(file: PsiFile): List<DreamShaderNamespaceCallableCandidate> {
    val importedSourceTexts = DreamShaderImportClosureResolver.resolveImportClosure(file)
        .drop(1)
        .map { it.text }
    return DreamShaderSignatureHelpAnalyzer.collectDeclaredCallables(
        sourceText = file.text,
        additionalSourceTexts = importedSourceTexts
    ).mapNotNull { callable ->
        val parts = callable.name.split("::").filter { it.isNotBlank() }
        if (parts.size < 2) return@mapNotNull null
        val memberName = parts.last()
        DreamShaderNamespaceCallableCandidate(
            namespacePath = parts.dropLast(1),
            item = DreamShaderCompletionItem(
                label = memberName,
                insertText = "$memberName()",
                detail = callable.signature.presentableText,
                tailText = callable.signature.presentableText.substringAfter(callable.name, missingDelimiterValue = "")
                    .ifBlank { callable.signature.presentableText.substringAfter(memberName, missingDelimiterValue = "") },
                typeText = "callable",
                icon = DreamShaderIcons.FUNCTION,
                priority = 72.0,
                caretOffset = memberName.length + 1
            )
        )
    }
}

/**
 * Bridge `settings.json` 提供的枚举别名，按补全使用的小写键展开（含同义键，
 * 如 materialdomain/domain、blendmode/rendertype）。缺失时返回空表，由调用方回退硬编码。
 */
private fun collectBridgeSettingValueOverrides(file: PsiFile): Map<String, List<String>> {
    val repository = file.project.getService(DreamShaderBridgeSettingsRepository::class.java) ?: return emptyMap()
    val result = mutableMapOf<String, List<String>>()
    fun put(targetKeys: List<String>, bridgeKey: String) {
        val aliases = repository.mappingsForKey(bridgeKey)
            .map { it.alias }
            .filter { it.isNotBlank() }
            .distinct()
        if (aliases.isNotEmpty()) targetKeys.forEach { result[it] = aliases }
    }
    put(listOf("materialdomain", "domain"), "materialdomain")
    put(listOf("shadingmodel"), "shadingmodel")
    put(listOf("blendmode", "rendertype"), "blendmode")
    return result
}

/**
 * Bridge `settings.json` 的枚举显示名查找表：补全键（小写）→（别名小写 → displayName/name）。
 * 用于在补全项 detail 中展示中文 displayName 与对应 UE 枚举名。
 */
private fun collectBridgeSettingValueDisplayNames(file: PsiFile): Map<String, Map<String, String>> {
    val repository = file.project.getService(DreamShaderBridgeSettingsRepository::class.java) ?: return emptyMap()
    val result = mutableMapOf<String, Map<String, String>>()
    fun put(targetKeys: List<String>, bridgeKey: String) {
        val lookup = linkedMapOf<String, String>()
        repository.mappingsForKey(bridgeKey).forEach { mapping ->
            val alias = mapping.alias.takeIf { it.isNotBlank() } ?: return@forEach
            val display = mapping.displayName?.takeIf { it.isNotBlank() }
                ?: mapping.name?.takeIf { it.isNotBlank() }
                ?: return@forEach
            lookup.putIfAbsent(alias.lowercase(Locale.ROOT), display)
        }
        if (lookup.isNotEmpty()) targetKeys.forEach { result[it] = lookup }
    }
    put(listOf("materialdomain", "domain"), "materialdomain")
    put(listOf("shadingmodel"), "shadingmodel")
    put(listOf("blendmode", "rendertype"), "blendmode")
    return result
}

/**
 * Implementation of DreamShaderCompletionContributor.
 */
class DreamShaderCompletionContributor : CompletionContributor() {
    /**
     * IntelliJ completion entrypoint that delegates all domain logic to
     * [DreamShaderCompletionSuggester].
     */
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet
                ) {
                    val file = parameters.originalFile
                    if (file.language != DreamShaderLanguage) return
                    val importPrefix = DreamShaderCompletionSuggester.importCompletionPrefix(file.text, parameters.offset)
                    val targetResult = importPrefix?.let { result.withPrefixMatcher(it.prefix) } ?: result

                    val suggestions = DreamShaderCompletionSuggester.suggest(
                        text = file.text,
                        offset = parameters.offset,
                        importCandidates = collectProjectImportCandidates(file),
                        callableCandidates = collectCallableCompletionCandidates(file),
                        namespaceCallableCandidates = collectNamespaceCallableCompletionCandidates(file),
                        materialExpressionCatalogEntries = collectMaterialExpressionCatalogEntries(file),
                        bridgeSettingValueOverrides = collectBridgeSettingValueOverrides(file),
                        bridgeSettingValueDisplayNames = collectBridgeSettingValueDisplayNames(file)
                    )
                    suggestions.forEach { suggestion ->
                        val builder = LookupElementBuilder.create(suggestion.label)
                            .withTailText(suggestion.tailText, true)
                            .withTypeText(suggestion.typeText ?: suggestion.detail, true)
                            .withIcon(suggestion.icon)
                        val element = when {
                            suggestion.snippet != null ->
                                builder.withInsertHandler(DreamShaderTemplateInsertHandler(suggestion.snippet))
                            suggestion.insertText != suggestion.label || suggestion.replacementStartOffset != null ->
                                builder.withInsertHandler(
                                    DreamShaderInsertTextHandler(
                                        insertText = suggestion.insertText,
                                        caretOffset = suggestion.caretOffset,
                                        replacementStartOffset = suggestion.replacementStartOffset
                                    )
                                )
                            else -> builder
                        }
                        targetResult.addElement(PrioritizedLookupElement.withPriority(element, suggestion.priority))
                    }
                }
            }
        )
    }
}

internal object DreamShaderCompletionAutoPopup {
    fun shouldAutoPopup(text: String, offset: Int, charTyped: Char): Boolean {
        val safeOffset = offset.coerceIn(0, text.length)
        if (charTyped == '"') {
            return isImportStringStart(text, safeOffset) || isSettingValueStringStart(text, safeOffset)
        }
        if (charTyped == '@' && isImportStringStart(text, safeOffset)) return true
        if (isOffsetInCommentOrString(text, safeOffset)) return false
        val context = DreamShaderCompletionContextAnalyzer.analyze(text, safeOffset)
        if (context.isInCommentOrString) return false
        return when (charTyped) {
            '.' -> context.isInSectionBody || context.isFunctionLikeDeclaration
            ':' -> context.isInSectionBody || context.isFunctionLikeDeclaration
            '(' -> context.isInSectionBody || context.isFunctionLikeDeclaration
            '=' -> context.isInSettingsOrOptionsSection
            else -> false
        }
    }

    private fun isImportStringStart(text: String, offset: Int): Boolean {
        val linePrefix = linePrefix(text, offset)
        return Regex("""^\s*import\s+"[^"]*$""").matches(linePrefix)
    }

    private fun isSettingValueStringStart(text: String, offset: Int): Boolean {
        val linePrefix = linePrefix(text, offset)
        return linePrefix.contains('=') && linePrefix.count { it == '"' } % 2 == 1
    }

    private fun linePrefix(text: String, offset: Int): String {
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOfAny(charArrayOf('\n', '\r'), safeOffset - 1).let { if (it < 0) 0 else it + 1 }
        return text.substring(lineStart, safeOffset)
    }

    private fun isOffsetInCommentOrString(text: String, offset: Int): Boolean {
        var i = 0
        var inString = false
        var escaped = false
        var inLineComment = false
        var inBlockComment = false
        val safeOffset = offset.coerceIn(0, text.length)
        while (i < safeOffset) {
            val ch = text[i]
            val next = text.getOrNull(i + 1)
            when {
                inLineComment -> {
                    if (ch == '\n' || ch == '\r') inLineComment = false
                }
                inBlockComment -> {
                    if (ch == '*' && next == '/') {
                        inBlockComment = false
                        i++
                    }
                }
                inString -> {
                    if (escaped) {
                        escaped = false
                    } else if (ch == '\\') {
                        escaped = true
                    } else if (ch == '"') {
                        inString = false
                    }
                }
                ch == '/' && next == '/' -> {
                    inLineComment = true
                    i++
                }
                ch == '/' && next == '*' -> {
                    inBlockComment = true
                    i++
                }
                ch == '"' -> inString = true
            }
            i++
        }
        return inString || inLineComment || inBlockComment
    }
}

class DreamShaderCompletionTypedHandler : TypedHandlerDelegate() {
    override fun checkAutoPopup(
        charTyped: Char,
        project: Project,
        editor: Editor,
        file: PsiFile
    ): Result {
        if (file.language != DreamShaderLanguage) return Result.CONTINUE
        val text = editor.document.charsSequence.toString()
        val offset = editor.caretModel.offset.coerceIn(0, text.length)
        if (!DreamShaderCompletionAutoPopup.shouldAutoPopup(text, offset, charTyped)) {
            return Result.CONTINUE
        }
        AutoPopupController.getInstance(project).scheduleAutoPopup(editor)
        return Result.STOP
    }
}
