package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFile

/**
 * 1.x GLSL 别名提示（B3，上游遗留宽容规则 L2 / `DSH5277`）。
 *
 * 上游 2.0 把「1.x 源码可写 GLSL 拼写、按 HLSL 名读取」编号为 DSH5277（引擎在绑定器
 * 对函数别名 `mix`/`fract`/`mod` 发 warning）；类型别名（`vec3`→`float3` 等，来自
 * `LangLegacyExpressions.cpp` 的 `GLegacyTypeAliases`）在引擎侧是静默归一。本 pass 对
 * 两者给出提示级注解 + 一键替换，仅用于 1.x 文件；`.dss`/`.dsi` 中引擎直接报错
 * （`DSH4250`），插件不再提示。
 *
 * 保守性：注释/字符串内不报；文件内若声明了同名 Function/GraphFunction（遮蔽内建），
 * 则整个文件静默跳过该别名。
 */
internal object DreamShaderLegacyAliasHintPass {

    /** 内建核心算子的 GLSL 函数别名（上游 `IRCoreOps.cpp` 的 `GlslAlias` 列）。 */
    private val FUNCTION_ALIASES: Map<String, String> = mapOf(
        "mix" to "lerp",
        "fract" to "frac",
        "mod" to "fmod",
    )

    /** 1.x 类型别名（上游 `LangLegacyExpressions.cpp` 的 `GLegacyTypeAliases`，小写拼写）。 */
    private val TYPE_ALIASES: Map<String, String> = mapOf(
        "vec2" to "float2", "vec3" to "float3", "vec4" to "float4",
        "ivec2" to "int2", "ivec3" to "int3", "ivec4" to "int4",
        "uvec2" to "uint2", "uvec3" to "uint3", "uvec4" to "uint4",
        "bvec2" to "bool2", "bvec3" to "bool3", "bvec4" to "bool4",
        "mat2" to "float2x2", "mat3" to "float3x3", "mat4" to "float4x4",
        "materialattributes" to "material",
        "staticbool" to "bool", "staticboolparameter" to "bool",
        "texture2d" to "Texture2D", "texturecube" to "TextureCube",
        "texture2darray" to "Texture2DArray", "texture3d" to "Texture3D",
        "volumetexture" to "VolumeTexture", "samplerstate" to "SamplerState",
        "substrate" to "Substrate",
    )

    fun annotate(
        fileName: String,
        sourceText: String,
        tokens: List<DreamShaderLexedToken>,
        topLevelDeclarations: List<DreamShaderDeclaration>,
        holder: AnnotationHolder
    ) {
        val extension = DreamShaderDialects.extensionOf(fileName)
        if (extension in DreamShaderDialects.V2_EXTENSIONS) return

        val masked = maskedRanges(tokens)
        val shadowed = declaredFunctionNames(topLevelDeclarations)

        FUNCTION_ALIASES.forEach { (alias, hlsl) ->
            if (alias in shadowed) return@forEach
            Regex("\\b${Regex.escape(alias)}\\s*\\(").findAll(sourceText).forEach { match ->
                // 只标单词本身，不含括号。
                val wordLength = alias.length
                reportAlias(sourceText, match.range.first, wordLength, masked, alias, hlsl, holder, isType = false)
            }
        }
        TYPE_ALIASES.forEach { (alias, hlsl) ->
            Regex("\\b${Regex.escape(alias)}\\b").findAll(sourceText).forEach { match ->
                reportAlias(sourceText, match.range.first, alias.length, masked, alias, hlsl, holder, isType = true)
            }
        }
    }

    private fun reportAlias(
        sourceText: String,
        startOffset: Int,
        length: Int,
        masked: List<TextRange>,
        alias: String,
        hlsl: String,
        holder: AnnotationHolder,
        isType: Boolean
    ) {
        val end = (startOffset + length).coerceAtMost(sourceText.length)
        if (startOffset >= end) return
        val range = TextRange(startOffset, end)
        if (masked.any { it.intersects(range) }) return
        val message = DreamShaderBundle.message(
            if (isType) "diagnostic.dsh5277.glslTypeAlias" else "diagnostic.dsh5277.glslFunctionAlias",
            alias, hlsl
        )
        holder.newAnnotation(HighlightSeverity.WEAK_WARNING, message)
            .range(range)
            .tooltip(message)
            .withFix(ReplaceGlslAliasFix(range, alias, hlsl))
            .create()
    }

    /** 文件内声明为 Function/GraphFunction 的名字（1.x 大小写不敏感），命中即跳过该别名。 */
    private fun declaredFunctionNames(declarations: List<DreamShaderDeclaration>): Set<String> =
        declarations
            .filter { it.keywordText()?.lowercase() in setOf("function", "graphfunction") }
            .mapNotNull { it.declarationName()?.lowercase() }
            .toSet()

    private fun maskedRanges(tokens: List<DreamShaderLexedToken>): List<TextRange> =
        tokens.filter {
            it.type == DreamShaderTokenTypes.LINE_COMMENT ||
                it.type == DreamShaderTokenTypes.BLOCK_COMMENT ||
                it.type == DreamShaderTokenTypes.STRING
        }.map { it.range }
}

/** DSH5277 修复：GLSL 拼写替换为 HLSL 名。 */
internal class ReplaceGlslAliasFix(
    private val range: TextRange,
    private val alias: String,
    private val hlsl: String
) : IntentionAction {

    override fun startInWriteAction(): Boolean = true

    override fun getText(): String =
        DreamShaderBundle.message("intention.dsh5277.replaceAlias", alias, hlsl)

    override fun getFamilyName(): String = FAMILY_NAME

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        val document = editor?.document ?: return
        WriteCommandAction.runWriteCommandAction(project) {
            document.replaceString(range.startOffset, range.endOffset, hlsl)
        }
    }

    companion object {
        const val FAMILY_NAME: String = "DreamShader: replace GLSL alias with HLSL spelling"
    }
}
