package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderStatusBarVisibilityTest : BasePlatformTestCase() {
    fun testStatusBarVisibilitySetting() {
        val factory = DreamShaderBridgeStatusBarWidgetFactory()
        val settings = project.getService(DreamShaderProjectSettings::class.java).state

        settings.showStatusBar = true
        assertTrue("Expected status bar widget to be available when showStatusBar=true", factory.isAvailable(project))

        settings.showStatusBar = false
        assertFalse("Expected status bar widget to be unavailable when showStatusBar=false", factory.isAvailable(project))
    }
}
