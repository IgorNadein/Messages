package com.afkanerd.deku.attachments.protocol

import org.junit.Assert.assertEquals
import org.junit.Test

class AttachmentContextCodecTest {
    @Test
    fun roundTripBindsManifestAndTransferKey() {
        val size = 1024L
        val context = AttachmentContext(
            AttachmentManifest(
                transferId = TransferId.random(),
                mediaType = AttachmentManifest.MediaType.VOICE,
                mimeType = "audio/ogg",
                filename = "голос.ogg",
                originalSize = size,
                encodedSize = size,
                totalChunks = TransferLimits.chunkCount(size),
                sha256 = ByteArray(32) { it.toByte() },
                codec = "opus",
                sampleRate = 16_000,
                durationMs = 10_000,
            ),
            ByteArray(32) { (255 - it).toByte() },
        )
        assertEquals(context, AttachmentContextCodec.decode(AttachmentContextCodec.encode(context)))
    }
}
