package com.github.tsdaer.dreamshaderlanguagesupport.language.settings
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.PROJECT)
@State(
    name = "DreamShaderProjectSettings",
    storages = [Storage("dreamshader-language-support.xml")]
)
/**
 * Persistent per-project settings for DreamShader language support.
 *
 * These values are the single source of truth for runtime feature switches
 * (Bridge integration, CodeLens/status visibility) and package index sources.
 */
class DreamShaderProjectSettings : PersistentStateComponent<DreamShaderProjectSettings.State> {
    /**
     * Serialized state stored in `dreamshader-language-support.xml`.
     */
    data class State(
        /** Optional explicit project root used by Bridge path resolution. */
        var projectRoot: String = "",
        /** Optional explicit manifest path for `UE.Expression(Class="...")` completion. */
        var materialExpressionManifestPath: String = "",
        /** Unreal Engine source root scanned for `UMaterialExpression` headers. */
        var unrealEngineSourceRoot: String = "",
        /** Whether best-effort source scanning of material expressions is enabled. */
        var materialExpressionScanEnabled: Boolean = false,
        /** Optional path where scanned material-expression cache JSON is stored. */
        var materialExpressionScanCachePath: String = "",
        /** UI toggle for future status bar integration. */
        var showStatusBar: Boolean = true,
        /** UI toggle for future CodeLens integration. */
        var enableCodeLens: Boolean = true,
        /** Suffix used when generating placeholder variable names for missing out arguments. */
        var outArgumentPlaceholderSuffix: String = "Out",
        /** Preferred replacement extension for unsupported import-extension quick fixes. */
        var preferredImportExtension: String = "dsh",
        /** Whether applying import-extension quick-fix should update preferred extension. */
        var autoUpdatePreferredImportExtension: Boolean = false,
        /** Preferred package index sources (multi-source mode). */
        var packageStoreIndexUrls: MutableList<String> = mutableListOf(),
        /** Backward-compatible single source setting used when list mode is empty. */
        var packageStoreIndexUrl: String = "",
        /** Optional GitHub token for package search API requests. */
        var packageStoreGitHubToken: String = "",
        /** Optional hover doc override entries, one per line: key=value. */
        var hoverDocumentationOverrides: String = "",
        /** External command template for recompiling current DreamShader asset. */
        var bridgeRecompileCurrentCommand: String = "",
        /** External command template for recompiling all DreamShader assets. */
        var bridgeRecompileAllCommand: String = "",
        /** External command template for cleaning generated DreamShader shaders/assets. */
        var bridgeCleanGeneratedShadersCommand: String = ""
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
