package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialect
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

/**
 * Shared completion ordering scale (M13 C10). Both contributors map their items onto these
 * constants so a mixed popup orders by relevance instead of by contributor registration
 * order - before C10 the 2.0 entries rode at priority 0 and sank under the 1.x noise.
 *
 * Scale (larger = higher): typed members on known receivers dominate, then namespace and
 * named-argument contexts, callables, declaration scaffolding, keywords, types, and finally
 * the degraded fallback lists. The 1.x suggester keeps its exclusive early-return contexts
 * (import paths, settings values, declaration heads) where ordering is internal only.
 */
internal object DreamShaderCompletionPriorities {
    /** Swizzle / struct field / matrix cell / texture method / material attribute members. */
    const val TYPED_MEMBER = 92.0
    const val SUBSTRATE_PIN = 90.0
    const val BASE_OUTPUT_MEMBER = 90.0
    const val BASE_OUTPUT_ROOT = 85.0
    const val INSTANCE_PARAMETER = 84.0
    const val DECLARATION_HEAD_ARGUMENT = 83.0
    const val NAMESPACE_ROOT = 80.0
    const val NAMED_ARGUMENT = 78.0
    const val CATALOG_NAMESPACE_MEMBER = 78.0
    const val CATALOG_UE_MEMBER = 75.0
    const val CATALOG_NAMESPACE_ROOT = 70.0
    const val NAMESPACE_CALLABLE = 72.0
    const val CALLABLE = 65.0
    const val SECTION_GROUP_MODIFIER = 46.0
    const val SECTION = 45.0
    const val SECTION_GROUP = 44.0
    const val SECTION_GROUP_ALIAS = 42.0
    const val KEYWORD = 40.0
    const val QUALIFIER = 38.0
    const val TYPE = 35.0
    const val HLSL_INTRINSIC = 30.0
    const val CONSTRUCTOR = 25.0
    const val LOCAL_SYMBOL = 20.0
}

/**
 * Dialect dispatch for completion (M13 C8). The five DreamShader extensions share one
 * [com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage], so both
 * completion contributors receive every DreamShader file; without this gate the 1.x
 * contributor answered `.` in `.dss` files with 1.x top-level keywords. Detection follows the
 * parser's own dispatch rules and is cached on the file PSI.
 */
internal object DreamShaderCompletionDialects {
    private val DIALECT_KEY: Key<CachedValue<DreamShaderDialect>> = Key.create("dreamshader.completion.dialect")
    private val REGION_KEY: Key<CachedValue<DreamShaderCompletionRegions>> =
        Key.create("dreamshader.completion.dialectRegions")

    fun dialectOf(file: DreamShaderPsiFile): DreamShaderDialect =
        CachedValuesManager.getManager(file.project).getCachedValue(
            file,
            DIALECT_KEY,
            { CachedValueProvider.Result.create(compute(file), file) },
            false,
        )

    /** The 1.x contributor stays silent in pure 2.0 files (mixed `.dsh` keeps 1.x service). */
    fun servesV1(file: DreamShaderPsiFile): Boolean = dialectOf(file) != DreamShaderDialect.V2

    /** The 2.0 contributor only answers pure 2.0 files (`.dss`/`.dsi` and V2-marked `.dsh`). */
    fun servesV2(file: DreamShaderPsiFile): Boolean = dialectOf(file) == DreamShaderDialect.V2

    /**
     * Offset-aware dispatch (M15 C23). Pure files keep the file-level answer at every offset;
     * a HEADER_MIXED `.dsh` is parsed by the 2.0 front end, so the caret's enclosing node
     * decides: inside an opaque legacy block the 1.x contributor serves, inside a real V2
     * declaration the 2.0 one does, and the top-level gaps between declarations host both
     * dialects legitimately (both contributors serve there).
     */
    fun servesV1At(file: DreamShaderPsiFile, offset: Int): Boolean = regionAt(file, offset) != Region.V2

    fun servesV2At(file: DreamShaderPsiFile, offset: Int): Boolean = regionAt(file, offset) != Region.V1

    /** Which dialect(s) own the text at [offset]: the per-region three-valued answer. */
    fun regionAt(file: DreamShaderPsiFile, offset: Int): Region {
        val dialect = dialectOf(file)
        if (dialect != DreamShaderDialect.HEADER_MIXED) {
            return if (dialect == DreamShaderDialect.V2) Region.V2 else Region.V1
        }
        return CachedValuesManager.getManager(file.project).getCachedValue(
            file,
            REGION_KEY,
            { CachedValueProvider.Result.create(computeRegions(file), file) },
            false,
        ).regionAt(offset)
    }

    enum class Region { V1, V2, BOTH }

    private fun compute(file: DreamShaderPsiFile): DreamShaderDialect {
        val name = file.virtualFile?.name ?: file.name
        return DreamShaderDialects.detect(name, file.text)
    }

    /** Top-level ranges of legacy blocks vs real V2 declarations, as emitted by the 2.0 parser. */
    private fun computeRegions(file: DreamShaderPsiFile): DreamShaderCompletionRegions {
        val legacyBlocks = mutableListOf<TextRange>()
        val v2Declarations = mutableListOf<TextRange>()
        file.node.getChildren(null).forEach { node ->
            when (node.elementType) {
                DreamShader2ElementTypes.LEGACY_BLOCK -> legacyBlocks.add(node.textRange)
                DreamShader2ElementTypes.UNIFORM_DECLARATION,
                DreamShader2ElementTypes.GLOBAL_DECLARATION,
                DreamShader2ElementTypes.STRUCT_DECLARATION,
                DreamShader2ElementTypes.FUNCTION_DECLARATION,
                DreamShader2ElementTypes.PRAGMA_DIRECTIVE,
                DreamShader2ElementTypes.PREPROCESSOR_DIRECTIVE,
                DreamShader2ElementTypes.INCLUDE_DIRECTIVE,
                DreamShader2ElementTypes.DOC_BLOCK,
                -> v2Declarations.add(node.textRange)
            }
        }
        return DreamShaderCompletionRegions(legacyBlocks, v2Declarations)
    }
}

/** Region index for a HEADER_MIXED `.dsh`: top-level node ranges, asked per caret offset. */
internal class DreamShaderCompletionRegions(
    private val legacyBlocks: List<TextRange>,
    private val v2Declarations: List<TextRange>,
) {
    fun regionAt(offset: Int): DreamShaderCompletionDialects.Region = when {
        legacyBlocks.any { offset >= it.startOffset && offset < it.endOffset } ->
            DreamShaderCompletionDialects.Region.V1
        v2Declarations.any { offset >= it.startOffset && offset < it.endOffset } ->
            DreamShaderCompletionDialects.Region.V2
        else -> DreamShaderCompletionDialects.Region.BOTH
    }
}

/** One legacy block's 1.x reparse: the block's host-file range plus the parsed sub-file. */
internal class DreamShaderLegacySubtree(val blockRange: TextRange, val parsed: com.intellij.psi.PsiFile)

/**
 * A 1.x reparse per legacy block of a mixed `.dsh` (M17 C32). The blocks are opaque token
 * flat nodes in the 2.0 tree — no [com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration]
 * or section nodes — so the 1.x completion context cannot see declarations inside them from
 * the host PSI. Each block's text goes through `createFileFromText` with the dialect-aware
 * parser (legacy-only text routes to the frozen 1.x front end), the C27 structure-view rule
 * shared by the completion context. Cached on the file PSI; a malformed block yields a
 * sub-file whose context analysis degrades, never a diagnostic.
 */
internal object DreamShaderLegacyCompletionSubtrees {
    private val KEY: Key<CachedValue<List<DreamShaderLegacySubtree>>> =
        Key.create("dreamshader.completion.legacySubtrees")

    fun of(file: DreamShaderPsiFile): List<DreamShaderLegacySubtree> =
        CachedValuesManager.getManager(file.project).getCachedValue(
            file,
            KEY,
            { CachedValueProvider.Result.create(compute(file), file) },
            false,
        )

    /** The subtree whose legacy block hosts [offset], end-exclusive like the region index. */
    fun subtreeAt(file: DreamShaderPsiFile, offset: Int): DreamShaderLegacySubtree? =
        of(file).firstOrNull { offset >= it.blockRange.startOffset && offset < it.blockRange.endOffset }

    private fun compute(file: DreamShaderPsiFile): List<DreamShaderLegacySubtree> {
        val result = mutableListOf<DreamShaderLegacySubtree>()
        file.node.getChildren(null).forEach { node ->
            if (node.elementType != DreamShader2ElementTypes.LEGACY_BLOCK) return@forEach
            val text = node.text
            if (text.isBlank()) return@forEach
            val parsed = com.intellij.psi.PsiFileFactory.getInstance(file.project)
                .createFileFromText("legacy.dsh", com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage, text)
                ?: return@forEach
            result.add(DreamShaderLegacySubtree(node.textRange, parsed))
        }
        return result
    }
}

/**
 * One construction path for lookup elements across both contributors (C10): every item
 * carries its priority, so [PrioritizedLookupElement] wrapping is not optional anymore.
 */
internal object DreamShaderLookupElementFactory {
    fun create(item: DreamShaderCompletionItem): LookupElement {
        val builder = LookupElementBuilder.create(item.label)
            .withTailText(item.tailText, true)
            .withTypeText(item.typeText ?: item.detail, true)
            .withIcon(item.icon)
        val element = when {
            item.snippet != null -> builder.withInsertHandler(DreamShaderTemplateInsertHandler(item.snippet))
            item.insertText != item.label || item.replacementStartOffset != null ->
                builder.withInsertHandler(
                    DreamShaderInsertTextHandler(
                        insertText = item.insertText,
                        caretOffset = item.caretOffset,
                        replacementStartOffset = item.replacementStartOffset,
                    )
                )
            else -> builder
        }
        return PrioritizedLookupElement.withPriority(element, item.priority)
    }
}
