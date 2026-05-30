package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderFileType
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportClosureResolver
import com.github.tsdaer.dreamshaderlanguagesupport.language.psi.DreamShaderDeclaration
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FileTypeIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.LocalSearchScope
import com.intellij.psi.search.SearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.util.Processor
import java.nio.file.Files
import java.nio.file.Paths
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
            val namespacePath = enclosingNamespacePath(declaration) + declarationName
            return DeclarationProfile(
                namespacePath = namespacePath,
                isNamespaceDeclaration = true
            )
        }

        return DeclarationProfile(
            namespacePath = enclosingNamespacePath(declaration),
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

        val enclosingNamespacePath = enclosingNamespacePath(identifier)
        return enclosingNamespacePath == profile.namespacePath
    }

    private fun enclosingNamespacePath(element: PsiElement): List<String> {
        val path = mutableListOf<String>()
        var current = PsiTreeUtil.getParentOfType(element, DreamShaderDeclaration::class.java, false)
        while (current != null) {
            if (current.keywordText() == "namespace") {
                val name = current.declarationName()
                if (!name.isNullOrBlank()) {
                    path.add(name)
                }
            }
            current = PsiTreeUtil.getParentOfType(current, DreamShaderDeclaration::class.java, true)
        }
        path.reverse()
        return path
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
        val project = seedFile.project
        val projectBasePath = project.basePath ?: return listOf(seedFile)
        val psiManager = PsiManager.getInstance(project)
        val allFilesByPath = linkedMapOf<String, PsiFile>()
        collectAllDreamShaderPsiFiles(project, projectBasePath, psiManager).forEach { psiFile ->
            val key = fileKey(psiFile.virtualFile) ?: return@forEach
            allFilesByPath.putIfAbsent(key, psiFile)
        }
        allFilesByPath.putIfAbsent(fileKey(seedVf) ?: seedVf.path, seedFile)

        val importsByPath = mutableMapOf<String, MutableSet<String>>()
        val importersByPath = mutableMapOf<String, MutableSet<String>>()
        for ((fromKey, psiFile) in allFilesByPath) {
            for (importedPsi in DreamShaderImportClosureResolver.resolveDirectImports(psiFile)) {
                val importedVf = importedPsi.virtualFile ?: continue
                val importedKey = fileKey(importedVf) ?: continue

                importsByPath.getOrPut(fromKey) { linkedSetOf() }.add(importedKey)
                importersByPath.getOrPut(importedKey) { linkedSetOf() }.add(fromKey)
            }
        }

        val visited = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(fileKey(seedVf) ?: seedVf.path)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            if (!visited.add(current)) continue

            val neighbors = linkedSetOf<String>()
            importsByPath[current]?.let { neighbors.addAll(it) }
            importersByPath[current]?.let { neighbors.addAll(it) }
            for (next in neighbors) {
                if (!visited.contains(next)) {
                    queue.addLast(next)
                }
            }
        }

        return visited.mapNotNull { path -> allFilesByPath[path] }
    }

    private fun collectAllDreamShaderPsiFiles(
        project: com.intellij.openapi.project.Project,
        projectBasePath: String,
        psiManager: PsiManager
    ): List<PsiFile> {
        val filesByKey = linkedMapOf<String, PsiFile>()
        FileTypeIndex.getFiles(DreamShaderFileType.INSTANCE, GlobalSearchScope.allScope(project)).forEach { vf ->
            val psi = psiManager.findFile(vf) ?: return@forEach
            if (psi.language != DreamShaderLanguage) return@forEach
            val key = fileKey(vf) ?: return@forEach
            filesByKey.putIfAbsent(key, psi)
        }

        val fs = LocalFileSystem.getInstance()
        val rootPath = runCatching { Paths.get(projectBasePath) }.getOrNull() ?: return emptyList()
        if (!Files.exists(rootPath) || !Files.isDirectory(rootPath)) return filesByKey.values.toList()

        Files.walk(rootPath).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { path ->
                val extension = path.fileName.toString().substringAfterLast('.', "").lowercase()
                if (extension !in IMPORT_EXTENSIONS) return@forEach

                val candidateVf = fs.findFileByPath(path.toString().replace('\\', '/')) ?: return@forEach
                val candidatePsi = psiManager.findFile(candidateVf) ?: return@forEach
                if (candidatePsi.language != DreamShaderLanguage) return@forEach
                val key = fileKey(candidateVf) ?: return@forEach
                filesByKey.putIfAbsent(key, candidatePsi)
            }
        }
        return filesByKey.values.toList()
    }

    private fun fileKey(vf: com.intellij.openapi.vfs.VirtualFile?): String? = vf?.url

    private data class DeclarationProfile(
        val namespacePath: List<String>,
        val isNamespaceDeclaration: Boolean
    )

    companion object {
        private val IMPORT_EXTENSIONS = setOf("dsm", "dsf", "dsh")
        private val CALLABLE_NAME_ATTRIBUTE_DECLARATIONS = setOf(
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
