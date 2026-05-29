package com.github.tsdaer.dreamshaderlanguagesupport.language.settings

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Component
import java.awt.Container

abstract class DreamShaderSettingsUiTestBase : BasePlatformTestCase() {
    protected fun <T : Component> findComponentByName(
        root: Container,
        name: String,
        type: Class<T>
    ): T {
        return findComponentByNameOrNull(root, name, type)
            ?: error("Component not found: $name (${type.simpleName})")
    }

    private fun <T : Component> findComponentByNameOrNull(
        root: Container,
        name: String,
        type: Class<T>
    ): T? {
        if (name == root.name && type.isInstance(root)) return type.cast(root)
        root.components.forEach { child ->
            if (name == child.name && type.isInstance(child)) return type.cast(child)
            if (child is Container) {
                val nested = findComponentByNameOrNull(child, name, type)
                if (nested != null) return nested
            }
        }
        return null
    }
}

