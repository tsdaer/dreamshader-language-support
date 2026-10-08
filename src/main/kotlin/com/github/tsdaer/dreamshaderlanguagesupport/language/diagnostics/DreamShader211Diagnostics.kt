package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes as E
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2InstanceModel
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2SemanticResolver
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.*

/** Source-proven restrictions only. Engine-specific pins and uncertain graph lowering stay silent. */
internal object DreamShader211Diagnostics {
    private data class Problem(val range: TextRange, val code: String, val key: String, val args: List<String> = emptyList())
    private val KEY = Key.create<CachedValue<List<Problem>>>("dreamshader211.restrictions")

    fun annotate(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        DreamShaderPreprocessorModel.of(file).issues.forEach { issue ->
            holder.newAnnotation(HighlightSeverity.ERROR, "(${issue.code}) ${issue.message}").range(issue.range).create()
        }
        val problems = CachedValuesManager.getManager(file.project).getCachedValue(file, KEY, {
            CachedValueProvider.Result.create(collect(file), PsiModificationTracker.MODIFICATION_COUNT)
        }, false)
        problems.forEach { problem -> holder.newAnnotation(HighlightSeverity.ERROR,
            "(${problem.code}) " + DreamShaderBundle.message(problem.key, *problem.args.toTypedArray())).range(problem.range).create() }
    }

    private fun collect(file: DreamShaderPsiFile): List<Problem> {
        val result = mutableListOf<Problem>()
        val branches = DreamShaderPreprocessorModel.of(file)
        val analysis = DreamShader2FileAnalysis.of(file)
        for (function in analysis.functions) {
            if (branches.stateAt(function.textOffset) == DreamShaderPreprocessorModel.State.INACTIVE) continue
            if (function.hasCustomBody()) {
                val parameters = function.parameterList()?.parameters().orEmpty()
                for (parameter in parameters) {
                    if (DreamShader2Type.fromSpelling(parameter.typeName()) is DreamShader2Type.Matrix)
                        result += Problem(parameter.textRange, "DSH4361", "diagnostic.dsh4361.customMatrix")
                    if (parameter.typeName() == "material" && !parameter.text.trim().startsWith("out "))
                        result += Problem(parameter.textRange, "DSH6252", "diagnostic.dsh6252.customMaterialInput")
                }
                val body = function.children.firstOrNull { it.node.elementType == E.CUSTOM_BODY } ?: continue
                val prefix = "void __body() "
                val parsed = PsiFileFactory.getInstance(file.project).createFileFromText("custom_check.dss", DreamShaderLanguage, prefix + body.text) as? DreamShaderPsiFile ?: continue
                for (call in psiTreeWalk(parsed).filter { it.node.elementType == E.CALL_EXPRESSION && it.text.startsWith("UE.") }) {
                    val arguments = call.children.firstOrNull { it.node.elementType == E.ARGUMENT_LIST } ?: continue
                    for (reference in psiTreeWalk(arguments).filter { it.node.elementType == E.REFERENCE_EXPRESSION }) {
                        val binding = DreamShader2SemanticResolver.resolve(reference.text, reference) ?: continue
                        if (!DreamShader2SemanticResolver.isLocal(binding.owner)) continue
                        val relative = reference.textRange.shiftRight(body.textOffset - prefix.length)
                        if (body.textRange.contains(relative)) result += Problem(relative, "DSH6326", "diagnostic.dsh6326.liftedLocal", listOf(reference.text))
                    }
                }
            } else {
                for (loop in psiTreeWalk(function).filter { it.node.elementType in setOf(E.WHILE_STATEMENT, E.DO_WHILE_STATEMENT, E.FOR_STATEMENT) }) {
                    if (branches.stateAt(loop.textOffset) == DreamShaderPreprocessorModel.State.INACTIVE) continue
                    val children = psiTreeWalk(loop).toList()
                    if (children.any { it.node.elementType == E.RETURN_STATEMENT || it.node.elementType == E.JUMP_STATEMENT && it.text.trim().startsWith("break") }) continue
                    val forever = Regex("""^while\s*\(\s*(true|1)\s*\)|^for\s*\(\s*;\s*;\s*\)""").containsMatchIn(loop.text.trim())
                    if (forever) result += Problem(loop.textRange, "DSH4360", "diagnostic.dsh4360.unboundedLoop")
                }
            }
        }
        val inference = DreamShader2InferenceContext.forFile(file, file.project.getService(com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols::class.java))
        for (member in psiTreeWalk(file).filter { it.node.elementType == E.MEMBER_ACCESS_EXPRESSION }) {
            if (branches.stateAt(member.textOffset) == DreamShaderPreprocessorModel.State.INACTIVE) continue
            val nodes = com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren(member)
            val base = nodes.firstOrNull()?.psi ?: continue
            val name = nodes.lastOrNull()?.takeIf { it.elementType == com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.IDENTIFIER } ?: continue
            val type = inference.typeOf(base)
            if ((type is DreamShader2Type.Scalar || type is DreamShader2Type.Vector) &&
                inference.typeOf(member) == DreamShader2Type.Unknown && DreamShader2Swizzle.type(type, name.text) == DreamShader2Type.Unknown)
                result += Problem(name.psi.textRange, "DSH4230", "diagnostic.dsh4230.swizzle", listOf(name.text))
        }
        for (constructor in psiTreeWalk(file).filter { it.node.elementType == E.CONSTRUCTOR_EXPRESSION }) {
            if (branches.stateAt(constructor.textOffset) != DreamShaderPreprocessorModel.State.INACTIVE && inference.typeOf(constructor) is DreamShader2Type.Matrix)
                result += Problem(constructor.textRange, "DSH4361", "diagnostic.dsh4361.graphMatrix")
        }
        if (file.name.endsWith(".dsi")) {
            val material = file.children.firstOrNull { it is DreamShader2PragmaDirective && it.pragmaName() == "material" }
            material?.let { result += Problem(it.textRange, "DSH7254", "diagnostic.dsh7254.instanceMaterial") }
            for (uniform in analysis.uniforms) {
                if (branches.stateAt(uniform.textOffset) == DreamShaderPreprocessorModel.State.INACTIVE) continue
                if (uniform.isStaticSwitch() && uniform.typeName() !in setOf("bool", "bool4"))
                    result += Problem(uniform.textRange, "DSH7260", "diagnostic.dsh7260.instanceStatic")
            }
            val visited = mutableSetOf(file.virtualFile?.path ?: file.name)
            var parent = DreamShader2InstanceModel.parentSource(file)
            while (parent != null) {
                if (!visited.add(parent.virtualFile?.path ?: parent.name)) {
                    file.children.firstOrNull { it is DreamShader2PragmaDirective && it.pragmaName() == "instance" }?.let {
                        result += Problem(it.textRange, "DSH8263", "diagnostic.dsh8263.instanceCycle") }
                    break
                }
                parent = DreamShader2InstanceModel.parentSource(parent)
            }
        }
        return result.distinctBy { it.range to it.code }
    }
}
