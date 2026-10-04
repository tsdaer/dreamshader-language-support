package com.github.tsdaer.dreamshaderlanguagesupport.language.preview

import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Decoder for the editor bridge WebSocket preview framing (M12 A7, upstream
 * `Docs/tools/bridge.md` "Framing"): every outbound (editor -> client) message is a
 * binary frame `[u32 LE length][u8 tag][payload]` where length counts the tag byte,
 * tag 1 is UTF-8 JSON, tag 2 a raw PNG and tag 3 a self-describing RGBA8 raw frame
 * (24-byte header + `width * height * 4` pixels, alpha always 255).
 *
 * Inbound (client -> editor) messages are plain UTF-8 JSON with no framing at all.
 */
internal object DreamShaderPreviewFrameDecoder {
    const val TAG_JSON: Int = 1
    const val TAG_PNG: Int = 2
    const val TAG_RAW_FRAME: Int = 3

    const val RAW_HEADER_BYTES: Int = 24
    const val RAW_FORMAT_RGBA8: Int = 1
    const val RAW_VERSION: Int = 1

    const val FLAG_COMPILING: Int = 1 shl 0
    const val FLAG_PROBE_ACTIVE: Int = 1 shl 1
    const val FLAG_PROBE_PENDING: Int = 1 shl 2
    const val FLAG_KEYFRAME: Int = 1 shl 3

    data class RawFrame(
        val version: Int,
        val format: Int,
        val width: Int,
        val height: Int,
        val flags: Int,
        val frameIndex: Long,
        val orbitYaw: Float,
        val orbitPitch: Float,
        val probeLine: Long,
        val pixels: ByteArray,
    ) {
        val compiling: Boolean get() = flags and FLAG_COMPILING != 0
        val probeActive: Boolean get() = flags and FLAG_PROBE_ACTIVE != 0
        val probePending: Boolean get() = flags and FLAG_PROBE_PENDING != 0
        val keyframe: Boolean get() = flags and FLAG_KEYFRAME != 0

        fun toBufferedImage(): BufferedImage? {
            if (format != RAW_FORMAT_RGBA8 || width <= 0 || height <= 0) return null
            val expected = width * height * 4
            if (pixels.size < expected) return null
            val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
            var index = 0
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val r = pixels[index].toInt() and 0xFF
                    val g = pixels[index + 1].toInt() and 0xFF
                    val b = pixels[index + 2].toInt() and 0xFF
                    val a = pixels[index + 3].toInt() and 0xFF
                    image.setRGB(x, y, (a shl 24) or (r shl 16) or (g shl 8) or b)
                    index += 4
                }
            }
            return image
        }
    }

    sealed class Frame {
        class Json(val text: String) : Frame()
        class Png(val bytes: ByteArray) : Frame()
        class Raw(val frame: RawFrame) : Frame()
    }

    /** Payload bytes following the tag (or null when the envelope is malformed). */
    fun decodeFrame(binary: ByteArray): Frame? {
        if (binary.size < 5) return null
        val buffer = ByteBuffer.wrap(binary).order(ByteOrder.LITTLE_ENDIAN)
        val length = buffer.int.toLong() and 0xFFFFFFFFL
        val tag = binary[4].toInt() and 0xFF
        if (length < 1 || binary.size.toLong() < length) return null
        val payloadLength = (length - 1).toInt()
        if (binary.size - 5 < payloadLength) return null
        val payload = binary.copyOfRange(5, 5 + payloadLength)
        return when (tag) {
            TAG_JSON -> Frame.Json(String(payload, Charsets.UTF_8))
            TAG_PNG -> Frame.Png(payload)
            TAG_RAW_FRAME -> decodeRawFrame(payload)
            else -> null
        }
    }

    private fun decodeRawFrame(payload: ByteArray): Frame? {
        if (payload.size < RAW_HEADER_BYTES) return null
        val header = ByteBuffer.wrap(payload).order(ByteOrder.LITTLE_ENDIAN)
        val version = header.get().toInt() and 0xFF
        val format = header.get().toInt() and 0xFF
        val width = header.short.toInt() and 0xFFFF
        val height = header.short.toInt() and 0xFFFF
        val flags = header.short.toInt() and 0xFFFF
        val frameIndex = header.int.toLong() and 0xFFFFFFFFL
        val orbitYaw = header.float
        val orbitPitch = header.float
        val probeLine = header.int.toLong() and 0xFFFFFFFFL
        val pixels = payload.copyOfRange(RAW_HEADER_BYTES, payload.size)
        return Frame.Raw(
            RawFrame(
                version = version,
                format = format,
                width = width,
                height = height,
                flags = flags,
                frameIndex = frameIndex,
                orbitYaw = orbitYaw,
                orbitPitch = orbitPitch,
                probeLine = probeLine,
                pixels = pixels,
            )
        )
    }

    /** Builds the client-side frame envelope; used by tests and (rarely) outbound needs. */
    fun encodeFrame(tag: Int, payload: ByteArray): ByteArray {
        val buffer = ByteBuffer.allocate(5 + payload.size).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(payload.size + 1)
        buffer.put(tag.toByte())
        buffer.put(payload)
        return buffer.array()
    }
}
