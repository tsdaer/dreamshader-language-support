package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderPsiUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import java.util.Locale

/**
 * 自定义参考文献搜索顶级DreamShader声明。
 *
 * 当前实现为文件本地和文本/PSI混合：它找到匹配
 * 标识符标记标记并过滤声明的头/注释/字符串。
 */
class DreamShaderReferencesSearchExecutor : com.intellij.util.QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
    override fun execute(queryParameters: ReferencesSearch.SearchParameters, consumer: Processor<in PsiReference>): Boolean {
        val declaration = queryParameters.elementToSearch as? DreamShaderDeclaration ?: return true
        val file = declaration.containingFile ?: return true
        val declarationNames = declarationSymbolNames(declaration)
        if (declarationNames.isEmpty()) return true
        val declarationProfile = buildDeclarationProfile(declaration)
        val searchScope = queryParameters.effectiveSearchScope
        val candidateFiles = resolveImportClosure(file).filter { candidate ->
            isFileInSearchScope(candidate, searchScope)
        }

        val declarationNameId = declaration.nameIdentifier
        for (candidateFile in candidateFiles) {
            val identifiers = PsiTreeUtil.collectElements(candidateFile) { element ->
                val t = element.node?.elementType
                t == DreamShaderTokenTypes.IDENTIFIER && declarationNames.contains(element.text)
            }

            for (identifier in identifiers) {
                if (identifier == declarationNameId) continue
                if (!isInsideDeclarationTree(identifier)) continue
                if (isDeclarationNameIdentifier(identifier)) continue
                if (!matchesDeclarationProfile(identifier, declarationProfile)) continue
                if (!consumer.process(DreamShaderLightReference(identifier, declaration))) return false
            }
        }
        return true
    }

    private fun isFileInSearchScope(file: PsiFile, scope: SearchScope): Boolean {
        val fileVf = file.virtualFile ?: return false
        return when (scope) {
            is GlobalSearchScope -> scope.contains(fileVf)
            is LocalSearchScope -> containsFileInLocalScope(scope, fileVf)
            else -> true
        }
    }

    private fun containsFileInLocalScope(scope: LocalSearchScope, targetFile: com.intellij.openapi.vfs.VirtualFile): Boolean {
        return scope.scope.any { scopedElement ->
            val scopedFile = scopedElement.containingFile ?: return@any false
            val scopedVf = scopedFile.virtualFile ?: return@any false
            scopedVf == targetFile
        }
    }

    private fun isInsideDeclarationTree(element: PsiElement): Boolean {
        val declaration =
            PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false) ?: return false
        val tokenType = element.node?.elementType
        return tokenType != DreamShaderTokenTypes.LINE_COMMENT &&
            tokenType != DreamShaderTokenTypes.BLOCK_COMMENT &&
            tokenType != DreamShaderTokenTypes.STRING
    }

    private fun isDeclarationNameIdentifier(identifier: PsiElement): Boolean {
        val declaration = PsiTreeUtil.getParentOfType(identifier, DreamShaderDeclaration::class.java, false) ?: return false
        return declaration.nameIdentifier == identifier
    }

    private fun buildDeclarationProfile(declaration: DreamShaderDeclaration): DeclarationProfile {
        val declarationName = declaration.declarationName().orEmpty()
        if (declaration.keywordText() == "namespace") {
        val namespacePath = DreamShaderPsiUtil.enclosingNamespacePath(declaration) + declarationName
            return DeclarationProfile(
                namespacePath = namespacePath,
                isNamespaceDeclaration = true
            )
        }

        return DeclarationProfile(
            namespacePath = DreamShaderPsiUtil.enclosingNamespacePath(declaration),
            isNamespaceDeclaration = false
        )
    }

    private fun matchesDeclarationProfile(identifier: PsiElement, profile: DeclarationProfile): Boolean {
        val fileText = identifier.containingFile?.text ?: return false
        val identifierRange = identifier.textRange ?: return false
        val qualifierChainBefore = readQualifierChainBeforeIdentifier(fileText, identifierRange.startOffset)
        val hasDoubleColonAfter = hasDoubleColonAfter(fileText, identifierRange.endOffset)

        if (profile.isNamespaceDeclaration) {
            if (!hasDoubleColonAfter) return false
            return qualifierChainBefore + identifier.text == profile.namespacePath
        }

        if (profile.namespacePath.isEmpty()) {
            // Top-level declarations should not consume namespace-qualified member usages.
            return qualifierChainBefore.isEmpty()
        }

        if (qualifierChainBefore.isNotEmpty()) {
            return qualifierChainBefore == profile.namespacePath
        }

        val enclosingNamespacePath = DreamShaderPsiUtil.enclosingNamespacePathAt(identifier)
        return enclosingNamespacePath == profile.namespacePath
    }

    private fun hasDoubleColonAfter(text: String, startOffset: Int): Boolean {
        var i = startOffset
        while (i < text.length && text[i].isWhitespace()) i++
        return i + 1 < text.length && text[i] == ':' && text[i + 1] == ':'
    }

    private fun readQualifierChainBeforeIdentifier(text: String, anchorOffset: Int): List<String> {
        val qualifiers = mutableListOf<String>()
        var i = anchorOffset - 1
        while (true) {
            while (i >= 0 && text[i].isWhitespace()) i--
            if (i < 1 || text[i] != ':' || text[i - 1] != ':') break

            i -= 2
            while (i >= 0 && text[i].isWhitespace()) i--
            if (i < 0 || !isIdentifierChar(text[i])) return emptyList()

            val end = i
            while (i >= 0 && isIdentifierChar(text[i])) i--
            val start = i + 1
            if (start > end) return emptyList()
            qualifiers.add(text.substring(start, end + 1))
        }

        qualifiers.reverse()
        return qualifiers
    }

    private fun isIdentifierChar(ch: Char): Boolean = ch == '_' || ch.isLetterOrDigit()

    private fun declarationSymbolNames(declaration: DreamShaderDeclaration): Set<String> {
        val names = linkedSetOf<String>()
        val explicit = declaration.declarationName().orEmpty().trim()
        if (explicit.isNotBlank() && !explicit.equals("name", ignoreCase = true)) {
            names.add(explicit)
        }

        val keyword = declaration.keywordText().orEmpty().lowercase(Locale.ROOT)
        if (keyword in CALLABLE_NAME_ATTRIBUTE_DECLARATIONS) {
            val attrName = extractNameAttributeValue(declaration)
            if (!attrName.isNullOrBlank()) {
                names.add(attrName)
                names.add(attrName.substringAfterLast('/').substringAfterLast('\\'))
            }
        }
        return names.filter { it.isNotBlank() }.toSet()
    }

    private fun extractNameAttributeValue(declaration: DreamShaderDeclaration): String? {
        val bodyStart = declaration.bodyTextRange()?.startOffset ?: declaration.text.length
        if (bodyStart <= 0 || bodyStart > declaration.text.length) return null
        val head = declaration.text.substring(0, bodyStart)
        return NAME_ATTRIBUTE_REGEX.find(head)?.groupValues?.getOrNull(1)?.trim()
    }

    private fun resolveImportClosure(sourceFile: PsiFile): List<PsiFile> {
        val seedFile = sourceFile.containingFile
        val seedVf = seedFile.virtualFile ?: return listOf(seedFile)
        val filesByKey = linkedMapOf<String, PsiFile>()
        val visited = linkedSetOf<String>()
        val queue = ArrayDeque<PsiFile>()
        queue.add(seedFile)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            val currentKey = fileKey(current.virtualFile) ?: continue
            if (!visited.add(currentKey)) continue
            filesByKey.putIfAbsent(currentKey, current)

            val neighbors = linkedMapOf<String, PsiFile>()
            DreamShaderImportClosureResolver.resolveDirectImports(current).forEach { importedFile ->
                val key = fileKey(importedFile.virtualFile) ?: return@forEach
                neighbors.putIfAbsent(key, importedFile)
            }
            resolveDirectImporters(current).forEach { importerFile ->
                val key = fileKey(importerFile.virtualFile) ?: return@forEach
                neighbors.putIfAbsent(key, importerFile)
            }

            for ((key, neighbor) in neighbors) {
                if (!visited.contains(key)) {
                    queue.addLast(neighbor)
                }
            }
        }

        return visited.mapNotNull { key -> filesByKey[key] }
    }

    private fun resolveDirectImporters(targetFile: PsiFile): List<PsiFile> {
        return CachedValuesManager.getManager(targetFile.project).getCachedValue(
            targetFile,
            DIRECT_IMPORTERS_KEY,
            {
                CachedValueProvider.Result.create(
                    computeDirectImporters(targetFile),
                    PsiModificationTracker.MODIFICATION_COUNT,
                    VirtualFileManager.VFS_STRUCTURE_MODIFICATIONS
                )
            },
            false
        )
    }

    private fun computeDirectImporters(targetFile: PsiFile): List<PsiFile> {
        val targetVf = targetFile.virtualFile ?: return emptyList()
        val targetKey = fileKey(targetVf) ?: return emptyList()
        val project = targetFile.project
        val psiManager = PsiManager.getInstance(project)
        val candidates = linkedMapOf<String, PsiFile>()
        val scope = GlobalSearchScope.projectScope(project)

        targetVf.parent?.children
            ?.asSequence()
            ?.filter { candidateVf -> isImportCandidateFile(candidateVf) && candidateVf != targetVf }
            ?.mapNotNull { candidateVf -> psiManager.findFile(candidateVf) }
            ?.filter { candidatePsi -> candidatePsi.language == DreamShaderLanguage }
            ?.forEach { candidatePsi ->
                val candidateKey = fileKey(candidatePsi.virtualFile) ?: return@forEach
                candidates.putIfAbsent(candidateKey, candidatePsi)
            }

        for (word in importSearchWordsFor(targetVf, project.basePath)) {
            CacheManager.getInstance(project).processFilesWithWord(
                Processor { candidatePsi ->
                    val candidateVf = candidatePsi.virtualFile ?: return@Processor true
                    if (candidateVf == targetVf) return@Processor true
                    if (!isImportCandidateFile(candidateVf)) return@Processor true

                    if (candidatePsi.language != DreamShaderLanguage) return@Processor true
                    val candidateKey = fileKey(candidateVf) ?: return@Processor true
                    candidates.putIfAbsent(candidateKey, candidatePsi)
                    true
                },
                word,
                UsageSearchContext.IN_STRINGS,
                scope,
                true
            )
        }

        return candidates.values.filter { candidate ->
            DreamShaderImportClosureResolver.resolveDirectImports(candidate)
                .any { importedFile -> fileKey(importedFile.virtualFile) == targetKey }
        }
    }

    private fun importSearchWordsFor(targetVf: VirtualFile, projectBasePath: String?): Set<String> {
        val words = linkedSetOf<String>()
        addSearchWords(targetVf.nameWithoutExtension, words)

        val normalizedPath = targetVf.path.replace('\\', '/')
        val projectRelativePath = projectBasePath
            ?.replace('\\', '/')
            ?.trimEnd('/')
            ?.let { base ->
                normalizedPath.removePrefix("$base/")
                    .takeIf { it != normalizedPath }
            }

        val relevantSegments = projectRelativePath
            ?.split('/')
            ?.filter { it.isNotBlank() }
            ?: listOfNotNull(targetVf.parent?.name, targetVf.name)
        relevantSegments.forEach { segment ->
            if (!segment.equals(targetVf.extension.orEmpty(), ignoreCase = true)) {
                addSearchWords(segment.substringBeforeLast('.'), words)
            }
        }

        return words.filter { it.length >= MIN_IMPORT_SEARCH_WORD_LENGTH }.toSet()
    }

    private fun addSearchWords(text: String, words: MutableSet<String>) {
        text.split(Regex("[^A-Za-z0-9_]+"))
            .asSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach(words::add)
    }

    private fun isImportCandidateFile(file: VirtualFile): Boolean {
        return file.isValid &&
            !file.isDirectory &&
            file.extension?.lowercase(Locale.ROOT) in IMPORT_EXTENSIONS
    }

    private fun fileKey(vf: com.intellij.openapi.vfs.VirtualFile?): String? = vf?.url

    private data class DeclarationProfile(
        val namespacePath: List<String>,
        val isNamespaceDeclaration: Boolean
    )

    companion object {
        private val DIRECT_IMPORTERS_KEY: Key<CachedValue<List<PsiFile>>> =
            Key.create("dreamshader.import.direct.importers.files")
        private const val MIN_IMPORT_SEARCH_WORD_LENGTH = 2
        private val IMPORT_EXTENSIONS = setOf("dsm", "dsf", "dsh")
        private val CALLABLE_NAME_ATTRIBUTE_DECLARATIONS = setOf(
            "namespace",
            "virtualfunction",
            "shaderfunction",
            "shaderlayer",
            "shaderlayerblend"
        )
        private val NAME_ATTRIBUTE_REGEX = Regex("\\bName\\s*=\\s*\"([^\"]+)\"")
    }
}

/**
 * Lightweight synthetic reference used by [DreamShaderReferencesSearchExecutor].
 */
private class DreamShaderLightReference(
    private val sourceElement: PsiElement,
    private val targetDeclaration: DreamShaderDeclaration
) : com.intellij.psi.PsiReference {
    override fun getElement(): PsiElement = sourceElement

    override fun getRangeInElement(): TextRange = TextRange(0, sourceElement.textLength)

    override fun resolve(): PsiElement = targetDeclaration

    override fun getCanonicalText(): String = sourceElement.text

    override fun handleElementRename(newElementName: String): PsiElement {
        if (newElementName.isBlank()) return sourceElement
        val replacement = createIdentifierFromText(newElementName) ?: return sourceElement
        return sourceElement.replace(replacement)
    }

    override fun bindToElement(element: PsiElement): PsiElement = sourceElement

    override fun isReferenceTo(element: PsiElement): Boolean = element == targetDeclaration

    override fun getVariants(): Array<Any> = emptyArray()

    override fun isSoft(): Boolean = true

    private fun createIdentifierFromText(newName: String): PsiElement? {
        val dummyFile = PsiFileFactory.getInstance(sourceElement.project).createFileFromText(
            "dummy.dsh",
            DreamShaderLanguage,
            "Function $newName { }"
        )
        val declaration = PsiTreeUtil.findChildOfType(dummyFile, DreamShaderDeclaration::class.java) ?: return null
        return declaration.nameIdentifier
    }
}
