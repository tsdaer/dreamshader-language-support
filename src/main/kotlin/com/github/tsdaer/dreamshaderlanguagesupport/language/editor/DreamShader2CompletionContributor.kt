package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2InferenceContext
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2Type
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2Argument
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedArgument
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2PragmaDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import java.util.Locale
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ProcessingContext

/**
 * Context-aware completion for the 2.0 dialect (C3), PSI-driven and only active in
 * `.dss`/`.dsi` files:
 *
 * - named arguments for catalog-known calls (`UE.LinearInterpolate(` suggests the
 *   not-yet-filled `inputs[]` pins with `Name = ` insertion; already-filled names and
 *   positionally consumed pins are excluded);
 * - `Substrate` builder member completion (`S.` where `S` is Substrate-typed suggests
 *   the builder pins, minus the ones already written);
 * - `.dsi` parent-parameter completion (`uniform <Type> ` suggests the parent's
 *   parameter schema from the Bridge symbol index);
 * - `#pragma material(...)` key/value completion (Bridge settings.json first, the 1.x
 *   settings-key table as offline fallback).
 *
 * Everything degrades silently when Bridge data is absent - completion without the
 * engine simply shows fewer entries, never errors.
 */
class DreamShader2CompletionContributor : CompletionContributor() {
    init {
        extend(
            CompletionType.BASIC,
            PlatformPatterns.psiElement(),
            object : CompletionProvider<CompletionParameters>() {
                override fun addCompletions(
                    parameters: CompletionParameters,
                    context: ProcessingContext,
                    result: CompletionResultSet,
                ) {
                    val file = parameters.originalFile as? DreamShaderPsiFile ?: return
                    val extension = DreamShaderDialects.extensionOf(file.name)
                    if (extension != "dss" && extension != "dsi") return

                    val offset = parameters.offset
                    val leaf = file.findElementAt(if (offset > 0) offset - 1 else offset) ?: return
                    val items = DreamShader2CompletionSuggester.suggest(
                        file = file,
                        caretLeaf = leaf,
                        catalogEntries = DreamShaderMaterialExpressionManifest.catalogEntries(file.project, manifestPath(file)),
                    )
                    items.forEach { entry ->
                        result.addElement(
                            LookupElementBuilder.create(entry.label)
                                .withTailText(entry.tailText, true)
                                .withTypeText(entry.typeText, true)
                                .withInsertHandler(entry.insertText?.let { text ->
                                    DreamShaderInsertTextHandler(
                                        insertText = text,
                                        caretOffset = entry.caretOffset,
                                        replacementStartOffset = entry.replacementStartOffset,
                                    )
                                })
                        )
                    }
                }
            },
        )
    }

    private fun manifestPath(file: DreamShaderPsiFile): String? =
        file.project.getService(com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings::class.java)
            ?.state?.materialExpressionManifestPath
}

internal data class DreamShader2CompletionEntry(
    val label: String,
    val insertText: String? = null,
    val caretOffset: Int? = null,
    val replacementStartOffset: Int? = null,
    val tailText: String = "",
    val typeText: String? = null,
)

internal object DreamShader2CompletionSuggester {
    fun suggest(file: DreamShaderPsiFile, caretLeaf: PsiElement, catalogEntries: List<DreamShaderMaterialExpressionInfo>): List<DreamShader2CompletionEntry> {
        return namedArgumentCompletions(caretLeaf, catalogEntries)
            ?: substrateMemberCompletions(file, caretLeaf, catalogEntries)
            ?: instanceParameterCompletions(file, caretLeaf)
            ?: pragmaMaterialCompletions(file, caretLeaf)
            ?: emptyList()
    }

    // ------------------------------------------------------------------
    // C3.1 named arguments
    // ------------------------------------------------------------------

    fun namedArgumentCompletions(
        caretLeaf: PsiElement,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShader2CompletionEntry>? {
        val argumentList = nearestAncestorOfType(caretLeaf, DreamShader2ElementTypes.ARGUMENT_LIST) ?: return null

        val call = argumentList.parent
        val calleeName = callCalleeName(call) ?: return null
        val entry = findCatalogEntry(calleeName, catalogEntries) ?: return null
        if (entry.parameters.isEmpty()) return emptyList()

        val arguments = argumentList.children.filter {
            PsiUtilCore.getElementType(it) in setOf(DreamShader2ElementTypes.ARGUMENT, DreamShader2ElementTypes.NAMED_ARGUMENT)
        }
        val filledNames = arguments.filterIsInstance<DreamShader2NamedArgument>()
            .mapNotNull { it.argumentName() }
            .map { it.lowercase() }
            .toSet()
        val positionalCount = arguments.count { PsiUtilCore.getElementType(it) == DreamShader2ElementTypes.ARGUMENT }
        val positionallyConsumed = entry.parameters.take(positionalCount).map { it.name.lowercase() }.toSet()
        val excluded = filledNames + positionallyConsumed

        return entry.parameters
            .filter { it.name.lowercase() !in excluded }
            .map { parameter ->
                DreamShader2CompletionEntry(
                    label = parameter.name,
                    insertText = "${parameter.name} = ",
                    caretOffset = parameter.name.length + 3,
                    tailText = parameter.type?.let { " : $it" } ?: "",
                    typeText = "named argument",
                )
            }
    }

    /** The dotted callee spelling of a CALL_EXPRESSION (`UE.LinearInterpolate`, `lerp`, ...). */
    private fun callCalleeName(call: PsiElement?): String? {
        if (call == null || PsiUtilCore.getElementType(call) != DreamShader2ElementTypes.CALL_EXPRESSION) return null
        val arguments = call.children.firstOrNull {
            PsiUtilCore.getElementType(it) == DreamShader2ElementTypes.ARGUMENT_LIST
        } ?: return null
        val callStart = call.textRange.startOffset
        return call.text.substring(0, arguments.textRange.startOffset - callStart).trim().takeIf { it.isNotEmpty() }
    }

    private fun findCatalogEntry(
        calleeName: String,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): DreamShaderMaterialExpressionInfo? {
        val normalized = calleeName.trim()
        return catalogEntries.firstOrNull { info ->
            info.qualifiedName.equals(normalized, ignoreCase = true) ||
                info.ueName.equals(normalized, ignoreCase = true) ||
                info.aliases.any { it.equals(normalized, ignoreCase = true) }
        }
    }

    // ------------------------------------------------------------------
    // C3.2 Substrate builder members
    // ------------------------------------------------------------------

    fun substrateMemberCompletions(
        file: DreamShaderPsiFile,
        caretLeaf: PsiElement,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShader2CompletionEntry>? {
        val memberAccess = nearestAncestorOfType(caretLeaf, DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION) ?: return null
        val receiverName = memberAccess.firstChild?.text?.trim().orEmpty()
        if (receiverName.isEmpty() || !receiverName[0].isLetter()) return emptyList()

        val psiFile = file as? DreamShaderPsiFile ?: return emptyList()
        val workspace = file.project.getService(DreamShader2WorkspaceSymbols::class.java)
        val inference = DreamShader2InferenceContext.forFile(psiFile, workspace)
        val receiverType = inference.typeOfName(receiverName, memberAccess)
        if (receiverType !is DreamShader2Type.Substrate) return emptyList()

        val catalogPins = catalogEntries
            .filter { it.namespace.equals("Substrate", ignoreCase = true) }
            .flatMap { it.parameters.map { parameter -> parameter.name } }
        val pins = (catalogPins + FALLBACK_SUBSTRATE_PINS).distinct()

        val written = substrateWrittenMembers(file, receiverName)
        return pins
            .filter { pin -> pin !in written }
            .map { pin ->
                DreamShader2CompletionEntry(label = pin, tailText = "", typeText = "Substrate pin")
            }
    }

    /** All member names the builder variable has already had applied (conservatively). */
    private fun substrateWrittenMembers(file: DreamShaderPsiFile, receiverName: String): Set<String> {
        val names = mutableSetOf<String>()
        PsiTreeUtil.collectElements(file) { true }.forEach { element ->
            if (PsiUtilCore.getElementType(element) != DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION) return@forEach
            if (!element.text.startsWith("$receiverName.")) return@forEach
            val member = element.lastChild?.text?.trim().orEmpty()
            if (member.isNotEmpty() && member != "." && member != receiverName) names.add(member)
        }
        return names
    }

    // ------------------------------------------------------------------
    // C3.3 .dsi parent parameters
    // ------------------------------------------------------------------

    fun instanceParameterCompletions(
        file: DreamShaderPsiFile,
        caretLeaf: PsiElement,
    ): List<DreamShader2CompletionEntry>? {
        if (DreamShaderDialects.extensionOf(file.name) != "dsi") return null
        val uniform = PsiTreeUtil.getParentOfType(caretLeaf, DreamShader2UniformDeclaration::class.java) ?: return null
        // Only the declaration-name slot (the identifier after the type) suggests parents.
        nearestAncestorOfType(caretLeaf, DreamShaderTokenTypes.IDENTIFIER) ?: return null
        val schemaParameters = parentSchemaParameters(file.project, file) ?: return emptyList()
        val declared = uniform.declarationName().orEmpty()
        return schemaParameters
            .filter { parameter -> parameter.name != declared }
            .map { parameter ->
                DreamShader2CompletionEntry(
                    label = parameter.name,
                    tailText = parameter.type?.let { " : $it" } ?: "",
                    typeText = "parent parameter",
                )
            }
    }

    private fun parentSchemaParameters(project: Project, file: DreamShaderPsiFile): List<ParentParameter>? {
        val virtualFile = file.virtualFile ?: return null
        val repository = project.getService(DreamShaderSymbolIndexRepository::class.java)
        val snapshot = repository.indexForSource(virtualFile)
        val schema = snapshot.data?.instance?.schema ?: return null
        if (!schema.valid) return null
        return schema.parameters.mapNotNull { parameter ->
            val name = parameter.name ?: return@mapNotNull null
            ParentParameter(name, parameter.type)
        }
    }

    internal data class ParentParameter(val name: String, val type: String?)

    /**
     * Offline fallback for the Substrate builder pins, from upstream
     * `Docs/language-v2/substrate.md` @2.0.2 (the pin names its examples, sugar tables
     * and DSH5295/5296 rules spell out). The engine catalog wins when it is available.
     */
    private val FALLBACK_SUBSTRATE_PINS = listOf(
        "BaseColor", "Metallic", "Specular", "DiffuseAlbedo", "F0", "IOR",
        "Roughness", "Haziness", "SecondRoughness", "SecondRoughnessWeight",
        "Transmittance", "SSSMFP", "Thickness",
    )

    /**
     * Offline fallback for the `#pragma material(...)` keys, from upstream
     * `Docs/settings/material.md` @2.0.2 plus the spellings `Docs/language-v2/index.md`
     * shows. Bridge settings.json wins when it is available.
     */
    private val FALLBACK_MATERIAL_PRAGMA_KEYS = listOf(
        "MaterialDomain", "BlendMode", "ShadingModel", "TwoSided", "Wireframe",
        "OpacityMaskClipValue", "DitheredLODTransition", "DitherOpacityMask",
        "LightingMode", "TranslucentLightingMode", "TranslucencyLightingMode",
        "RefractionMode", "PhysicalMaterial", "PhysicalMaterialMask", "Lightmass",
        "MobileSeparateTranslucency", "AlwaysEvaluateWorldPositionOffset", "ResponsiveAA",
        "ThinSurface", "Substrate", "NumCustomizedUVs", "RenderType",
        "bAllowNegativeEmissiveColor", "bCastDynamicShadowAsMasked", "bCastRayTracedShadows",
        "bEnableResponsiveAA", "bScreenSpaceReflections", "bContactShadows",
        "bDisableDepthTest", "bOutputTranslucentVelocity", "bWriteOnlyAlpha",
        "BlendableOutputAlpha", "bTangentSpaceNormal", "bFullyRough", "bIsSky",
        "bIsThinSurface", "bHasPixelAnimation",
    )

    // ------------------------------------------------------------------
    // C3.4 #pragma material keys and values
    // ------------------------------------------------------------------

    fun pragmaMaterialCompletions(
        file: DreamShaderPsiFile,
        caretLeaf: PsiElement,
    ): List<DreamShader2CompletionEntry>? {
        val beforeCaret = file.text.substring(0, (caretLeaf.textRange.startOffset).coerceAtMost(file.textLength))
        val lineStart = beforeCaret.lastIndexOf('\n').let { if (it < 0) 0 else it + 1 }
        val linePrefix = beforeCaret.substring(lineStart)
        if (!Regex("""^\s*#pragma\s+material\s*\(""").containsMatchIn(linePrefix)) return null

        val valueContext = pragmaValueContext(linePrefix) ?: return pragmaKeyCompletions(file)

        val key = valueContext.lowercase()
        val bridgeValues = com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeSettingsRepository(file.project)
            .mappingsForKey(key)
            .map { it.alias }
            .filter { it.isNotBlank() }
        val fallbackValues = DreamShaderCompletionData.settingValueMappings[valueContext.lowercase(Locale.ROOT)].orEmpty()
        val values = (bridgeValues + fallbackValues).distinct()
        return values.map { value ->
            DreamShader2CompletionEntry(label = value, insertText = "\"$value\"", typeText = "material setting")
        }
    }

    /** The setting key when the caret sits in the value position (`Key = ` before caret). */
    private fun pragmaValueContext(beforeCaret: String): String? {
        val assignment = Regex("""(\w+)\s*=\s*[^\s=]*$""")
            .find(beforeCaret) ?: return null
        return assignment.groupValues[1]
    }

    private fun pragmaKeyCompletions(file: DreamShaderPsiFile): List<DreamShader2CompletionEntry> {
        val keys = linkedSetOf<String>()
        com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeSettingsRepository(file.project)
            .refresh(file.virtualFile)
            .keys
            .forEach { keys.add(it) }
        FALLBACK_MATERIAL_PRAGMA_KEYS.forEach { keys.add(it) }
        return keys.sorted().map { key ->
            DreamShader2CompletionEntry(label = key, insertText = "$key = ", typeText = "material setting")
        }
    }
}

/** The nearest ancestor (inclusive of parents only) whose node type equals [type]. */
internal fun nearestAncestorOfType(start: PsiElement?, type: com.intellij.psi.tree.IElementType): PsiElement? {
    var current: PsiElement? = start
    while (current != null) {
        if (PsiUtilCore.getElementType(current) == type) return current
        current = current.parent
    }
    return null
}
