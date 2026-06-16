package com.github.tsdaer.dreamshaderlanguagesupport.language.editor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

class DreamShaderUnrealSourceLocatorTest {
    @TempDir
    lateinit var tempDir: Path

    /** 造一个引擎目录，含 Engine/Source，可选 Build.version。 */
    private fun makeEngine(
        parent: File,
        name: String,
        version: Triple<Int, Int, Int>? = Triple(5, 4, 0)
    ): File {
        val root = File(parent, name)
        File(root, "Engine/Source").mkdirs()
        if (version != null) {
            val buildDir = File(root, "Engine/Build").apply { mkdirs() }
            val (major, minor, patch) = version
            File(buildDir, "Build.version").writeText(
                """{ "MajorVersion": $major, "MinorVersion": $minor, "PatchVersion": $patch }"""
            )
        }
        return root
    }

    @Test
    fun `locates engine from sln relative engine paths`() {
        val workspace = tempDir.resolve("workspace").toFile().apply { mkdirs() }
        val projectDir = File(workspace, "honkai_rts").apply { mkdirs() }
        // 引擎在 ..\UnrealEngine\UntoonEngine 相对工程目录。
        val engineRoot = makeEngine(workspace, "UnrealEngine/UntoonEngine")

        val slnText = buildString {
            appendLine("Microsoft Visual Studio Solution File, Format Version 12.00")
            repeat(3) {
                appendLine("""Project = "..\UnrealEngine\UntoonEngine\Engine\Source\Programs\X$it.csproj"""")
            }
            appendLine("""Project = "..\Other\Wrong\Engine\Source\Y.csproj"""")
        }
        File(projectDir, "honkai_rts.sln").writeText(slnText)

        val candidate = DreamShaderUnrealSourceLocator.locate(projectDir).single()

        assertEquals(engineRoot.canonicalFile.path.replace('\\', '/'), candidate.engineRoot)
        assertTrue(candidate.sourceRoot.endsWith("Engine/Source"))
        assertEquals("5.4", candidate.version)
    }

    @Test
    fun `ignores sln engine paths whose Engine Source is missing`() {
        val workspace = tempDir.resolve("ws2").toFile().apply { mkdirs() }
        val projectDir = File(workspace, "proj").apply { mkdirs() }
        // sln 指向的引擎不存在 Engine/Source。
        File(projectDir, "proj.sln").writeText(
            """Project = "..\NoSuchEngine\Engine\Source\X.csproj""""
        )

        assertTrue(DreamShaderUnrealSourceLocator.locate(projectDir).isEmpty())
    }

    @Test
    fun `candidateAt recognizes engine root by Engine Source`() {
        val engineRoot = makeEngine(tempDir.resolve("eng").toFile().apply { mkdirs() }, "MyEngine", version = null)

        val candidate = DreamShaderUnrealSourceLocator.candidateAt(File(engineRoot.path))

        assertTrue(candidate != null)
        assertNull(candidate!!.version)
        assertTrue(candidate.sourceRoot.endsWith("Engine/Source"))
    }

    @Test
    fun `candidateAt parses patch version when nonzero`() {
        val engineRoot = makeEngine(tempDir.resolve("engp").toFile().apply { mkdirs() }, "E", version = Triple(5, 3, 2))

        assertEquals("5.3.2", DreamShaderUnrealSourceLocator.candidateAt(engineRoot)?.version)
    }

    @Test
    fun `candidateAt returns null without Engine Source`() {
        val plain = tempDir.resolve("plain").toFile().apply { mkdirs() }

        assertNull(DreamShaderUnrealSourceLocator.candidateAt(plain))
    }

    @Test
    fun `prefers Classes Materials scan root when present`() {
        val engineRoot = makeEngine(tempDir.resolve("engmat").toFile().apply { mkdirs() }, "E", version = null)
        File(engineRoot, "Engine/Source/Runtime/Engine/Classes/Materials").mkdirs()

        val candidate = DreamShaderUnrealSourceLocator.candidateAt(engineRoot)

        assertTrue(candidate!!.sourceRoot.endsWith("Engine/Source/Runtime/Engine/Classes/Materials"))
    }

    @Test
    fun `prefers Public Materials scan root when Classes Materials absent`() {
        val engineRoot = makeEngine(tempDir.resolve("engpub").toFile().apply { mkdirs() }, "E", version = null)
        File(engineRoot, "Engine/Source/Runtime/Engine/Public/Materials").mkdirs()

        val candidate = DreamShaderUnrealSourceLocator.candidateAt(engineRoot)

        assertTrue(candidate!!.sourceRoot.endsWith("Engine/Source/Runtime/Engine/Public/Materials"))
    }

    @Test
    fun `falls back to Engine Source when no Materials directory`() {
        val engineRoot = makeEngine(tempDir.resolve("engfb").toFile().apply { mkdirs() }, "E", version = null)

        val candidate = DreamShaderUnrealSourceLocator.candidateAt(engineRoot)

        assertTrue(candidate!!.sourceRoot.endsWith("Engine/Source"))
    }

    @Test
    fun `uproject without EngineAssociation yields no candidate`() {
        val projectDir = tempDir.resolve("game").toFile().apply { mkdirs() }
        File(projectDir, "Game.uproject").writeText("""{ "FileVersion": 3 }""")

        assertTrue(DreamShaderUnrealSourceLocator.locate(projectDir).isEmpty())
    }

    @Test
    fun `returns empty for null start`() {
        assertTrue(DreamShaderUnrealSourceLocator.locate(null).isEmpty())
    }
}
