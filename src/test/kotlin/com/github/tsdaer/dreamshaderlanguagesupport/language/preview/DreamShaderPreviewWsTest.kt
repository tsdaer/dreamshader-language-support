package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import com.github.tsdaer.dreamshaderlanguagesupport.language.settings.DreamShaderProjectSettings
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Milestone M12 A7: the WebSocket preview transport - frame decoding (tag 1/2/3
 * envelopes and the 24-byte RGBA8 raw header), the client's outbound JSON contract,
 * and a local mock WebSocket server round-trip.
 */
class DreamShaderPreviewWsTest : BasePlatformTestCase() {
    // ------------------------------------------------------------------
    // frame envelope
    // ------------------------------------------------------------------

    fun testDecodeJsonFrame() {
        val payload = """{"type":"previewResult","status":"ready"}""".toByteArray(Charsets.UTF_8)
        val frame = DreamShaderPreviewFrameDecoder.decodeFrame(
            DreamShaderPreviewFrameDecoder.encodeFrame(DreamShaderPreviewFrameDecoder.TAG_JSON, payload),
        )
        assertTrue(frame is DreamShaderPreviewFrameDecoder.Frame.Json)
        assertEquals(payload.decodeToString(), (frame as DreamShaderPreviewFrameDecoder.Frame.Json).text)
    }

    fun testDecodePngFrame() {
        val payload = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47)
        val frame = DreamShaderPreviewFrameDecoder.decodeFrame(
            DreamShaderPreviewFrameDecoder.encodeFrame(DreamShaderPreviewFrameDecoder.TAG_PNG, payload),
        )
        assertTrue(frame is DreamShaderPreviewFrameDecoder.Frame.Png)
        assertTrue(payload.contentEquals((frame as DreamShaderPreviewFrameDecoder.Frame.Png).bytes))
    }

    fun testDecodeMalformedFramesReturnsNull() {
        assertNull(DreamShaderPreviewFrameDecoder.decodeFrame(byteArrayOf(1, 2)))
        // length claims more bytes than present
        val buffer = ByteBuffer.allocate(9).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(100)
        buffer.put(DreamShaderPreviewFrameDecoder.TAG_JSON.toByte())
        buffer.put(byteArrayOf(1, 2, 3, 4))
        assertNull(DreamShaderPreviewFrameDecoder.decodeFrame(buffer.array()))
    }

    // ------------------------------------------------------------------
    // raw RGBA8 frame (tag 3)
    // ------------------------------------------------------------------

    private fun rawFrameBytes(width: Int, height: Int, flags: Int, frameIndex: Long): ByteArray {
        val header = ByteBuffer.allocate(DreamShaderPreviewFrameDecoder.RAW_HEADER_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
        header.put(DreamShaderPreviewFrameDecoder.RAW_VERSION.toByte())
        header.put(DreamShaderPreviewFrameDecoder.RAW_FORMAT_RGBA8.toByte())
        header.putShort(width.toShort())
        header.putShort(height.toShort())
        header.putShort(flags.toShort())
        header.putInt(frameIndex.toInt())
        header.putFloat(-157.5f)
        header.putFloat(-11.25f)
        header.putInt(7)
        val pixels = ByteArray(width * height * 4) { index -> ((index * 7) and 0xFF).toByte() }
        return DreamShaderPreviewFrameDecoder.encodeFrame(
            DreamShaderPreviewFrameDecoder.TAG_RAW_FRAME,
            header.array() + pixels,
        )
    }

    fun testDecodeRawFrameHeader() {
        val frame = DreamShaderPreviewFrameDecoder.decodeFrame(rawFrameBytes(4, 3, DreamShaderPreviewFrameDecoder.FLAG_PROBE_ACTIVE or DreamShaderPreviewFrameDecoder.FLAG_KEYFRAME, 42))
        assertTrue(frame is DreamShaderPreviewFrameDecoder.Frame.Raw)
        val raw = (frame as DreamShaderPreviewFrameDecoder.Frame.Raw).frame
        assertEquals(4, raw.width)
        assertEquals(3, raw.height)
        assertEquals(42, raw.frameIndex)
        assertEquals(7L, raw.probeLine)
        assertEquals(-157.5f, raw.orbitYaw)
        assertEquals(-11.25f, raw.orbitPitch)
        assertTrue(raw.probeActive)
        assertTrue(raw.keyframe)
        assertFalse(raw.compiling)
    }

    fun testRawFramePaintsBufferedImage() {
        val frame = DreamShaderPreviewFrameDecoder.decodeFrame(rawFrameBytes(2, 2, 0, 1))
        val raw = (frame as DreamShaderPreviewFrameDecoder.Frame.Raw).frame
        val image = raw.toBufferedImage()
        assertNotNull(image)
        assertEquals(2, image!!.width)
        assertEquals(2, image.height)
    }

    fun testRawFrameRejectsTruncatedHeader() {
        val header = ByteBuffer.allocate(DreamShaderPreviewFrameDecoder.RAW_HEADER_BYTES - 1)
            .order(ByteOrder.LITTLE_ENDIAN)
        repeat(DreamShaderPreviewFrameDecoder.RAW_HEADER_BYTES - 1) { header.put(0.toByte()) }
        val frame = DreamShaderPreviewFrameDecoder.decodeFrame(
            DreamShaderPreviewFrameDecoder.encodeFrame(DreamShaderPreviewFrameDecoder.TAG_RAW_FRAME, header.array()),
        )
        assertNull("payload shorter than the 24-byte header must be rejected", frame)
    }

    fun testRawFrameWithUnknownFormatYieldsNoImage() {
        val header = ByteBuffer.allocate(DreamShaderPreviewFrameDecoder.RAW_HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        header.put(1.toByte())
        header.put(9.toByte()) // unknown format
        header.putShort(2)
        header.putShort(2)
        header.putShort(0)
        header.putInt(0)
        header.putFloat(0f)
        header.putFloat(0f)
        header.putInt(0)
        val frame = DreamShaderPreviewFrameDecoder.decodeFrame(
            DreamShaderPreviewFrameDecoder.encodeFrame(DreamShaderPreviewFrameDecoder.TAG_RAW_FRAME, header.array()),
        )
        assertTrue(frame is DreamShaderPreviewFrameDecoder.Frame.Raw)
        assertNull((frame as DreamShaderPreviewFrameDecoder.Frame.Raw).frame.toBufferedImage())
    }

    // ------------------------------------------------------------------
    // client message contract
    // ------------------------------------------------------------------

    fun testClientMessagesMatchUpstreamContract() {
        val client = RecordingSendClient(17864)
        client.sendPreviewMaterial("C:/Proj/DShader/M_Shine.dsm", "sphere", "req-1", 4.0, "raw")
        assertEquals(
            """{"type":"previewMaterial","sourceFile":"C:/Proj/DShader/M_Shine.dsm","mesh":"sphere","requestId":"req-1","stream":true,"frameRate":4.0,"encoding":"raw"}""",
            client.lastMessage,
        )

        client.sendPreviewControl("req-1", false, 0.0)
        assertEquals(
            """{"type":"previewControl","requestId":"req-1","stream":false,"frameRate":0.0}""",
            client.lastMessage,
        )

        client.sendProbe(12)
        assertEquals("""{"type":"setProbe","line":12}""", client.lastMessage)

        client.clearProbe()
        assertEquals("""{"type":"clearProbe"}""", client.lastMessage)
    }

    private class RecordingSendClient(port: Int) : DreamShaderPreviewWsClient(EmptyListener, port) {
        var lastMessage: String? = null

        override fun send(json: String): Boolean {
            lastMessage = json
            return true
        }
    }

    private object EmptyListener : DreamShaderPreviewWsClient.Listener {
        override fun onResult(json: String) {}
        override fun onFrame(image: BufferedImage?) {}
        override fun onProbeState(json: String) {}
    }

    // ------------------------------------------------------------------
    // mock server round-trip (java.net.http client against a local server)
    // ------------------------------------------------------------------

    fun testMockServerRoundTripDeliversRawFrame() {
        val server = MockPreviewServer()
        // The bridge answers a previewMaterial with a tag-1 previewResult followed by a
        // tag-3 raw frame - both as binary envelope frames on the same connection.
        server.onClientMessage = { json ->
            if (json.contains("previewMaterial")) {
                server.sendJsonFrame(
                    """{"type":"previewResult","status":"ready","sourceFile":"mock.dsm"}""",
                )
                server.sendRawFrame(rawFrameBytes(2, 1, 0, 0))
            }
        }
        server.start()
        try {
            val frames = mutableListOf<BufferedImage>()
            val results = mutableListOf<String>()
            val client = DreamShaderPreviewWsClient(
                listener = object : DreamShaderPreviewWsClient.Listener {
                    override fun onResult(json: String) {
                        results.add(json)
                    }

                    override fun onFrame(image: BufferedImage?) {
                        frames.add(image!!)
                    }

                    override fun onProbeState(json: String) {}
                },
                port = server.port,
            )
            assertTrue("expected the mock server to accept the connection", client.connect())
            client.send("""{"type":"previewMaterial","sourceFile":"mock.dsm","encoding":"raw"}""")

            val deadline = System.currentTimeMillis() + 5_000
            while (frames.isEmpty() && System.currentTimeMillis() < deadline) {
                Thread.sleep(25)
            }
            assertEquals("mock server answered previewResult", 1, results.size)
            assertEquals("mock server streamed one raw frame", 1, frames.size)
            assertEquals(2, frames.single().width)
            client.close()
        } finally {
            server.stop()
        }
    }

    fun testSettingsDriveTheTransportChoice() {
        val settings = project.getService(DreamShaderProjectSettings::class.java).state
        settings.previewTransport = "file"
        assertEquals("file", settings.previewTransport)
        settings.previewTransport = "websocket"
        settings.previewWebSocketPort = 17864
        assertEquals("websocket", settings.previewTransport)
        assertEquals(17864, settings.previewWebSocketPort)
    }
}
