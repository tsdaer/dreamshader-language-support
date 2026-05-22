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

class DreamShaderPackageIndexTest : BasePlatformTestCase() {
    fun testLoadsAndMergesMultipleIndexSources() {
        val sourceA = createTempJson(
            """
            [
              { "name": "@scope/a", "repository": "https://example.com/a.git" }
            ]
            """.trimIndent()
        )
        val sourceB = createTempJson(
            """
            [
              { "name": "@scope/b", "repository": "https://example.com/b.git" }
            ]
            """.trimIndent()
        )
        val result = DreamShaderPackageIndexLoader.loadFromSources(
            listOf(sourceA.absolutePathString(), sourceB.absolutePathString())
        )

        assertTrue(result.errors.isEmpty())
        val names = result.entries.map { it.name }.toSet()
        assertTrue(names.contains("@scope/a"))
        assertTrue(names.contains("@scope/b"))
    }

    fun testLegacySingleSourceIndexCompatibility() {
        val source = createTempJson(
            """
            [
              { "name": "@scope/legacy", "repository": "https://example.com/legacy.git" }
            ]
            """.trimIndent()
        )
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        settings.state.packageStoreIndexUrls = mutableListOf()
        settings.state.packageStoreIndexUrl = source.absolutePathString()

        val resolvedSources = DreamShaderPackageIndexLoader.resolveIndexSources(project)
        assertEquals(listOf(source.absolutePathString().replace('\\', '/').trimEnd('/')), resolvedSources)

        val result = DreamShaderPackageIndexLoader.loadFromSources(resolvedSources)
        assertEquals(1, result.entries.size)
        assertEquals("@scope/legacy", result.entries.first().name)
    }

    fun testParsesArrayAndObjectIndexShapes() {
        val arraySource = createTempJson(
            """
            [
              {
                "name": "@shape/array",
                "displayName": "Array Shape",
                "description": "array root",
                "version": "1.2.3",
                "repository": "https://example.com/array.git",
                "tags": ["noise", "math"]
              }
            ]
            """.trimIndent()
        )
        val objectSource = createTempJson(
            """
            {
              "packages": [
                {
                  "name": "@shape/object",
                  "displayName": "Object Shape",
                  "description": "object root",
                  "repository": "https://example.com/object.git",
                  "tags": ["utility"]
                }
              ]
            }
            """.trimIndent()
        )

        val arrayResult = DreamShaderPackageIndexLoader.loadFromSources(listOf(arraySource.absolutePathString()))
        val objectResult = DreamShaderPackageIndexLoader.loadFromSources(listOf(objectSource.absolutePathString()))

        assertTrue(arrayResult.errors.isEmpty())
        assertTrue(objectResult.errors.isEmpty())
        assertEquals("@shape/array", arrayResult.entries.first().name)
        assertEquals("Array Shape", arrayResult.entries.first().displayName)
        assertEquals("1.2.3", arrayResult.entries.first().version)
        assertEquals(listOf("noise", "math"), arrayResult.entries.first().tags)
        assertEquals("@shape/object", objectResult.entries.first().name)
        assertEquals("Object Shape", objectResult.entries.first().displayName)
        assertEquals(null, objectResult.entries.first().version)
        assertEquals(listOf("utility"), objectResult.entries.first().tags)
    }

    fun testMalformedIndexDoesNotBreakOtherSources() {
        val brokenSource = createTempJson(
            """
            { "packages": "broken" }
            """.trimIndent()
        )
        val validSource = createTempJson(
            """
            [
              { "name": "@scope/ok", "repository": "https://example.com/ok.git" }
            ]
            """.trimIndent()
        )

        val result = DreamShaderPackageIndexLoader.loadFromSources(
            listOf(brokenSource.absolutePathString(), validSource.absolutePathString())
        )

        assertEquals(1, result.entries.size)
        assertEquals("@scope/ok", result.entries.first().name)
        assertFalse(result.errors.isEmpty())
        assertTrue(result.errors.first().message.contains("Failed to parse package index"))
    }

    fun testResolvesLocalPathRelativeToIndexFile() {
        val workspaceRoot = Files.createTempDirectory("dreamshader-index-root-")
        val indexDir = Files.createDirectories(workspaceRoot.resolve("idx"))
        val localRepo = Files.createDirectories(workspaceRoot.resolve("repos").resolve("dream-noise"))
        val indexFile = indexDir.resolve("packages.json")
        Files.writeString(
            indexFile,
            """
            [
              {
                "name": "@typedreammoon/dream-noise",
                "repository": "https://example.com/fallback.git",
                "path": "../repos/dream-noise"
              }
            ]
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        val result = DreamShaderPackageIndexLoader.loadFromSources(listOf(indexFile.absolutePathString()))
        assertTrue(result.errors.isEmpty())
        val entry = result.entries.first()

        val installSource = DreamShaderPackageIndexLoader.resolveInstallSource(entry)
        assertTrue(installSource.resolvedFromLocalPath)
        assertEquals(
            localRepo.absolutePathString().replace('\\', '/').trimEnd('/'),
            installSource.sourcePathOrUrl
        )
    }

    fun testFallsBackToRepositoryWhenLocalPathMissing() {
        val workspaceRoot = Files.createTempDirectory("dreamshader-index-root-missing-")
        val indexDir = Files.createDirectories(workspaceRoot.resolve("idx"))
        val indexFile = indexDir.resolve("packages.json")
        Files.writeString(
            indexFile,
            """
            [
              {
                "name": "@typedreammoon/dream-noise",
                "repository": "https://github.com/TypeDreamMoon/dream-noise",
                "path": "../repos/not-exists"
              }
            ]
            """.trimIndent(),
            StandardCharsets.UTF_8
        )

        val result = DreamShaderPackageIndexLoader.loadFromSources(listOf(indexFile.absolutePathString()))
        assertTrue(result.errors.isEmpty())
        val entry = result.entries.first()
        val installSource = DreamShaderPackageIndexLoader.resolveInstallSource(entry)

        assertFalse(installSource.resolvedFromLocalPath)
        assertEquals("https://github.com/TypeDreamMoon/dream-noise", installSource.sourcePathOrUrl)
    }

    private fun createTempJson(content: String): Path {
        val file = Files.createTempFile("dreamshader-package-index-", ".json")
        Files.writeString(file, content, StandardCharsets.UTF_8)
        file.toFile().deleteOnExit()
        return file
    }
}
