package com.github.tsdaer.dreamshaderlanguagesupport.language.bridge

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B4 test debt: the Bridge file watcher's event-classification kernel, extracted from
 * `DreamShaderBridgeFileWatcher` so it can be tested without the platform VFS.
 */
class DreamShaderBridgeEventFilterTest {

    @Test
    fun knownBridgeArtifactsTriggerRefresh() {
        for (name in DreamShaderBridgeEventFilter.BRIDGE_FILE_NAMES) {
            assertTrue(
                DreamShaderBridgeEventFilter.isBridgeFileEvent("I:/Proj/Saved/DreamShader/Bridge/$name"),
                "expected $name to be a bridge file"
            )
        }
    }

    @Test
    fun previewPngFramesTriggerRefresh() {
        assertTrue(
            DreamShaderBridgeEventFilter.isBridgeFileEvent("I:/Proj/Saved/DreamShader/Bridge/Preview/M_Red_0.png")
        )
        assertTrue(
            DreamShaderBridgeEventFilter.isBridgeFileEvent("I:/Proj/Saved/DreamShader/Bridge/Preview/frame.PNG")
        )
    }

    @Test
    fun unrelatedPathsAreIgnored() {
        assertFalse(
            DreamShaderBridgeEventFilter.isBridgeFileEvent("I:/Proj/Saved/DreamShader/Bridge/Preview/frame.txt")
        )
        assertFalse(
            DreamShaderBridgeEventFilter.isBridgeFileEvent("I:/Proj/Saved/DreamShader/Bridge/unknown.json")
        )
        assertFalse(
            DreamShaderBridgeEventFilter.isBridgeFileEvent("I:/Proj/Saved/DreamShader/Other/diagnostics.json")
        )
        assertFalse(
            DreamShaderBridgeEventFilter.isBridgeFileEvent("I:/Proj/Content/Materials/M_Red.dsm")
        )
        assertFalse(DreamShaderBridgeEventFilter.isBridgeFileEvent("diagnostics.json"))
    }

    @Test
    fun windowsSeparatorsAreNormalized() {
        assertTrue(
            DreamShaderBridgeEventFilter.isBridgeFileEvent("I:\\Proj\\Saved\\DreamShader\\Bridge\\bridge.db")
        )
        assertTrue(
            DreamShaderBridgeEventFilter.isBridgeFileEvent("I:\\Proj\\Saved\\DreamShader\\Bridge\\Preview\\f.png")
        )
    }
}
