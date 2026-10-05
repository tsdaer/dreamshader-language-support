package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.codeInsight.hints.HintInfo
import com.intellij.codeInsight.hints.InlayInfo
import com.intellij.codeInsight.hints.InlayParameterHintsProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import java.util.*

/**
 * Provider implementation for DreamShaderInlayParameterHintsProvider.
 *
 * Signature resolution is dialect-segmented (M16 C28): the shared 1.x resolver covers legacy
 * regions, catalog builtins, and HLSL intrinsics in every file; when it comes up empty inside
 * a V2-served region, V2 user functions resolve from the file analysis and the include closure
 * (the same order the type pass uses), so `export float Foo(float a, …)` call sites show
 * parameter names too.
 */
class DreamShaderInlayParameterHintsProvider : InlayParameterHintsProvider {
    override fun getParameterHints(element: PsiElement): List<InlayInfo> {
        val settings = element.project.getService(DreamShaderProjectSettings::class.java)?.state
        if (settings != null && !settings.enableInlayParameterHints) return emptyList()

        if (element.node.elementType != DreamShaderTokenTypes.IDENTIFIER) return emptyList()
        if (isV2DeclarationName(element)) return emptyList()

        val callInfo = DreamShaderSignatureHelpAnalyzer.findCallAtName(
            text = element.containingFile.text,
            nameStartOffset = element.textRange.startOffset,
            nameEndOffset = element.textRange.endOffset
        ) ?: return emptyList()
        val parameterNames = parameterNamesOf(element, callInfo.functionName) ?: return emptyList()
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
        val parameterNames = parameterNamesOf(element, callInfo.functionName) ?: return null
        return HintInfo.MethodInfo(callInfo.functionName.lowercase(Locale.ROOT), parameterNames)
    }

    /**
     * True when [element] is the name leaf of a 2.0 declaration head. The 1.x head keywords
     * are covered by `isDeclarationHead`; V2 heads (`export float Tint(`) have no keyword to
     * recognize, so the tree decides: the identifier sits before the enclosing function's
     * parameter list, or anywhere inside a value/struct declaration (initializers included —
     * conservative by design). 1.x files have no such nodes and are unaffected.
     */
    private fun isV2DeclarationName(element: PsiElement): Boolean {
        val function = PsiTreeUtil.getParentOfType(element, DreamShader2FunctionDeclaration::class.java)
        if (function != null) {
            val parameterList = function.parameterList()
            if (parameterList == null || element.textRange.startOffset < parameterList.textRange.startOffset) {
                return true
            }
        }
        return PsiTreeUtil.getParentOfType(
            element,
            DreamShader2UniformDeclaration::class.java,
            DreamShader2GlobalDeclaration::class.java,
            DreamShader2StructDeclaration::class.java,
        ) != null
    }

    /** 1.x signatures first (legacy declarations + catalog); V2 user functions fill V2 regions. */
    private fun parameterNamesOf(element: PsiElement, functionName: String): List<String>? {
        val signatures = DreamShaderCallSignatureResolver.resolveSignatures(functionName, element.containingFile)
        if (signatures.isNotEmpty()) return signatures.first().parameterNames
        return v2UserFunctionParameterNames(element, functionName)
    }

    /**
     * Parameter names of a V2 user function declared in this file or its include closure; null
     * when the call site is not in a V2-served region or the name resolves to no V2 function.
     */
    private fun v2UserFunctionParameterNames(element: PsiElement, functionName: String): List<String>? {
        if (functionName.contains('.')) return null
        val file = element.containingFile as? DreamShaderPsiFile ?: return null
        if (!DreamShaderCompletionDialects.servesV2At(file, element.textRange.startOffset)) return null

        DreamShader2FileAnalysis.of(file).functionByName(functionName)?.let { function ->
            return function.parameterList()?.parameters()?.map { it.parameterName().orEmpty() }
        }
        val workspace = element.project.getService(DreamShader2WorkspaceSymbols::class.java)
        val closure = workspace.includeClosurePsi(file).orEmpty().filterIsInstance<DreamShaderPsiFile>()
        closure.forEach { includee ->
            DreamShader2FileAnalysis.of(includee).functionByName(functionName)?.let { function ->
                return function.parameterList()?.parameters()?.map { it.parameterName().orEmpty() }
            }
        }
        return null
    }

    override fun getDefaultBlackList(): Set<String> = emptySet()

    override fun isBlackListSupported(): Boolean = false

    @Deprecated("Required by legacy InlayParameterHintsProvider API.")
    @Suppress("OVERRIDE_DEPRECATION")
    override fun getBlacklistExplanationHTML(): String = "DreamShader inlay hints are based on built-in callable signatures."
}
