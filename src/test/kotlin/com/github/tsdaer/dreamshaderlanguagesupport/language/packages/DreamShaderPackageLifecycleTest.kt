package com.github.tsdaer.dreamshaderlanguagesupport.language.packages
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText
import kotlin.streams.asSequence

class DreamShaderPackageLifecycleTest : BasePlatformTestCase() {
    fun testInstallFromGithubShorthand() {
        val remote = createFakeRemoteRepo(
            name = "@typedreammoon/dream-noise",
            version = "1.0.0",
            repository = "https://github.com/TypeDreamMoon/dream-noise"
        )
        val git = FakeGitClient(
            available = true,
            clones = mapOf("https://github.com/TypeDreamMoon/dream-noise" to remote),
            commitByRepo = mutableMapOf(remote.toString() to "1111111")
        )
        val manager = DreamShaderPackageManager(project, git)

        val result = manager.installFromGitHub("TypeDreamMoon/dream-noise")
        assertTrue(result.success)
        assertEquals("@typedreammoon/dream-noise", result.packageName)

        val packageDir = packagesRoot().resolve("@typedreammoon").resolve("dream-noise")
        assertTrue(packageDir.exists())
        assertTrue(packageDir.resolve("dreamshader.package.json").isRegularFile())
        assertTrue(lockFile().isRegularFile())
    }

    fun testInstallFromGithubUrl() {
        val remote = createFakeRemoteRepo(
            name = "@typedreammoon/dream-noise",
            version = "1.0.1",
            repository = "https://github.com/TypeDreamMoon/dream-noise"
        )
        val git = FakeGitClient(
            available = true,
            clones = mapOf("https://github.com/TypeDreamMoon/dream-noise" to remote),
            commitByRepo = mutableMapOf(remote.toString() to "2222222")
        )
        val manager = DreamShaderPackageManager(project, git)

        val result = manager.installFromGitHub("https://github.com/TypeDreamMoon/dream-noise")
        assertTrue(result.success)
        assertEquals("@typedreammoon/dream-noise", result.packageName)
    }

    fun testInstallFailsWhenMetadataMissing() {
        val remote = Files.createTempDirectory("dreamshader-missing-metadata-")
        Files.writeString(remote.resolve("README.md"), "no metadata", StandardCharsets.UTF_8)
        val git = FakeGitClient(
            available = true,
            clones = mapOf("https://github.com/TypeDreamMoon/no-meta" to remote),
            commitByRepo = mutableMapOf(remote.toString() to "3333333")
        )
        val manager = DreamShaderPackageManager(project, git)

        val result = manager.installFromRepository("https://github.com/TypeDreamMoon/no-meta")
        assertFalse(result.success)
        assertEquals("Invalid package: missing dreamshader.package.json", result.message)
    }

    fun testInstallFailsWhenNameMissing() {
        val remote = Files.createTempDirectory("dreamshader-missing-name-")
        Files.writeString(
            remote.resolve("dreamshader.package.json"),
            """{ "version": "1.0.0" }""",
            StandardCharsets.UTF_8
        )
        val git = FakeGitClient(
            available = true,
            clones = mapOf("https://github.com/TypeDreamMoon/missing-name" to remote),
            commitByRepo = mutableMapOf(remote.toString() to "4444444")
        )
        val manager = DreamShaderPackageManager(project, git)

        val result = manager.installFromRepository("https://github.com/TypeDreamMoon/missing-name")
        assertFalse(result.success)
        assertEquals("Invalid package metadata: missing field 'name'", result.message)
    }

    fun testInstallWritesLockFileEntry() {
        val remote = createFakeRemoteRepo(
            name = "@typedreammoon/dream-noise",
            version = "1.2.3",
            repository = "https://github.com/TypeDreamMoon/dream-noise"
        )
        val git = FakeGitClient(
            available = true,
            clones = mapOf("https://github.com/TypeDreamMoon/dream-noise" to remote),
            commitByRepo = mutableMapOf(remote.toString() to "aaaaaaa")
        )
        val manager = DreamShaderPackageManager(project, git)

        val result = manager.installFromRepository("https://github.com/TypeDreamMoon/dream-noise")
        assertTrue(result.success)

        val text = lockFile().readText()
        assertTrue(text.contains(""""name": "@typedreammoon/dream-noise""""))
        assertTrue(text.contains(""""version": "1.2.3""""))
        assertTrue(text.contains(""""repository": "https://github.com/TypeDreamMoon/dream-noise""""))
        assertTrue(text.contains(""""commit": "aaaaaaa""""))
        assertTrue(text.contains(""""installPath": "DShader/Packages/@typedreammoon/dream-noise""""))
    }

    fun testUpdateRefreshesLockFileEntry() {
        val remote = createFakeRemoteRepo(
            name = "@typedreammoon/dream-noise",
            version = "1.0.0",
            repository = "https://github.com/TypeDreamMoon/dream-noise"
        )
        val git = FakeGitClient(
            available = true,
            clones = mapOf("https://github.com/TypeDreamMoon/dream-noise" to remote),
            commitByRepo = mutableMapOf(remote.toString() to "bbbbbbb")
        )
        val manager = DreamShaderPackageManager(project, git)
        assertTrue(manager.installFromRepository("https://github.com/TypeDreamMoon/dream-noise").success)

        val installedDir = packagesRoot().resolve("@typedreammoon").resolve("dream-noise")
        Files.writeString(
            installedDir.resolve("dreamshader.package.json"),
            """
            {
              "name": "@typedreammoon/dream-noise",
              "version": "2.0.0",
              "repository": "https://github.com/TypeDreamMoon/dream-noise"
            }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
        git.commitByRepo[installedDir.toString()] = "ccccccc"

        val updateResult = manager.updateInstalledPackage("@typedreammoon/dream-noise")
        assertTrue(updateResult.success)
        val text = lockFile().readText()
        assertTrue(text.contains(""""version": "2.0.0""""))
        assertTrue(text.contains(""""commit": "ccccccc""""))
    }

    fun testRemovePackageAndPruneLockEntry() {
        val remoteA = createFakeRemoteRepo("@scope/a", "1.0.0", "https://github.com/test/a")
        val remoteB = createFakeRemoteRepo("@scope/b", "1.0.0", "https://github.com/test/b")
        val git = FakeGitClient(
            available = true,
            clones = mapOf(
                "https://github.com/test/a" to remoteA,
                "https://github.com/test/b" to remoteB
            ),
            commitByRepo = mutableMapOf(
                remoteA.toString() to "1111111",
                remoteB.toString() to "2222222"
            )
        )
        val manager = DreamShaderPackageManager(project, git)
        assertTrue(manager.installFromRepository("https://github.com/test/a").success)
        assertTrue(manager.installFromRepository("https://github.com/test/b").success)

        val removed = manager.removeInstalledPackage("@scope/a")
        assertTrue(removed.success)
        assertFalse(packagesRoot().resolve("@scope").resolve("a").exists())
        assertTrue(packagesRoot().resolve("@scope").resolve("b").exists())

        val text = lockFile().readText()
        assertFalse(text.contains(""""name": "@scope/a""""))
        assertTrue(text.contains(""""name": "@scope/b""""))
    }

    fun testRemoveNonInstalledPackageFailsClearly() {
        val manager = DreamShaderPackageManager(project, FakeGitClient())
        val result = manager.removeInstalledPackage("@scope/not-installed")
        assertFalse(result.success)
        assertEquals("Package is not installed: @scope/not-installed", result.message)
    }

    fun testOpenPackagesFolderCreatesWhenMissing() {
        val manager = DreamShaderPackageManager(project, FakeGitClient())
        val root = manager.openPackagesFolder()
        assertTrue(root.exists())
        assertTrue(root.isDirectory())
        assertEquals(packagesRoot().toAbsolutePath().normalize(), root.toAbsolutePath().normalize())
    }

    fun testInstallFailsWhenGitUnavailable() {
        val manager = DreamShaderPackageManager(project, FakeGitClient(available = false))
        val result = manager.installFromRepository("https://github.com/TypeDreamMoon/dream-noise")
        assertFalse(result.success)
        assertEquals("git is required for package install/update", result.message)
    }

    private fun createFakeRemoteRepo(name: String, version: String, repository: String): Path {
        val dir = Files.createTempDirectory("dreamshader-remote-repo-")
        Files.createDirectories(dir.resolve("Library"))
        Files.writeString(
            dir.resolve("Library").resolve("Noise.dsh"),
            "Function Noise {}",
            StandardCharsets.UTF_8
        )
        Files.writeString(
            dir.resolve("dreamshader.package.json"),
            """
            {
              "name": "$name",
              "version": "$version",
              "repository": "$repository"
            }
            """.trimIndent(),
            StandardCharsets.UTF_8
        )
        return dir
    }

    private fun packagesRoot(): Path {
        val basePath = project.basePath ?: error("project base path is null")
        return Path.of(basePath).resolve("DShader").resolve("Packages")
    }

    private fun lockFile(): Path {
        val basePath = project.basePath ?: error("project base path is null")
        return Path.of(basePath).resolve("DShader").resolve("dreamshader.lock.json")
    }

    private class FakeGitClient(
        private val available: Boolean = true,
        private val clones: Map<String, Path> = emptyMap(),
        val commitByRepo: MutableMap<String, String> = mutableMapOf()
    ) : DreamShaderGitClient {
        override fun isAvailable(): Boolean = available

        override fun clone(source: String, targetDir: Path): DreamShaderGitCommandResult {
            val remote = clones[source] ?: return DreamShaderGitCommandResult(false, "clone source not found: $source")
            copyDirectory(remote, targetDir)
            val commit = commitByRepo[remote.toString()] ?: "fake-commit"
            commitByRepo[targetDir.toString()] = commit
            return DreamShaderGitCommandResult(true, "ok")
        }

        override fun pull(repoDir: Path): DreamShaderGitCommandResult {
            val commit = commitByRepo[repoDir.toString()] ?: "fake-commit"
            commitByRepo[repoDir.toString()] = commit
            return DreamShaderGitCommandResult(true, "ok")
        }

        override fun currentCommit(repoDir: Path): String? = commitByRepo[repoDir.toString()] ?: "fake-commit"

        private fun copyDirectory(from: Path, to: Path) {
            Files.walk(from).use { stream ->
                stream.asSequence().forEach { source ->
                    val relative = from.relativize(source)
                    val target = to.resolve(relative.toString())
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target)
                    } else {
                        Files.createDirectories(target.parent)
                        Files.copy(source, target)
                    }
                }
            }
        }
    }
}
