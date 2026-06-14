package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderJson
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

/**
 * GitHub 包搜索结果。
 */
internal data class DreamShaderGitHubSearchResult(
    val entries: List<DreamShaderPackageIndexEntry>,
    val errorMessage: String? = null
)

/** GitHub Search API 响应根：`{ "items": [ ... ] }`。 */
@Serializable
private data class GitHubSearchResponseDto(
    val items: List<GitHubRepositoryDto> = emptyList()
)

/** GitHub 仓库条目，未知字段由宽容解析忽略。 */
@Serializable
private data class GitHubRepositoryDto(
    val name: String? = null,
    @SerialName("full_name") val fullName: String? = null,
    @SerialName("html_url") val htmlUrl: String? = null,
    val description: String? = null,
    val topics: List<String> = emptyList()
)

/**
 * DreamShader GitHub 包搜索器。
 *
 * 基于 GitHub Search API 查询仓库并转换为包索引条目视图。
 */
internal object DreamShaderGitHubPackageSearch {
    private const val SEARCH_URL = "https://api.github.com/search/repositories"

    fun searchDreamShaderPackages(
        query: String,
        githubToken: String?
    ): DreamShaderGitHubSearchResult {
        val keyword = query.trim()
        if (keyword.isBlank()) return DreamShaderGitHubSearchResult(emptyList(), null)

        val q = buildQuery(keyword)
        val url = "$SEARCH_URL?q=${urlEncode(q)}&per_page=20"
        val response = fetch(url, githubToken?.trim().orEmpty())
            ?: return DreamShaderGitHubSearchResult(
                emptyList(),
                "Failed to request GitHub search API."
            )
        if (response.statusCode !in 200..299) {
            return DreamShaderGitHubSearchResult(
                emptyList(),
                "GitHub search failed (${response.statusCode})."
            )
        }

        val payload = DreamShaderJson.decodeOrNull<GitHubSearchResponseDto>(response.body)
            ?: return DreamShaderGitHubSearchResult(emptyList(), "Failed to parse GitHub search response.")
        return DreamShaderGitHubSearchResult(payload.items.mapNotNull(::toEntry), null)
    }

    /** 测试入口：解析完整搜索响应载荷为包条目列表。 */
    internal fun parseSearchPayload(rawJson: String): List<DreamShaderPackageIndexEntry> {
        val payload = DreamShaderJson.decodeOrNull<GitHubSearchResponseDto>(rawJson) ?: return emptyList()
        return payload.items.mapNotNull(::toEntry)
    }

    private fun buildQuery(keyword: String): String {
        return "$keyword topic:dreamshader in:name,description,readme"
    }

    private fun toEntry(dto: GitHubRepositoryDto): DreamShaderPackageIndexEntry? {
        val fullName = dto.fullName?.takeIf { it.isNotBlank() } ?: return null
        val htmlUrl = dto.htmlUrl?.takeIf { it.isNotBlank() } ?: return null
        return DreamShaderPackageIndexEntry(
            name = "@github/$fullName",
            displayName = dto.name,
            description = dto.description,
            version = null,
            repository = htmlUrl,
            source = "github-search",
            path = null,
            tags = dto.topics
        )
    }

    /**
     * Data model for HttpResponse.
     */
    private data class HttpResponse(
        val statusCode: Int,
        val body: String
    )

    private fun fetch(url: String, token: String): HttpResponse? {
        return runCatching {
            val connection = URI(url).toURL().openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 7000
            connection.readTimeout = 12000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            connection.setRequestProperty("User-Agent", "dreamshader-language-support")
            if (token.isNotBlank()) {
                connection.setRequestProperty("Authorization", "Bearer $token")
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.use { String(it.readBytes(), StandardCharsets.UTF_8) }.orEmpty()
            HttpResponse(status, body)
        }.getOrNull()
    }

    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
