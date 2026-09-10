package com.afkanerd.deku.attachments

import com.afkanerd.deku.attachments.protocol.AttachmentContext
import com.afkanerd.deku.attachments.storage.AttachmentTransferEntity
import com.afkanerd.deku.attachments.storage.AttachmentTransferStatus
import com.afkanerd.deku.attachments.storage.ChunkTracker
import com.afkanerd.deku.attachments.protocol.TransferId
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Allows a dual-SIM phone to receive its own real Data-SMS transfer without
 * weakening the normal remote path. The returned offer must be byte-for-byte
 * equivalent to the locally prepared transfer and contain the same random key.
 */
internal fun isMatchingSameDeviceLoopbackOffer(
    existing: AttachmentTransferEntity,
    incomingAddress: String,
    incomingSubscriptionId: Int,
    attachmentContext: AttachmentContext,
    keyMatches: Boolean,
): Boolean {
    val manifest = attachmentContext.manifest
    return existing.outgoing &&
        existing.status in setOf(
            AttachmentTransferStatus.WAITING_ACCEPT.name,
            AttachmentTransferStatus.RETRYING.name,
        ) &&
        existing.transport == attachmentContext.transport.name &&
        attachmentContext.transport in setOf(
            AttachmentWireTransport.DATA_SMS,
            AttachmentWireTransport.STANDARD_SMS,
        ) &&
        (existing.address != incomingAddress ||
            existing.subscriptionId != incomingSubscriptionId) &&
        keyMatches &&
        existing.transferId == manifest.transferId.toHex() &&
        existing.mediaType == manifest.mediaType.name &&
        existing.mimeType == manifest.mimeType &&
        existing.filename == manifest.filename &&
        existing.originalSize == manifest.originalSize &&
        existing.encodedSize == manifest.encodedSize &&
        existing.totalChunks == manifest.totalChunks &&
        existing.sha256.contentEquals(manifest.sha256) &&
        existing.codec == manifest.codec &&
        existing.width == manifest.width &&
        existing.height == manifest.height &&
        existing.sampleRate == manifest.sampleRate &&
        existing.durationMs == manifest.durationMs &&
        existing.chunkPlaintextBytes == attachmentContext.chunkPlaintextBytes
}

/** Builds the recipient-side timeline row for a transfer between two SIMs in this device. */
internal fun sameDeviceLoopbackMirror(
    outgoing: AttachmentTransferEntity,
    incomingAddress: String,
    incomingSubscriptionId: Int,
    incomingIdentityFingerprint: ByteArray,
    completedPath: String,
    now: Long,
): AttachmentTransferEntity {
    require(outgoing.outgoing)
    require(outgoing.status == AttachmentTransferStatus.COMPLETED.name)
    val received = ChunkTracker.empty(outgoing.totalChunks).apply {
        repeat(outgoing.totalChunks) { mark(it) }
    }
    return outgoing.copy(
        transferId = loopbackMirrorId(
            outgoing.transferId,
            incomingAddress,
            incomingSubscriptionId,
        ),
        address = incomingAddress,
        identityFingerprint = incomingIdentityFingerprint.copyOf(),
        subscriptionId = incomingSubscriptionId,
        outgoing = false,
        sourcePath = null,
        partialPath = null,
        completedPath = completedPath,
        ratchetOffer = null,
        receivedBitmap = received.serialize(),
        sentBitmap = ByteArray(0),
        acknowledgedBitmap = ByteArray(0),
        smsReceived = outgoing.totalChunks,
        retryCount = 0,
        lastError = null,
        updatedAt = now,
        expiresAt = Long.MAX_VALUE,
    )
}

internal fun loopbackMirrorId(
    transferId: String,
    incomingAddress: String,
    incomingSubscriptionId: Int,
): String {
    val digest = MessageDigest.getInstance("SHA-256")
    digest.update("deku-loopback-recipient-v1".toByteArray())
    digest.update(TransferId.fromHex(transferId).toByteArray())
    digest.update(incomingAddress.toByteArray())
    digest.update(ByteBuffer.allocate(Int.SIZE_BYTES).putInt(incomingSubscriptionId).array())
    return TransferId.fromBytes(digest.digest().copyOf(TransferId.BYTE_LENGTH)).toHex()
}
