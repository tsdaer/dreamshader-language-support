package com.github.tsdaer.dreamshaderlanguagesupport.language.settings

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue

/**
 * B4 test debt: restored smoke-level coverage for the project settings UI (the suite deleted
 * in 0.0.7). The lifecycle — build, reset, isModified, apply, dispose — must run without
 * throwing on a bare test project.
 */
class DreamShaderSettingsConfigurableSmokeTest : DreamShaderSettingsUiTestBase() {

    fun testLifecycleCreateResetApplyDispose() {
        val configurable = DreamShaderSettingsConfigurable(project)

        val component = configurable.createComponent()
        assertNotNull(component, "createComponent must build the settings panel")
        assertTrue(component.componentCount > 0, "settings panel must have content")

        configurable.reset()
        assertFalse(configurable.isModified, "freshly reset UI must not report modifications")

        configurable.apply()
        assertFalse(configurable.isModified, "apply with an untouched UI must not report modifications")

        configurable.disposeUIResources()
    }

    fun testIsModifiedReflectsStateRoundTrip() {
        val configurable = DreamShaderSettingsConfigurable(project)
        configurable.createComponent()
        configurable.reset()
        assertFalse(configurable.isModified)

        val state = project.getService(DreamShaderProjectSettings::class.java).state
        val previousRoot = state.projectRoot
        state.projectRoot = "I:/Some/Other/Project"

        assertTrue(
            configurable.isModified,
            "changing the backing state must make the untouched UI count as modified"
        )

        state.projectRoot = previousRoot
        assertFalse(configurable.isModified, "restoring the state must realign UI and state")

        configurable.reset()
        assertFalse(configurable.isModified, "reset after a round-trip stays clean")

        configurable.disposeUIResources()
    }
}
