package com.afkanerd.deku.attachments.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsFrameCodecTest {
    private val transferId = TransferId.fromBytes(ByteArray(16) { it.toByte() })

    @Test
    fun frameRoundTripUsesExactBinaryLength() {
        val frame = SmsFrame(
            packetType = SmsPacketType.TRANSFER_CHUNK,
            transferId = transferId,
            chunkIndex = 7,
            totalChunks = 9,
            payload = ByteArray(94) { (it * 3).toByte() },
        )
        val encoded = SmsFrameCodec.encode(frame)
        assertEquals(120, encoded.size)
        val decoded = SmsFrameCodec.decode(encoded) as SmsFrameCodec.DecodeResult.Success
        assertEquals(frame, decoded.frame)
    }

    @Test
    fun rejectsUnknownVersionTypeAndLengthBeforePayloadAllocation() {
        val valid = SmsFrameCodec.encode(
            SmsFrame(packetType = SmsPacketType.TRANSFER_CHUNK, transferId = transferId, chunkIndex = 0,
                totalChunks = 1, payload = ByteArray(16))
        )
        for (mutation in listOf<(ByteArray) -> Unit>(
            { it[2] = 99 },
            { it[3] = 99 },
            { it[25] = 93 },
        )) {
            val malformed = valid.copyOf().also(mutation)
            assertTrue(SmsFrameCodec.decode(malformed) is SmsFrameCodec.DecodeResult.Rejected)
        }
    }

    @Test
    fun rejectsHugeOrInvalidChunkDeclarations() {
        val bytes = SmsFrameCodec.encode(
            SmsFrame(packetType = SmsPacketType.TRANSFER_CHUNK, transferId = transferId, chunkIndex = 0,
                totalChunks = 1, payload = ByteArray(16))
        )
        bytes[23] = 0x10
        bytes[24] = 0x01
        assertTrue(SmsFrameCodec.decode(bytes) is SmsFrameCodec.DecodeResult.Rejected)

        val wrongIndex = bytes.copyOf().also {
            it[21] = 0
            it[22] = 2
            it[23] = 0
            it[24] = 1
        }
        assertTrue(SmsFrameCodec.decode(wrongIndex) is SmsFrameCodec.DecodeResult.Rejected)
    }

    @Test
    fun acceptsOnlyDocumentedProtectionFlags() {
        val unprotected = SmsFrame(
            packetType = SmsPacketType.TRANSFER_OFFER,
            flags = AttachmentProtocolFlags.UNPROTECTED,
            transferId = transferId,
            chunkIndex = 0,
            totalChunks = 1,
            payload = byteArrayOf(1),
        )
        assertEquals(
            unprotected,
            (SmsFrameCodec.decode(SmsFrameCodec.encode(unprotected)) as
                SmsFrameCodec.DecodeResult.Success).frame,
        )

        val unknownFlag = SmsFrameCodec.encode(unprotected).also { encoded ->
            encoded[4] = 0x08
        }
        assertTrue(SmsFrameCodec.decode(unknownFlag) is SmsFrameCodec.DecodeResult.Rejected)

        val conflictingPayloadFlags = SmsFrameCodec.encode(unprotected).also { encoded ->
            encoded[4] = (AttachmentProtocolFlags.MMS_PAYLOAD or
                AttachmentProtocolFlags.CLOUD_PAYLOAD).toByte()
        }
        assertTrue(
            SmsFrameCodec.decode(conflictingPayloadFlags) is SmsFrameCodec.DecodeResult.Rejected
        )
    }

    @Test
    fun transferIdIsDefensivelyCopied() {
        val source = ByteArray(16) { 7 }
        val id = TransferId.fromBytes(source)
        source.fill(9)
        assertArrayEquals(ByteArray(16) { 7 }, id.toByteArray())
        assertEquals(id, TransferId.fromHex(id.toHex()))
    }
}
