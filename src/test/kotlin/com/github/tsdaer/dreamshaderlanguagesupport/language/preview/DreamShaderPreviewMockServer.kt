package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import java.io.BufferedInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * A minimal loopback WebSocket server for the A7 integration test: performs the HTTP
 * upgrade handshake, reads the (masked) text frames the client sends, and can push a
 * binary preview frame - the bridge's `[u32 LE length][tag][payload]` envelope - to the
 * client. Single-connection, single test lifetime.
 */
internal class MockPreviewServer {
    private val socket = ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress())
    private val running = AtomicBoolean(false)
    private var clientSocket: Socket? = null
    private var output: DataOutputStream? = null
    val port: Int get() = socket.localPort

    fun start() {
        running.set(true)
        thread(name = "mock-preview-server", isDaemon = true) {
            runCatching {
                socket.accept()?.let { accepted ->
                    clientSocket = accepted
                    handshake(accepted)
                    output = DataOutputStream(accepted.getOutputStream())
                    readClientFrames(accepted)
                }
            }
        }
    }

    /** Invoked with each (unmasked) JSON text frame the client sends. */
    var onClientMessage: ((String) -> Unit)? = null

    /** Sends a binary WebSocket frame carrying a tag-1 JSON envelope (previewResult...). */
    fun sendJsonFrame(json: String) {
        pushBinary(DreamShaderPreviewFrameDecoder.encodeFrame(DreamShaderPreviewFrameDecoder.TAG_JSON, json.toByteArray(Charsets.UTF_8)))
    }

    /** Sends a binary WebSocket frame carrying the given bridge envelope payload. */
    fun sendRawFrame(envelope: ByteArray) {
        pushBinary(envelope)
    }

    private fun pushBinary(envelope: ByteArray) {
        val deadline = System.currentTimeMillis() + 5_000
        while (output == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        val stream = output ?: return
        stream.write(serverFrame(envelope))
        stream.flush()
    }

    fun stop() {
        running.set(false)
        runCatching { clientSocket?.close() }
        runCatching { socket.close() }
    }

    private fun handshake(socket: Socket) {
        val reader = BufferedInputStream(socket.getInputStream())
        val key = StringBuilder().let { headers ->
            val bytes = ByteArray(4096)
            val read = reader.read(bytes)
            String(bytes, 0, read).lineSequence()
                .firstOrNull { it.startsWith("Sec-WebSocket-Key:", ignoreCase = true) }
                ?.substringAfter(':')?.trim()
        } ?: throw IllegalStateException("no Sec-WebSocket-Key")
        val accept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + WS_GUID).toByteArray(Charsets.US_ASCII)),
        )
        val response = buildString {
            append("HTTP/1.1 101 Switching Protocols\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Accept: ").append(accept).append("\r\n")
            append("\r\n")
        }
        socket.getOutputStream().write(response.toByteArray(Charsets.US_ASCII))
        socket.getOutputStream().flush()
    }

    /** Reads and discards the client's masked text frames, keeping the connection alive. */
    private fun readClientFrames(socket: Socket) {
        val reader = BufferedInputStream(socket.getInputStream())
        while (running.get()) {
            val header = reader.readNBytes(2)
            if (header.size < 2 || header[0].toInt() and 0x0F == 0x8) break
            val masked = header[1].toInt() and 0x80 != 0
            var length = header[1].toInt() and 0x7F
            if (length == 126) {
                length = java.nio.ByteBuffer.wrap(reader.readNBytes(2)).short.toInt() and 0xFFFF
            } else if (length == 127) {
                length = java.nio.ByteBuffer.wrap(reader.readNBytes(8)).long.toInt()
            }
            val mask = if (masked) reader.readNBytes(4) else null
            val payload = reader.readNBytes(length)
            if (mask != null) {
                for (i in payload.indices) payload[i] = (payload[i].toInt() xor mask[i % 4].toInt()).toByte()
            }
            onClientMessage?.invoke(String(payload, Charsets.UTF_8))
            // Text frames carrying the client's requests are the bridge's business; the
            // test only needs the connection to stay open.
        }
    }

    private fun serverFrame(payload: ByteArray): ByteArray {
        val stream = java.io.ByteArrayOutputStream()
        stream.write(0x82) // FIN + binary opcode
        if (payload.size < 126) {
            stream.write(payload.size)
        } else {
            stream.write(126)
            stream.write((payload.size shr 8) and 0xFF)
            stream.write(payload.size and 0xFF)
        }
        stream.write(payload)
        return stream.toByteArray()
    }

    companion object {
        private const val WS_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
    }
}
