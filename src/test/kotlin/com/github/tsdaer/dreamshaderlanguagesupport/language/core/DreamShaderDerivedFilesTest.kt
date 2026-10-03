package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.DreamShaderProblemHighlightFilter
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * A9: derived (generated) source recognition and the problem-highlight truth table.
 * Decompiled output must not be error-highlighted while hand-written sources keep
 * their diagnostics.
 */
class DreamShaderDerivedFilesTest : BasePlatformTestCase() {

    // ------------------------------------------------------------------
    // path classification truth table (pure)
    // ------------------------------------------------------------------

    fun testDerivedPathTruthTable() {
        val derived = listOf(
            "I:/Proj/DShader/Decompiled/Materials/Game/Materials/M_Steel.dsm",
            "I:/Proj/DShader/Decompiled/Instances/MI_Steel.dsi",
            "I:/Proj/Content/Intermediate/DreamShader/M_Red.gen.dss",
            "I:\\Proj\\DShader\\Decompiled\\Functions\\MF_X.dss",
        )
        derived.forEach { path ->
            assertTrue(DreamShaderDerivedFiles.isDerivedPath(path), "expected derived: $path")
        }

        val sources = listOf(
            "I:/Proj/DShader/Materials/M_Hand.dsm",
            "I:/Proj/DShader/Decompiled.dsm",
            "I:/Proj/Intermediate/Other/Thing.txt",
            "I:/Proj/DShader/Materials/MyDecompiledNotes.dsh",
            "",
        )
        sources.forEach { path ->
            assertFalse(DreamShaderDerivedFiles.isDerivedPath(path), "expected hand-written: '$path'")
        }
    }

    fun testIsDerivedFileNullSafe() {
        assertFalse(DreamShaderDerivedFiles.isDerivedFile(null))
    }

    // ------------------------------------------------------------------
    // problemHighlightFilter truth table (fixture files)
    // ------------------------------------------------------------------

    fun testProblemHighlightFilterTruthTable() {
        val decompiled = myFixture.addFileToProject(
            "DShader/Decompiled/Materials/M_Steel.dss",
            "#pragma material(Domain = Surface)\nexport void M_Steel(inout material m) { m.Unknown((); }\n"
        )
        val intermediate = myFixture.addFileToProject(
            "Intermediate/DreamShader/generated.dss",
            "#pragma material(Domain = Surface)\n"
        )
        val handWritten = myFixture.addFileToProject(
            "DShader/Materials/M_Hand.dss",
            "#pragma material(Domain = Surface)\n"
        )
        val legacy = myFixture.addFileToProject(
            "DShader/Decompiled/Old.dsm",
            "Shader(Name=\"Decompiled/Old\") {\n}\n"
        )
        val unrelated = myFixture.addFileToProject(
            "Docs/readme.txt",
            "hello"
        )

        val filter = DreamShaderProblemHighlightFilter()
        // Derived DreamShader sources: no platform error highlighting.
        assertFalse(filter.shouldHighlight(decompiled))
        assertFalse(filter.shouldHighlight(intermediate))
        assertFalse(filter.shouldHighlight(legacy))
        // Hand-written sources keep their diagnostics.
        assertTrue(filter.shouldHighlight(handWritten))
        // The filter never influences unrelated file types, derived or not.
        assertTrue(filter.shouldHighlight(unrelated))

        assertEquals(filter.shouldHighlight(decompiled), filter.shouldProcessInBatch(decompiled))
    }
}
