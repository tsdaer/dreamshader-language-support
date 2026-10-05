package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderIcons
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2InferenceContext
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2Type
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.dreamShader2DeclaratorNames
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.dreamShader2DeclaredTypeSpelling
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.materialAttributeType
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.psiTreeWalk
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedArgument
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionProvider
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.completion.CompletionType
import com.intellij.openapi.project.Project
import com.intellij.patterns.PlatformPatterns
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiUtilCore
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.ProcessingContext
import java.util.Locale

/**
 * Context-aware completion for the 2.0 dialect (M13), PSI-driven and only active in pure
 * 2.0 files (`.dss`/`.dsi` and V2-marked `.dsh`):
 *
 * - receiver-typed member completion after `.` (C9): swizzles from the C2 vector type,
 *   struct fields via the symbol model, matrix cells, texture `sample`, `material`
 *   attributes, `Substrate` builder pins, and catalog namespaces (`UE.*` / `Substrate.*`);
 *   an unknown receiver degrades to namespace roots plus callables, never keywords;
 * - named arguments for catalog-known calls, only at the parameter-name slot (C11), and
 *   documented enum values at their value slots (`Source = ` on `Enum`-annotated parameters);
 * - `.dsi` parent-parameter completion from the Bridge symbol index;
 * - `#pragma material(...)` key/value completion (Bridge settings.json first, the 1.x
 *   settings-key table as offline fallback), quote-aware at value positions;
 * - `#include "..."` path candidates (C14) over the project's 2.0 sources;
 * - bare-identifier completion (C13): locals, parameters, uniforms/globals, structs,
 *   callables from the file and the workspace symbol model, constructors, HLSL intrinsics,
 *   and dialect keywords.
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
                    if (!DreamShaderCompletionDialects.servesV2(file)) return

                    val offset = parameters.offset
                    val leaf = file.findElementAt(if (offset > 0) offset - 1 else offset) ?: return
                    val settings = file.project.getService(DreamShaderProjectSettings::class.java)
                    val items = DreamShader2CompletionSuggester.suggest(
                        file = file,
                        caretLeaf = leaf,
                        offset = offset,
                        catalogEntries = DreamShaderMaterialExpressionManifest.catalogEntries(
                            file.project,
                            settings?.state?.materialExpressionManifestPath,
                        ),
                    )
                    items.forEach { entry ->
                        result.addElement(DreamShaderLookupElementFactory.create(entry))
                    }
                }
            },
        )
    }
}

internal object DreamShader2CompletionSuggester {
    fun suggest(
        file: DreamShaderPsiFile,
        caretLeaf: PsiElement,
        offset: Int,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShaderCompletionItem> {
        return pragmaMaterialCompletions(file, offset)
            ?: includePathCompletions(file, offset)
            ?: memberCompletions(file, caretLeaf, catalogEntries)
            ?: namedArgumentCompletions(caretLeaf, catalogEntries)
            ?: namedArgumentValueCompletions(offset, caretLeaf, catalogEntries)
            ?: instanceParameterCompletions(file, caretLeaf)
            ?: instanceOverrideValueCompletions(file, offset)
            ?: bareIdentifierCompletions(file, caretLeaf)
            ?: emptyList()
    }

    // ------------------------------------------------------------------
    // C9 receiver-typed member completion
    // ------------------------------------------------------------------

    fun memberCompletions(
        file: DreamShaderPsiFile,
        caretLeaf: PsiElement,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShaderCompletionItem>? {
        val memberAccess =
            nearestAncestorOfType(caretLeaf, DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION) ?: return null
        val receiverText = receiverTextOf(memberAccess) ?: return emptyList()

        namespaceMemberCompletions(receiverText, catalogEntries)?.let { return it }

        val workspace = file.project.getService(DreamShader2WorkspaceSymbols::class.java)
        val inference = DreamShader2InferenceContext.forFile(file, workspace)
        return when (val receiverType = receiverTypeOf(memberAccess, receiverText, inference)) {
            is DreamShader2Type.Vector -> swizzleCompletions(receiverType)
            is DreamShader2Type.Scalar -> scalarMemberCompletions(receiverType)
            is DreamShader2Type.Matrix -> matrixMemberCompletions(receiverType)
            is DreamShader2Type.StructType -> structFieldCompletions(receiverType, inference)
            is DreamShader2Type.Texture -> textureMemberCompletions()
            is DreamShader2Type.Material -> materialMemberCompletions()
            is DreamShader2Type.Substrate -> substrateMemberCompletions(file, memberAccess, receiverText, catalogEntries)
            else -> degradedMemberCompletions(file, catalogEntries)
        }
    }

    /** The expression before the trailing dot; null when the member access is malformed. */
    private fun receiverTextOf(memberAccess: PsiElement): String? {
        val text = memberAccess.text
        val lastDot = text.lastIndexOf('.')
        if (lastDot <= 0) return null
        return text.substring(0, lastDot).trim().takeIf { it.isNotEmpty() }
    }

    /**
     * Receiver typing: a single-node receiver (variable, constructor, call) is typed through
     * the C2 inference directly; a dotted-name chain resolves left to right through the same
     * member rules the inference applies to written code.
     */
    private fun receiverTypeOf(
        memberAccess: PsiElement,
        receiverText: String,
        inference: DreamShader2InferenceContext,
    ): DreamShader2Type {
        val nodes = dreamShader2MeaningfulChildren(memberAccess)
        val lastDotIndex = nodes.indexOfLast { it.elementType == DreamShaderTokenTypes.DOT }
        if (lastDotIndex > 0) {
            val receiverNodes = nodes.subList(0, lastDotIndex)
            if (receiverNodes.size == 1 && receiverNodes[0].elementType != DreamShaderTokenTypes.DOT) {
                return inference.typeOf(receiverNodes[0].psi)
            }
        }
        if (!receiverText.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*(\.[A-Za-z_][A-Za-z0-9_]*)*"""))) {
            return DreamShader2Type.Unknown
        }
        val parts = receiverText.split('.')
        var type = inference.typeOfName(parts.first(), memberAccess)
        for (part in parts.drop(1)) {
            type = memberTypeOf(type, part, inference)
            if (type is DreamShader2Type.Unknown) break
        }
        return type
    }

    /** The member rules of [DreamShader2InferenceContext.memberOf], replayed for chains. */
    private fun memberTypeOf(
        base: DreamShader2Type,
        member: String,
        inference: DreamShader2InferenceContext,
    ): DreamShader2Type = when {
        base is DreamShader2Type.Vector -> swizzleType(base, member)
        base is DreamShader2Type.Scalar && member.length == 1 && member[0].lowercaseChar() in SWIZZLE_LETTERS -> base
        base is DreamShader2Type.Material -> materialAttributeType(member)
        base is DreamShader2Type.StructType ->
            inference.structFields(base.name).firstOrNull { it.fieldName() == member }
                ?.let { DreamShader2Type.fromSpelling(it.typeName()) }
                ?: DreamShader2Type.Unknown
        base is DreamShader2Type.Texture && member.equals("sample", ignoreCase = true) ->
            DreamShader2Type.Vector(DreamShader2Type.Family.FLOAT, 4)
        else -> DreamShader2Type.Unknown
    }

    private fun swizzleType(base: DreamShader2Type.Vector, member: String): DreamShader2Type {
        if (member.isEmpty()) return DreamShader2Type.Unknown
        val letters = member.lowercase()
        if (letters.any { it !in SWIZZLE_LETTERS }) return DreamShader2Type.Unknown
        return when (letters.length) {
            1 -> DreamShader2Type.Scalar(base.family)
            in 2..4 -> DreamShader2Type.Vector(base.family, letters.length)
            else -> DreamShader2Type.Unknown
        }
    }

    /** All in-set swizzles (`xyzw` and `rgba`, HLSL forbids mixing sets), lengths 1..4. */
    private fun swizzleCompletions(type: DreamShader2Type.Vector): List<DreamShaderCompletionItem> {
        val family = type.family.spelling
        val sets = listOf("xyzw", "rgba").map { it.take(type.size) }.distinct()
        val items = mutableListOf<DreamShaderCompletionItem>()
        sets.forEach { letters ->
            for (length in 1..4) {
                swizzleCombinations(letters, length).forEach { combo ->
                    items.add(
                        DreamShaderCompletionItem(
                            label = combo,
                            tailText = " : $family$length",
                            typeText = "swizzle",
                            icon = DreamShaderIcons.DECLARATION,
                            priority = DreamShaderCompletionPriorities.TYPED_MEMBER,
                        )
                    )
                }
            }
        }
        return items.distinctBy { it.label }
    }

    private fun swizzleCombinations(letters: String, length: Int): List<String> {
        var current = letters.map { it.toString() }
        repeat(length - 1) {
            current = current.flatMap { prefix -> letters.map { prefix + it } }
        }
        return current
    }

    private fun scalarMemberCompletions(type: DreamShader2Type.Scalar): List<DreamShaderCompletionItem> =
        listOf("x", "r").map { letter ->
            DreamShaderCompletionItem(
                label = letter,
                tailText = " : ${type.family.spelling}",
                typeText = "swizzle",
                icon = DreamShaderIcons.DECLARATION,
                priority = DreamShaderCompletionPriorities.TYPED_MEMBER,
            )
        }

    private fun matrixMemberCompletions(type: DreamShader2Type.Matrix): List<DreamShaderCompletionItem> {
        val family = type.family.spelling
        val items = mutableListOf<DreamShaderCompletionItem>()
        for (row in 0 until type.rows) {
            for (col in 0 until type.cols) {
                items.add(
                    DreamShaderCompletionItem(
                        label = "_m$row$col",
                        tailText = " : $family",
                        typeText = "matrix cell",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = DreamShaderCompletionPriorities.TYPED_MEMBER,
                    )
                )
            }
        }
        return items
    }

    private fun structFieldCompletions(
        type: DreamShader2Type.StructType,
        inference: DreamShader2InferenceContext,
    ): List<DreamShaderCompletionItem> =
        inference.structFields(type.name).mapNotNull { field ->
            val name = field.fieldName() ?: return@mapNotNull null
            DreamShaderCompletionItem(
                label = name,
                tailText = field.typeName()?.let { " : $it" } ?: "",
                typeText = "field",
                icon = DreamShaderIcons.DECLARATION,
                priority = DreamShaderCompletionPriorities.TYPED_MEMBER,
            )
        }

    private fun textureMemberCompletions(): List<DreamShaderCompletionItem> =
        listOf(
            DreamShaderCompletionItem(
                label = "sample",
                insertText = "sample()",
                caretOffset = "sample()".length - 1,
                tailText = " : float4",
                typeText = "texture method",
                icon = DreamShaderIcons.FUNCTION,
                priority = DreamShaderCompletionPriorities.TYPED_MEMBER,
            )
        )

    /** Mirrors the attribute table of [materialAttributeType] - keep the two in sync. */
    private fun materialMemberCompletions(): List<DreamShaderCompletionItem> {
        val attributes = linkedMapOf(
            "FrontMaterial" to "Substrate",
            "BaseColor" to "float3",
            "EmissiveColor" to "float3",
            "Normal" to "float3",
            "Tangent" to "float3",
            "WorldPositionOffset" to "float3",
            "Opacity" to "float",
            "Roughness" to "float",
            "Metallic" to "float",
            "Specular" to "float",
            "AmbientOcclusion" to "float",
            "PixelDepthOffset" to "float",
        )
        return attributes.map { (name, type) ->
            DreamShaderCompletionItem(
                label = name,
                tailText = " : $type",
                typeText = "material attribute",
                icon = DreamShaderIcons.DECLARATION,
                priority = DreamShaderCompletionPriorities.TYPED_MEMBER,
            )
        }
    }

    fun substrateMemberCompletions(
        file: DreamShaderPsiFile,
        memberAccess: PsiElement,
        receiverText: String,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShaderCompletionItem> {
        val pins = linkedMapOf<String, String?>()
        catalogEntries
            .filter { it.namespace.equals("Substrate", ignoreCase = true) }
            .forEach { entry ->
                entry.parameters.forEach { parameter -> pins.putIfAbsent(parameter.name, parameter.type) }
            }
        FALLBACK_SUBSTRATE_PINS.forEach { pins.putIfAbsent(it, null) }

        val written = substrateWrittenMembers(file, memberAccess, receiverText)
        return pins.filterKeys { pin -> pin !in written }.map { (pin, type) ->
            DreamShaderCompletionItem(
                label = pin,
                tailText = type?.let { " : $it" } ?: "",
                typeText = "Substrate pin",
                icon = DreamShaderIcons.DECLARATION,
                priority = DreamShaderCompletionPriorities.SUBSTRATE_PIN,
            )
        }
    }

    /** All member names the builder variable has already had applied (conservatively). */
    private fun substrateWrittenMembers(file: DreamShaderPsiFile, useSite: PsiElement, receiverName: String): Set<String> {
        val scope = PsiTreeUtil.getParentOfType(useSite, DreamShader2FunctionDeclaration::class.java) ?: file
        val names = mutableSetOf<String>()
        psiTreeWalk(scope).forEach { element ->
            if (PsiUtilCore.getElementType(element) != DreamShader2ElementTypes.MEMBER_ACCESS_EXPRESSION) return@forEach
            if (!element.text.startsWith("$receiverName.")) return@forEach
            val member = element.lastChild?.text?.trim().orEmpty()
            if (member.isNotEmpty() && member != "." && member != receiverName) names.add(member)
        }
        return names
    }

    /** `UE.` / `Substrate.` / other catalog namespaces complete their catalog members. */
    private fun namespaceMemberCompletions(
        receiverText: String,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShaderCompletionItem>? {
        if (!receiverText.matches(Regex("""[A-Za-z_][A-Za-z0-9_]*"""))) return null
        val namespace = catalogEntries.map { it.namespace }
            .firstOrNull { it.equals(receiverText, ignoreCase = true) }
            ?: when {
                // The two builtin namespaces stay suggestable even without Bridge catalog data.
                receiverText.equals("UE", ignoreCase = true) -> "UE"
                receiverText.equals("Substrate", ignoreCase = true) -> "Substrate"
                else -> return null
            }
        val items = DreamShaderCompletionSuggester.catalogCompletionItems(catalogEntries, namespace).toMutableList()
        if (namespace.equals("UE", ignoreCase = true)) {
            DreamShaderCompletionData.ueBuiltins.forEach { builtin ->
                items.add(builtin.copy(priority = DreamShaderCompletionPriorities.CATALOG_UE_MEMBER))
            }
        }
        if (namespace.equals("Substrate", ignoreCase = true)) {
            // Offline fallback: the engine catalog normally carries the Substrate factories;
            // without it only the documented Slab builder stays suggestable.
            if (items.none { it.label.equals("Slab", ignoreCase = true) }) {
                items.add(
                    DreamShaderCompletionItem(
                        label = "Slab",
                        insertText = "Slab(...)",
                        detail = "Substrate.Slab",
                        typeText = "builder factory",
                        icon = DreamShaderIcons.FUNCTION,
                        priority = DreamShaderCompletionPriorities.CATALOG_NAMESPACE_MEMBER,
                    )
                )
            }
        }
        return items
    }

    /** Unknown receiver: namespace roots plus callables - never the 1.x keyword dump. */
    private fun degradedMemberCompletions(
        file: DreamShaderPsiFile,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShaderCompletionItem> {
        val items = mutableListOf<DreamShaderCompletionItem>()
        val roots = linkedSetOf("UE")
        catalogEntries.map { it.namespace }
            .filter { it.isNotBlank() }
            .forEach { roots.add(it) }
        roots.forEach { namespace ->
            items.add(
                DreamShaderCompletionItem(
                    label = namespace,
                    insertText = "$namespace.",
                    detail = "$namespace material graph namespace",
                    typeText = "namespace",
                    icon = DreamShaderIcons.DECLARATION,
                    priority = DreamShaderCompletionPriorities.NAMESPACE_ROOT,
                )
            )
        }
        val workspace = file.project.getService(DreamShader2WorkspaceSymbols::class.java)
        items.addAll(callableCompletionItems(file, DreamShader2FileAnalysis.of(file), workspace))
        return items
    }

    // ------------------------------------------------------------------
    // C11 named arguments, only at the parameter-name slot
    // ------------------------------------------------------------------

    fun namedArgumentCompletions(
        caretLeaf: PsiElement,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShaderCompletionItem>? {
        val argumentList = nearestAncestorOfType(caretLeaf, DreamShader2ElementTypes.ARGUMENT_LIST) ?: return null
        if (!isParameterNameSlot(argumentList, caretLeaf)) return null

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
                DreamShaderCompletionItem(
                    label = parameter.name,
                    insertText = "${parameter.name} = ",
                    caretOffset = parameter.name.length + 3,
                    tailText = parameter.type?.let { " : $it" } ?: "",
                    typeText = "named argument",
                    icon = DreamShaderIcons.SECTION_INPUTS,
                    priority = DreamShaderCompletionPriorities.NAMED_ARGUMENT,
                )
            }
    }

    /**
     * True when the caret sits where a parameter name is expected: the text between the
     * argument list's opening paren (or the last top-level comma) and the caret holds no
     * `=`, no nested call, no closed list, and no string literal.
     */
    private fun isParameterNameSlot(argumentList: PsiElement, caretLeaf: PsiElement): Boolean {
        val range = argumentList.textRange
        val caret = caretLeaf.textRange.startOffset.coerceIn(range.startOffset, range.endOffset)
        val before = argumentList.text.substring(0, caret - range.startOffset)
        var depth = 0
        var slotStart = -1
        var inString = false
        var index = 0
        while (index < before.length) {
            val ch = before[index]
            when {
                inString -> {
                    if (ch == '\\') index++
                    else if (ch == '"') inString = false
                }
                ch == '"' -> inString = true
                ch == '(' -> {
                    depth++
                    if (depth == 1) slotStart = index
                }
                ch == ')' -> depth--
                ch == ',' && depth == 1 -> slotStart = index
            }
            index++
        }
        if (inString) return false
        val slot = if (slotStart >= 0) before.substring(slotStart + 1) else before
        return !slot.contains('=') && !slot.contains('(') && !slot.contains(')') && !slot.contains('"')
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
    // C11.2 enum values at named-argument value positions
    // ------------------------------------------------------------------

    /**
     * Value completion for a named argument's value slot (`Source = ` / `Source = "Wo`).
     * Only catalog parameters annotated `Enum` suggest values, from the documented
     * basis-name tables (upstream `Docs/builtins/transform.md`; the engine catalog marks
     * the type but carries no value lists). A non-enum parameter or a slot that is no
     * longer a plain value prefix (closed quote, nested call) returns null so the chain
     * falls through to identifier completion. The typed prefix is left to the platform
     * PrefixMatcher - the caret text may carry the completion dummy identifier, so
     * self-filtering here would empty the popup in the real editor. Quoting follows D9:
     * an open quote takes a bare value, a bare slot inserts quoted.
     */
    fun namedArgumentValueCompletions(
        offset: Int,
        caretLeaf: PsiElement,
        catalogEntries: List<DreamShaderMaterialExpressionInfo>,
    ): List<DreamShaderCompletionItem>? {
        val argumentList = nearestAncestorOfType(caretLeaf, DreamShader2ElementTypes.ARGUMENT_LIST) ?: return null
        val slot = namedArgumentValueSlot(argumentList, offset) ?: return null

        val call = argumentList.parent
        val calleeName = callCalleeName(call) ?: return null
        val entry = findCatalogEntry(calleeName, catalogEntries) ?: return null
        val parameter = entry.parameters.firstOrNull { it.name.equals(slot.parameterName, ignoreCase = true) }
            ?: return null
        if (!parameter.type.equals("Enum", ignoreCase = true)) return null

        val values = documentedEnumValues(entry, parameter.name) ?: return null
        return values.map { value ->
            DreamShaderCompletionItem(
                label = value,
                insertText = if (slot.insideQuotes) value else "\"$value\"",
                detail = "${entry.qualifiedName}.${parameter.name}",
                typeText = "enum value",
                icon = DreamShaderIcons.SECTION_SETTINGS,
                priority = DreamShaderCompletionPriorities.NAMED_ARGUMENT,
            )
        }
    }

    private class NamedArgumentValueSlot(
        val parameterName: String,
        val insideQuotes: Boolean,
    )

    /**
     * The named argument whose value the caret sits in: the text between the argument
     * list's opening paren (or the last top-level comma) and the caret holds `Name = pre`.
     * Name slots, nested calls, strings outside the value prefix, and closed values
     * return null.
     */
    private fun namedArgumentValueSlot(
        argumentList: PsiElement,
        caretOffset: Int,
    ): NamedArgumentValueSlot? {
        val range = argumentList.textRange
        val caret = caretOffset.coerceIn(range.startOffset, range.endOffset)
        val before = argumentList.text.substring(0, caret - range.startOffset)
        var depth = 0
        var slotStart = -1
        var inString = false
        var index = 0
        while (index < before.length) {
            val ch = before[index]
            when {
                inString -> {
                    if (ch == '\\') index++
                    else if (ch == '"') inString = false
                }
                ch == '"' -> inString = true
                ch == '(' -> {
                    depth++
                    if (depth == 1) slotStart = index
                }
                ch == ')' -> depth--
                ch == ',' && depth == 1 -> slotStart = index
            }
            index++
        }
        if (slotStart < 0) return null
        val slot = before.substring(slotStart + 1)
        val assignment = Regex("""([A-Za-z_][A-Za-z0-9_]*)\s*=\s*([\s\S]*)$""").find(slot) ?: return null
        val rawValuePrefix = assignment.groupValues[2]
        val insideQuotes = rawValuePrefix.count { it == '"' } % 2 == 1
        val valuePrefix = if (insideQuotes) rawValuePrefix.removePrefix("\"") else rawValuePrefix.trim()
        if (valuePrefix.contains('(') || valuePrefix.contains('"')) return null
        if (!insideQuotes && valuePrefix.contains(' ')) return null
        return NamedArgumentValueSlot(
            parameterName = assignment.groupValues[1],
            insideQuotes = insideQuotes,
        )
    }

    /**
     * Documented enum values for an `Enum`-annotated parameter, keyed by catalog class
     * name (upstream `Docs/builtins/transform.md` @2.0.2: vector bases take `Tangent`,
     * position bases take `PeriodicWorld`/`TranslatedWorld`; `Tangent` is not a position
     * basis and `PeriodicWorld` is not a vector basis). Unknown parameters degrade silently.
     */
    private fun documentedEnumValues(
        entry: DreamShaderMaterialExpressionInfo,
        parameterName: String,
    ): List<String>? {
        val table = FALLBACK_ENUM_VALUES[entry.className] ?: FALLBACK_ENUM_VALUES[entry.ueName] ?: return null
        return table[parameterName.lowercase(Locale.ROOT)]
    }

    private val FALLBACK_ENUM_VALUES = mapOf(
        "Transform" to mapOf(
            "source" to listOf("Tangent", "Local", "World", "View", "Camera", "Instance"),
            "destination" to listOf("Tangent", "Local", "World", "View", "Camera", "Instance"),
        ),
        "TransformPosition" to mapOf(
            "source" to listOf("Local", "World", "PeriodicWorld", "TranslatedWorld", "FirstPersonTranslatedWorld", "View", "Camera", "Instance"),
            "destination" to listOf("Local", "World", "PeriodicWorld", "TranslatedWorld", "FirstPersonTranslatedWorld", "View", "Camera", "Instance"),
        ),
    )

    // ------------------------------------------------------------------
    // C3.3 .dsi parent parameters
    // ------------------------------------------------------------------

    fun instanceParameterCompletions(
        file: DreamShaderPsiFile,
        caretLeaf: PsiElement,
    ): List<DreamShaderCompletionItem>? {
        if (DreamShaderDialects.extensionOf(file.name) != "dsi") return null
        val uniform = PsiTreeUtil.getParentOfType(caretLeaf, DreamShader2UniformDeclaration::class.java) ?: return null
        // Only the declaration-name slot (the identifier after the type) suggests parents.
        nearestAncestorOfType(caretLeaf, DreamShaderTokenTypes.IDENTIFIER) ?: return null
        val schemaParameters = parentSchemaParameters(file.project, file) ?: return emptyList()
        val declared = uniform.declarationName().orEmpty()
        return schemaParameters
            .filter { parameter -> parameter.name != declared }
            .map { parameter ->
                DreamShaderCompletionItem(
                    label = parameter.name,
                    tailText = parameter.type?.let { " : $it" } ?: "",
                    typeText = "parent parameter",
                    icon = DreamShaderIcons.DECLARATION,
                    priority = DreamShaderCompletionPriorities.INSTANCE_PARAMETER,
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

    // ------------------------------------------------------------------
    // A10 .dsi override values, typed from the declared override spelling
    // ------------------------------------------------------------------

    /**
     * Value-slot completion of a `.dsi` override (`uniform float3 BaseColor = `). The
     * declared type spelling drives the templates, so suggestions match the parent schema
     * type by construction (the DSH7259 rule ties the two together) and work without
     * Bridge data. Resource-typed overrides stay empty - their value spellings are the
     * engine's business.
     */
    fun instanceOverrideValueCompletions(file: DreamShaderPsiFile, offset: Int): List<DreamShaderCompletionItem>? {
        if (DreamShaderDialects.extensionOf(file.name) != "dsi") return null
        val linePrefix = linePrefixBefore(file.text, offset) ?: return null
        if (!Regex("""^\s*uniform\b""").containsMatchIn(linePrefix)) return null
        if (!Regex("""[A-Za-z_][A-Za-z0-9_]*\s*=\s*[^\s=;]*$""").containsMatchIn(linePrefix)) return null
        val typeMatch = Regex("""^\s*uniform\s+([A-Za-z_][A-Za-z0-9_]*)""").find(linePrefix) ?: return null
        return overrideValueItems(DreamShader2Type.fromSpelling(typeMatch.groupValues[1]))
    }

    private fun overrideValueItems(type: DreamShader2Type): List<DreamShaderCompletionItem> {
        fun item(value: String, tail: String) = DreamShaderCompletionItem(
            label = value,
            insertText = value,
            tailText = " : $tail",
            typeText = "override value",
            icon = DreamShaderIcons.SECTION_INPUTS,
            priority = DreamShaderCompletionPriorities.INSTANCE_PARAMETER,
        )
        return when (type) {
            is DreamShader2Type.Scalar -> when (type.family) {
                DreamShader2Type.Family.BOOL -> listOf("true", "false").map { item(it, "bool") }
                DreamShader2Type.Family.INT, DreamShader2Type.Family.UINT -> listOf(item("0", "${type.family.spelling}"))
                else -> listOf(item("0.0", "${type.family.spelling}"))
            }
            is DreamShader2Type.Vector -> {
                val spelling = "${type.family.spelling}${type.size}"
                val zero = if (type.family == DreamShader2Type.Family.BOOL) "false" else "0.0"
                listOf(item("$spelling(${List(type.size) { zero }.joinToString(", ")})", spelling))
            }
            is DreamShader2Type.Matrix -> {
                val spelling = "${type.family.spelling}${type.rows}x${type.cols}"
                listOf(item("$spelling(1.0)", spelling))
            }
            else -> emptyList()
        }
    }

    // ------------------------------------------------------------------
    // C3.4 / D9 #pragma material keys and quote-aware values
    // ------------------------------------------------------------------

    fun pragmaMaterialCompletions(file: DreamShaderPsiFile, offset: Int): List<DreamShaderCompletionItem>? {
        val linePrefix = linePrefixBefore(file.text, offset) ?: return null
        if (!Regex("""^\s*#pragma\s+material\s*\(""").containsMatchIn(linePrefix)) return null

        val valueContext = pragmaValueContext(linePrefix) ?: return pragmaKeyCompletions(file)

        val key = valueContext.lowercase()
        val bridgeValues = com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeSettingsRepository(file.project)
            .mappingsForKey(key)
            .map { it.alias }
            .filter { it.isNotBlank() }
        val fallbackValues = DreamShaderCompletionData.settingValueMappings[valueContext.lowercase(Locale.ROOT)].orEmpty()
        val values = (bridgeValues + fallbackValues).distinct()
        // Inside an already-opened string the value inserts bare (D9); a bare value slot
        // inserts quoted so the closing quote arrives with the selection.
        val insideQuotes = linePrefix.count { it == '"' } % 2 == 1
        return values.map { value ->
            DreamShaderCompletionItem(
                label = value,
                insertText = if (insideQuotes) value else "\"$value\"",
                typeText = "material setting",
                icon = DreamShaderIcons.SECTION_SETTINGS,
                priority = DreamShaderCompletionPriorities.NAMED_ARGUMENT,
            )
        }
    }

    /** The setting key when the caret sits in the value position (`Key = ` before caret). */
    private fun pragmaValueContext(beforeCaret: String): String? {
        val assignment = Regex("""(\w+)\s*=\s*[^\s=]*$""")
            .find(beforeCaret) ?: return null
        return assignment.groupValues[1]
    }

    private fun pragmaKeyCompletions(file: DreamShaderPsiFile): List<DreamShaderCompletionItem> {
        val keys = linkedSetOf<String>()
        com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderBridgeSettingsRepository(file.project)
            .refresh(file.virtualFile)
            .keys
            .forEach { keys.add(it) }
        FALLBACK_MATERIAL_PRAGMA_KEYS.forEach { keys.add(it) }
        return keys.sorted().map { key ->
            DreamShaderCompletionItem(
                label = key,
                insertText = "$key = ",
                typeText = "material setting",
                icon = DreamShaderIcons.SECTION_SETTINGS,
                priority = DreamShaderCompletionPriorities.NAMED_ARGUMENT,
            )
        }
    }

    // ------------------------------------------------------------------
    // C14 #include path candidates
    // ------------------------------------------------------------------

    fun includePathCompletions(file: DreamShaderPsiFile, offset: Int): List<DreamShaderCompletionItem>? {
        val text = file.text
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOfAny(charArrayOf('\n', '\r'), (safeOffset - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        val linePrefix = text.substring(lineStart, safeOffset)
        val match = Regex("""^\s*#include\s+"([^"]*)$""").find(linePrefix) ?: return null
        val prefix = match.groupValues[1]
        val contentStart = lineStart + match.groups[1]!!.range.first
        return collectProjectImportCandidates(file, V2_INCLUDE_EXTENSIONS)
            .filter { it.startsWith(prefix, ignoreCase = true) }
            .sorted()
            .map { path ->
                DreamShaderCompletionItem(
                    label = path,
                    insertText = path,
                    detail = "DreamShader include",
                    replacementStartOffset = contentStart,
                )
            }
    }

    // ------------------------------------------------------------------
    // C13 bare-identifier completion over the 2.0 symbol model
    // ------------------------------------------------------------------

    fun bareIdentifierCompletions(file: DreamShaderPsiFile, caretLeaf: PsiElement): List<DreamShaderCompletionItem> {
        if (isInCommentOrString(caretLeaf)) return emptyList()
        val analysis = DreamShader2FileAnalysis.of(file)
        val workspace = file.project.getService(DreamShader2WorkspaceSymbols::class.java)
        val items = LinkedHashMap<String, DreamShaderCompletionItem>()
        fun add(item: DreamShaderCompletionItem) {
            items.putIfAbsent(item.label.lowercase(Locale.ROOT), item)
        }

        val enclosingFunction = PsiTreeUtil.getParentOfType(caretLeaf, DreamShader2FunctionDeclaration::class.java)
        if (enclosingFunction != null) {
            localCompletionItems(enclosingFunction, caretLeaf).forEach(::add)
            parameterCompletionItems(enclosingFunction).forEach(::add)
        }
        (analysis.uniforms.asSequence() + analysis.globals.asSequence()).forEach { declaration ->
            val type = dreamShader2DeclaredTypeSpelling(declaration)
            dreamShader2DeclaratorNames(declaration).forEach { name ->
                add(
                    DreamShaderCompletionItem(
                        label = name,
                        tailText = type?.let { " : $it" } ?: "",
                        typeText = "uniform",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = DreamShaderCompletionPriorities.LOCAL_SYMBOL,
                    )
                )
            }
        }
        analysis.structs.forEach { struct ->
            struct.declarationName()?.let { name ->
                add(
                    DreamShaderCompletionItem(
                        label = name,
                        typeText = "struct",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = DreamShaderCompletionPriorities.TYPE,
                    )
                )
            }
        }
        callableCompletionItems(file, analysis, workspace).forEach(::add)
        TYPE_KEYWORDS.forEach { type ->
            add(
                DreamShaderCompletionItem(
                    label = type,
                    insertText = "$type()",
                    tailText = "()",
                    typeText = "constructor",
                    icon = DreamShaderIcons.FUNCTION,
                    priority = DreamShaderCompletionPriorities.CONSTRUCTOR,
                )
            )
        }
        DreamShaderCompletionData.hlslIntrinsics.forEach { intrinsic ->
            add(
                DreamShaderCompletionItem(
                    label = intrinsic,
                    insertText = "$intrinsic()",
                    tailText = "()",
                    typeText = "HLSL",
                    icon = DreamShaderIcons.FUNCTION,
                    priority = DreamShaderCompletionPriorities.HLSL_INTRINSIC,
                )
            )
        }
        if (enclosingFunction == null) {
            listOf("export", "extern", "struct", "uniform").forEach { keyword ->
                add(
                    DreamShaderCompletionItem(
                        label = keyword,
                        typeText = "declaration",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = DreamShaderCompletionPriorities.KEYWORD,
                    )
                )
            }
            add(
                DreamShaderCompletionItem(
                    label = "#pragma",
                    insertText = "#pragma material()",
                    caretOffset = "#pragma material(".length,
                    typeText = "directive",
                    icon = DreamShaderIcons.SECTION_SETTINGS,
                    priority = DreamShaderCompletionPriorities.KEYWORD,
                )
            )
            add(
                DreamShaderCompletionItem(
                    label = "#include",
                    insertText = "#include \"\"",
                    caretOffset = "#include ".length,
                    typeText = "directive",
                    priority = DreamShaderCompletionPriorities.KEYWORD,
                )
            )
        } else {
            listOf("if", "else", "for", "while", "return").forEach { keyword ->
                add(
                    DreamShaderCompletionItem(
                        label = keyword,
                        typeText = "keyword",
                        priority = DreamShaderCompletionPriorities.KEYWORD,
                    )
                )
            }
        }
        return items.values.toList()
    }

    /** Locals declared textually before the caret inside the enclosing function. */
    private fun localCompletionItems(
        function: DreamShader2FunctionDeclaration,
        caretLeaf: PsiElement,
    ): List<DreamShaderCompletionItem> {
        val caretStart = caretLeaf.textRange.startOffset
        val items = LinkedHashMap<String, DreamShaderCompletionItem>()
        psiTreeWalk(function).forEach { statement ->
            if (PsiUtilCore.getElementType(statement) != DreamShader2ElementTypes.VARIABLE_DECLARATION_STATEMENT) {
                return@forEach
            }
            if (statement.textRange.startOffset >= caretStart) return@forEach
            val type = dreamShader2DeclaredTypeSpelling(statement)
            localDeclaratorNames(statement).forEach { name ->
                items.putIfAbsent(
                    name.lowercase(Locale.ROOT),
                    DreamShaderCompletionItem(
                        label = name,
                        tailText = type?.let { " : $it" } ?: "",
                        typeText = "local",
                        icon = DreamShaderIcons.DECLARATION,
                        priority = DreamShaderCompletionPriorities.LOCAL_SYMBOL,
                    ),
                )
            }
        }
        return items.values.toList()
    }

    /** Declarator names of one local declaration statement (same shape the inference reads). */
    private fun localDeclaratorNames(statement: PsiElement): List<String> {
        val names = mutableListOf<String>()
        var sawType = false
        var expectName = false
        dreamShader2MeaningfulChildren(statement).forEach { child ->
            when (child.elementType) {
                DreamShaderTokenTypes.TYPE -> sawType = true
                DreamShaderTokenTypes.IDENTIFIER -> when {
                    // A user-type spelling arrives as IDENTIFIER: the first one is the type.
                    !sawType -> sawType = true
                    expectName || names.isEmpty() -> {
                        names.add(child.text)
                        expectName = false
                    }
                }
                DreamShaderTokenTypes.COMMA -> expectName = true
                else -> Unit
            }
        }
        return names
    }

    private fun parameterCompletionItems(function: DreamShader2FunctionDeclaration): List<DreamShaderCompletionItem> =
        function.parameterList()?.parameters().orEmpty().mapNotNull { parameter ->
            val name = parameter.parameterName() ?: return@mapNotNull null
            DreamShaderCompletionItem(
                label = name,
                tailText = parameter.typeName()?.let { " : $it" } ?: "",
                typeText = "parameter",
                icon = DreamShaderIcons.DECLARATION,
                priority = DreamShaderCompletionPriorities.LOCAL_SYMBOL,
            )
        }

    private fun callableCompletionItems(
        file: DreamShaderPsiFile,
        analysis: DreamShader2FileAnalysis,
        workspace: DreamShader2WorkspaceSymbols?,
    ): List<DreamShaderCompletionItem> {
        val entries = LinkedHashMap<String, String?>()
        analysis.functions.forEach { function ->
            function.declarationName()?.let { name -> entries.putIfAbsent(name, function.returnTypeText()) }
        }
        workspace?.exportedFunctions(file.virtualFile)?.forEach { entry ->
            entries.putIfAbsent(entry.name, entry.returnType)
        }
        return entries.map { (name, returnType) ->
            DreamShaderCompletionItem(
                label = name,
                insertText = "$name()",
                caretOffset = name.length + 1,
                tailText = "()",
                typeText = "function",
                icon = DreamShaderIcons.FUNCTION,
                priority = DreamShaderCompletionPriorities.CALLABLE,
            )
        }
    }

    private fun isInCommentOrString(leaf: PsiElement): Boolean {
        var current: PsiElement? = leaf
        while (current != null) {
            when (PsiUtilCore.getElementType(current)) {
                DreamShaderTokenTypes.STRING,
                DreamShaderTokenTypes.LINE_COMMENT,
                DreamShaderTokenTypes.BLOCK_COMMENT,
                -> return true
            }
            current = current.parent
        }
        return false
    }

    private fun linePrefixBefore(text: String, offset: Int): String? {
        val safeOffset = offset.coerceIn(0, text.length)
        val lineStart = text.lastIndexOfAny(charArrayOf('\n', '\r'), (safeOffset - 1).coerceAtLeast(0))
            .let { if (it < 0) 0 else it + 1 }
        return text.substring(lineStart, safeOffset)
    }

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

    /** x/y/z/w, r/g/b/a, s/t/p/q — the three HLSL swizzle sets, any case. */
    private const val SWIZZLE_LETTERS = "xyzwrgbastpq"
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
