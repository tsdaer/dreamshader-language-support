package com.github.tsdaer.dreamshaderlanguagesupport.language.settings

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.welcome.showWelcomeDialog
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction

class DreamShaderOpenWelcomeAction : DumbAwareAction(
    DreamShaderBundle.message("hub.button.openWelcome"),
    DreamShaderBundle.message("hub.button.openWelcome"),
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        showWelcomeDialog(project, forceManual = true)
    }
}
