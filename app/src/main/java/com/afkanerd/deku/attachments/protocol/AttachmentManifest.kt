package com.afkanerd.deku.attachments.protocol

data class AttachmentManifest(
    val protocolVersion: Int = TransferLimits.PROTOCOL_VERSION,
    val transferId: TransferId,
    val mediaType: MediaType,
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
) {
    enum class MediaType(val wireValue: Int) {
        FILE(1), PHOTO(2), VOICE(3);

        companion object {
            fun fromWireValue(value: Int): MediaType? = entries.firstOrNull { it.wireValue == value }
        }
    }

    override fun equals(other: Any?): Boolean = other is AttachmentManifest &&
        protocolVersion == other.protocolVersion && transferId == other.transferId &&
        mediaType == other.mediaType && mimeType == other.mimeType && filename == other.filename &&
        originalSize == other.originalSize && encodedSize == other.encodedSize &&
        totalChunks == other.totalChunks && sha256.contentEquals(other.sha256) &&
        codec == other.codec && width == other.width && height == other.height &&
        sampleRate == other.sampleRate && durationMs == other.durationMs

    override fun hashCode(): Int = transferId.hashCode()
}
