package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * The bundled DSH code catalog (A5): schema-checked loading, lookup, free-text code
 * extraction and documentation URL construction.
 */
class DreamShaderDshCodeCatalogTest {

    @Test
    fun catalogLoadsAndCoversKnownCodes() {
        assertTrue(!DreamShaderDshCodeCatalog.isEmpty, "bundled catalog must not degrade to empty")

        val dsh2248 = DreamShaderDshCodeCatalog.lookup("DSH2248")
        assertNotNull(dsh2248)
        assertEquals("error", dsh2248!!.severity)
        assertEquals("DSH2xxx.md#dsh2248", dsh2248.docAnchor)
        assertNotNull(dsh2248.summaryZh, "curated codes must carry a Chinese summary")

        val dsh3203 = DreamShaderDshCodeCatalog.lookup("dsh3203")
        assertNotNull(dsh3203, "lookup is case-insensitive")
        assertTrue(dsh3203!!.message.contains("#include"))
    }

    @Test
    fun unknownCodeLookupsDegradeToNull() {
        assertNull(DreamShaderDshCodeCatalog.lookup(null))
        assertNull(DreamShaderDshCodeCatalog.lookup(""))
        assertNull(DreamShaderDshCodeCatalog.lookup("DSH9999"))
        assertNull(DreamShaderDshCodeCatalog.lookup("not-a-code"))
    }

    @Test
    fun firstCodeInExtractsInlineCodes() {
        assertEquals("DSH2248", DreamShaderDshCodeCatalog.firstCodeIn("boom: DSH2248 in message"))
        assertEquals("DSH1030", DreamShaderDshCodeCatalog.firstCodeIn("prefix DSH1030 suffix"))
        assertNull(DreamShaderDshCodeCatalog.firstCodeIn("no code here"))
        assertNull(DreamShaderDshCodeCatalog.firstCodeIn("DSH12 is too short"))
        assertNull(DreamShaderDshCodeCatalog.firstCodeIn(null))
    }

    @Test
    fun documentationUrlPointsIntoUpstreamDocs() {
        val entry = requireNotNull(DreamShaderDshCodeCatalog.lookup("DSH2248"))
        val url = DreamShaderDshCodeCatalog.documentationUrl(entry)
        assertEquals(
            "https://github.com/TypeDreamMoon/DreamShader/blob/v2.2.0/Docs/diagnostics/DSH2xxx.md#dsh2248",
            url
        )
    }

    @Test
    fun catalogIncludesNewCodesAndMatchesBySeverity() {
        assertEquals("error", DreamShaderDshCodeCatalog.lookup("DSH6224")?.severity)
        assertEquals("info", DreamShaderDshCodeCatalog.lookup("DSH8277")?.severity)
        assertEquals(listOf("DSH8278"), DreamShaderDshCodeCatalog.matching("dsh827", "warning")
            .map { it.code }.filter { it >= "DSH8277" })
    }
}
