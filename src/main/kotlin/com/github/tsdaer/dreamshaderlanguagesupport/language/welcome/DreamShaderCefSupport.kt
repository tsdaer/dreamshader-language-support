package com.github.tsdaer.dreamshaderlanguagesupport.language.welcome

import com.intellij.openapi.diagnostic.Logger

private val LOG = Logger.getInstance("DreamShaderWelcome")

internal fun isCefSupported(): Boolean = try {
    Class.forName("com.intellij.ui.jcef.JBCefApp")
        .getMethod("isSupported")
        .invoke(null) as? Boolean == true
} catch (t: Throwable) {
    LOG.warn("Failed to check JBCefApp support", t)
    false
}
