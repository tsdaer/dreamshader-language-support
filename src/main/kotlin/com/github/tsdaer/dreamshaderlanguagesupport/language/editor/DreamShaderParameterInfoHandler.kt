package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.lang.parameterInfo.CreateParameterInfoContext
import com.intellij.lang.parameterInfo.ParameterInfoHandler
import com.intellij.lang.parameterInfo.ParameterInfoUIContext
import com.intellij.lang.parameterInfo.UpdateParameterInfoContext
import com.intellij.psi.PsiFile

/**
 * 由 [DreamShaderSignatureHelpAnalyzer] 支持的 IntelliJ 参数信息处理程序。
 *
 * 该组件仅提供UI粘合和代理调用解析/签名
 * 分析仪分辨率以保持一个真实来源。
 */
class DreamShaderParameterInfoHandler : ParameterInfoHandler<PsiFile, DreamShaderCallSignature> {
    override fun findElementForParameterInfo(context: CreateParameterInfoContext): PsiFile? {
        val file = context.file
        if (file.language != DreamShaderLanguage) return null
        val call = DreamShaderSignatureHelpAnalyzer.findEnclosingCall(
            file.text,
            context.offset,
            allowStringInterior = true,
        ) ?: return null
        val signatures = DreamShaderCallSignatureResolver.resolveSignatures(call.functionName, file)
        if (signatures.isEmpty()) return null
        context.itemsToShow = signatures.toTypedArray()
        context.highlightedElement = file.findElementAt(call.nameStartOffset)
        return file
    }

    override fun showParameterInfo(element: PsiFile, context: CreateParameterInfoContext) {
        context.showHint(element, context.offset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): PsiFile? {
        val owner = context.parameterOwner as? PsiFile
        val file = context.file
        if (file.language != DreamShaderLanguage) return null
        val call = DreamShaderSignatureHelpAnalyzer.findEnclosingCall(file.text, context.offset, allowStringInterior = true) ?: return null
        if (owner != null && owner.isValid && owner == file) return owner
        return file
    }

    override fun processFoundElementForUpdatingParameterInfo(element: PsiFile?, context: UpdateParameterInfoContext) {
        if (element != null) {
            context.parameterOwner = element
        }
    }

    override fun updateParameterInfo(element: PsiFile, context: UpdateParameterInfoContext) {
        val call = DreamShaderSignatureHelpAnalyzer.findEnclosingCall(
            element.text,
            context.offset,
            allowStringInterior = true,
        )
        if (call == null) {
            context.removeHint()
            return
        }

        val signatures = DreamShaderCallSignatureResolver.resolveSignatures(call.functionName, element)
        if (signatures.isEmpty()) {
            context.removeHint()
            return
        }

        // Named arguments map to their parameter by name; the positional comma count only
        // decides when the caret is between arguments or the name has no match.
        val parameterIndex = DreamShaderSignatureHelpAnalyzer.parameterIndex(
            element.text,
            call,
            signatures.first(),
            context.offset,
        )
        context.setCurrentParameter(parameterIndex)

        val objects = context.objectsToView
        for (i in objects.indices) {
            val row = objects[i] as? DreamShaderCallSignature ?: continue
            row.activeParameter = DreamShaderSignatureHelpAnalyzer.parameterIndex(element.text, call, row, context.offset)
            val named = call.arguments.filter { it.isNamed }.map { it.text.substringBefore('=').trim() }
            context.setUIComponentEnabled(i, named.all { it in row.parameterNames })
        }
    }

    override fun updateUI(signature: DreamShaderCallSignature, context: ParameterInfoUIContext) {
        val parameterIndex = signature.activeParameter.takeIf { it != -2 } ?: context.currentParameterIndex
        val parameterRanges = signature.parameterRanges

        val highlightRange = if (parameterIndex in parameterRanges.indices) {
            parameterRanges[parameterIndex]
        } else {
            null
        }

        context.setupUIComponentPresentation(
            signature.presentableText,
            highlightRange?.first ?: -1,
            highlightRange?.last?.plus(1) ?: -1,
            false,
            false,
            false,
            context.defaultParameterColor
        )
    }
}
