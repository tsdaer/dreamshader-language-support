package com.github.tsdaer.dreamshaderlanguagesupport.language.packages

import com.github.tsdaer.dreamshaderlanguagesupport.language.core.DreamShaderBundle
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import java.io.File
import java.net.URI
import java.util.*

/**
 * 包商店服务。
 *
 * 负责索引源增删、商店数据加载与关键词搜索（含 GitHub 搜索入口）。
 */
@Service(Service.Level.PROJECT)
/**
 * Service implementation for DreamShaderPackageStoreService.
 */
internal class DreamShaderPackageStoreService(private val project: Project) {
    fun addIndexSource(source: String): DreamShaderPackageSourceMutationResult {
        val normalized = normalizeSource(source) ?: return DreamShaderPackageSourceMutationResult(
            changed = false,
            message = DreamShaderBundle.message("package.store.invalidSource")
        )
        val settings = project.getService(DreamShaderProjectSettings::class.java)
        val state = settings.state

        val existingNormalized = currentConfiguredSources(state).mapNotNull { normalizeSource(it) }.toMutableSet()
        if (normalized in existingNormalized) {
            return DreamShaderPackageSourceMutationResult(
                changed = false,
                message = DreamShaderBundle.message("package.store.duplicateSource", normalized)
            )
        }

        val updated = if (state.packageStoreIndexUrls.isEmpty() && state.packageStoreIndexUrl.isNotBlank()) {
            val merged = currentConfiguredSources(state).toMutableList()
            merged.add(normalized)
            merged
        } else {
            state.packageStoreIndexUrls.toMutableList().also { it.add(normalized) }
        }

        state.packageStoreIndexUrls = deduplicate(updated).toMutableList()
        state.packageStoreIndexUrl = ""
        return DreamShaderPackageSourceMutationResult(
            changed = true,
            message = DreamShaderBundle.message("package.store.addedSource", normalized)
        )
    }

    fun removeIndexSource(source: String): DreamShaderPackageSourceMutationResult {
        val normalized = normalizeSource(source) ?: return DreamShaderPackageSourceMutationResult(
            changed = false,
            message = DreamShaderBundle.message("package.store.invalidSource")
        )

        val settings = project.getService(DreamShaderProjectSettings::class.java)
        val state = settings.state
        val before = currentConfiguredSources(state)
        val after = before.filterNot { normalizeSource(it) == normalized }
        if (after.size == before.size) {
            return DreamShaderPackageSourceMutationResult(
                changed = false,
                message = DreamShaderBundle.message("package.store.sourceNotFound", normalized)
            )
        }

        state.packageStoreIndexUrls = deduplicate(after).toMutableList()
        state.packageStoreIndexUrl = ""
        return DreamShaderPackageSourceMutationResult(
            changed = true,
            message = DreamShaderBundle.message("package.store.removedSource", normalized)
        )
    }

    fun loadStore(query: String? = null): DreamShaderPackageStoreSnapshot {
        val sources = DreamShaderPackageIndexLoader.resolveIndexSources(project)
        val result = DreamShaderPackageIndexLoader.loadFromSources(sources)
        val filtered = filterEntries(result.entries, query)
        return DreamShaderPackageStoreSnapshot(
            sources = sources,
            entries = filtered,
            errors = result.errors
        )
    }

    fun searchGitHubPackages(query: String): DreamShaderGitHubSearchResult {
        val token = project.getService(DreamShaderProjectSettings::class.java).state.packageStoreGitHubToken
        return DreamShaderGitHubPackageSearch.searchDreamShaderPackages(query, token)
    }

    private fun filterEntries(
        entries: List<DreamShaderPackageIndexEntry>,
        query: String?
    ): List<DreamShaderPackageIndexEntry> {
        val q = query?.trim().orEmpty()
        if (q.isBlank()) return entries.sortedBy { it.name.lowercase(Locale.ROOT) }
        val lowered = q.lowercase(Locale.ROOT)
        return entries.filter { entry ->
            entry.name.lowercase(Locale.ROOT).contains(lowered) ||
                entry.displayName?.lowercase(Locale.ROOT)?.contains(lowered) == true ||
                entry.description?.lowercase(Locale.ROOT)?.contains(lowered) == true ||
                entry.tags.any { it.lowercase(Locale.ROOT).contains(lowered) }
        }.sortedBy { it.name.lowercase(Locale.ROOT) }
    }

    private fun currentConfiguredSources(state: DreamShaderProjectSettings.State): List<String> {
        val multi = state.packageStoreIndexUrls.map { it.trim() }.filter { it.isNotBlank() }
        if (multi.isNotEmpty()) return deduplicate(multi)
        val legacy = state.packageStoreIndexUrl.trim()
        return if (legacy.isBlank()) emptyList() else listOf(legacy)
    }

    private fun deduplicate(values: List<String>): List<String> {
        val seen = linkedSetOf<String>()
        values.forEach { value ->
            val normalized = normalizeSource(value) ?: return@forEach
            seen.add(normalized)
        }
        return seen.toList()
    }

    private fun normalizeSource(source: String): String? {
        val trimmed = source.trim()
        if (trimmed.isBlank()) return null
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            return trimmed.trimEnd('/')
        }
        if (trimmed.startsWith("file://", ignoreCase = true)) {
            val file = runCatching { File(URI(trimmed)) }.getOrNull() ?: return null
            return normalizePath(file.path)
        }
        return normalizePath(trimmed)
    }

    private fun normalizePath(path: String): String {
        return path.replace('\\', '/').trimEnd('/')
    }
}

/**
 * 包商店快照（源列表、条目列表、错误列表）。
 */
internal data class DreamShaderPackageStoreSnapshot(
    val sources: List<String>,
    val entries: List<DreamShaderPackageIndexEntry>,
    val errors: List<DreamShaderPackageIndexLoadError>
)

/**
 * 索引源变更结果。
 */
internal data class DreamShaderPackageSourceMutationResult(
    val changed: Boolean,
    val message: String
)
