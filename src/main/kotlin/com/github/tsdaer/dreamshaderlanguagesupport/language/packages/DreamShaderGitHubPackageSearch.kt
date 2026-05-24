package com.github.tsdaer.dreamshaderlanguagesupport.language.packages
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import java.net.HttpURLConnection
import java.net.URI
import java.nio.charset.StandardCharsets

internal data class DreamShaderGitHubSearchResult(
    val entries: List<DreamShaderPackageIndexEntry>,
    val errorMessage: String? = null
)

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

        return runCatching {
            val items = extractTopLevelObjectsFromItemsArray(response.body)
            val entries = items.mapNotNull { parseRepository(it) }
            DreamShaderGitHubSearchResult(entries, null)
        }.getOrElse {
            DreamShaderGitHubSearchResult(emptyList(), "Failed to parse GitHub search response.")
        }
    }

    private fun buildQuery(keyword: String): String {
        return "$keyword topic:dreamshader in:name,description,readme"
    }

    private fun parseRepository(item: String): DreamShaderPackageIndexEntry? {
        val fullName = findStringField(item, "full_name") ?: return null
        val htmlUrl = findStringField(item, "html_url") ?: return null
        val description = findStringField(item, "description")
        val displayName = findStringField(item, "name")
        val tags = extractTopics(item)
        return DreamShaderPackageIndexEntry(
            name = "@github/$fullName",
            displayName = displayName,
            description = description,
            version = null,
            repository = htmlUrl,
            source = "github-search",
            path = null,
            tags = tags
        )
    }

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

    private fun extractTopLevelObjectsFromItemsArray(text: String): List<String> {
        val itemsArray = extractArrayField(text, "items") ?: return emptyList()
        return extractTopLevelObjects(itemsArray)
    }

    private fun extractTopics(objectText: String): List<String> {
        val array = extractArrayField(objectText, "topics") ?: return emptyList()
        return Regex(""""((?:[^"\\]|\\.)*)"""").findAll(array).map { match ->
            unescapeJsonString(match.groupValues[1])
        }.toList()
    }

    private fun extractArrayField(text: String, field: String): String? {
        val index = text.indexOf("\"$field\"")
        if (index < 0) return null
        val openBracket = text.indexOf('[', index)
        if (openBracket < 0) return null
        var depth = 0
        var inString = false
        var escaped = false
        for (i in openBracket until text.length) {
            val ch = text[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return text.substring(openBracket, i + 1)
                }
            }
        }
        return null
    }

    private fun extractTopLevelObjects(arrayText: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        var objectStart = -1
        var inString = false
        var escaped = false

        for (i in arrayText.indices) {
            val ch = arrayText[i]
            if (inString) {
                if (escaped) {
                    escaped = false
                } else if (ch == '\\') {
                    escaped = true
                } else if (ch == '"') {
                    inString = false
                }
                continue
            }
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) objectStart = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && objectStart >= 0) {
                        result.add(arrayText.substring(objectStart, i + 1))
                        objectStart = -1
                    }
                }
            }
        }
        return result
    }

    private fun findStringField(text: String, name: String): String? {
        val regex = Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
        val match = regex.find(text) ?: return null
        return unescapeJsonString(match.groupValues[1])
    }

    private fun unescapeJsonString(raw: String): String {
        return raw
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
            .replace("\\n", "\n")
            .replace("\\r", "\r")
            .replace("\\t", "\t")
    }

    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, StandardCharsets.UTF_8)
    }
}
