package com.github.tsdaer.dreamshaderlanguagesupport.language.core
import com.intellij.lang.Language

/**
 * $name 单例对象。
 */
object DreamShaderLanguage : Language("DreamShaderLang") {
    @Suppress("unused")
    private fun readResolve(): Any = DreamShaderLanguage
}
