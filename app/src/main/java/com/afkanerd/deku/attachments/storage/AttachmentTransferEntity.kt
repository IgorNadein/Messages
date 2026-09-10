package com.afkanerd.deku.attachments.storage

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(
    tableName = "AttachmentTransfer",
    indices = [Index("address"), Index("status"), Index("updatedAt")],
)
data class AttachmentTransferEntity(
    @PrimaryKey val transferId: String,
    val address: String,
    val identityFingerprint: ByteArray,
    val subscriptionId: Int,
    val outgoing: Boolean,
    @ColumnInfo(defaultValue = "'SECURE'")
    val protection: String = "SECURE",
    @ColumnInfo(defaultValue = "'DATA_SMS'")
    val transport: String = "DATA_SMS",
    @ColumnInfo(defaultValue = "78")
    val chunkPlaintextBytes: Int = 78,
    val remoteProvider: String? = null,
    val remoteLocator: String? = null,
    val remoteDeleteLocator: String? = null,
    val mediaType: String,
    val mimeType: String,
    val filename: String,
    val originalSize: Long,
    val encodedSize: Long,
    val totalChunks: Int,
    val sha256: ByteArray,
    val codec: String = "",
    val width: Int = 0,
    val height: Int = 0,
    val sampleRate: Int = 0,
    val durationMs: Long = 0,
    val status: String,
    val sourcePath: String? = null,
    val partialPath: String? = null,
    val completedPath: String? = null,
    val ratchetOffer: ByteArray? = null,
    val receivedBitmap: ByteArray = ByteArray(0),
    val sentBitmap: ByteArray = ByteArray(0),
    val acknowledgedBitmap: ByteArray = ByteArray(0),
    val controlSequence: Int = 0,
    val smsSent: Int = 0,
    val smsDelivered: Int = 0,
    val smsReceived: Int = 0,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val expiresAt: Long,
)

enum class AttachmentTransferStatus {
    PREPARING,
    WAITING_ACCEPT,
    OFFERED,
    SENDING,
    RECEIVING,
    PAUSED,
    RETRYING,
    VERIFYING,
    COMPLETED,
    FAILED,
    CANCELLED;

    val terminal: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}
