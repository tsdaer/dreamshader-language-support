package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType

class DreamShaderLiveTemplateContext : TemplateContextType("DreamShader") {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return templateActionContext.file.language.`is`(DreamShaderLanguage)
    }
}
