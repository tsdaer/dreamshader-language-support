package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2PragmaDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.impl.dreamShader2Leafs
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.util.PsiTreeUtil

/**
 * `.dsi` override checks against the parent's parameter schema (plan A6.2).
 *
 * The authority is the Bridge symbol index's `instance.schema` — the parameter list the
 * engine itself built from the parent at its last compile. When the schema is absent or
 * invalid the pass reports nothing at all (red line: degrade silently, never misreport).
 *
 * - `DSH7258` — the override names no parameter of the parent.
 * - `DSH7264` — the name matches a parent parameter only in case.
 * - `DSH7259` — the override declares another type than the parent's parameter.
 * - `DSH7268` — the name exists on the parent only as a layer/blend parameter (detected from
 *   the parent source's own symbol index when the parent is a DreamShader source in the
 *   project; otherwise the plain DSH7258 wording is used).
 */
internal object DreamShader2InstanceOverridePass {

    private val PARENT_KEY = Regex("Parent\\s*=\\s*\"([^\"]+)\"")

    fun annotate(file: DreamShaderPsiFile, holder: AnnotationHolder) {
        val virtualFile = file.virtualFile ?: return
        val workspace = file.project.getService(DreamShader2WorkspaceSymbols::class.java)
        val schema = workspace.instanceSchema(virtualFile) ?: return

        val parameters = schema.parameters.filter { !it.name.isNullOrBlank() }
        val exact = parameters.associateBy { it.name!! }
        val caseInsensitive = parameters.groupBy { it.name!!.lowercase() }
        val parentName = parentNameOf(file)
        val parentIndex = parentName?.let { loadParentDeclarations(file, it) }

        PsiTreeUtil.getChildrenOfTypeAsList(file, DreamShader2UniformDeclaration::class.java).forEach { uniform ->
            declarators(uniform).forEach { declarator ->
                val name = declarator.name ?: return@forEach
                val parameter = exact[name]
                when {
                    parameter != null -> checkType(uniform, parameter, declarator.node, holder)
                    caseInsensitive.containsKey(name.lowercase()) -> {
                        val actual = caseInsensitive.getValue(name.lowercase()).first().name ?: name
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            DreamShaderBundle.message("diagnostic.dsh7264.caseMismatchedParameter", name, actual),
                        )
                            .range(declarator.node.psi)
                            .create()
                    }
                    else -> {
                        val isLayerOnly = parentIndex?.any { declaration ->
                            declaration.kind == "uniform" &&
                                declaration.name == name &&
                                (!declaration.container.isNullOrBlank() ||
                                    declaration.role in setOf("layer", "layerblend"))
                        } == true
                        val key = if (isLayerOnly) {
                            "diagnostic.dsh7268.layerOnlyParameter"
                        } else {
                            "diagnostic.dsh7258.unknownParentParameter"
                        }
                        holder.newAnnotation(
                            HighlightSeverity.ERROR,
                            DreamShaderBundle.message(key, name, parentName ?: ""),
                        )
                            .range(declarator.node.psi)
                            .create()
                    }
                }
            }
        }
    }

    private fun checkType(
        uniform: DreamShader2UniformDeclaration,
        parameter: com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexSchemaParameter,
        nameNode: com.intellij.lang.ASTNode,
        holder: AnnotationHolder,
    ) {
        val kind = parameter.kind.orEmpty()
        if (kind.contains("static", ignoreCase = true) && !uniform.isStaticSwitch()) {
            // A static switch must be overridden as `/// @static uniform bool …`.
            holder.newAnnotation(
                HighlightSeverity.ERROR,
                DreamShaderBundle.message(
                    "diagnostic.dsh7259.parentParameterTypeMismatch",
                    parameter.name ?: "",
                    kind,
                    uniform.typeName().orEmpty(),
                ),
            )
                .range(nameNode.psi)
                .create()
            return
        }
        val parentType = parameter.type?.trim()?.takeIf { it.isNotEmpty() } ?: return
        val overrideType = uniform.typeName().orEmpty().trim().takeIf { it.isNotEmpty() } ?: return
        if (normalizeType(parentType) == normalizeType(overrideType)) return
        // A float4 parameter may be overridden with float3 (keeps the parent's alpha).
        val keepsAlpha = normalizeType(parentType) == "float4" && normalizeType(overrideType) == "float3"
        if (keepsAlpha) return
        holder.newAnnotation(
            HighlightSeverity.ERROR,
            DreamShaderBundle.message(
                "diagnostic.dsh7259.parentParameterTypeMismatch",
                parameter.name ?: "",
                parentType,
                overrideType,
            ),
        )
            .range(nameNode.psi)
            .create()
    }

    private fun normalizeType(spelling: String): String = spelling.trim().lowercase().let {
        if (it == "float1") "float" else it
    }

    private fun parentNameOf(file: DreamShaderPsiFile): String? =
        PsiTreeUtil.getChildrenOfTypeAsList(file, DreamShader2PragmaDirective::class.java)
            .firstOrNull { it.pragmaName().equals("instance", ignoreCase = true) }
            ?.let { pragma -> PARENT_KEY.find(pragma.text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() } }

    /**
     * Declarations of the parent source's symbol index, when the parent is a bare name that
     * resolves to a DreamShader source in this project; null otherwise (object-path parents
     * and non-source parents have no source-level index to consult). The parent resolves as a
     * sibling of the instance file first — the upstream layout — then falls back to the
     * project-wide file-name index.
     */
    private fun loadParentDeclarations(
        file: DreamShaderPsiFile,
        parentName: String,
    ): List<com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexDeclaration>? {
        if (parentName.startsWith("/") || parentName.contains('.')) return null
        val sibling = file.virtualFile?.parent?.findChild("$parentName.dss")
        val parent = sibling ?: FilenameIndex.getVirtualFilesByName(
            "$parentName.dss",
            GlobalSearchScope.projectScope(file.project),
        ).firstOrNull() ?: return null
        return file.project.getService(DreamShaderSymbolIndexRepository::class.java)
            .indexForSource(parent)
            .data
            ?.declarations
    }

    /** Declarator names of one uniform declaration with their name nodes, in source order. */
    private fun declarators(uniform: DreamShader2UniformDeclaration): List<Declarator> {
        val result = mutableListOf<Declarator>()
        var sawType = false
        var expectName = false
        dreamShader2Leafs(uniform).forEach { leaf ->
            when (leaf.elementType) {
                DreamShaderTokenTypes.TYPE -> sawType = true
                DreamShaderTokenTypes.IDENTIFIER -> if (sawType) {
                    if (expectName || result.isEmpty()) {
                        result.add(Declarator(leaf.text, leaf))
                        expectName = false
                    }
                }
                DreamShaderTokenTypes.COMMA -> expectName = true
                else -> Unit
            }
        }
        return result
    }

    private data class Declarator(val name: String?, val node: com.intellij.lang.ASTNode)
}
