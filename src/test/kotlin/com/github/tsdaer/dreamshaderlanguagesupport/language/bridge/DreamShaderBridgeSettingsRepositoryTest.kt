package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class DreamShaderBridgeSettingsRepositoryTest : BasePlatformTestCase() {
    fun testParsesMappingsByKey() {
        val repository = project.getService(DreamShaderBridgeSettingsRepository::class.java)
        val json = """
            {
              "schema": "DreamShader.Settings",
              "version": 1,
              "mappings": {
                "ShadingModel": [
                  { "alias": "DefaultLit", "value": 1, "name": "MSM_DefaultLit", "displayName": "默认光照" },
                  { "alias": "Substrate", "value": 12, "name": "MSM_Strata", "displayName": "Substrate" }
                ],
                "BlendMode": [
                  { "alias": "Opaque", "value": 0, "name": "BLEND_Opaque", "displayName": "不透明" }
                ]
              }
            }
        """.trimIndent()

        val parsed = repository.parseSettingsJson(json)
        assertEquals(2, parsed.size)

        val shadingModel = parsed["shadingmodel"] ?: error("shadingmodel mapping missing")
        assertEquals(2, shadingModel.size)
        val defaultLit = shadingModel.first { it.alias == "DefaultLit" }
        assertEquals(1, defaultLit.value)
        assertEquals("MSM_DefaultLit", defaultLit.name)
        assertEquals("默认光照", defaultLit.displayName)

        val blendMode = parsed["blendmode"] ?: error("blendmode mapping missing")
        assertEquals(setOf("Opaque"), blendMode.map { it.alias }.toSet())
    }

    fun testReturnsEmptyWithoutMappings() {
        val repository = project.getService(DreamShaderBridgeSettingsRepository::class.java)
        assertTrue(repository.parseSettingsJson("").isEmpty())
        assertTrue(repository.parseSettingsJson("").isEmpty())
    }
}
