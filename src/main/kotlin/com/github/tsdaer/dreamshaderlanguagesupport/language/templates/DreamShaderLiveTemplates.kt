@file:Suppress("DEPRECATION")

package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderLanguage
import com.intellij.codeInsight.template.TemplateActionContext
import com.intellij.codeInsight.template.TemplateContextType
import com.intellij.codeInsight.template.impl.DefaultLiveTemplatesProvider

class DreamShaderLiveTemplateContext : TemplateContextType(
    "DREAM_SHADER",
    "DreamShader"
) {
    override fun isInContext(templateActionContext: TemplateActionContext): Boolean {
        return templateActionContext.file.language.`is`(DreamShaderLanguage)
    }
}

class DreamShaderLiveTemplatesProvider : DefaultLiveTemplatesProvider {
    override fun getDefaultLiveTemplateFiles(): Array<String> {
        return arrayOf("/liveTemplates/DreamShader")
    }

    override fun getHiddenLiveTemplateFiles(): Array<String> {
        return emptyArray()
    }
}
