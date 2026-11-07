package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes as E
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderCallSignature
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderSignatureHelpAnalyzer
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes as T
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiUtil
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2MeaningfulChildren
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

private val qualifiedSeparator = Regex("""\s*::\s*""")

internal fun normalizeLegacyQualifiedCall(name: String): String =
    if ("::" in name) name.replace(qualifiedSeparator, "::") else name

/** 1.x functions exposed to a 2.x file through its actual include closure. */
internal object DreamShaderLegacyNamespaceSymbols {
    data class Callable(val path: List<String>, val signature: DreamShaderCallSignature, val identifier: PsiElement)

    private val KEY = Key.create<CachedValue<List<Callable>>>("dreamshader.legacy.namespace.callables")

    fun visible(file: DreamShaderPsiFile): Sequence<Callable> =
        (sequenceOf<PsiFile>(file) + DreamShaderImportClosureResolver.resolveImportClosure(file)
            .asSequence().filter { it != file }).flatMap { declarations(it).asSequence() }

    fun resolve(file: DreamShaderPsiFile, name: String): List<Callable> {
        val path = normalizeLegacyQualifiedCall(name).split("::")
        if (path.size < 2) return emptyList()
        val local = declarations(file).filter { it.path == path }
        return local.ifEmpty { visible(file).filter { it.path == path }.toList() }
    }

    fun referenceParts(reference: PsiElement?): List<PsiElement> {
        if (reference == null || reference.node.elementType != E.REFERENCE_EXPRESSION ||
            reference.node.findChildByType(T.OPERATOR)?.text != "::") return emptyList()
        return dreamShader2MeaningfulChildren(reference).filter { it.elementType == T.IDENTIFIER }.map { it.psi }
    }

    private fun declarations(file: PsiFile): List<Callable> =
        CachedValuesManager.getManager(file.project).getCachedValue(file, KEY, {
            val declarations = buildList {
                fun collect(parsed: PsiFile, base: Int) {
                    for (declaration in DreamShaderPsiUtil.declarationsIn(parsed)) {
                        ProgressManager.checkCanceled()
                        if (!declaration.isFunctionLike()) continue
                        val namespace = DreamShaderPsiUtil.enclosingNamespacePath(declaration)
                        if (namespace.isEmpty()) continue
                        val name = declaration.declarationName() ?: continue
                        val nameLeaf = declaration.nameIdentifier ?: continue
                        val path = namespace + name
                        val signature = DreamShaderSignatureHelpAnalyzer.legacyNamespaceSignature(
                            path.joinToString("::"), declaration.text,
                            nameLeaf.textRange.endOffset - declaration.textRange.startOffset,
                            declaration.returnType(),
                        ) ?: continue
                        val target = if (parsed == file) nameLeaf else file.findElementAt(base + nameLeaf.textOffset) ?: continue
                        add(Callable(path, signature, target))
                    }
                }
                collect(file, 0)
                // Mixed headers hold the legacy blocks as opaque nodes. Parse only those
                // blocks and keep the navigation target in the original header.
                for (block in file.children.filter { it.node.elementType == E.LEGACY_BLOCK }) {
                    ProgressManager.checkCanceled()
                    val parsed = PsiFileFactory.getInstance(file.project)
                        .createFileFromText("legacy.dsh", DreamShaderLanguage, block.text)
                    collect(parsed, block.textRange.startOffset)
                }
            }
            CachedValueProvider.Result.create(declarations, file)
        }, false)
}
