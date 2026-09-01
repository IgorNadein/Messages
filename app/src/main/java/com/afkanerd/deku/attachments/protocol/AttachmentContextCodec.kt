package com.afkanerd.deku.attachments.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AttachmentContext(
    val manifest: AttachmentManifest,
    val masterKey: ByteArray,
) {
    init { require(masterKey.size == 32) }

    override fun equals(other: Any?): Boolean = other is AttachmentContext &&
        manifest == other.manifest && masterKey.contentEquals(other.masterKey)

    override fun hashCode(): Int = 31 * manifest.hashCode() + masterKey.contentHashCode()
}

object AttachmentContextCodec {
    private const val FORMAT_VERSION = 1
    private const val KEY_BYTES = 32

    fun encode(context: AttachmentContext): ByteArray {
        val manifest = AttachmentManifestCodec.encode(context.manifest)
        require(manifest.size <= TransferLimits.MAX_MANIFEST_BYTES)
        return ByteBuffer.allocate(1 + 2 + manifest.size + KEY_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .put(FORMAT_VERSION.toByte())
            .putShort(manifest.size.toShort())
            .put(manifest)
            .put(context.masterKey)
            .array()
    }

    fun decode(data: ByteArray): AttachmentContext {
        require(data.size in (1 + 2 + KEY_BYTES + 1)..(1 + 2 + TransferLimits.MAX_MANIFEST_BYTES + KEY_BYTES)) {
            "Attachment context length is out of bounds"
        }
        val input = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        require(input.get().toInt() and 0xff == FORMAT_VERSION) { "Unknown attachment context version" }
        val manifestLength = input.short.toInt() and 0xffff
        require(manifestLength in 1..TransferLimits.MAX_MANIFEST_BYTES)
        require(input.remaining() == manifestLength + KEY_BYTES) { "Attachment context length mismatch" }
        val manifest = AttachmentManifestCodec.decode(ByteArray(manifestLength).also(input::get))
        val key = ByteArray(KEY_BYTES).also(input::get)
        return AttachmentContext(manifest, key)
    }
}
