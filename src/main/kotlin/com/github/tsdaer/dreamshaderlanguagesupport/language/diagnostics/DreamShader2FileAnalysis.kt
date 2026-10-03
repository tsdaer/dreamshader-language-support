package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructField
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2FirstLeafAfterType
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiTreeUtil

/**
 * Per-file summary of the 2.0 declarations the diagnostics passes and the type inference
 * consume (plan A6/C2). Cached on the file PSI — recomputed only when the tree changes —
 * so annotation passes never re-walk the file per element (performance red line).
 */
internal class DreamShader2FileAnalysis(
    val functions: List<DreamShader2FunctionDeclaration>,
    val structs: List<DreamShader2StructDeclaration>,
    val uniforms: List<DreamShader2UniformDeclaration>,
    val globals: List<DreamShader2GlobalDeclaration>,
) {
    fun functionByName(name: String): DreamShader2FunctionDeclaration? =
        functions.firstOrNull { it.declarationName() == name }

    fun structByName(name: String): DreamShader2StructDeclaration? =
        structs.firstOrNull { it.declarationName() == name }

    fun structFields(structName: String): List<DreamShader2StructField> {
        val struct = structByName(structName) ?: return emptyList()
        return PsiTreeUtil.getChildrenOfTypeAsList(struct, DreamShader2StructField::class.java)
    }

    companion object {
        private val ANALYSIS_KEY: Key<CachedValue<DreamShader2FileAnalysis>> = Key.create("dreamshader2.fileAnalysis")

        fun of(file: DreamShaderPsiFile): DreamShader2FileAnalysis =
            CachedValuesManager.getManager(file.project).getCachedValue(
                file,
                ANALYSIS_KEY,
                { CachedValueProvider.Result.create(compute(file), file) },
                false,
            )

        private fun compute(file: DreamShaderPsiFile): DreamShader2FileAnalysis = DreamShader2FileAnalysis(
            functions = file.children.filterIsInstance<DreamShader2FunctionDeclaration>(),
            structs = file.children.filterIsInstance<DreamShader2StructDeclaration>(),
            uniforms = file.children.filterIsInstance<DreamShader2UniformDeclaration>(),
            globals = file.children.filterIsInstance<DreamShader2GlobalDeclaration>(),
        )
    }
}

/**
 * All declarator names spelled in one declaration: the first identifier-style leaf after the
 * type, then every identifier that follows a comma. `uniform float a, b = 2, c;` → [a, b, c].
 */
internal fun dreamShader2DeclaratorNames(declaration: DreamShader2NamedDeclaration): List<String> {
    val names = mutableListOf<String>()
    var sawTypeLeaf = false
    var expectName = false
    com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2Leafs(declaration).forEach { leaf ->
        when (leaf.elementType) {
            DreamShaderTokenTypes.TYPE -> sawTypeLeaf = true
            DreamShaderTokenTypes.IDENTIFIER -> when {
                // A user-type spelling arrives as IDENTIFIER: the first one is the type.
                !sawTypeLeaf -> sawTypeLeaf = true
                expectName || names.isEmpty() -> {
                    names.add(leaf.text)
                    expectName = false
                }
            }
            DreamShaderTokenTypes.COMMA -> expectName = true
            else -> Unit
        }
    }
    return names.ifEmpty { listOfNotNull(declaration.declarationName()) }
}

/** Type spelling of a typed declarator node (uniform / global / local / struct field). */
internal fun dreamShader2DeclaredTypeSpelling(declaration: com.intellij.psi.PsiElement): String? =
    com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2TypeSpellingOf(declaration)

/**
 * Depth-first pre-order walk of the PSI subtree, as a lazy sequence. The modern platform
 * removed the `Condition`-based `PsiTreeUtil.collectElementsOfType`, and the vararg-Class
 * overload cannot express node-type filters, so the passes walk directly.
 */
internal fun psiTreeWalk(root: PsiElement): Sequence<PsiElement> = sequence {
    val stack = ArrayDeque<PsiElement>()
    stack.addLast(root)
    while (stack.isNotEmpty()) {
        val current = stack.removeLast()
        yield(current)
        for (index in current.children.indices.reversed()) {
            stack.addLast(current.children[index])
        }
    }
}
