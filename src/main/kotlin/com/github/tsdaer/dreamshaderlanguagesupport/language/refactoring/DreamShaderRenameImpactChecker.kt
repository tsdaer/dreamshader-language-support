package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDerivedFiles
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch

/**
 * The rename safety net (M12 C5): before a 2.0 declaration is renamed this checker
 * spells out the workspace impact -
 *
 * - a `uniform` may be overridden in `.dsi` material instances (verified parent chains
 *   and mapped parameter names); renaming the parent silently breaks the override link, so those
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

        if (declaration is DreamShader2UniformDeclaration &&
            com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2InstanceModel.metadata(declaration, "name").isNullOrBlank()) {
            val source = declaration.containingFile as? com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
            val instanceModel = com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2InstanceModel
            for (vf in com.intellij.psi.search.FilenameIndex.getAllFilesByExt(project, "dsi", scope)) {
                com.intellij.openapi.progress.ProgressManager.checkCanceled()
                if (DreamShaderDerivedFiles.isDerivedFile(vf)) continue
                val child = com.intellij.psi.PsiManager.getInstance(project).findFile(vf)
                    as? com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile ?: continue
                val seen = mutableSetOf<String>()
                var parent = instanceModel.parentSource(child)
                while (parent != null && seen.add(parent.virtualFile?.path ?: parent.name) && parent != source)
                    parent = instanceModel.parentSource(parent)
                if (parent != source || source == null) continue
                if (com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis.of(child).uniforms.any {
                        instanceModel.parameterName(it) == name }) {
                    impacts += Impact(Impact.Kind.OVERRIDE, vf.path, "material instance overrides this parameter `$name`")
                }
            }
        }

        if (declaration is DreamShader2FunctionDeclaration && declaration.isExport()) {
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

}
