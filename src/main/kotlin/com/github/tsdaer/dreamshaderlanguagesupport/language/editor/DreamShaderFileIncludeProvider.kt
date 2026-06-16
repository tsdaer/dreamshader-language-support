package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderFileType
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.github.tsdaer.dreamshaderlanguagesupport.language.packages.DreamShaderImportResolver
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileSystemItem
import com.intellij.psi.PsiManager
import com.intellij.psi.impl.include.FileIncludeInfo
import com.intellij.psi.impl.include.FileIncludeProvider
import com.intellij.util.Consumer
import com.intellij.util.indexing.FileContent

class DreamShaderFileIncludeProvider : FileIncludeProvider() {
    private val importRegex = Regex("""(?m)\bimport\s+"([^"]+)"""")

    override fun getId(): String = "dreamshader"

    override fun acceptFile(file: VirtualFile): Boolean {
        val extension = file.extension?.lowercase() ?: return false
        return extension in setOf("dsm", "dsf", "dsh")
    }

    override fun getIncludeInfos(content: FileContent): Array<FileIncludeInfo> {
        if (!acceptFile(content.file)) return emptyArray()
        return importRegex.findAll(content.contentAsText)
            .mapNotNull { match ->
                val path = match.groupValues.getOrNull(1)?.trim().orEmpty()
                path.takeIf { it.isNotBlank() }?.let { FileIncludeInfo(it) }
            }
            .toList()
            .toTypedArray()
    }

    override fun registerFileTypesUsedForIndexing(fileTypeSink: Consumer<in FileType>) {
        fileTypeSink.consume(DreamShaderFileType.INSTANCE)
    }

    override fun resolveIncludedFile(info: FileIncludeInfo, context: PsiFile): PsiFileSystemItem? {
        if (context.language != DreamShaderLanguage) return null
        val target = DreamShaderImportResolver.resolveImport(context, info.fileName) ?: return null
        return PsiManager.getInstance(context.project).findFile(target)
    }

    internal fun testIncludeInfos(text: String): List<String> {
        return importRegex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf(String::isNotBlank) }
            .toList()
    }
}
