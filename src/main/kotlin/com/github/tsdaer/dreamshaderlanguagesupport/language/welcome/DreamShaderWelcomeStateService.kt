package com.github.tsdaer.dreamshaderlanguagesupport.language.welcome

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@Service(Service.Level.APP)
@State(
    name = "DreamShaderWelcomeState",
    storages = [Storage("dreamshader-language-support-welcome.xml")]
)
class DreamShaderWelcomeStateService : PersistentStateComponent<DreamShaderWelcomeStateService.State> {
    data class State(
        var lastSeenPluginVersion: String = ""
    )

    enum class WelcomeReason {
        FIRST_INSTALL,
        UPDATED,
        MANUAL
    }

    data class WelcomeDecision(
        val reason: WelcomeReason,
        val previousVersion: String?,
        val currentVersion: String
    )

    private var state = State()

    @Volatile
    private var shownForVersionInSession: String? = null

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    @Synchronized
    fun currentRecordedVersion(): String? {
        return state.lastSeenPluginVersion.trim().ifBlank { null }
    }

    @Synchronized
    fun decideAndMark(currentVersion: String): WelcomeDecision? {
        val normalizedCurrent = currentVersion.trim()
        if (normalizedCurrent.isEmpty()) return null

        if (shownForVersionInSession == normalizedCurrent) return null

        val previous = state.lastSeenPluginVersion.trim()
        if (previous == normalizedCurrent) {
            shownForVersionInSession = normalizedCurrent
            return null
        }

        val reason = if (previous.isBlank()) {
            WelcomeReason.FIRST_INSTALL
        } else {
            WelcomeReason.UPDATED
        }

        state.lastSeenPluginVersion = normalizedCurrent
        shownForVersionInSession = normalizedCurrent

        return WelcomeDecision(
            reason = reason,
            previousVersion = previous.ifBlank { null },
            currentVersion = normalizedCurrent
        )
    }
}
