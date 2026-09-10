package com.afkanerd.deku.attachments.crypto

import com.afkanerd.deku.attachments.protocol.AttachmentProtocolFlags
import com.afkanerd.deku.attachments.protocol.TransferId
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MmsMediaContainerTest {
    private val transferId = TransferId.fromBytes(ByteArray(16) { it.toByte() })
    private val key = ByteArray(32) { (it * 7).toByte() }

    @Test
    fun everyPartRoundTripsWithBoundMetadata() {
        repeat(3) { index ->
            val plaintext = ByteArray(1024 + index) { (it + index).toByte() }
            val encoded = MmsMediaContainer.encrypt(
                masterKey = key,
                flags = AttachmentProtocolFlags.MMS_PAYLOAD,
                transferId = transferId,
                partIndex = index,
                totalParts = 3,
                plaintext = plaintext,
            )
            val result = MmsMediaContainer.decrypt(key, encoded)
                as MmsMediaContainer.DecodeResult.Success
            assertEquals(index, result.part.header.partIndex)
            assertEquals(3, result.part.header.totalParts)
            assertArrayEquals(plaintext, result.part.plaintext)
        }
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val encoded = MmsMediaContainer.encrypt(
            masterKey = key,
            flags = AttachmentProtocolFlags.MMS_PAYLOAD,
            transferId = transferId,
            partIndex = 0,
            totalParts = 1,
            plaintext = ByteArray(512) { it.toByte() },
        ).also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }

        assertTrue(
            MmsMediaContainer.decrypt(key, encoded) is
                MmsMediaContainer.DecodeResult.AuthenticationFailed
        )
    }
}
