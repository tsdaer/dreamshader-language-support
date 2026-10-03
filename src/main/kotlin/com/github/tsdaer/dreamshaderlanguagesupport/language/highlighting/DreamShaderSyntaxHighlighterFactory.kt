package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Dialect-aware highlighter source: `.dss` / `.dsi` (and any file whose content carries V2
 * markers is parsed that way anyway) highlight with the 2.0 lexer, the rest keep the frozen V1
 * highlighter. Extension decides here because highlighting has no parse; the content predicate
 * and the parser dispatch agree by construction on marker presence.
 */
class DreamShaderSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter {
        val extension = virtualFile?.extension
        return if (extension != null && DreamShaderDialects.V2_EXTENSIONS.contains(extension.lowercase())) {
            DreamShaderSyntaxHighlighterV2()
        } else {
            DreamShaderSyntaxHighlighter()
        }
    }
}
