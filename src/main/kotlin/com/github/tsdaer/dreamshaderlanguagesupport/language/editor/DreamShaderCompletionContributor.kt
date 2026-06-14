package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLanguageKeywords
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderLexer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderParserDefinition
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.DreamShaderPsiParser
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderSection
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.codeInsight.completion.*
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.lang.impl.PsiBuilderFactoryImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import java.nio.charset.StandardCharsets
import java.util.*

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
    val caretOffset: Int? = null
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
        materialExpressionCatalogEntries: List<DreamShaderMaterialExpressionInfo> = emptyList()
    ): List<DreamShaderCompletionItem> {
        val context = DreamShaderCompletionContextAnalyzer.analyze(text, offset)
        val suggestions = linkedMapOf<String, DreamShaderCompletionItem>()
        fun add(item: DreamShaderCompletionItem) {
            suggestions.putIfAbsent(item.label.lowercase(Locale.ROOT), item)
        }
        fun addAll(items: Iterable<DreamShaderCompletionItem>) = items.forEach(::add)

        val importPrefix = importPrefix(text, offset)
        if (importPrefix != null) {
            importCandidates
                .filter { it.lowercase(Locale.ROOT).startsWith(importPrefix.lowercase(Locale.ROOT)) }
                .sorted()
                .forEach { path ->
                    add(DreamShaderCompletionItem(label = path, insertText = path, detail = "DreamShader import"))
                }
            return suggestions.values.toList()
        }

        val linePrefix = linePrefix(text, offset)
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
                else -> DreamShaderCompletionData.settingValueMappings[key].orEmpty()
            }
            values
                .filter { value -> value.lowercase(Locale.ROOT).startsWith(valuePrefix) }
                .forEach { value ->
                    add(DreamShaderCompletionItem(label = value, insertText = value, detail = "$key value"))
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
                        detail = "Base.$member"
                    )
                )
            }
            return suggestions.values.toList()
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
            catalogNamespaces
                .map { namespace ->
                    DreamShaderCompletionItem(
                        label = namespace,
                        insertText = "$namespace.",
                        detail = "$namespace material graph namespace"
                    )
                }
                .forEach(::add)
        }

        if (context.isTopLevel) {
            DreamShaderLanguageKeywords.TOP_LEVEL_KEYWORDS.forEach { keyword ->
                add(DreamShaderCompletionItem(keyword))
            }
        }

        if (context.isInDeclarationBody) {
            DreamShaderLanguageKeywords.SECTION_KEYWORDS.forEach { section ->
                add(DreamShaderCompletionItem(section))
            }
        }

        if (context.isTypeCompletionContext) {
            TYPE_KEYWORDS.forEach { type ->
                add(DreamShaderCompletionItem(type))
            }
        }

        if (isOutputsSection(context) && !linePrefix.contains("Base.")) {
            add(DreamShaderCompletionItem(label = "Base", insertText = "Base.", detail = "Root material output namespace"))
        }

        if (isGraphLikeContext(context)) {
            add(DreamShaderCompletionItem(label = "UE", insertText = "UE.", detail = "Unreal material graph namespace"))
            DreamShaderCompletionData.hlslIntrinsics.forEach { intrinsic ->
                add(DreamShaderCompletionItem(label = intrinsic, insertText = "$intrinsic()", detail = "HLSL intrinsic"))
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

    private fun linePrefix(text: String, offset: Int): String {
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOfAny(charArrayOf('\n', '\r'), safeOffset - 1).let { if (it < 0) 0 else it + 1 }
        return text.substring(lineStart, safeOffset)
    }

    private fun importPrefix(text: String, offset: Int): String? {
        val safeOffset = offset.coerceIn(0, text.length)
        val prefix = text.substring(0, safeOffset)
        val lineStart = prefix.lastIndexOfAny(charArrayOf('\n', '\r')).let { if (it < 0) 0 else it + 1 }
        val line = prefix.substring(lineStart)
        val match = Regex("""^\s*import\s+"([^"]*)$""").find(line) ?: return null
        return match.groupValues[1]
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
                    detail = entry.qualifiedName
                )
            }
            .distinctBy { it.label.lowercase(Locale.ROOT) }
            .toList()
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
    private val caretOffset: Int?
) : InsertHandler<com.intellij.codeInsight.lookup.LookupElement> {
    override fun handleInsert(
        insertionContext: InsertionContext,
        item: com.intellij.codeInsight.lookup.LookupElement
    ) {
        val start = insertionContext.startOffset
        val end = insertionContext.tailOffset
        insertionContext.document.replaceString(start, end, insertText)
        val finalCaret = start + (caretOffset ?: insertText.length)
        insertionContext.editor.caretModel.moveToOffset(finalCaret.coerceIn(start, start + insertText.length))
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

                    val suggestions = DreamShaderCompletionSuggester.suggest(
                        text = file.text,
                        offset = parameters.offset,
                        importCandidates = collectProjectImportCandidates(file),
                        materialExpressionCatalogEntries = collectMaterialExpressionCatalogEntries(file)
                    )
                    suggestions.forEach { suggestion ->
                        val builder = LookupElementBuilder.create(suggestion.label)
                            .withTypeText(suggestion.detail, true)
                        if (suggestion.insertText != suggestion.label) {
                            result.addElement(
                                builder.withInsertHandler(
                                    DreamShaderInsertTextHandler(
                                        insertText = suggestion.insertText,
                                        caretOffset = suggestion.caretOffset
                                    )
                                )
                            )
                        } else {
                            result.addElement(builder)
                        }
                    }
                }
            }
        )
    }
}
