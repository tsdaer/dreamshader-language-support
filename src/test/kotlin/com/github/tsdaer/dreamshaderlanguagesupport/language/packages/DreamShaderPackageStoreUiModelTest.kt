package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.DreamShaderProjectSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.absolutePathString

class DreamShaderPackageStoreUiModelTest : BasePlatformTestCase() {
    fun testAddIndexSourceDeduplicates() {
        val service = project.getService(DreamShaderPackageStoreService::class.java)
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.packageStoreIndexUrls = mutableListOf()
        settings.state.packageStoreIndexUrl = ""

        val first = service.addIndexSource("https://example.com/store/packages.json")
        val second = service.addIndexSource("https://example.com/store/packages.json/")

        assertTrue(first.changed)
        assertFalse(second.changed)
        assertEquals(1, settings.state.packageStoreIndexUrls.size)
        assertEquals("https://example.com/store/packages.json", settings.state.packageStoreIndexUrls.first())
    }

    fun testRemoveIndexSourceRefreshesStoreData() {
        val sourceA = createIndex(
            """
            [
              { "name": "@scope/a", "description": "alpha", "repository": "https://example.com/a.git" }
            ]
            """.trimIndent()
        )
        val sourceB = createIndex(
            """
            [
              { "name": "@scope/b", "description": "beta", "repository": "https://example.com/b.git" }
            ]
            """.trimIndent()
        )
        val service = project.getService(DreamShaderPackageStoreService::class.java)
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.packageStoreIndexUrls = mutableListOf(
            sourceA.absolutePathString(),
            sourceB.absolutePathString()
        )
        settings.state.packageStoreIndexUrl = ""

        val before = service.loadStore()
        assertEquals(setOf("@scope/a", "@scope/b"), before.entries.map { it.name }.toSet())

        val remove = service.removeIndexSource(sourceA.absolutePathString())
        assertTrue(remove.changed)
        val after = service.loadStore()
        assertEquals(setOf("@scope/b"), after.entries.map { it.name }.toSet())
    }

    fun testStoreSearchMatchesNameDescriptionAndTags() {
        val source = createIndex(
            """
            [
              {
                "name": "@typedreammoon/dream-noise",
                "displayName": "Noise Toolkit",
                "description": "procedural noise utilities",
                "repository": "https://example.com/noise.git",
                "tags": ["noise", "procedural"]
              },
              {
                "name": "@typedreammoon/dream-water",
                "displayName": "Water Surface",
                "description": "water rendering helpers",
                "repository": "https://example.com/water.git",
                "tags": ["water", "surface"]
              }
            ]
            """.trimIndent()
        )
        val service = project.getService(DreamShaderPackageStoreService::class.java)
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.packageStoreIndexUrls = mutableListOf(source.absolutePathString())
        settings.state.packageStoreIndexUrl = ""

        val byName = service.loadStore("@typedreammoon/dream-noise")
        assertEquals(listOf("@typedreammoon/dream-noise"), byName.entries.map { it.name })

        val byDescription = service.loadStore("rendering")
        assertEquals(listOf("@typedreammoon/dream-water"), byDescription.entries.map { it.name })

        val byTag = service.loadStore("procedural")
        assertEquals(listOf("@typedreammoon/dream-noise"), byTag.entries.map { it.name })
    }

    private fun createIndex(content: String): Path {
        val file = Files.createTempFile("dreamshader-store-ui-index-", ".json")
        Files.writeString(file, content, StandardCharsets.UTF_8)
        file.toFile().deleteOnExit()
        return file
    }
}
