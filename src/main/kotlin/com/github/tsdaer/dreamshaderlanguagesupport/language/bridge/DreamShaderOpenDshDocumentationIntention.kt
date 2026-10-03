package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDshCodeCatalog
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderDshCodeEntry
import com.intellij.codeInsight.intention.IntentionAction
import com.intellij.ide.BrowserUtil
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFile

/**
 * Alt+Enter action attached to bridge diagnostics that carry a DSH code: opens the code's
 * page in the upstream documentation.
 */
internal class DreamShaderOpenDshDocumentationIntention(
    private val entry: DreamShaderDshCodeEntry
) : IntentionAction {

    override fun startInWriteAction(): Boolean = false

    override fun getText(): String =
        "Open DSH documentation: ${entry.code}"

    override fun getFamilyName(): String = FAMILY_NAME

    override fun isAvailable(project: Project, editor: Editor?, file: PsiFile?): Boolean = true

    override fun invoke(project: Project, editor: Editor?, file: PsiFile?) {
        BrowserUtil.browse(DreamShaderDshCodeCatalog.documentationUrl(entry))
    }

    companion object {
        const val FAMILY_NAME: String = "DreamShader: open DSH documentation"
    }
}
