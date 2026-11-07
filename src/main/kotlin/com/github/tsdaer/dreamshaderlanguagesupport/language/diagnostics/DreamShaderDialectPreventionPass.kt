package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPragmas
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * 面向 DSH 码的预防性检查（A5.3）。
 *
 * 与上游硬错误一一对应的保守行级启发式：只在模式无歧义时报告，绝不与
 * Bridge/`dsc check` 争夺权威。
 *
 * - `DSH2248`：`.dsm`/`.dsf`（仅 1.x 前端）里出现 2.0 声明（`uniform`/`export`/`extern`/`#pragma`/`#include`）。
 * - `DSH3203`：`#include <...>` 尖括号写法（`.dss`/`.dsi`/`.dsh`），附引号快速修复。
 * - `DSH2249` 由既有 `DreamShaderLanguageRules.dshDisallowedTopLevelDeclarations` 检查覆盖，
 *   本 pass 不重复报告（该检查的消息文案引用该码）。
 */
/** `#include <path>` 尖括号写法（DSH3203），pass 与修复动作共用。 */
private val ANGLE_INCLUDE = Regex("#include\\s*<([^>\\n]*)>")

internal object DreamShaderDialectPreventionPass {

    private val V2_DECLARATION_LINE = Regex(
        "^\\s*(?:(uniform|export|extern)\\s+[A-Za-z_]\\w*(\\s|$)|(#pragma|#include)\\s)"
    )

    fun annotate(
        fileName: String,
        sourceText: String,
        tokens: List<DreamShaderLexedToken>,
        holder: AnnotationHolder
    ) {
        val extension = DreamShaderDialects.extensionOf(fileName)
        val masked = maskedRanges(tokens)
        when (extension) {
            in DreamShaderDialects.V1_EXTENSIONS ->
                annotateV2SyntaxInLegacyFile(extension, sourceText, masked, holder)
            in setOf("dss", "dsi", "dsh") ->
                annotateAngleBracketInclude(sourceText, masked, holder)
        }
    }

    private fun annotateV2SyntaxInLegacyFile(
        extension: String,
        sourceText: String,
        masked: List<TextRange>,
        holder: AnnotationHolder
    ) {
        forEachLineRange(sourceText) { lineText, lineStart, _ ->
            val match = V2_DECLARATION_LINE.find(lineText) ?: return@forEachLineRange
            val range = TextRange(lineStart + match.range.first, lineStart + match.range.last + 1)
            if (masked.any { it.intersects(range) }) return@forEachLineRange
            val marker = match.groupValues.drop(1).firstOrNull { it.isNotEmpty() }
                ?: match.value.trim().split(WHITESPACE).first()
            if (marker == "#pragma" && DreamShaderPragmas.isSharedAt(lineText, lineText.indexOf('#'))) {
                return@forEachLineRange
            }
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.dsh2248.v2SyntaxInLegacyFile", extension, marker)
            )
                .range(range)
                .create()
        }
    }

    private fun annotateAngleBracketInclude(
        sourceText: String,
        masked: List<TextRange>,
        holder: AnnotationHolder
    ) {
        forEachLineRange(sourceText) { lineText, lineStart, _ ->
            val match = ANGLE_INCLUDE.find(lineText) ?: return@forEachLineRange
            val range = TextRange(lineStart + match.range.first, lineStart + match.range.last + 1)
            if (masked.any { it.intersects(range) }) return@forEachLineRange
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message("diagnostic.dsh3203.angleBracketInclude")
            )
                .range(range)
                .withFix(QuoteIncludePathFix(lineStart, lineText))
                .create()
        }
    }

    private inline fun forEachLineRange(sourceText: String, action: (String, Int, Int) -> Unit) {
        var lineStart = 0
        sourceText.lineSequence().forEach { lineText ->
            action(lineText, lineStart, lineStart + lineText.length)
            lineStart += lineText.length + 1
        }
    }

    /** 注释与字符串字面量覆盖的区间，匹配落在其内即视为非代码文本。 */
    private fun maskedRanges(tokens: List<DreamShaderLexedToken>): List<TextRange> =
        tokens.filter {
            it.type == DreamShaderTokenTypes.LINE_COMMENT ||
                it.type == DreamShaderTokenTypes.BLOCK_COMMENT ||
                it.type == DreamShaderTokenTypes.STRING
        }.map { it.range }

    private const val WHITESPACE = ' '
}

/** DSH3203 修复：`#include <path>` → `#include "path"`。 */
private class QuoteIncludePathFix(
    private val lineStart: Int,
    private val lineText: String
) : IntentionAction {

    override fun startInWriteAction(): Boolean = true

    override fun getText(): String =
        DreamShaderBundle.message(
            "intention.dsh3203.quoteIncludePath",
            ANGLE_INCLUDE.find(lineText)?.groupValues?.get(1).orEmpty()
        )

    override fun getFamilyName(): String = FAMILY_NAME

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val document = editor?.document ?: return
        val match = ANGLE_INCLUDE.find(lineText) ?: return
        document.replaceString(
            lineStart + match.range.first,
            lineStart + match.range.last + 1,
            "#include \"${match.groupValues[1]}\""
        )
    }

    companion object {
        const val FAMILY_NAME: String = "DreamShader: quote #include path"
    }
}
