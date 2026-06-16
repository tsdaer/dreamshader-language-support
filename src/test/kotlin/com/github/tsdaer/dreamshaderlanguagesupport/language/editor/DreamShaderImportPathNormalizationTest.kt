package com.github.tsdaer.dreamshaderlanguagesupport.language.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DreamShaderImportPathNormalizationTest {
    @Test
    fun `normalizes scoped package file path to import path`() {
        val candidates = normalizeProjectRelativeImportCandidates(
            "DShader/Packages/@typedreammoon/dream-noise/Library/Noise.dsh"
        )

        assertEquals(listOf("@typedreammoon/dream-noise/Library/Noise.dsh"), candidates)
    }

    @Test
    fun `normalizes unscoped package file path to import path`() {
        val candidates = normalizeProjectRelativeImportCandidates(
            "DShader/Packages/dream-common/Library/Main.dsh"
        )

        assertEquals(listOf("dream-common/Library/Main.dsh"), candidates)
    }

    @Test
    fun `keeps dshader relative form and project relative fallback for non package files`() {
        val candidates = normalizeProjectRelativeImportCandidates("DShader/Library/Common.dsh")

        assertEquals(listOf("Library/Common.dsh", "DShader/Library/Common.dsh"), candidates)
    }
}
