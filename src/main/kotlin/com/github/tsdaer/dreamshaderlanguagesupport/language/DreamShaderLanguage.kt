package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.lang.Language

object DreamShaderLanguage : Language("DreamShaderLang") {
    @Suppress("unused")
    private fun readResolve(): Any = DreamShaderLanguage
}
