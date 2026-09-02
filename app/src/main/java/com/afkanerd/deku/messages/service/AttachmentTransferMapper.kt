package com.afkanerd.deku.messages.service

import com.afkanerd.deku.attachments.storage.AttachmentTransferEntity
import com.afkanerd.deku.attachments.storage.AttachmentTransferStatus
import com.afkanerd.deku.attachments.storage.ChunkTracker
import com.afkanerd.deku.messages.domain.AttachmentKind
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.AttachmentTransferState
import com.afkanerd.deku.messages.domain.MessageDirection
import com.afkanerd.deku.messages.domain.MediaTransport

internal object AttachmentTransferMapper {
    fun map(entity: AttachmentTransferEntity): AttachmentTransfer {
        val status = runCatching { AttachmentTransferStatus.valueOf(entity.status) }
            .getOrDefault(AttachmentTransferStatus.FAILED)
        val bitmap = if(entity.outgoing) entity.acknowledgedBitmap else entity.receivedBitmap
        val completed = runCatching {
            ChunkTracker.restore(entity.totalChunks, bitmap).count()
        }.getOrDefault(0).coerceIn(0, entity.totalChunks)
        return AttachmentTransfer(
            stableId = "attachment-${entity.transferId}",
            timestampMillis = entity.createdAt,
            direction = if(entity.outgoing) MessageDirection.OUTGOING else MessageDirection.INCOMING,
            kind = when(entity.mediaType) {
                "PHOTO" -> AttachmentKind.PHOTO
                "VOICE" -> AttachmentKind.VOICE
                else -> AttachmentKind.FILE
            },
            fileName = entity.filename,
            mimeType = entity.mimeType,
            encodedBytes = entity.encodedSize,
            completedSms = completed,
            totalSms = entity.totalChunks,
            state = when(status) {
                AttachmentTransferStatus.PREPARING -> AttachmentTransferState.PREPARING
                AttachmentTransferStatus.WAITING_ACCEPT -> AttachmentTransferState.WAITING
                AttachmentTransferStatus.OFFERED -> AttachmentTransferState.OFFERED
                AttachmentTransferStatus.SENDING -> AttachmentTransferState.SENDING
                AttachmentTransferStatus.RECEIVING -> AttachmentTransferState.RECEIVING
                AttachmentTransferStatus.PAUSED -> AttachmentTransferState.PAUSED
                AttachmentTransferStatus.RETRYING -> AttachmentTransferState.RETRYING
                AttachmentTransferStatus.VERIFYING -> AttachmentTransferState.VERIFYING
                AttachmentTransferStatus.COMPLETED -> AttachmentTransferState.COMPLETED
                AttachmentTransferStatus.FAILED -> AttachmentTransferState.FAILED
                AttachmentTransferStatus.CANCELLED -> AttachmentTransferState.CANCELLED
            },
            completedPath = entity.completedPath,
            durationMillis = entity.durationMs,
            hasError = !entity.lastError.isNullOrBlank(),
            isSecure = entity.protection == "SECURE",
            transport = runCatching { MediaTransport.valueOf(entity.transport) }
                .getOrDefault(MediaTransport.DATA_SMS),
        )
    }
}
