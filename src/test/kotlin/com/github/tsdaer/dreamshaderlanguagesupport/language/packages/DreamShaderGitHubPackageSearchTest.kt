package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DreamShaderGitHubPackageSearchTest {
    @Test
    fun `parse item object into package entry`() {
        val payload = """
            {
              "items": [
                {
                  "name": "dream-noise",
                  "full_name": "TypeDreamMoon/dream-noise",
                  "html_url": "https://github.com/TypeDreamMoon/dream-noise",
                  "description": "DreamShader noise package",
                  "topics": ["dreamshader", "noise"]
                }
              ]
            }
        """.trimIndent()

        val entries = DreamShaderGitHubPackageSearch.parseSearchPayload(payload)
        assertEquals(1, entries.size)
        val entry = entries.first()

        assertEquals("@github/TypeDreamMoon/dream-noise", entry.name)
        assertEquals("dream-noise", entry.displayName)
        assertEquals("https://github.com/TypeDreamMoon/dream-noise", entry.repository)
        assertEquals("github-search", entry.source)
        assertEquals(listOf("dreamshader", "noise"), entry.tags)
    }

    @Test
    fun `extract items array from github search payload`() {
        val payload = """
            {
              "total_count": 1,
              "items": [
                {
                  "name": "dream-water",
                  "full_name": "TypeDreamMoon/dream-water",
                  "html_url": "https://github.com/TypeDreamMoon/dream-water",
                  "description": "Water package"
                }
              ]
            }
        """.trimIndent()

        val entries = DreamShaderGitHubPackageSearch.parseSearchPayload(payload)
        assertEquals(1, entries.size)
        assertEquals("@github/TypeDreamMoon/dream-water", entries.first().name)
        assertTrue(entries.first().tags.isEmpty())
    }
}
