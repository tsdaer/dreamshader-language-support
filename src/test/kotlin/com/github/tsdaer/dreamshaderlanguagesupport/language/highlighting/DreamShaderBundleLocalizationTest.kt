package com.github.tsdaer.dreamshaderlanguagesupport.language.highlighting
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderBundleLocalizationTest : BasePlatformTestCase() {
    fun testChineseBundleContainsAllBaseKeys() {
        val base = readBundleKeys("messages/DreamShaderBundle.properties")
        val zh = readBundleKeys("messages/DreamShaderBundle_zh_CN.properties")
        val missing = base - zh
        assertTrue("Missing zh_CN bundle keys: ${missing.sorted()}", missing.isEmpty())
    }

    private fun readBundleKeys(resourcePath: String): Set<String> {
        val stream = javaClass.classLoader.getResourceAsStream(resourcePath)
        assertTrue("Resource not found: $resourcePath", stream != null)
        stream!!.bufferedReader(Charsets.UTF_8).use { reader ->
            return reader.lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("!") }
                .mapNotNull { line ->
                    val index = line.indexOf('=')
                    if (index <= 0) null else line.substring(0, index).trim()
                }
                .toSet()
        }
    }
}
