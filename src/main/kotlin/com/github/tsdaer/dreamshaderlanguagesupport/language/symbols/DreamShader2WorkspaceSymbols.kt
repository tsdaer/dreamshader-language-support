package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexOverride
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexRepository
import com.github.tsdaer.dreamshaderlanguagesupport.language.bridge.DreamShaderSymbolIndexInstanceSchema
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2StubIndexesKeys
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2IncludeDirective
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.Service.Level
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.intellij.psi.util.PsiTreeUtil

/**
 * The thin bridge between the two symbol sources (plan C1, StubIndex route):
 *
 * 1. **Bridge `dreamshader-symbol-index` v1** (authoritative — the engine rewrote it at its
 *    last compile), via [DreamShaderSymbolIndexRepository].
 * 2. **Platform stub indexes** (offline fallback — always present, refreshed by the IDE).
 *
 * Merge priority: a Bridge entry shadows a same-name stub entry; the union fills the rest.
 * Bridge data absent or schema-incompatible degrades silently to stub-only — no caller sees
 * an error, per the architecture red lines. Consumers: A6's `.dsi` override checks (parent
 * schema) and C2's cross-file type inference (include closures); goto/find-usages ride the
 * stub indexes directly.
 */
@Service(Level.PROJECT)
class DreamShader2WorkspaceSymbols(private val project: Project) {

    enum class SymbolSource { BRIDGE, STUB }

    data class ExportedFunctionEntry(
        val name: String,
        val returnType: String?,
        val isExtern: Boolean,
        val source: SymbolSource,
    )

    data class NamedTypeEntry(
        val name: String,
        val type: String?,
        val source: SymbolSource,
    )

    /** Exported/extern functions visible to [virtualFile]'s compilation: Bridge first, stub fallback. */
    fun exportedFunctions(virtualFile: VirtualFile?): List<ExportedFunctionEntry> {
        val bridge = bridgeDeclarations(virtualFile)
            .filter { it.kind == KIND_FUNCTION && it.role in LINKAGE_ROLES }
            .mapNotNull { declaration ->
                declaration.name?.takeIf { it.isNotBlank() }?.let {
                    ExportedFunctionEntry(it, declaration.type, declaration.role == "extern", SymbolSource.BRIDGE)
                }
            }
        val stub = query(DreamShader2StubIndexesKeys.FUNCTION, DreamShader2FunctionDeclaration::class.java) { psi ->
            val name = psi.declarationName() ?: return@query null
            ExportedFunctionEntry(name, psi.returnTypeText(), psi.isExtern(), SymbolSource.STUB)
        }
        return mergeByName(bridge, stub) { it.name }
    }

    /** Structs visible to [virtualFile]: Bridge first, stub fallback. */
    fun structs(virtualFile: VirtualFile?): List<NamedTypeEntry> {
        val bridge = bridgeDeclarations(virtualFile)
            .filter { it.kind == KIND_STRUCT }
            .mapNotNull { declaration ->
                declaration.name?.takeIf { it.isNotBlank() }?.let {
                    NamedTypeEntry(it, declaration.type, SymbolSource.BRIDGE)
                }
            }
        val stub = query(DreamShader2StubIndexesKeys.STRUCT, DreamShader2StructDeclaration::class.java) { psi ->
            NamedTypeEntry(psi.declarationName() ?: return@query null, null, SymbolSource.STUB)
        }
        return mergeByName(bridge, stub) { it.name }
    }

    /** Uniform declarators visible to [virtualFile]: Bridge first, stub fallback. */
    fun uniforms(virtualFile: VirtualFile?): List<NamedTypeEntry> {
        val bridge = bridgeDeclarations(virtualFile)
            .filter { it.kind == KIND_UNIFORM }
            .mapNotNull { declaration ->
                declaration.name?.takeIf { it.isNotBlank() }?.let {
                    NamedTypeEntry(it, declaration.type, SymbolSource.BRIDGE)
                }
            }
        val stub = query(DreamShader2StubIndexesKeys.UNIFORM, DreamShader2UniformDeclaration::class.java) { psi ->
            NamedTypeEntry(psi.declarationName() ?: return@query null, psi.typeName(), SymbolSource.STUB)
        }
        return mergeByName(bridge, stub) { it.name }
    }

    /**
     * The parent's parameter schema of a `.dsi` file, straight from the Bridge index.
     * `null` whenever the schema is absent or invalid — callers must degrade silently.
     */
    internal fun instanceSchema(virtualFile: VirtualFile?): DreamShaderSymbolIndexInstanceSchema? {
        val schema = bridgeIndexData(virtualFile)?.instance?.schema ?: return null
        return schema.takeIf { it.valid }
    }

    /**
     * The `.dsi` file's own override declarations as the Bridge saw them at last compile;
     * empty when no Bridge data. The PSI tree stays the live truth — this is only consulted
     * for cross-checks the engine already performed.
     */
    internal fun instanceOverrides(virtualFile: VirtualFile?): List<DreamShaderSymbolIndexOverride> =
        bridgeIndexData(virtualFile)?.instance?.overrides ?: emptyList()

    /**
     * PSI files pulled in by the include closure of [file]: `#include "…"` / `import "…";`
     * resolved through the shared import path semantics, breadth-first, depth- and
     * cycle-limited. Unresolved targets are skipped silently.
     */
    fun includeClosurePsi(file: PsiFile, maxDepth: Int = DEFAULT_INCLUDE_DEPTH): List<PsiFile> {
        val startPath = file.virtualFile?.path ?: return emptyList()
        val psiManager = PsiManager.getInstance(project)
        val visitedPaths = mutableSetOf(startPath)
        val result = mutableListOf<PsiFile>()
        var frontier = listOf(file)
        repeat(maxDepth) {
            if (frontier.isEmpty()) return result
            val next = mutableListOf<PsiFile>()
            frontier.forEach { current ->
                includesOf(current).forEach { path ->
                    // 2.0 `#include` targets may be .dss/.dsi as well as .dsh; the resolver
                    // defaults to the frozen 1.x import extension set.
                    val target = DreamShaderImportResolver.resolveImport(current, path, DreamShaderImportResolver.V2_INCLUDE_EXTENSIONS)
                        ?: return@forEach
                    if (!visitedPaths.add(target.path)) return@forEach
                    val psi = psiManager.findFile(target) ?: return@forEach
                    next.add(psi)
                    result.add(psi)
                }
            }
            frontier = next
        }
        return result
    }

    private fun includesOf(file: PsiFile): List<String> =
        PsiTreeUtil.collectElementsOfType(file, DreamShader2IncludeDirective::class.java)
            .mapNotNull { it.pathText() }

    private fun <PsiT : PsiElement, T> query(
        key: StubIndexKey<String, PsiT>,
        psiClass: Class<PsiT>,
        map: (PsiT) -> T?,
    ): List<T> {
        val scope = GlobalSearchScope.projectScope(project)
        val index = StubIndex.getInstance()
        val result = mutableListOf<T>()
        return try {
            for (name in index.getAllKeys(key, project)) {
                StubIndex.getElements(key, name, project, scope, psiClass).forEach { psi ->
                    map(psi)?.let { result.add(it) }
                }
            }
            result
        } catch (e: IndexNotReadyException) {
            // The project is still indexing: the offline fallback stays silent (no opinion,
            // no diagnostics) and answers fully once indexing completes.
            emptyList()
        }
    }

    private fun bridgeDeclarations(virtualFile: VirtualFile?) =
        bridgeIndexData(virtualFile)?.declarations ?: emptyList()

    private fun bridgeIndexData(virtualFile: VirtualFile?) =
        project.getService(DreamShaderSymbolIndexRepository::class.java)
            .indexForSource(virtualFile)
            .data

    private inline fun <T> mergeByName(
        bridge: List<T>,
        stub: List<T>,
        nameOf: (T) -> String,
    ): List<T> {
        val bridgeNames = bridge.mapTo(mutableSetOf()) { nameOf(it) }
        return bridge + stub.filter { nameOf(it) !in bridgeNames }
    }

    companion object {
        private const val KIND_FUNCTION = "function"
        private const val KIND_STRUCT = "struct"
        private const val KIND_UNIFORM = "uniform"
        private val LINKAGE_ROLES = setOf("export", "extern")
        const val DEFAULT_INCLUDE_DEPTH = 16
    }
}
