package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDerivedFiles
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDialects
import com.intellij.codeInsight.actions.ReaderModeMatcher
import com.intellij.codeInsight.actions.ReaderModeProvider
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * 反编译产物默认进入阅读模式（A9）：文档渲染、弱化标点，服务「只读浏览生成物」。
 *
 * 对非 DreamShader 源文件返回 null（不表态），不干预平台其他判定。
 */
class DreamShaderDerivedReaderModeMatcher : ReaderModeMatcher {

    override fun matches(
        project: Project,
        file: VirtualFile,
        editor: Editor?,
        mode: ReaderModeProvider.ReaderMode
    ): Boolean? {
        val extension = file.extension?.lowercase() ?: return null
        if (!DreamShaderDialects.isDreamShaderExtension(extension)) return null
        return DreamShaderDerivedFiles.isDerivedFile(file)
    }
}
