package com.github.tsdaer.dreamshaderlanguagesupport.language.settings

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "DreamShaderApplicationSettings",
    storages = [Storage("dreamshader-app-settings.xml")]
)
class DreamShaderApplicationSettings : PersistentStateComponent<DreamShaderApplicationSettings.State> {
    data class State(
        var packageStoreIndexUrls: MutableList<String> = mutableListOf(),
        var packageStoreIndexUrl: String = "",
        var packageStoreGitHubToken: String = "",
        var enableGitHubPackageSearch: Boolean = true,
        var bridgeRecompileCurrentCommand: String = "",
        var bridgeRecompileAllCommand: String = "",
        var bridgeCleanGeneratedShadersCommand: String = ""
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
