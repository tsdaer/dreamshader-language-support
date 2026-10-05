package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialect
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.intellij.codeInsight.completion.PrioritizedLookupElement
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.openapi.util.Key
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

    private fun compute(file: DreamShaderPsiFile): DreamShaderDialect {
        val name = file.virtualFile?.name ?: file.name
        return DreamShaderDialects.detect(name, file.text)
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
