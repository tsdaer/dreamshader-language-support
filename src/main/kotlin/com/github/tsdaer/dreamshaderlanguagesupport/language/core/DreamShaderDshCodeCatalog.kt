package com.github.tsdaer.dreamshaderlanguagesupport.language.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.concurrent.atomic.AtomicReference

/**
 * One bundled DSH diagnostic-code entry.
 *
 * [message] is the verbatim upstream message template (`{0}`-style placeholders included);
 * [docAnchor] points into the upstream docs tree (`DSH2xxx.md#dsh2248`); [summaryZh] is a
 * hand-curated Chinese summary that only exists for codes the plugin itself references.
 */
@Serializable
internal data class DreamShaderDshCodeEntry(
    val code: String,
    val severity: String,
    val message: String,
    val docAnchor: String,
    @SerialName("summary_zh") val summaryZh: String? = null
)

@Serializable
private data class DreamShaderDshCodeFile(
    val schema: String,
    val version: Int,
    val source: String? = null,
    val codes: List<DreamShaderDshCodeEntry> = emptyList()
)

/**
 * Read-only lookup over the bundled DSH diagnostic-code catalog
 * (`messages/dreamshader-dsh-codes.json`, generated from the upstream
 * `Docs/diagnostics/README.md` by `scripts/generate_dsh_codes.py`).
 *
 * The catalog is static bundled data: a schema mismatch degrades to an empty
 * catalog (no lookups, no crash), matching the bridge-artifact red line.
 */
internal object DreamShaderDshCodeCatalog {
    private const val RESOURCE_PATH = "/messages/dreamshader-dsh-codes.json"
    private const val EXPECTED_SCHEMA = "DreamShader.DshCodes"
    private const val EXPECTED_VERSION = 1

    /** Upstream docs are browsable on GitHub; [DreamShaderDshCodeEntry.docAnchor] is appended. */
    const val UPSTREAM_DOCS_BASE_URL: String =
        "https://github.com/TypeDreamMoon/DreamShader/blob/main/Docs/diagnostics/"

    private val cache = AtomicReference<Map<String, DreamShaderDshCodeEntry>?>(null)

    val isEmpty: Boolean get() = codesByCode().isEmpty()

    fun lookup(code: String?): DreamShaderDshCodeEntry? {
        val normalized = code?.trim()?.uppercase() ?: return null
        return codesByCode()[normalized]
    }

    fun documentationUrl(entry: DreamShaderDshCodeEntry): String =
        UPSTREAM_DOCS_BASE_URL + entry.docAnchor

    /**
     * First `DSHnnnn` code occurring in free text — the form used by bridge messages that
     * carry the code inline instead of in a dedicated field.
     */
    fun firstCodeIn(text: String?): String? {
        if (text.isNullOrEmpty()) return null
        val match = DSH_CODE_PATTERN.find(text) ?: return null
        return match.value.uppercase()
    }

    private fun codesByCode(): Map<String, DreamShaderDshCodeEntry> {
        cache.get()?.let { return it }
        val loaded = loadCatalog()
        return if (cache.compareAndSet(null, loaded)) loaded else cache.get() ?: loaded
    }

    private fun loadCatalog(): Map<String, DreamShaderDshCodeEntry> {
        return runCatching {
            val text = javaClass.getResourceAsStream(RESOURCE_PATH)
                ?.readBytes()
                ?.toString(Charsets.UTF_8)
                ?: return emptyMap()
            val parsed = DreamShaderJson.lenient.decodeFromString(
                DreamShaderDshCodeFile.serializer(),
                text
            )
            if (parsed.schema != EXPECTED_SCHEMA || parsed.version != EXPECTED_VERSION) {
                return emptyMap()
            }
            parsed.codes.associateBy { it.code.uppercase() }
        }.getOrDefault(emptyMap())
    }

    private val DSH_CODE_PATTERN: Regex = Regex("DSH(\\d{4})")
}
