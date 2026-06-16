package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionInfo
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.DreamShaderMaterialExpressionManifest
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.impl.FakePsiElement
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Locale

internal object DreamShaderUnrealSourceTargetResolver {
    fun resolve(element: PsiElement, ueName: String): PsiElement? {
        val file = element.containingFile ?: return null
        val project = element.project
        val settings = project.getService(DreamShaderProjectSettings::class.java)?.state ?: return null
        if (!settings.materialExpressionScanEnabled) return null

        val sourceRoot = settings.unrealEngineSourceRoot.trim().takeIf { it.isNotBlank() } ?: return null
        val entries = DreamShaderMaterialExpressionManifest.catalogEntries(
            project = project,
            explicitManifestPath = settings.materialExpressionManifestPath
        )
        val candidateClassNames = candidateClassNames(ueName, entries)
        if (candidateClassNames.isEmpty()) return null

        val cacheKey = UnrealSourceIndexKey(
            sourceRoot = sourceRoot,
            manifestPath = settings.materialExpressionManifestPath,
            scanCachePath = settings.materialExpressionScanCachePath
        )
        val cached = cachedIndex(file, cacheKey)
        val index = if (cached.key == cacheKey) cached.index else buildIndex(cacheKey)
        val target = candidateClassNames.asSequence()
            .mapNotNull { className -> index.byClassName[className.lowercase(Locale.ROOT)] }
            .firstOrNull() ?: return null
        val vf = LocalFileSystem.getInstance().findFileByPath(target.path) ?: return null
        if (!vf.isValid || vf.isDirectory) return null

        val psiFile = com.intellij.psi.PsiManager.getInstance(project).findFile(vf) ?: return null
        return UnrealSourcePsiElement(
            project = project,
            file = psiFile,
            virtualFile = vf,
            className = target.className,
            offset = target.offset.coerceAtLeast(0)
        )
    }

    private fun cachedIndex(
        file: PsiFile,
        key: UnrealSourceIndexKey
    ): CachedUnrealSourceIndex {
        return CachedValuesManager.getManager(file.project).getCachedValue(
            file,
            SOURCE_INDEX_KEY,
            {
                CachedValueProvider.Result.create(
                    CachedUnrealSourceIndex(key, buildIndex(key)),
                    PsiModificationTracker.MODIFICATION_COUNT
                )
            },
            false
        )
    }

    private fun candidateClassNames(
        ueName: String,
        entries: List<DreamShaderMaterialExpressionInfo>
    ): List<String> {
        val names = linkedSetOf<String>()
        entries.filter { entry ->
            entry.namespace.equals("UE", ignoreCase = true) &&
                entry.ueName.equals(ueName, ignoreCase = true)
        }.forEach { entry ->
            entry.className.takeIf { it.isNotBlank() }?.let(names::add)
        }
        names.add(ueName)
        names.add("MaterialExpression$ueName")
        names.add("UMaterialExpression$ueName")
        return names.toList()
    }

    private fun buildIndex(key: UnrealSourceIndexKey): UnrealSourceIndex {
        val root = File(key.sourceRoot)
        if (!root.exists() || !root.isDirectory) return UnrealSourceIndex(emptyMap())

        val byClassName = linkedMapOf<String, UnrealSourceLocation>()
        root.walkTopDown()
            .onEnter { dir -> dir.name.lowercase(Locale.ROOT) !in SKIPPED_DIRECTORIES }
            .filter { it.isFile && it.extension.equals("h", ignoreCase = true) }
            .forEach { header ->
                val text = runCatching { header.readText(StandardCharsets.UTF_8) }.getOrNull() ?: return@forEach
                CLASS_DECL_REGEX.findAll(text).forEach { match ->
                    val className = match.groupValues.getOrNull(1)?.takeIf { it.isNotBlank() } ?: return@forEach
                    val baseName = match.groupValues.getOrNull(2).orEmpty()
                    if (!isMaterialExpressionClassCandidate(className) &&
                        !isMaterialExpressionClassCandidate(baseName)
                    ) {
                        return@forEach
                    }
                    val key = className.lowercase(Locale.ROOT)
                    byClassName.putIfAbsent(
                        key,
                        UnrealSourceLocation(
                            path = normalize(header.path),
                            className = className,
                            offset = match.range.first + match.value.indexOf(className).coerceAtLeast(0)
                        )
                    )
                }
            }
        return UnrealSourceIndex(byClassName)
    }

    private fun isMaterialExpressionClassCandidate(className: String): Boolean {
        return className.startsWith("UMaterialExpression") || className.startsWith("MaterialExpression")
    }

    private fun normalize(path: String): String = path.replace('\\', '/')

    private class UnrealSourcePsiElement(
        private val project: Project,
        private val file: PsiFile,
        private val virtualFile: VirtualFile,
        private val className: String,
        private val offset: Int
    ) : FakePsiElement() {
        override fun getParent(): PsiElement = file
        override fun getProject(): Project = project
        override fun getContainingFile(): PsiFile = file
        override fun getName(): String = className
        override fun getText(): String = className
        override fun getTextOffset(): Int = offset
        override fun canNavigate(): Boolean = virtualFile.isValid
        override fun canNavigateToSource(): Boolean = canNavigate()
        override fun navigate(requestFocus: Boolean) {
            OpenFileDescriptor(project, virtualFile, offset).navigate(requestFocus)
        }
    }

    private data class UnrealSourceIndexKey(
        val sourceRoot: String,
        val manifestPath: String,
        val scanCachePath: String
    )

    private data class CachedUnrealSourceIndex(
        val key: UnrealSourceIndexKey,
        val index: UnrealSourceIndex
    )

    private data class UnrealSourceIndex(val byClassName: Map<String, UnrealSourceLocation>)

    private data class UnrealSourceLocation(
        val path: String,
        val className: String,
        val offset: Int
    )

    private val SKIPPED_DIRECTORIES = setOf("intermediate", "generated", "binaries", "saved", ".git")

    private val CLASS_DECL_REGEX = Regex(
        """\bclass\s+(?:[A-Za-z_][A-Za-z0-9_]*\s+)?(U[A-Za-z0-9_]+)\s*:\s*public\s+(U[A-Za-z0-9_]+)"""
    )

    private val SOURCE_INDEX_KEY: Key<CachedValue<CachedUnrealSourceIndex>> =
        Key.create("dreamshader.unreal.source.index")
}
