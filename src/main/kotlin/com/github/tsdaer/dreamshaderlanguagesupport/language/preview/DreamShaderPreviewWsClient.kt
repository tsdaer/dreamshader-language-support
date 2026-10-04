package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import java.net.URI
import java.net.http.HttpClient
import java.net.http.WebSocket
import java.nio.ByteBuffer
import java.util.concurrent.CompletionStage
import java.util.concurrent.atomic.AtomicReference

/**
 * The live-preview WebSocket client (M12 A7): connects to the editor bridge's
 * loopback-only server (`ws://127.0.0.1:{port}`, the bridge's own WebSocket server),
 * sends plain UTF-8 JSON messages (`previewMaterial` / `previewControl` / `setProbe` /
 * `clearProbe`) and consumes the binary frames the editor answers with.
 *
 * The `png` encoding pairs a tag-1 `previewFrame` JSON message with the tag-2 PNG that
 * follows it (arrival order on the same connection); `raw` frames (tag 3) are
 * self-describing and need no pairing. The file-transport preview path is untouched -
 * this client is opt-in through the `previewTransport` setting.
 */
internal open class DreamShaderPreviewWsClient(
    private val listener: Listener,
    private val port: Int,
) : WebSocket.Listener {
    interface Listener {
        /** Session result (`previewResult` JSON message). */
        fun onResult(json: String)

        /** A decoded frame, ready to paint. */
        fun onFrame(image: java.awt.image.BufferedImage?)

        /** Probe state after any set/clear or regeneration move. */
        fun onProbeState(json: String)

        fun onClosed() {}
        fun onError(message: String) {}
    }

    private val webSocketRef = AtomicReference<WebSocket?>(null)

    /** True while a `previewFrame` JSON awaits its PNG (the png-encoding pairing rule). */
    private var awaitingPng = false

    @Volatile
    private var closed = false

    fun connect(): Boolean {
        if (webSocketRef.get() != null) return true
        return runCatching {
            val client = HttpClient.newHttpClient()
            client.newWebSocketBuilder()
                .buildAsync(URI.create("ws://127.0.0.1:$port"), this)
                .whenComplete { ws, error ->
                    if (error != null) {
                        listener.onError(error.message ?: "connect failed")
                    } else {
                        webSocketRef.compareAndSet(null, ws)
                    }
                }
            true
        }.getOrDefault(false)
    }

    /** Sends a plain UTF-8 JSON message (the inbound direction has no framing). */
    open fun send(json: String): Boolean {
        // The handshake completes asynchronously; give it a short grace period so the
        // first previewMaterial right after connect() is not silently dropped.
        val deadline = System.currentTimeMillis() + 2_000
        while (webSocketRef.get() == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(25)
        }
        val ws = webSocketRef.get() ?: return false
        return runCatching {
            ws.sendText(json, true)
            true
        }.getOrDefault(false)
    }

    fun sendPreviewMaterial(sourceFile: String, mesh: String, requestId: String, frameRate: Double, encoding: String = "raw"): Boolean {
        return send(
            buildString {
                append("{\"type\":\"previewMaterial\",\"sourceFile\":\"").append(escape(sourceFile))
                append("\",\"mesh\":\"").append(escape(mesh))
                append("\",\"requestId\":\"").append(escape(requestId))
                append("\",\"stream\":true,\"frameRate\":").append(frameRate)
                append(",\"encoding\":\"").append(encoding).append("\"}")
            }
        )
    }

    fun sendPreviewControl(requestId: String, streaming: Boolean, frameRate: Double) {
        send(
            buildString {
                append("{\"type\":\"previewControl\",\"requestId\":\"").append(escape(requestId))
                append("\",\"stream\":").append(streaming)
                append(",\"frameRate\":").append(frameRate).append("}")
            }
        )
    }

    /** Attaches the Graph probe at the editor's caret line ("editor line linkage"). */
    fun sendProbe(line: Int, name: String = "") {
        send(buildString {
            append("{\"type\":\"setProbe\",\"line\":").append(line.coerceAtLeast(0))
            if (name.isNotBlank()) append(",\"name\":\"").append(escape(name)).append("\"")
            append("}")
        })
    }

    fun clearProbe() {
        send("{\"type\":\"clearProbe\"}")
    }

    fun close() {
        closed = true
        webSocketRef.getAndSet(null)?.sendClose(WebSocket.NORMAL_CLOSURE, "client closed")
    }

    override fun onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage<*>? {
        val text = data.toString()
        when {
            text.contains("\"previewResult\"") -> listener.onResult(text)
            text.contains("\"probeState\"") -> listener.onProbeState(text)
            text.contains("\"previewFrame\"") -> awaitingPng = true
        }
        webSocket.request(1)
        return null
    }

    override fun onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage<*>? {
        val bytes = ByteArray(data.remaining()).also { data.get(it) }
        when (val frame = DreamShaderPreviewFrameDecoder.decodeFrame(bytes)) {
            is DreamShaderPreviewFrameDecoder.Frame.Json -> listener.onResult(frame.text)
            is DreamShaderPreviewFrameDecoder.Frame.Png -> {
                awaitingPng = false
                val image = runCatching { javax.imageio.ImageIO.read(frame.bytes.inputStream()) }.getOrNull()
                listener.onFrame(image)
            }
            is DreamShaderPreviewFrameDecoder.Frame.Raw -> {
                awaitingPng = false
                listener.onFrame(frame.frame.toBufferedImage())
            }
            null -> listener.onError("malformed preview frame")
        }
        webSocket.request(1)
        return null
    }

    override fun onClose(webSocket: WebSocket, statusCode: Int, reason: String?): CompletionStage<*>? {
        webSocketRef.compareAndSet(webSocket, null)
        listener.onClosed()
        return null
    }

    override fun onError(webSocket: WebSocket, error: Throwable) {
        listener.onError(error.message ?: "websocket error")
    }

    private fun escape(value: String): String =
        buildString {
            value.forEach { ch ->
                when (ch) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> append(ch)
                }
            }
        }

    companion object {
        /** Default loopback port; the bridge server refuses non-loopback binds anyway. */
        const val DEFAULT_PORT: Int = 17864
    }
}
