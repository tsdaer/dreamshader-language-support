package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.psi.PsiElement
import java.util.*

/**
 * Provider implementation for DreamShaderInlayParameterHintsProvider.
 */
class DreamShaderInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        val settings = element.project.getService(DreamShaderProjectSettings::class.java)?.state
        if (settings != null && !settings.enableInlayParameterHints) return emptyList()

        if (element.node.elementType != DreamShaderTokenTypes.IDENTIFIER) return emptyList()

        val callInfo = DreamShaderSignatureHelpAnalyzer.findCallAtName(
            text = element.containingFile.text,
            nameStartOffset = element.textRange.startOffset,
            nameEndOffset = element.textRange.endOffset
        ) ?: return emptyList()
        val signatures = DreamShaderCallSignatureResolver.resolveSignatures(callInfo.functionName, element.containingFile)
        if (signatures.isEmpty()) return emptyList()

        val parameterNames = signatures.first().parameterNames
        if (parameterNames.isEmpty()) return emptyList()

        val hintCount = minOf(callInfo.arguments.size, parameterNames.size)
        if (hintCount <= 0) return emptyList()

        val hints = ArrayList<InlayInfo>(hintCount)
        for (index in 0 until hintCount) {
            val argument = callInfo.arguments[index]
            if (argument.isNamed) continue

            val parameterName = parameterNames[index]
            if (parameterName.isBlank()) continue

            hints.add(InlayInfo("$parameterName:", argument.startOffset))
        }
        return hints
    }

    override fun getHintInfo(element: PsiElement): HintInfo? {
        val settings = element.project.getService(DreamShaderProjectSettings::class.java)?.state
        if (settings != null && !settings.enableInlayParameterHints) return null

        val callInfo = DreamShaderSignatureHelpAnalyzer.findCallAtName(
            text = element.containingFile.text,
            nameStartOffset = element.textRange.startOffset,
            nameEndOffset = element.textRange.endOffset
        ) ?: return null
        val signatures = DreamShaderCallSignatureResolver.resolveSignatures(callInfo.functionName, element.containingFile)
        if (signatures.isEmpty()) return null
        val parameterNames = signatures.first().parameterNames
        return HintInfo.MethodInfo(callInfo.functionName.lowercase(Locale.ROOT), parameterNames)
    }

    override fun getDefaultBlackList(): Set<String> = emptySet()

    override fun isBlackListSupported(): Boolean = false

    @Deprecated("Required by legacy InlayParameterHintsProvider API.")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getBlacklistExplanationHTML(): String = "DreamShader inlay hints are based on built-in callable signatures."

    @Suppress("OVERRIDE_DEPRECATION")
    override fun getSettingsPreview(): String = """
        Shader Main {
            Graph {
                float2 uv = UE.TexCoord(0);
                float v = saturate(roughness);
            }
        }
    """.trimIndent()
}
