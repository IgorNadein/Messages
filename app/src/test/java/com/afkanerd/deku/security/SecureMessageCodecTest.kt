package com.afkanerd.deku.security

import java.util.Base64
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.random.Random
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureMessageCodecTest {
    @Test
    fun messageLengthIsUnsignedAbove127() {
        val header = ByteArray(40) { it.toByte() }
        val cipherText = ByteArray(200) { (it * 3).toByte() }
        val encoded = byteArrayOf(
            SecureMessageCodec.TYPE_MESSAGE,
            header.size.toByte(),
            cipherText.size.toByte(),
        ) + header + cipherText

        val decoded = SecureMessageCodec.decodeMessageOrNull(encoded)!!
        assertArrayEquals(header, decoded.header)
        assertArrayEquals(cipherText, decoded.cipherText)
    }

    @Test
    fun textDecoderAcceptsMultipartWhitespaceButNotTrailingPayload() {
        val header = ByteArray(40)
        val cipherText = ByteArray(48)
        val packet = byteArrayOf(
            SecureMessageCodec.TYPE_MESSAGE,
            header.size.toByte(),
            cipherText.size.toByte(),
        ) + header + cipherText
        val base64 = Base64.getEncoder().encodeToString(packet)
        val folded = base64.chunked(12).joinToString("\n")

        assertArrayEquals(cipherText, SecureMessageCodec.decodeTextOrNull(folded)!!.cipherText)
        assertNull(SecureMessageCodec.decodeMessageOrNull(packet + 0x00))
    }

    @Test
    fun keyExchangeRequiresExactX25519Length() {
        val key = ByteArray(32) { it.toByte() }
        val packet = byteArrayOf(
            SecureMessageCodec.TYPE_REQUEST,
            key.size.toByte(),
        ) + key

        val decoded = SecureMessageCodec.decodeKeyExchangeOrNull(packet)!!
        assertEquals(SecureMessageCodec.TYPE_REQUEST, decoded.type)
        assertArrayEquals(key, decoded.publicKey)
        assertNull(SecureMessageCodec.decodeKeyExchangeOrNull(packet.copyOf(packet.size - 1)))
        assertNull(SecureMessageCodec.decodeKeyExchangeOrNull(packet + 0x00))
    }

    @Test
    fun v2SupportsCiphertextLongerThan255Bytes() {
        val header = ByteArray(40) { it.toByte() }
        val cipherText = ByteArray(4096) { (it * 7).toByte() }
        val prefix = ByteBuffer.allocate(9)
            .order(ByteOrder.BIG_ENDIAN)
            .put(SecureMessageCodec.TYPE_MESSAGE)
            .put(0)
            .put(2)
            .putShort(header.size.toShort())
            .putInt(cipherText.size)
            .array()

        val decoded = SecureMessageCodec.decodeMessageOrNull(prefix + header + cipherText)!!
        assertArrayEquals(header, decoded.header)
        assertArrayEquals(cipherText, decoded.cipherText)
    }

    @Test
    fun malformedPacketsNeverThrow() {
        val random = Random(0x5ec0de)
        repeat(20_000) {
            val bytes = random.nextBytes(random.nextInt(0, 512))
            val message = runCatching { SecureMessageCodec.decodeMessageOrNull(bytes) }
            val exchange = runCatching { SecureMessageCodec.decodeKeyExchangeOrNull(bytes) }
            assertTrue(message.isSuccess)
            assertTrue(exchange.isSuccess)
        }
    }
}
