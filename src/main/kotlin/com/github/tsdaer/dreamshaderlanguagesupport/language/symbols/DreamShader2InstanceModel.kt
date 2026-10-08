package com.github.tsdaer.dreamshaderlanguagesupport.language.symbols

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration
import com.intellij.psi.PsiManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.openapi.vfs.VirtualFileManager
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings

/** Distinguish a source binding from its external material parameter name. */
internal object DreamShader2InstanceModel {
    fun metadata(declaration: com.intellij.psi.PsiElement, key: String): String? {
        val doc = declaration.children.firstOrNull { it.node.elementType == DreamShader2ElementTypes.DOC_BLOCK }?.text ?: return null
        return Regex("""(?m)^\s*///\s*@$key(?:\s+([^\r\n]*))?\s*$""").find(doc)?.groupValues?.get(1)?.trim()
    }

    fun parameterName(uniform: DreamShader2UniformDeclaration, variable: String = uniform.declarationName().orEmpty()): String =
        metadata(uniform, "name")?.trim('"')?.takeIf { it.isNotBlank() } ?: variable

    fun parentName(file: DreamShaderPsiFile): String? = file.children
        .firstOrNull { it.node.elementType == DreamShader2ElementTypes.PRAGMA_DIRECTIVE && it.text.contains("instance") }
        ?.let { Regex("""\bParent\s*=\s*"([^"]+)"""").find(it.text)?.groupValues?.get(1) }

    private val parentKey = Key.create<CachedValue<DreamShaderPsiFile?>>("dreamshader2.instanceParent")
    private val namesKey = Key.create<CachedValue<List<String>>>("dreamshader2.instanceParentNames")
    private val configKey = Key.create<String>("dreamshader2.instanceSourceConfig")

    private fun invalidateConfiguration(file: DreamShaderPsiFile) {
        val config = file.project.getService(DreamShaderProjectSettings::class.java).state.sourceDirectory
        if (file.getUserData(configKey) != config) {
            file.putUserData(parentKey, null)
            file.putUserData(namesKey, null)
            file.putUserData(configKey, config)
        }
    }

    fun parentSource(file: DreamShaderPsiFile): DreamShaderPsiFile? {
        ProgressManager.checkCanceled()
        if (DumbService.isDumb(file.project)) return null
        invalidateConfiguration(file)
        return try {
            CachedValuesManager.getManager(file.project).getCachedValue(file, parentKey, {
                CachedValueProvider.Result.create(findParentSource(file), PsiModificationTracker.MODIFICATION_COUNT,
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, DumbService.getInstance(file.project).modificationTracker)
            }, false)
        } catch (_: IndexNotReadyException) { null }
    }

    fun parentNames(file: DreamShaderPsiFile): List<String> {
        if (DumbService.isDumb(file.project)) return emptyList()
        invalidateConfiguration(file)
        return try {
            CachedValuesManager.getManager(file.project).getCachedValue(file, namesKey, {
                val root = DreamShaderImportResolver.sourceRoot(file)?.trimEnd('/')
                val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(file.project)
                val names = if (root == null) emptyList() else listOf("dss", "dsi")
                    .flatMap { com.intellij.psi.search.FilenameIndex.getAllFilesByExt(file.project, it, scope) }
                    .filter { ProgressManager.checkCanceled(); it != file.virtualFile && it.path.startsWith("$root/", ignoreCase = true) }
                    .filter { (PsiManager.getInstance(file.project).findFile(it) as? DreamShaderPsiFile)?.let(::isParentAsset) == true }
                    .map { it.nameWithoutExtension }.distinct().sorted()
                CachedValueProvider.Result.create(names, PsiModificationTracker.MODIFICATION_COUNT,
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS, DumbService.getInstance(file.project).modificationTracker)
            }, false)
        } catch (_: IndexNotReadyException) { emptyList() }
    }

    private fun findParentSource(file: DreamShaderPsiFile): DreamShaderPsiFile? {
        val name = parentName(file) ?: return null
        if (name.startsWith('/') || name.contains('.')) return null
        val root = DreamShaderImportResolver.sourceRoot(file)?.trimEnd('/') ?: return null
        val scope = com.intellij.psi.search.GlobalSearchScope.projectScope(file.project)
        val matches = listOf("dss", "dsi").flatMap { ext -> com.intellij.psi.search.FilenameIndex.getVirtualFilesByName("$name.$ext", scope) }
            .filter { it.path.startsWith("$root/", ignoreCase = true) }
            .mapNotNull { PsiManager.getInstance(file.project).findFile(it) as? DreamShaderPsiFile }
            .filter(::isParentAsset)
        return matches.singleOrNull()
    }

    fun isParentAsset(file: DreamShaderPsiFile): Boolean = file.children.any { it.node.elementType == DreamShader2ElementTypes.PRAGMA_DIRECTIVE &&
        it.text.contains(Regex("\\b(material|instance)\\b")) } || DreamShader2FileAnalysis.of(file).functions.any {
        it.isExport() && it.returnTypeText() == "void" && it.parameterList()?.parameters()?.any { parameter -> parameter.typeName() == "material" } == true }

    /** Suggestions only. Validation always requires the engine's schema. */
    fun sourceParameters(file: DreamShaderPsiFile): List<Pair<String, String?>> {
        val seen = mutableSetOf<String>()
        var current = parentSource(file)
        val parameters = linkedMapOf<String, String?>()
        while (current != null && seen.add(current.virtualFile?.path ?: current.name)) {
            ProgressManager.checkCanceled()
            val source = current
            val visibleFiles = listOf(source) + source.project.getService(DreamShader2WorkspaceSymbols::class.java)
                .includeClosurePsi(source).filterIsInstance<DreamShaderPsiFile>()
            visibleFiles.forEach { parentFile ->
                DreamShader2FileAnalysis.of(parentFile).uniforms.forEach { uniform ->
                    com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.dreamShader2DeclaratorNames(uniform).forEach { variable ->
                        parameters.putIfAbsent(parameterName(uniform, variable), uniform.typeName())
                    }
                }
            }
            current = if (current.name.endsWith(".dsi")) parentSource(current) else null
        }
        return parameters.map { it.key to it.value }
    }
}
