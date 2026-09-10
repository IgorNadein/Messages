package com.afkanerd.deku.attachments.protocol

object TransferLimits {
    const val PROTOCOL_VERSION: Int = 1
    const val FRAME_BYTES: Int = 120
    const val FRAME_HEADER_BYTES: Int = 26
    const val AEAD_TAG_BYTES: Int = 16
    const val FRAME_PAYLOAD_BYTES: Int = FRAME_BYTES - FRAME_HEADER_BYTES
    const val CHUNK_PLAINTEXT_BYTES: Int = FRAME_PAYLOAD_BYTES - AEAD_TAG_BYTES

    const val MAX_TRANSFER_BYTES: Int = 256 * 1024
    const val MAX_MMS_TRANSFER_BYTES: Int = 25 * 1024 * 1024
    const val MMS_PART_PLAINTEXT_BYTES: Int = 64 * 1024
    const val CONFIRM_TRANSFER_BYTES: Int = 32 * 1024
    const val MAX_TOTAL_CHUNKS: Int = 4096
    const val CONFIRM_SMS_COUNT: Int = 500
    const val MAX_MANIFEST_BYTES: Int = 1024
    const val MAX_FILENAME_BYTES: Int = 128
    const val MAX_MIME_BYTES: Int = 96
    const val MAX_CODEC_BYTES: Int = 32
    const val MAX_ACTIVE_TRANSFERS_PER_CONTACT: Int = 4
    const val MAX_PENDING_TRANSFERS: Int = 16
    const val MAX_OFFER_FRAGMENTS: Int = 32
    const val ACK_WINDOW_BITS: Int = 64

    fun chunkCount(encodedBytes: Long): Int {
        require(encodedBytes in 0..MAX_TRANSFER_BYTES.toLong()) { "Encoded size is out of bounds" }
        if (encodedBytes == 0L) return 0
        return ((encodedBytes + CHUNK_PLAINTEXT_BYTES - 1) / CHUNK_PLAINTEXT_BYTES).toInt()
    }

    fun mmsPartCount(encodedBytes: Long): Int = partCount(
        encodedBytes = encodedBytes,
        partBytes = MMS_PART_PLAINTEXT_BYTES,
        maxBytes = MAX_MMS_TRANSFER_BYTES,
    )

    fun partCount(encodedBytes: Long, partBytes: Int, maxBytes: Int): Int {
        require(partBytes > 0)
        require(encodedBytes in 0..maxBytes.toLong()) { "Encoded size is out of bounds" }
        if(encodedBytes == 0L) return 0
        return ((encodedBytes + partBytes - 1) / partBytes).toInt()
    }

    fun estimatedDataSms(encodedBytes: Long, offerBytes: Int = 0): Int {
        val chunks = chunkCount(encodedBytes)
        require(offerBytes in 0..MAX_MANIFEST_BYTES) { "Offer size is out of bounds" }
        val offers = if (offerBytes == 0) 0 else
            (offerBytes + FRAME_PAYLOAD_BYTES - 1) / FRAME_PAYLOAD_BYTES
        // One ACCEPT and one COMPLETE are the fixed control minimum. ACK traffic is
        // intentionally excluded because its exact count depends on loss and pacing.
        return chunks + offers + if (chunks > 0 || offers > 0) 2 else 0
    }
}
