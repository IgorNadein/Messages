package com.afkanerd.deku.attachments.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AttachmentManifestCodecTest {
    @Test
    fun unicodeManifestRoundTrip() {
        val manifest = manifest(filename = "фото-東京.webp", size = 10_000)
        val encoded = AttachmentManifestCodec.encode(manifest)
        assertTrue(encoded.size <= TransferLimits.MAX_MANIFEST_BYTES)
        assertEquals(manifest, AttachmentManifestCodec.decode(encoded))
    }

    @Test
    fun dangerousFilenameIsNeutralized() {
        assertEquals("_secret.apk.safe", AttachmentManifestCodec.sanitizeFilename("../secret.apk"))
        assertEquals("a_b.txt", AttachmentManifestCodec.sanitizeFilename("a/b.txt"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsHugeDeclaredFile() {
        AttachmentManifestCodec.encode(
            manifest(size = TransferLimits.MAX_MMS_TRANSFER_BYTES.toLong() + 1)
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsInconsistentChunkCount() {
        AttachmentManifestCodec.encode(manifest(size = 100).copy(totalChunks = 4000))
    }

    private fun manifest(filename: String = "data.bin", size: Long): AttachmentManifest = AttachmentManifest(
        transferId = TransferId.fromBytes(ByteArray(16) { it.toByte() }),
        mediaType = AttachmentManifest.MediaType.FILE,
        mimeType = "application/octet-stream",
        filename = filename,
        originalSize = size,
        encodedSize = size,
        totalChunks = if (size <= TransferLimits.MAX_TRANSFER_BYTES) TransferLimits.chunkCount(size) else 1,
        sha256 = ByteArray(32) { (it * 5).toByte() },
    )
}
