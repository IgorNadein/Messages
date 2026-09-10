package com.afkanerd.deku.attachments

import com.afkanerd.deku.attachments.protocol.AttachmentContext
import com.afkanerd.deku.attachments.protocol.AttachmentManifest
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.deku.attachments.protocol.TransferLimits
import com.afkanerd.deku.attachments.storage.AttachmentTransferEntity
import com.afkanerd.deku.attachments.storage.AttachmentTransferStatus
import com.afkanerd.deku.attachments.storage.ChunkTracker
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SameDeviceLoopbackPolicyTest {
    private val transferId = TransferId.random()
    private val digest = ByteArray(32) { it.toByte() }
    private val manifest = AttachmentManifest(
        transferId = transferId,
        mediaType = AttachmentManifest.MediaType.FILE,
        mimeType = "image/png",
        filename = "test.png",
        originalSize = 2_360,
        encodedSize = 2_360,
        totalChunks = TransferLimits.chunkCount(2_360),
        sha256 = digest,
    )
    private val context = AttachmentContext(manifest, ByteArray(32) { 7 })
    private val outgoing = AttachmentTransferEntity(
        transferId = transferId.toHex(),
        address = "+79960000002",
        identityFingerprint = ByteArray(0),
        subscriptionId = 2,
        outgoing = true,
        protection = AttachmentProtection.UNPROTECTED.name,
        mediaType = manifest.mediaType.name,
        mimeType = manifest.mimeType,
        filename = manifest.filename,
        originalSize = manifest.originalSize,
        encodedSize = manifest.encodedSize,
        totalChunks = manifest.totalChunks,
        sha256 = digest,
        status = AttachmentTransferStatus.WAITING_ACCEPT.name,
        createdAt = 1,
        updatedAt = 1,
        expiresAt = Long.MAX_VALUE,
    )

    @Test
    fun `matching returned offer activates dual sim loopback`() {
        assertTrue(isMatchingSameDeviceLoopbackOffer(
            existing = outgoing,
            incomingAddress = "+79960000001",
            incomingSubscriptionId = 12,
            attachmentContext = context,
            keyMatches = true,
        ))
    }

    @Test
    fun `wrong key or changed manifest cannot activate loopback`() {
        assertFalse(isMatchingSameDeviceLoopbackOffer(
            outgoing,
            "+79960000001",
            12,
            context,
            keyMatches = false,
        ))
        assertFalse(isMatchingSameDeviceLoopbackOffer(
            outgoing,
            "+79960000001",
            12,
            context.copy(manifest = manifest.copy(filename = "other.png")),
            keyMatches = true,
        ))
    }

    @Test
    fun `same endpoint and incoming records never use loopback shortcut`() {
        assertFalse(isMatchingSameDeviceLoopbackOffer(
            outgoing,
            outgoing.address,
            outgoing.subscriptionId,
            context,
            keyMatches = true,
        ))
        assertFalse(isMatchingSameDeviceLoopbackOffer(
            outgoing.copy(outgoing = false),
            "+79960000001",
            12,
            context,
            keyMatches = true,
        ))
    }

    @Test
    fun `completed loopback creates a distinct incoming timeline row`() {
        val completed = outgoing.copy(
            status = AttachmentTransferStatus.COMPLETED.name,
            completedPath = "/files/test.png",
        )
        val mirror = sameDeviceLoopbackMirror(
            outgoing = completed,
            incomingAddress = "+79960000001",
            incomingSubscriptionId = 12,
            incomingIdentityFingerprint = ByteArray(32) { 9 },
            completedPath = "/files/test.png",
            now = 100,
        )

        assertNotEquals(completed.transferId, mirror.transferId)
        assertFalse(mirror.outgoing)
        assertEquals("+79960000001", mirror.address)
        assertEquals(12, mirror.subscriptionId)
        assertEquals(AttachmentTransferStatus.COMPLETED.name, mirror.status)
        assertEquals("/files/test.png", mirror.completedPath)
        assertEquals(
            mirror.totalChunks,
            ChunkTracker.restore(mirror.totalChunks, mirror.receivedBitmap).count(),
        )
    }

    @Test
    fun `recipient mirror id is stable but endpoint specific`() {
        assertEquals(
            loopbackMirrorId(outgoing.transferId, "+79960000001", 12),
            loopbackMirrorId(outgoing.transferId, "+79960000001", 12),
        )
        assertNotEquals(
            loopbackMirrorId(outgoing.transferId, "+79960000001", 12),
            loopbackMirrorId(outgoing.transferId, "+79960000003", 13),
        )
    }

    @Test
    fun `only an unmuted completed recipient transfer posts a notification`() {
        assertTrue(shouldPostIncomingAttachmentNotification(
            outgoing = false,
            status = AttachmentTransferStatus.COMPLETED.name,
            isMuted = false,
        ))
        assertFalse(shouldPostIncomingAttachmentNotification(
            outgoing = true,
            status = AttachmentTransferStatus.COMPLETED.name,
            isMuted = false,
        ))
        assertFalse(shouldPostIncomingAttachmentNotification(
            outgoing = false,
            status = AttachmentTransferStatus.RECEIVING.name,
            isMuted = false,
        ))
        assertFalse(shouldPostIncomingAttachmentNotification(
            outgoing = false,
            status = AttachmentTransferStatus.COMPLETED.name,
            isMuted = true,
        ))
    }
}
