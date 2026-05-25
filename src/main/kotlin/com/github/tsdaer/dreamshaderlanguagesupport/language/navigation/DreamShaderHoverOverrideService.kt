package com.github.tsdaer.dreamshaderlanguagesupport.language.navigation

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
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
        val parsed = linkedMapOf<String, String>()
        raw.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("#")) return@forEach
            val idx = trimmed.indexOf('=')
            if (idx <= 0) return@forEach

            val key = trimmed.substring(0, idx).trim().lowercase(Locale.ROOT)
            if (key.isBlank()) return@forEach
            val value = trimmed.substring(idx + 1).trim()
            if (value.isBlank()) return@forEach
            parsed[key] = value
        }
        return parsed
    }
}

