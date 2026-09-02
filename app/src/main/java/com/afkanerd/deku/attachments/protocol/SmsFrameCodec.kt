package com.afkanerd.deku.attachments.protocol

import java.nio.ByteBuffer
import java.nio.ByteOrder

object SmsFrameCodec {
    private const val MAGIC: Int = 0x444b

    sealed interface DecodeResult {
        data class Success(val frame: SmsFrame) : DecodeResult
        data class Rejected(val reason: String) : DecodeResult
    }

    fun encode(frame: SmsFrame): ByteArray {
        validateSemanticBounds(frame)?.let { throw IllegalArgumentException(it) }
        return ByteBuffer.allocate(TransferLimits.FRAME_HEADER_BYTES + frame.payload.size)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(MAGIC.toShort())
            .put(frame.protocolVersion.toByte())
            .put(frame.packetType.wireValue.toByte())
            .put(frame.flags.toByte())
            .put(frame.transferId.toByteArray())
            .putShort(frame.chunkIndex.toShort())
            .putShort(frame.totalChunks.toShort())
            .put(frame.payload.size.toByte())
            .put(frame.payload)
            .array()
    }

    fun headerBytes(frame: SmsFrame, payloadLength: Int = frame.payload.size): ByteArray {
        require(payloadLength in 0..TransferLimits.FRAME_PAYLOAD_BYTES)
        return ByteBuffer.allocate(TransferLimits.FRAME_HEADER_BYTES)
            .order(ByteOrder.BIG_ENDIAN)
            .putShort(MAGIC.toShort())
            .put(frame.protocolVersion.toByte())
            .put(frame.packetType.wireValue.toByte())
            .put(frame.flags.toByte())
            .put(frame.transferId.toByteArray())
            .putShort(frame.chunkIndex.toShort())
            .putShort(frame.totalChunks.toShort())
            .put(payloadLength.toByte())
            .array()
    }

    fun decode(data: ByteArray): DecodeResult {
        if (data.size < TransferLimits.FRAME_HEADER_BYTES || data.size > TransferLimits.FRAME_BYTES) {
            return DecodeResult.Rejected("Frame length is out of bounds")
        }
        val input = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN)
        if (input.short.toInt() and 0xffff != MAGIC) return DecodeResult.Rejected("Unknown magic")
        val version = input.get().toInt() and 0xff
        if (version != TransferLimits.PROTOCOL_VERSION) return DecodeResult.Rejected("Unknown version")
        val type = SmsPacketType.fromWireValue(input.get().toInt() and 0xff)
            ?: return DecodeResult.Rejected("Unknown packet type")
        val flags = input.get().toInt() and 0xff
        val transferBytes = ByteArray(TransferId.BYTE_LENGTH).also(input::get)
        val index = input.short.toInt() and 0xffff
        val total = input.short.toInt() and 0xffff
        val payloadLength = input.get().toInt() and 0xff
        if (payloadLength > TransferLimits.FRAME_PAYLOAD_BYTES || payloadLength != input.remaining()) {
            return DecodeResult.Rejected("Payload length mismatch")
        }
        val payload = ByteArray(payloadLength).also(input::get)
        val frame = SmsFrame(version, type, flags, TransferId.fromBytes(transferBytes), index, total, payload)
        validateSemanticBounds(frame)?.let { return DecodeResult.Rejected(it) }
        return DecodeResult.Success(frame)
    }

    private fun validateSemanticBounds(frame: SmsFrame): String? = when (frame.packetType) {
        SmsPacketType.TRANSFER_OFFER -> when {
            !AttachmentProtocolFlags.isSupported(frame.flags) -> "Unsupported attachment flags"
            frame.totalChunks !in 1..TransferLimits.MAX_OFFER_FRAGMENTS -> "Offer fragment count is out of bounds"
            frame.chunkIndex >= frame.totalChunks -> "Offer fragment index is out of bounds"
            frame.payload.isEmpty() -> "Empty offer fragment"
            else -> null
        }
        SmsPacketType.TRANSFER_CHUNK -> when {
            !AttachmentProtocolFlags.isSupported(frame.flags) -> "Unsupported attachment flags"
            frame.totalChunks !in 1..TransferLimits.MAX_TOTAL_CHUNKS -> "Chunk count is out of bounds"
            frame.chunkIndex >= frame.totalChunks -> "Chunk index is out of bounds"
            frame.payload.size < TransferLimits.AEAD_TAG_BYTES -> "Encrypted chunk is too short"
            else -> null
        }
        else -> when {
            !AttachmentProtocolFlags.isSupported(frame.flags) -> "Unsupported attachment flags"
            frame.totalChunks > TransferLimits.MAX_TOTAL_CHUNKS -> "Chunk count is out of bounds"
            frame.payload.size < TransferLimits.AEAD_TAG_BYTES -> "Encrypted control packet is too short"
            else -> null
        }
    }
}
