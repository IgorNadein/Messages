package com.afkanerd.deku.attachments.protocol

import com.afkanerd.deku.attachments.AttachmentWireTransport
import java.nio.ByteBuffer
import java.nio.ByteOrder

data class AttachmentContext(
    val manifest: AttachmentManifest,
    val masterKey: ByteArray,
    val transport: AttachmentWireTransport = AttachmentWireTransport.DATA_SMS,
    val chunkPlaintextBytes: Int = TransferLimits.CHUNK_PLAINTEXT_BYTES,
    val remoteSource: RemoteAttachmentSource? = null,
) {
    init {
        require(masterKey.size == 32)
        require(chunkPlaintextBytes > 0)
        require((transport == AttachmentWireTransport.CLOUD_STORAGE) == (remoteSource != null))
    }

    override fun equals(other: Any?): Boolean = other is AttachmentContext &&
        manifest == other.manifest && masterKey.contentEquals(other.masterKey) &&
        transport == other.transport && chunkPlaintextBytes == other.chunkPlaintextBytes &&
        remoteSource == other.remoteSource

    override fun hashCode(): Int {
        var result = 31 * manifest.hashCode() + masterKey.contentHashCode()
        result = 31 * result + transport.hashCode()
        result = 31 * result + chunkPlaintextBytes
        return 31 * result + (remoteSource?.hashCode() ?: 0)
    }
}

data class RemoteAttachmentSource(val providerCode: Int, val locator: String) {
    init {
        require(providerCode in 1..255)
        require(locator.toByteArray(Charsets.UTF_8).size in 1..MAX_LOCATOR_BYTES)
        val uri = requireNotNull(runCatching { java.net.URI(locator) }.getOrNull()) {
            "Invalid cloud locator"
        }
        require(uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank())
        require(uri.rawUserInfo == null && uri.rawFragment == null)
    }

    companion object {
        const val MAX_LOCATOR_BYTES = 1024
    }
}

object AttachmentContextCodec {
    private const val FORMAT_VERSION = 3
    private const val KEY_BYTES = 32

    fun encode(context: AttachmentContext): ByteArray {
        val manifest = AttachmentManifestCodec.encode(context.manifest)
        val locator = context.remoteSource?.locator?.toByteArray(Charsets.UTF_8) ?: ByteArray(0)
        require(manifest.size <= TransferLimits.MAX_MANIFEST_BYTES)
        require(locator.size <= RemoteAttachmentSource.MAX_LOCATOR_BYTES)
        return ByteBuffer.allocate(1 + 1 + 4 + 1 + 2 + locator.size + 2 + manifest.size + KEY_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .put(FORMAT_VERSION.toByte())
            .put(context.transport.wireCode.toByte())
            .putInt(context.chunkPlaintextBytes)
            .put((context.remoteSource?.providerCode ?: 0).toByte())
            .putShort(locator.size.toShort())
            .put(locator)
            .putShort(manifest.size.toShort())
            .put(manifest)
            .put(context.masterKey)
            .array()
    }

    fun decode(data: ByteArray): AttachmentContext {
        require(data.size in (1 + 2 + KEY_BYTES + 1)..(
            1 + 1 + 4 + 1 + 2 + RemoteAttachmentSource.MAX_LOCATOR_BYTES +
                2 + TransferLimits.MAX_MANIFEST_BYTES + KEY_BYTES
            )) {
            "Attachment context length is out of bounds"
        }
        val input = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        return when(val version = input.get().toInt() and 0xff) {
            1 -> decodeV1(input)
            2 -> decodeV2(input)
            FORMAT_VERSION -> decodeV3(input)
            else -> throw IllegalArgumentException("Unknown attachment context version $version")
        }
    }

    private fun decodeV1(input: ByteBuffer): AttachmentContext = decodeBody(
        input = input,
        transport = AttachmentWireTransport.DATA_SMS,
        chunkPlaintextBytes = TransferLimits.CHUNK_PLAINTEXT_BYTES,
    )

    private fun decodeV2(input: ByteBuffer): AttachmentContext {
        require(input.remaining() >= 1 + 4 + 2 + KEY_BYTES + 1)
        val transport = AttachmentWireTransport.fromWireCode(input.get().toInt() and 0xff)
            ?: throw IllegalArgumentException("Unknown attachment transport")
        val chunkPlaintextBytes = input.int
        require(chunkPlaintextBytes in 1..TransferLimits.MMS_PART_PLAINTEXT_BYTES)
        return decodeBody(input, transport, chunkPlaintextBytes)
    }

    private fun decodeV3(input: ByteBuffer): AttachmentContext {
        require(input.remaining() >= 1 + 4 + 1 + 2 + 2 + KEY_BYTES + 1)
        val transport = AttachmentWireTransport.fromWireCode(input.get().toInt() and 0xff)
            ?: throw IllegalArgumentException("Unknown attachment transport")
        val chunkPlaintextBytes = input.int
        require(chunkPlaintextBytes in 1..TransferLimits.MMS_PART_PLAINTEXT_BYTES)
        val providerCode = input.get().toInt() and 0xff
        val locatorLength = input.short.toInt() and 0xffff
        require(locatorLength in 0..RemoteAttachmentSource.MAX_LOCATOR_BYTES)
        require(input.remaining() >= locatorLength + 2 + KEY_BYTES + 1)
        val locatorBytes = ByteArray(locatorLength).also(input::get)
        val locator = Charsets.UTF_8.newDecoder().runCatching {
            decode(ByteBuffer.wrap(locatorBytes)).toString()
        }.getOrElse { throw IllegalArgumentException("Invalid cloud locator encoding", it) }
        require(locator.toByteArray(Charsets.UTF_8).contentEquals(locatorBytes))
        val remoteSource = if(providerCode == 0) {
            require(locatorLength == 0)
            null
        } else {
            RemoteAttachmentSource(providerCode, locator)
        }
        return decodeBody(input, transport, chunkPlaintextBytes, remoteSource)
    }

    private fun decodeBody(
        input: ByteBuffer,
        transport: AttachmentWireTransport,
        chunkPlaintextBytes: Int,
        remoteSource: RemoteAttachmentSource? = null,
    ): AttachmentContext {
        val manifestLength = input.short.toInt() and 0xffff
        require(manifestLength in 1..TransferLimits.MAX_MANIFEST_BYTES)
        require(input.remaining() == manifestLength + KEY_BYTES) { "Attachment context length mismatch" }
        val manifest = AttachmentManifestCodec.decode(ByteArray(manifestLength).also(input::get))
        val key = ByteArray(KEY_BYTES).also(input::get)
        return AttachmentContext(manifest, key, transport, chunkPlaintextBytes, remoteSource)
    }
}
