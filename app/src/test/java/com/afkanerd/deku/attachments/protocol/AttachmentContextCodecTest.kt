package com.afkanerd.deku.attachments.protocol

import com.afkanerd.deku.attachments.AttachmentWireTransport
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

    @Test
    fun cloudRoundTripBindsStableTransportAndRemoteSource() {
        val size = 4096L
        val context = AttachmentContext(
            manifest = AttachmentManifest(
                transferId = TransferId.random(),
                mediaType = AttachmentManifest.MediaType.FILE,
                mimeType = "application/octet-stream",
                filename = "archive.bin",
                originalSize = size,
                encodedSize = size,
                totalChunks = 1,
                sha256 = ByteArray(32) { (it * 3).toByte() },
            ),
            masterKey = ByteArray(32) { (it + 1).toByte() },
            transport = AttachmentWireTransport.CLOUD_STORAGE,
            chunkPlaintextBytes = TransferLimits.MMS_PART_PLAINTEXT_BYTES,
            remoteSource = RemoteAttachmentSource(
                providerCode = 2,
                locator = "https://example.test/public/object-id",
            ),
        )

        assertEquals(context, AttachmentContextCodec.decode(AttachmentContextCodec.encode(context)))
    }

    @Test
    fun `ordinary sms transport survives context round trip`() {
        val size = 78L
        val context = AttachmentContext(
            manifest = AttachmentManifest(
                transferId = TransferId.random(),
                mediaType = AttachmentManifest.MediaType.FILE,
                mimeType = "application/octet-stream",
                filename = "packet.bin",
                originalSize = size,
                encodedSize = size,
                totalChunks = 1,
                sha256 = ByteArray(32),
            ),
            masterKey = ByteArray(32) { 4 },
            transport = AttachmentWireTransport.STANDARD_SMS,
        )
        assertEquals(context, AttachmentContextCodec.decode(AttachmentContextCodec.encode(context)))
    }
}
