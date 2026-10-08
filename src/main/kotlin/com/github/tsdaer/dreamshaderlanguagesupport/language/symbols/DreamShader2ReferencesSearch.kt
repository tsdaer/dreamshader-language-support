package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDerivedFiles
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiReference
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.util.Processor
import com.intellij.psi.util.PsiTreeUtil

/**
 * Workspace-level usages for the 2.0 declarations (M12 C5). The 1.x executor covers the
 * legacy declarations; this one serves the stub-backed V2 declarations so that
 * find-usages and the rename safety net see callers across the include closure.
 *
 * The search is conservative: candidate files come from the IDE word index, are filtered
 * to DreamShader sources, and derived files (Decompiled / Intermediate, M10) are excluded.
 */
class DreamShader2ReferencesSearchExecutor :
    com.intellij.util.QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val declaration = queryParameters.elementToSearch as? DreamShader2NamedDeclaration ?: return true
        val name = declaration.declarationName()?.takeIf { it.isNotBlank() } ?: return true
        val file = declaration.containingFile ?: return true
        val project = file.project

        val candidates = linkedMapOf<String, PsiFile>()
        candidateFilesFor(project, name, queryParameters.effectiveSearchScope).forEach { psi ->
            candidates.putIfAbsent(psi.virtualFile?.path ?: return@forEach, psi)
        }
        // The declaration's own file always participates.
        file.virtualFile?.let { vf -> candidates.putIfAbsent(vf.path, file) }

        for (candidate in candidates.values) {
            val identifiers = PsiTreeUtil.collectElements(candidate) { element ->
                element.node?.elementType == DreamShaderTokenTypes.IDENTIFIER && element.text == name
            }
            for (identifier in identifiers) {
                val binding = DreamShader2SemanticResolver.resolveIdentifier(identifier) ?: continue
                if (binding.owner != declaration || binding.identifier == identifier) continue
                val reference = identifier.parent.reference as? DreamShader2LightReference ?: DreamShader2LightReference(identifier, declaration)
                if (!consumer.process(reference)) return false
            }
        }
        return true
    }

    private fun candidateFilesFor(project: com.intellij.openapi.project.Project, name: String, scope: SearchScope): List<PsiFile> {
        val psiManager = PsiManager.getInstance(project)
        val globalScope = GlobalSearchScope.projectScope(project)
        val found = linkedMapOf<String, com.intellij.openapi.vfs.VirtualFile>()

        CacheManager.getInstance(project).processFilesWithWord(
            Processor { psiFile ->
                val virtualFile = psiFile.virtualFile
                if (virtualFile != null && isSearchable(virtualFile, scope)) {
                    found.putIfAbsent(virtualFile.path, virtualFile)
                }
                true
            },
            name,
            UsageSearchContext.IN_CODE,
            globalScope,
            true,
        )
        return found.values.mapNotNull { vf -> psiManager.findFile(vf) }.filter { it.language == DreamShaderLanguage }
    }

    private fun isSearchable(vf: com.intellij.openapi.vfs.VirtualFile, scope: SearchScope): Boolean {
        if (!vf.isValid || vf.isDirectory) return false
        if (vf.extension !in SEARCHABLE_EXTENSIONS) return false
        if (DreamShaderDerivedFiles.isDerivedFile(vf)) return false
        return when (scope) {
            is GlobalSearchScope -> scope.contains(vf)
            is com.intellij.psi.search.LocalSearchScope -> scope.scope.any {
                (it as? PsiFile)?.virtualFile == vf
            }
            else -> true
        }
    }

    private val SEARCHABLE_EXTENSIONS = setOf("dss", "dsi", "dsh", "dsm", "dsf")
}

/** Lightweight synthetic reference for V2 declaration usages. */
internal class DreamShader2LightReference(
    private val sourceElement: PsiElement,
    private val targetDeclaration: DreamShader2NamedDeclaration,
    private val identifier: PsiElement = sourceElement,
) : PsiReference {
    override fun equals(other: Any?): Boolean = other is DreamShader2LightReference && other.sourceElement == sourceElement && other.targetDeclaration == targetDeclaration
    override fun hashCode(): Int = sourceElement.hashCode() * 31 + targetDeclaration.hashCode()
    override fun getElement(): PsiElement = sourceElement

    override fun getRangeInElement(): TextRange = TextRange.from(identifier.textOffset - sourceElement.textOffset, identifier.textLength)

    override fun resolve(): PsiElement? = targetDeclaration

    override fun getCanonicalText(): String = sourceElement.text

    override fun handleElementRename(newElementName: String): PsiElement {
        if (newElementName.isBlank()) return sourceElement
        val replacement = createIdentifierFromText(newElementName) ?: return sourceElement
        identifier.replace(replacement)
        return sourceElement
    }

    override fun bindToElement(element: PsiElement): PsiElement = sourceElement

    override fun isReferenceTo(element: PsiElement): Boolean {
        return element == targetDeclaration || PsiTreeUtil.isAncestor(targetDeclaration, element, false)
    }

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isSoft(): Boolean = true

    private fun createIdentifierFromText(newName: String): PsiElement? {
        val dummyFile = PsiFileFactory.getInstance(sourceElement.project).createFileFromText(
            "dummy.dss",
            DreamShaderLanguage,
            "export void $newName() {}",
        )
        val function = PsiTreeUtil.findChildOfType(dummyFile, DreamShader2FunctionDeclaration::class.java) ?: return null
        return (function as? com.intellij.psi.PsiNameIdentifierOwner)?.nameIdentifier
            ?: function.node.findChildByType(DreamShaderTokenTypes.IDENTIFIER)?.psi
    }
}
