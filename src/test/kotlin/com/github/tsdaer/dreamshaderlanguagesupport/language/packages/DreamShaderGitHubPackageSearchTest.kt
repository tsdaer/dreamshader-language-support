package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DreamShaderGitHubPackageSearchTest {
    @Test
    fun `parse item object into package entry`() {
        val item = """
            {
              "name": "dream-noise",
              "full_name": "TypeDreamMoon/dream-noise",
              "html_url": "https://github.com/TypeDreamMoon/dream-noise",
              "description": "DreamShader noise package",
              "topics": ["dreamshader", "noise"]
            }
        """.trimIndent()

        val entryMethod = DreamShaderGitHubPackageSearch::class.java.getDeclaredMethod(
            "parseRepository",
            String::class.java
        )
        entryMethod.isAccessible = true
        val entry = entryMethod.invoke(DreamShaderGitHubPackageSearch, item) as DreamShaderPackageIndexEntry

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

        val extractMethod = DreamShaderGitHubPackageSearch::class.java.getDeclaredMethod(
            "extractTopLevelObjectsFromItemsArray",
            String::class.java
        )
        extractMethod.isAccessible = true
        val items = extractMethod.invoke(DreamShaderGitHubPackageSearch, payload) as List<*>
        assertEquals(1, items.size)
        assertTrue((items.first() as String).contains("\"full_name\": \"TypeDreamMoon/dream-water\""))
    }
}
