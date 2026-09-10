package com.afkanerd.deku.attachments.crypto

import com.afkanerd.deku.attachments.protocol.AttachmentProtocolFlags
import com.afkanerd.deku.attachments.protocol.TransferId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CloudMediaContainerTest {
    @Test
    fun streamRoundTripAndTamperFailureAreAtomic() {
        val directory = Files.createTempDirectory("cloud-media-container").toFile()
        try {
            val source = directory.resolve("source.bin")
            val encrypted = directory.resolve("encrypted.dkc")
            val restored = directory.resolve("restored.bin")
            val rejected = directory.resolve("rejected.bin")
            val plaintext = ByteArray(128 * 1024 + 17) { (it * 11).toByte() }
            source.writeBytes(plaintext)
            val key = ByteArray(32) { (it + 9).toByte() }
            val transferId = TransferId.fromBytes(ByteArray(16) { (15 - it).toByte() })
            val flags = AttachmentProtocolFlags.CLOUD_PAYLOAD

            CloudMediaContainer.encrypt(key, flags, transferId, source, encrypted)
            assertFalse(encrypted.readBytes().containsSlice(plaintext.copyOfRange(0, 64)))
            CloudMediaContainer.decrypt(key, transferId, flags, encrypted, restored)
            assertArrayEquals(plaintext, restored.readBytes())

            val tampered = encrypted.readBytes().also {
                it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
            }
            encrypted.writeBytes(tampered)
            val failed = runCatching {
                CloudMediaContainer.decrypt(key, transferId, flags, encrypted, rejected)
            }
            assertTrue(failed.isFailure)
            assertFalse(rejected.exists())
        } finally {
            directory.deleteRecursively()
        }
    }

    private fun ByteArray.containsSlice(needle: ByteArray): Boolean =
        indices.any { offset ->
            offset + needle.size <= size && needle.indices.all { index ->
                this[offset + index] == needle[index]
            }
        }
}
