package com.github.tsdaer.dreamshaderlanguagesupport.language

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.PROJECT)
@State(
    name = "DreamShaderProjectSettings",
    storages = [Storage("dreamshader-language-support.xml")]
)
class DreamShaderProjectSettings : PersistentStateComponent<DreamShaderProjectSettings.State> {
    data class State(
        var projectRoot: String = "",
        var materialExpressionManifestPath: String = "",
        var showStatusBar: Boolean = true,
        var enableCodeLens: Boolean = true
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }
}
