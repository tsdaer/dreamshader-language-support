package com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDerivedFiles
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.intellij.codeInsight.daemon.ProblemHighlightFilter
import com.intellij.psi.PsiFile

/**
 * 派生文件高亮过滤器（A9）。
 *
 * `DShader/Decompiled/` 与 `Intermediate/DreamShader/` 目录下的源码是引擎生成物：
 * 平台对它们做错误高亮只会产生无法处置的误报，因此对这些文件返回 false。
 * 只读是「去掉误报」，不是禁止编辑——用户仍可手动修改，修改后过滤器不再放行误报的
 * 语义不变（路径仍是派生路径）。
 */
class DreamShaderProblemHighlightFilter : ProblemHighlightFilter() {

    override fun shouldHighlight(file: PsiFile): Boolean {
        if (!DreamShaderDerivedFiles.isDerivedFile(file.virtualFile)) return true
        val extension = file.virtualFile?.extension?.lowercase()
        return !(extension != null && DreamShaderDialects.isDreamShaderExtension(extension))
    }

    override fun shouldProcessInBatch(file: PsiFile): Boolean = shouldHighlight(file)
}
