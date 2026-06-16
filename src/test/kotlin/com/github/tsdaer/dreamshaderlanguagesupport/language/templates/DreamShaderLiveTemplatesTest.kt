package com.github.tsdaer.dreamshaderlanguagesupport.language.templates

import com.intellij.codeInsight.template.impl.TemplateSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderLiveTemplatesTest : BasePlatformTestCase() {
    fun testDreamShaderLiveTemplatesAreRegistered() {
        val templateSettings = TemplateSettings.getInstance()
        val expectedTemplates = setOf("shader", "sfun", "slayer", "slblend", "gfn", "iff", "ifel", "uee")

        val templates = templateSettings.templates
            .filter { it.groupName == "DreamShader" }
            .associateBy { it.key }

        assertEquals(expectedTemplates, templates.keys)
        assertEquals("DreamShader material shader", templates.getValue("shader").description)
        assertEquals("DreamShader UE.Expression", templates.getValue("uee").description)
    }
}
