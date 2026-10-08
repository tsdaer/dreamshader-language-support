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
    override fun getId(): String = "dreamshader"

    private fun hasDreamShaderExtension(extension: String?): Boolean {
        val normalizedExtension = extension?.lowercase() ?: return false
        return normalizedExtension in setOf("dsm", "dsf", "dsh", "dss", "dsi")
    }

    @Suppress("DEPRECATION")
    override fun acceptFile(file: VirtualFile): Boolean {
        return hasDreamShaderExtension(file.extension)
    }

    override fun getIncludeInfos(content: FileContent): Array<FileIncludeInfo> {
        if (!hasDreamShaderExtension(content.file.extension)) return emptyArray()
        return testIncludeInfos(content.contentAsText.toString()).map { FileIncludeInfo(it) }.toTypedArray()
    }

    @Suppress("DEPRECATION")
    override fun registerFileTypesUsedForIndexing(fileTypeSink: Consumer<in FileType>) {
        fileTypeSink.consume(DreamShaderFileType.INSTANCE)
    }

    override fun resolveIncludedFile(info: FileIncludeInfo, context: PsiFile): PsiFileSystemItem? {
        if (context.language != DreamShaderLanguage) return null
        val target = DreamShaderImportResolver.resolveImport(context, info.fileName) ?: return null
        return PsiManager.getInstance(context.project).findFile(target)
    }

    internal fun testIncludeInfos(text: String): List<String> {
        val lexer = com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShader2Lexer()
        val tokens = com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.DreamShaderTokenTypes
        lexer.start(text)
        val paths = mutableListOf<String>()
        var awaitingPath = false
        while (lexer.tokenType != null) {
            val type = lexer.tokenType
            val token = text.substring(lexer.tokenStart, lexer.tokenEnd)
            if (type !in setOf(tokens.WHITE_SPACE, tokens.LINE_COMMENT, tokens.BLOCK_COMMENT, tokens.DOC_COMMENT)) {
                if (awaitingPath && type == tokens.STRING && token.startsWith('"') && token.endsWith('"')) {
                    token.drop(1).dropLast(1).trim().takeIf { it.isNotBlank() }?.let(paths::add)
                }
                awaitingPath = token == "import" || type == tokens.PREPROCESSOR_DIRECTIVE && token == "#include"
            }
            lexer.advance()
        }
        return paths
    }
}
