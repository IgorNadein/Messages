package com.afkanerd.deku.attachments.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

object AttachmentManifestCodec {
    private const val FORMAT_VERSION = 1
    private const val FIXED_BYTES = 1 + 1 + TransferId.BYTE_LENGTH + 4 + 4 + 2 + 32 + 2 + 2 + 4 + 4

    fun encode(manifest: AttachmentManifest): ByteArray {
        val safeName = sanitizeFilename(manifest.filename)
        val name = safeName.toByteArray(Charsets.UTF_8)
        val mime = manifest.mimeType.toByteArray(Charsets.US_ASCII)
        val codec = manifest.codec.toByteArray(Charsets.US_ASCII)
        validate(manifest.copy(filename = safeName), name, mime, codec)
        val size = FIXED_BYTES + 1 + name.size + 1 + mime.size + 1 + codec.size
        require(size <= TransferLimits.MAX_MANIFEST_BYTES)
        return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
            .put(FORMAT_VERSION.toByte())
            .put(manifest.mediaType.wireValue.toByte())
            .put(manifest.transferId.toByteArray())
            .putInt(manifest.originalSize.toInt())
            .putInt(manifest.encodedSize.toInt())
            .putShort(manifest.totalChunks.toShort())
            .put(manifest.sha256)
            .putShort(manifest.width.toShort())
            .putShort(manifest.height.toShort())
            .putInt(manifest.sampleRate)
            .putInt(manifest.durationMs.toInt())
            .put(name.size.toByte()).put(name)
            .put(mime.size.toByte()).put(mime)
            .put(codec.size.toByte()).put(codec)
            .array()
    }

    fun decode(data: ByteArray): AttachmentManifest {
        require(data.size in FIXED_BYTES + 3..TransferLimits.MAX_MANIFEST_BYTES) { "Manifest length is out of bounds" }
        val input = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        require(input.get().toInt() and 0xff == FORMAT_VERSION) { "Unknown manifest version" }
        val type = AttachmentManifest.MediaType.fromWireValue(input.get().toInt() and 0xff)
            ?: throw IllegalArgumentException("Unknown media type")
        val transferId = TransferId.fromBytes(ByteArray(TransferId.BYTE_LENGTH).also(input::get))
        val originalSize = input.int.toLong() and 0xffffffffL
        val encodedSize = input.int.toLong() and 0xffffffffL
        val totalChunks = input.short.toInt() and 0xffff
        val digest = ByteArray(32).also(input::get)
        val width = input.short.toInt() and 0xffff
        val height = input.short.toInt() and 0xffff
        val sampleRate = input.int
        val duration = input.int.toLong() and 0xffffffffL
        val name = readString(input, TransferLimits.MAX_FILENAME_BYTES, Charsets.UTF_8, "filename")
        val mime = readString(input, TransferLimits.MAX_MIME_BYTES, Charsets.US_ASCII, "MIME")
        val codec = readString(input, TransferLimits.MAX_CODEC_BYTES, Charsets.US_ASCII, "codec")
        require(!input.hasRemaining()) { "Trailing manifest data" }
        require(name == sanitizeFilename(name)) { "Unsafe filename" }
        val manifest = AttachmentManifest(
            transferId = transferId, mediaType = type, mimeType = mime, filename = name,
            originalSize = originalSize, encodedSize = encodedSize, totalChunks = totalChunks,
            sha256 = digest, codec = codec, width = width, height = height,
            sampleRate = sampleRate, durationMs = duration,
        )
        validate(manifest, name.toByteArray(Charsets.UTF_8), mime.toByteArray(Charsets.US_ASCII), codec.toByteArray(Charsets.US_ASCII))
        return manifest
    }

    fun sanitizeFilename(input: String): String {
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFC)
            .replace(Regex("[\\u0000-\\u001f\\u007f/\\\\]"), "_")
            .replace("..", "_")
            .replace(Regex("_+"), "_")
            .trim().ifEmpty { "attachment" }
        val dangerous = setOf("apk", "dex", "jar", "js", "html", "htm", "sh", "bat", "cmd", "exe", "msi")
        val neutralized = if (normalized.substringAfterLast('.', "").lowercase() in dangerous) "$normalized.safe" else normalized
        var result = neutralized
        while (result.toByteArray(Charsets.UTF_8).size > TransferLimits.MAX_FILENAME_BYTES) {
            result = result.dropLast(1)
        }
        return result.ifEmpty { "attachment" }
    }

    private fun validate(manifest: AttachmentManifest, name: ByteArray, mime: ByteArray, codec: ByteArray) {
        require(manifest.protocolVersion == TransferLimits.PROTOCOL_VERSION)
        require(name.isNotEmpty() && name.size <= TransferLimits.MAX_FILENAME_BYTES)
        require(mime.isNotEmpty() && mime.size <= TransferLimits.MAX_MIME_BYTES && manifest.mimeType.all { it.code in 0x20..0x7e })
        require(codec.size <= TransferLimits.MAX_CODEC_BYTES && manifest.codec.all { it.code in 0x20..0x7e })
        require(manifest.originalSize in 0..Int.MAX_VALUE.toLong())
        require(manifest.encodedSize in 1..TransferLimits.MAX_MMS_TRANSFER_BYTES.toLong())
        require(manifest.totalChunks in 1..TransferLimits.MAX_TOTAL_CHUNKS)
        require(manifest.totalChunks.toLong() <= manifest.encodedSize) {
            "Non-empty attachment parts cannot outnumber encoded bytes"
        }
        require(manifest.sha256.size == 32)
        require(manifest.width in 0..0xffff && manifest.height in 0..0xffff)
        require(manifest.sampleRate in 0..384_000)
        require(manifest.durationMs in 0..0xffffffffL)
    }

    private fun readString(input: ByteBuffer, maxBytes: Int, charset: java.nio.charset.Charset, label: String): String {
        require(input.hasRemaining()) { "Missing $label length" }
        val length = input.get().toInt() and 0xff
        require(length in 0..maxBytes && input.remaining() >= length) { "$label length is out of bounds" }
        val raw = ByteArray(length).also(input::get)
        val decoded = charset.newDecoder().runCatching { decode(ByteBuffer.wrap(raw)).toString() }
            .getOrElse { throw IllegalArgumentException("Invalid $label encoding", it) }
        require(decoded.toByteArray(charset).contentEquals(raw)) { "Non-canonical $label encoding" }
        return decoded
    }
}
