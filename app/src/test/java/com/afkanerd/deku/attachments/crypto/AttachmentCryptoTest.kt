package com.afkanerd.deku.attachments.crypto

import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.deku.attachments.protocol.SmsPacketType
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.deku.attachments.protocol.TransferLimits
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentCryptoTest {
    private val key = ByteArray(32) { (it + 1).toByte() }
    private val id = TransferId.fromBytes(ByteArray(16) { (it + 20).toByte() })

    @Test
    fun maximumChunkRoundTripAndNeverAppearsAsPlaintext() {
        val plaintext = ByteArray(TransferLimits.CHUNK_PLAINTEXT_BYTES) { it.toByte() }
        val frame = AttachmentCrypto.encrypt(
            key, SmsPacketType.TRANSFER_CHUNK, id, 4, 10, plaintext,
            AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER,
        )
        assertFalse(frame.payload.contentEquals(plaintext))
        assertFalse(contains(frame.payload, plaintext))
        val result = AttachmentCrypto.decrypt(
            key, frame, AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER,
        ) as AttachmentCrypto.DecryptResult.Success
        assertArrayEquals(plaintext, result.plaintext)
    }

    @Test
    fun corruptedCiphertextAndTagFailAuthentication() {
        val frame = encrypted()
        for (index in listOf(0, frame.payload.lastIndex)) {
            val corrupted = frame.copy(payload = frame.payload.copyOf().also { it[index] = (it[index].toInt() xor 1).toByte() })
            assertTrue(AttachmentCrypto.decrypt(key, corrupted, AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER)
                is AttachmentCrypto.DecryptResult.AuthenticationFailed)
        }
    }

    @Test
    fun headerTransferAndDirectionAreAuthenticated() {
        val frame = encrypted()
        val mutations = listOf(
            frame.copy(chunkIndex = 2),
            frame.copy(totalChunks = 4),
            frame.copy(transferId = TransferId.random()),
            frame.copy(packetType = SmsPacketType.ACK),
            frame.copy(flags = AttachmentProtocolFlags.UNPROTECTED),
        )
        mutations.forEach {
            assertTrue(AttachmentCrypto.decrypt(key, it, AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER)
                is AttachmentCrypto.DecryptResult.AuthenticationFailed)
        }
        assertTrue(AttachmentCrypto.decrypt(key, frame, AttachmentCrypto.Direction.RESPONDER_TO_INITIATOR)
            is AttachmentCrypto.DecryptResult.AuthenticationFailed)
    }

    @Test
    fun retransmissionIsIdenticalButOtherIndexUsesDifferentCiphertext() {
        val a = encrypted()
        val repeated = encrypted()
        val other = AttachmentCrypto.encrypt(key, SmsPacketType.TRANSFER_CHUNK, id, 2, 3,
            byteArrayOf(1, 2, 3), AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER)
        assertArrayEquals(a.payload, repeated.payload)
        assertFalse(a.payload.contentEquals(other.payload))
    }

    private fun encrypted(): SmsFrame = AttachmentCrypto.encrypt(
        key, SmsPacketType.TRANSFER_CHUNK, id, 1, 3, byteArrayOf(1, 2, 3),
        AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER,
    )

    private fun contains(haystack: ByteArray, needle: ByteArray): Boolean {
        if (needle.isEmpty()) return true
        return (0..haystack.size - needle.size).any { offset ->
            needle.indices.all { haystack[offset + it] == needle[it] }
        }
    }
}
