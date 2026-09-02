package com.afkanerd.deku.messages.service

import com.afkanerd.deku.attachments.storage.AttachmentTransferEntity
import com.afkanerd.deku.attachments.storage.AttachmentTransferStatus
import com.afkanerd.deku.attachments.storage.ChunkTracker
import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.AttachmentTransferState
import com.afkanerd.deku.messages.domain.MessageDirection
import com.afkanerd.deku.messages.domain.MediaTransport
import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentTransferMapperTest {
    @Test
    fun manyTransportChunksBecomeOneHighLevelTransferWithAggregateProgress() {
        val received = ChunkTracker.empty(81).apply {
            repeat(64) { mark(it) }
        }.serialize()
        val item = AttachmentTransferMapper.map(
            transfer(
                outgoing = false,
                mediaType = "PHOTO",
                status = AttachmentTransferStatus.RECEIVING,
                receivedBitmap = received,
            )
        )

        assertEquals("attachment-transfer-id", item.stableId)
        assertEquals(AttachmentKind.PHOTO, item.kind)
        assertEquals(MessageDirection.INCOMING, item.direction)
        assertEquals(64, item.completedSms)
        assertEquals(81, item.totalSms)
        assertEquals(AttachmentTransferState.RECEIVING, item.state)
    }

    @Test
    fun malformedInternalStatusFailsSafeWithoutExposingPackets() {
        val item = AttachmentTransferMapper.map(transfer(status = null))

        assertEquals(AttachmentTransferState.FAILED, item.state)
        assertEquals("attachment-transfer-id", item.stableId)
        assertEquals(false, item.hasError)
    }

    @Test
    fun rawTransportFailureIsReducedToSafeBooleanForUi() {
        val item = AttachmentTransferMapper.map(
            transfer(lastError = "SMS send failed (0): internal modem detail")
        )

        assertEquals(true, item.hasError)
    }

    @Test
    fun unprotectedPacketTransferRemainsVisibleAsUnprotected() {
        val item = AttachmentTransferMapper.map(
            transfer(protection = "UNPROTECTED")
        )

        assertEquals(false, item.isSecure)
        assertEquals(MediaTransport.DATA_SMS, item.transport)
    }

    private fun transfer(
        outgoing: Boolean = true,
        mediaType: String = "FILE",
        status: AttachmentTransferStatus? = AttachmentTransferStatus.SENDING,
        receivedBitmap: ByteArray = ByteArray(0),
        lastError: String? = null,
        protection: String = "SECURE",
    ) = AttachmentTransferEntity(
        transferId = "transfer-id",
        address = "+79990000000",
        identityFingerprint = ByteArray(32),
        subscriptionId = 1,
        outgoing = outgoing,
        protection = protection,
        mediaType = mediaType,
        mimeType = "application/octet-stream",
        filename = "file.bin",
        originalSize = 2048,
        encodedSize = 2300,
        totalChunks = 81,
        sha256 = ByteArray(32),
        status = status?.name ?: "BROKEN",
        lastError = lastError,
        receivedBitmap = receivedBitmap,
        createdAt = 1000,
        updatedAt = 1000,
        expiresAt = 2000,
    )
}
