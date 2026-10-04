package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDerivedFiles
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2FunctionIndex
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2UniformIndex
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.stubs.StubIndex

/**
 * The rename safety net (M12 C5): before a 2.0 declaration is renamed this checker
 * spells out the workspace impact -
 *
 * - a `uniform` may be overridden in `.dsi` material instances (same-name uniform
 *   declarations); renaming the parent silently breaks the override link, so those
 *   files surface as `override` impacts;
 * - an `export` function's call sites across the include closure surface as
 *   `callSite` impacts; files under `Decompiled/` / `Intermediate/DreamShader/` are
 *   excluded (M10 derived-file semantics - they are regenerated, never hand-edited).
 */
object DreamShaderRenameImpactChecker {
    data class Impact(val kind: Kind, val filePath: String, val detail: String) {
        enum class Kind { OVERRIDE, CALL_SITE }
    }

    fun impactsOf(element: PsiElement): List<Impact> {
        val declaration = element as? DreamShader2NamedDeclaration ?: return emptyList()
        val name = declaration.declarationName()?.takeIf { it.isNotBlank() } ?: return emptyList()
        val project = element.project
        val scope = GlobalSearchScope.projectScope(project)

        val impacts = mutableListOf<Impact>()

        if (declaration is DreamShader2UniformDeclaration) {
            StubIndex.getInstance().processElements(
                DreamShader2UniformIndex.KEY,
                name,
                project,
                scope,
                DreamShader2UniformDeclaration::class.java,
            ) { overrideDeclaration ->
                val file = overrideDeclaration.containingFile?.virtualFile ?: return@processElements true
                if (file != declaration.containingFile?.virtualFile && !DreamShaderDerivedFiles.isDerivedFile(file)) {
                    impacts.add(
                        Impact(
                            Impact.Kind.OVERRIDE,
                            file.path,
                            "material instance overrides this parameter with `uniform ... $name`",
                        )
                    )
                }
                true
            }
        }

        if (declaration is DreamShader2FunctionDeclaration && declaration.isExport()) {
            StubIndex.getInstance().processElements(
                DreamShader2FunctionIndex.KEY,
                name,
                project,
                scope,
                DreamShader2FunctionDeclaration::class.java,
            ) { other ->
                val file = other.containingFile?.virtualFile
                if (file != null && !DreamShaderDerivedFiles.isDerivedFile(file) && file != declaration.containingFile?.virtualFile) {
                    // A sibling declaration of the same export name - e.g. an instance file
                    // re-exporting - counts as a rename impact site too.
                    impacts.add(
                        Impact(
                            Impact.Kind.CALL_SITE,
                            file.path,
                            "declares the same export name; include closures resolve it too",
                        )
                    )
                }
                true
            }
            ReferencesSearch.search(declaration, scope).findAll()
                .mapNotNull { reference -> reference.element?.containingFile?.virtualFile }
                .filter { !DreamShaderDerivedFiles.isDerivedFile(it) }
                .forEach { impacts.add(Impact(Impact.Kind.CALL_SITE, it.path, "calls this export function")) }
        }

        return impacts.distinctBy { it.kind to it.filePath }
    }

    /** Human-readable one-liners for the rename confirmation hint. */
    fun impactStatements(element: PsiElement): List<String> {
        return impactsOf(element).map { impact ->
            when (impact.kind) {
                Impact.Kind.OVERRIDE -> "Override in ${impact.filePath}: ${impact.detail}"
                Impact.Kind.CALL_SITE -> "Call site in ${impact.filePath}: ${impact.detail}"
            }
        }
    }

    private fun VirtualFile?.orNull(): VirtualFile? = this
}
