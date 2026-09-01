package com.afkanerd.deku.attachments

import com.afkanerd.deku.attachments.crypto.AttachmentCrypto
import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.deku.attachments.protocol.SmsPacketType
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.deku.attachments.protocol.TransferLimits
import com.afkanerd.deku.attachments.storage.ChunkTracker
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

class AttachmentPipelineTest {
    private val random = SecureRandom()

    @Test
    fun sizeMatrixRoundTripsInOrderReverseAndRandom() {
        listOf(1, 100, 1024, 10 * 1024, 100 * 1024).forEach { size ->
            val source = ByteArray(size).also(random::nextBytes)
            assertArrayEquals(source, transfer(source, Order.IN_ORDER))
            assertArrayEquals(source, transfer(source, Order.REVERSED))
            assertArrayEquals(source, transfer(source, Order.RANDOM))
        }
    }

    @Test
    fun zeroAndFfBinaryRoundTrip() {
        assertArrayEquals(ByteArray(4096), transfer(ByteArray(4096), Order.RANDOM))
        assertArrayEquals(ByteArray(4096) { 0xff.toByte() },
            transfer(ByteArray(4096) { 0xff.toByte() }, Order.REVERSED))
    }

    @Test
    fun duplicateIsIdempotentAndMissingNeverCompletes() {
        val source = ByteArray(1000).also(random::nextBytes)
        val key = AttachmentCrypto.generateMasterKey()
        val id = TransferId.random()
        val frames = frames(source, key, id)
        val tracker = ChunkTracker.empty(frames.size)
        frames.dropLast(1).flatMap { listOf(it, it) }.forEach { frame ->
            val plain = decrypt(key, frame)
            if (tracker.mark(frame.chunkIndex)) assertTrue(plain.isNotEmpty())
        }
        assertFalse(tracker.isComplete())
        assertArrayEquals(intArrayOf(frames.lastIndex), tracker.missing())
    }

    @Test
    fun ciphertextCannotBeMovedAcrossTransferOrIndex() {
        val key = AttachmentCrypto.generateMasterKey()
        val id = TransferId.random()
        val frame = frames(byteArrayOf(7, 8, 9), key, id).single()
        val wrongTransfer = frame.copy(transferId = TransferId.random())
        assertTrue(AttachmentCrypto.decrypt(key, wrongTransfer,
            AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER) is AttachmentCrypto.DecryptResult.AuthenticationFailed)
        val wrongIndex = frame.copy(chunkIndex = 1, totalChunks = 2)
        assertTrue(AttachmentCrypto.decrypt(key, wrongIndex,
            AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER) is AttachmentCrypto.DecryptResult.AuthenticationFailed)
    }

    @Test
    fun corruptionNeverProducesCompletedDigest() {
        val source = ByteArray(1024).also(random::nextBytes)
        val key = AttachmentCrypto.generateMasterKey()
        val frames = frames(source, key, TransferId.random()).toMutableList()
        frames[3] = frames[3].copy(payload = frames[3].payload.copyOf().also { it[0] = (it[0].toInt() xor 1).toByte() })
        val output = ByteArrayOutputStream()
        var authenticated = true
        frames.forEach { frame ->
            when (val result = AttachmentCrypto.decrypt(key, frame,
                AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER)) {
                is AttachmentCrypto.DecryptResult.Success -> output.write(result.plaintext)
                AttachmentCrypto.DecryptResult.AuthenticationFailed -> authenticated = false
            }
        }
        val digestMatches = MessageDigest.isEqual(
            MessageDigest.getInstance("SHA-256").digest(source),
            MessageDigest.getInstance("SHA-256").digest(output.toByteArray()),
        )
        assertFalse(authenticated && digestMatches)
    }

    private fun transfer(source: ByteArray, order: Order): ByteArray {
        val key = AttachmentCrypto.generateMasterKey()
        val frames = frames(source, key, TransferId.random()).toMutableList()
        when (order) {
            Order.IN_ORDER -> Unit
            Order.REVERSED -> frames.reverse()
            Order.RANDOM -> frames.shuffle()
        }
        val chunks = arrayOfNulls<ByteArray>(frames.size)
        val tracker = ChunkTracker.empty(frames.size)
        frames.forEach { frame ->
            chunks[frame.chunkIndex] = decrypt(key, frame)
            tracker.mark(frame.chunkIndex)
        }
        assertTrue(tracker.isComplete())
        return chunks.filterNotNull().fold(ByteArray(0)) { all, chunk -> all + chunk }
    }

    private fun frames(source: ByteArray, key: ByteArray, id: TransferId): List<SmsFrame> {
        val count = TransferLimits.chunkCount(source.size.toLong())
        return (0 until count).map { index ->
            val start = index * TransferLimits.CHUNK_PLAINTEXT_BYTES
            AttachmentCrypto.encrypt(
                key, SmsPacketType.TRANSFER_CHUNK, id, index, count,
                source.copyOfRange(start, minOf(source.size, start + TransferLimits.CHUNK_PLAINTEXT_BYTES)),
                AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER,
            )
        }
    }

    private fun decrypt(key: ByteArray, frame: SmsFrame): ByteArray =
        (AttachmentCrypto.decrypt(key, frame, AttachmentCrypto.Direction.INITIATOR_TO_RESPONDER)
            as AttachmentCrypto.DecryptResult.Success).plaintext

    private enum class Order { IN_ORDER, REVERSED, RANDOM }
}
