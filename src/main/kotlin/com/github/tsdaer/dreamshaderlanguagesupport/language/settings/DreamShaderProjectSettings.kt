package com.github.tsdaer.dreamshaderlanguagesupport.language.settings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.project.Project

@Service(Service.Level.PROJECT)
@State(
    name = "DreamShaderProjectSettings",
    storages = [Storage("dreamshader-language-support.xml")]
)
class DreamShaderProjectSettings : PersistentStateComponent<DreamShaderProjectSettings.State> {
    data class State(
        var projectRoot: String = "",
        var materialExpressionManifestPath: String = "",
        var unrealEngineSourceRoot: String = "",
        var materialExpressionScanEnabled: Boolean = false,
        var materialExpressionScanCachePath: String = "",
        var showStatusBar: Boolean = true,
        var enableCodeLens: Boolean = true,
        var enableInlayParameterHints: Boolean = true,
        var outArgumentPlaceholderSuffix: String = "Out",
        var preferredImportExtension: String = "dsh",
        var autoUpdatePreferredImportExtension: Boolean = false,
        var packageStoreIndexUrls: MutableList<String> = mutableListOf(),
        var packageStoreIndexUrl: String = "",
        var packageStoreGitHubToken: String = "",
        var enableGitHubPackageSearch: Boolean = true,
        var hoverDocumentationOverrides: String = "",
        var bridgeRecompileCurrentCommand: String = "",
        var bridgeRecompileAllCommand: String = "",
        var bridgeCleanGeneratedShadersCommand: String = "",
        var previewTransport: String = "file",
        var previewWebSocketPort: Int = 17864,
        var previewLiveFrameRate: Int = 2,
        var previewAutoRefreshDelayMs: Int = 1200,
        var sourceDirectory: String = "DShader"
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    fun resolvedGitHubToken(): String {
        return state.packageStoreGitHubToken.takeIf { it.isNotBlank() }
            ?: appSettings().state.packageStoreGitHubToken
    }

    fun resolvedPackageStoreIndexUrls(): List<String> {
        val projectUrls = state.packageStoreIndexUrls.filter { it.isNotBlank() }
        if (projectUrls.isNotEmpty()) return projectUrls
        val appUrls = appSettings().state.packageStoreIndexUrls.filter { it.isNotBlank() }
        if (appUrls.isNotEmpty()) return appUrls
        val fallback = state.packageStoreIndexUrl.takeIf { it.isNotBlank() }
            ?: appSettings().state.packageStoreIndexUrl.takeIf { it.isNotBlank() }
        return fallback?.let { listOf(it) }.orEmpty()
    }

    fun resolvedBridgeRecompileCurrentCommand(): String {
        return state.bridgeRecompileCurrentCommand.takeIf { it.isNotBlank() }
            ?: appSettings().state.bridgeRecompileCurrentCommand
    }

    fun resolvedBridgeRecompileAllCommand(): String {
        return state.bridgeRecompileAllCommand.takeIf { it.isNotBlank() }
            ?: appSettings().state.bridgeRecompileAllCommand
    }

    fun resolvedBridgeCleanGeneratedShadersCommand(): String {
        return state.bridgeCleanGeneratedShadersCommand.takeIf { it.isNotBlank() }
            ?: appSettings().state.bridgeCleanGeneratedShadersCommand
    }

    private fun appSettings(): DreamShaderApplicationSettings =
        ApplicationManager.getApplication().getService(DreamShaderApplicationSettings::class.java)
}
