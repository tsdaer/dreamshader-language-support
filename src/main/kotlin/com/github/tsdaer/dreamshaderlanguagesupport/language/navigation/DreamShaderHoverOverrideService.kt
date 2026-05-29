package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderHoverOverrideParser
import com.intellij.openapi.project.Project
import java.util.*

internal object DreamShaderHoverOverrideService {
    fun resolve(project: Project, key: String): String? {
        if (key.isBlank()) return null
        val raw = project.getService(DreamShaderProjectSettings::class.java).state.hoverDocumentationOverrides
        if (raw.isBlank()) return null

        val normalizedKey = key.trim().lowercase(Locale.ROOT)
        val entries = parseOverrides(raw)
        return entries[normalizedKey]
    }

    private fun parseOverrides(raw: String): Map<String, String> {
        return DreamShaderHoverOverrideParser.parse(raw).entries
    }
}
