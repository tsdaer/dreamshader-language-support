package com.github.tsdaer.dreamshaderlanguagesupport.language.refactoring

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys

/**
 * The Refactoring-menu entry for Inline Variable (M16 C25). The handler re-checks the
 * dialect and all preconditions, so the action only filters on the file's language.
 */
class DreamShaderInlineVariableAction : AnAction() {

    override fun actionPerformed(event: AnActionEvent) {
        val editor = event.getData(CommonDataKeys.EDITOR) ?: return
        val file = event.getData(CommonDataKeys.PSI_FILE) ?: return
        val project = event.project ?: return
        DreamShader2InlineVariableHandler().invoke(project, editor, file, event.dataContext)
    }

    override fun update(event: AnActionEvent) {
        event.presentation.isEnabledAndVisible =
            event.getData(CommonDataKeys.PSI_FILE)?.language == DreamShaderLanguage
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}
