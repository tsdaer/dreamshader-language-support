package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge
import com.github.tsdaer.dreamshaderlanguagesupport.language.core.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.lexer.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.parser.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.editor.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.navigation.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.diagnostics.*
import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.*

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue

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
