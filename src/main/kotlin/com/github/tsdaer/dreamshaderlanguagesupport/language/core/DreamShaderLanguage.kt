package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.lang.Language

/**
 * Singleton for DreamShaderLanguage.
 */
object DreamShaderLanguage : Language("DreamShaderLang") {
    @Suppress("unused")
    private fun readResolve(): Any = DreamShaderLanguage
}
