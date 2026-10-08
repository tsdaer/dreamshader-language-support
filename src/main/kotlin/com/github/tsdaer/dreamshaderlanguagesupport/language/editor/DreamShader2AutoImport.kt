package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2ElementTypes as E
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderPsiFile
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShader2StubIndexesKeys
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShader2FileAnalysis
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2FunctionDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2WorkspaceSymbols
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex

/** Expanded completion only; project symbols do not become visible until the include is inserted. */
internal object DreamShader2AutoImport {
    fun candidates(file: DreamShaderPsiFile, prefix: String, leaf: PsiElement): List<DreamShaderCompletionItem> =
        try { indexedCandidates(file, prefix, leaf) } catch (_: com.intellij.openapi.project.IndexNotReadyException) { emptyList() }

    private fun indexedCandidates(file: DreamShaderPsiFile, prefix: String, leaf: PsiElement): List<DreamShaderCompletionItem> {
        if (file.name.endsWith(".dsi") || DumbService.isDumb(file.project) || nearestAncestorOfType(leaf, E.CUSTOM_BODY) != null ||
            nearestAncestorOfType(leaf, E.MEMBER_ACCESS_EXPRESSION) != null) return emptyList()
        val line = file.text.take(leaf.textRange.endOffset).substringAfterLast('\n').trimStart()
        if (line.startsWith('#') || line.startsWith("///") || leaf.node.elementType in setOf(
                com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.STRING,
                com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.LINE_COMMENT,
                com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.BLOCK_COMMENT,
                com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes.DOC_COMMENT)) return emptyList()
        val source = file.virtualFile ?: return emptyList()
        val visible = file.project.getService(DreamShader2WorkspaceSymbols::class.java).includeClosurePsi(file)
            .mapNotNull { it.virtualFile }.toSet() + source
        val visibleNames = com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2SemanticResolver
            .visibleSymbols(file, leaf).mapTo(mutableSetOf()) { it.name }
        val result = mutableListOf<DreamShaderCompletionItem>()
        val scope = GlobalSearchScope.projectScope(file.project)
        val index = StubIndex.getInstance()
        val typeSlot = Regex("""^(?:uniform|static\s+const|const|static)\s+\w*$""").matches(line)
        val named = generateSequence(leaf) { it.parent }.firstOrNull { it is com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2NamedArgument }
        if (named != null && !file.text.substring(named.textOffset, leaf.textRange.endOffset).contains('=')) return emptyList()

        fun pathFor(declaration: PsiElement): String? {
            val target = declaration.containingFile as? DreamShaderPsiFile ?: return null
            val vf = target.virtualFile ?: return null
            if (vf in visible || vf.extension !in setOf("dsh", "dss") ||
                com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDerivedFiles.isDerivedFile(vf)) return null
            val analysis = DreamShader2FileAnalysis.of(target)
            if (analysis.functions.any { it.isExport() } || target.children.any {
                    it.node.elementType == E.PRAGMA_DIRECTIVE && it.text.contains(Regex("\\b(material|instance)\\b")) ||
                        it.node.elementType == E.LEGACY_BLOCK && (vf.extension == "dss" ||
                            it.text.contains(Regex("\\b(?:Shader|ShaderFunction|ShaderLayer|ShaderLayerBlend|Template)\\s*\\(")))
                }) return null
            return DreamShaderImportResolver.candidatePaths(file, vf).firstOrNull { DreamShaderImportResolver.resolveImport(file, it) == vf }
        }
        fun <T : PsiElement> collect(key: com.intellij.psi.stubs.StubIndexKey<String, T>, klass: Class<T>, item: (T, String, String) -> DreamShaderCompletionItem) {
            for (name in index.getAllKeys(key, file.project)) {
                ProgressManager.checkCanceled()
                if (name in visibleNames || !name.startsWith(prefix, ignoreCase = true)) continue
                for (declaration in StubIndex.getElements(key, name, file.project, scope, klass)) {
                    val path = pathFor(declaration) ?: continue
                    result += item(declaration, name, path)
                }
            }
        }
        if (!typeSlot) {
            collect(DreamShader2StubIndexesKeys.HEADER_FUNCTION, DreamShader2FunctionDeclaration::class.java) { function, name, path ->
                val signature = DreamShaderCallSignatureResolver.signatureOf(function)
                DreamShaderCompletionItem(name, "$name()", tailText = " ($path)", typeText = signature.returnType,
                    caretOffset = name.length + 1, priority = 60.0, includePath = path)
            }
            collect(DreamShader2StubIndexesKeys.UNIFORM, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2UniformDeclaration::class.java) { uniform, name, path ->
                DreamShaderCompletionItem(name, name, tailText = " ($path)", typeText = uniform.typeName(), priority = 60.0, includePath = path)
            }
            collect(DreamShader2StubIndexesKeys.HEADER_GLOBAL, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2GlobalDeclaration::class.java) { global, name, path ->
                DreamShaderCompletionItem(name, name, tailText = " ($path)", typeText = global.typeName(), priority = 60.0, includePath = path)
            }
        }
        collect(DreamShader2StubIndexesKeys.STRUCT, com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShader2StructDeclaration::class.java) { _, name, path ->
            DreamShaderCompletionItem(name, name, tailText = " ($path)", typeText = "struct type", priority = 60.0, includePath = path)
        }
        return result.distinctBy { it.label to it.includePath }
    }

    fun parentNames(file: DreamShaderPsiFile): List<String> =
        com.github.tsdaer.dreamshaderlanguagesupport.language.symbols.DreamShader2InstanceModel.parentNames(file)
}
