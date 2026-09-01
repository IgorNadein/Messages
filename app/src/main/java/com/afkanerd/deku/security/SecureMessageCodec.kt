package com.afkanerd.deku.security

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.io.encoding.Base64

/** Strict parser for the legacy Deku secure-SMS wire format. */
object SecureMessageCodec {
    const val TYPE_REQUEST: Byte = 0x01
    const val TYPE_ACCEPT: Byte = 0x02
    const val TYPE_MESSAGE: Byte = 0x03

    data class EncryptedMessage(
        val header: ByteArray,
        val cipherText: ByteArray,
    )

    data class KeyExchange(
        val type: Byte,
        val publicKey: ByteArray,
        val identityPublicKey: ByteArray? = null,
    )

    fun decodeTextOrNull(encoded: String): EncryptedMessage? = try {
        val compact = encoded.filterNot(Char::isWhitespace)
        if(compact.isEmpty() || compact.length > MAX_BASE64_TEXT_SIZE) return null
        decodeMessageOrNull(Base64.decode(compact))
    } catch (_: IllegalArgumentException) {
        null
    }

    fun decodeKeyExchangeTextOrNull(encoded: String): KeyExchange? = try {
        val compact = encoded.filterNot(Char::isWhitespace)
        if(compact.isEmpty() || compact.length > MAX_BASE64_TEXT_SIZE) return null
        decodeKeyExchangeOrNull(Base64.decode(compact))
    } catch (_: IllegalArgumentException) {
        null
    }

    fun decodeMessageOrNull(data: ByteArray): EncryptedMessage? {
        if(data.size < LEGACY_MESSAGE_PREFIX_SIZE || data[0] != TYPE_MESSAGE) return null

        val prefixSize: Int
        val headerLength: Int
        val cipherLength: Int
        if(data[1] == V2_MARKER) {
            if(data.size < V2_MESSAGE_PREFIX_SIZE || data[2] != V2_VERSION) return null
            val lengths = ByteBuffer.wrap(data, 3, 6).order(ByteOrder.BIG_ENDIAN)
            headerLength = lengths.short.toInt() and 0xffff
            cipherLength = lengths.int
            prefixSize = V2_MESSAGE_PREFIX_SIZE
        } else {
            headerLength = data[1].toInt() and 0xff
            cipherLength = data[2].toInt() and 0xff
            prefixSize = LEGACY_MESSAGE_PREFIX_SIZE
        }
        if(headerLength != SERIALIZED_HEADER_SIZE) return null
        if(cipherLength < MIN_CIPHER_SIZE || cipherLength > MAX_CIPHER_SIZE) return null

        val expectedSize = prefixSize.toLong() + headerLength + cipherLength
        if(expectedSize != data.size.toLong()) return null

        val headerEnd = prefixSize + headerLength
        return EncryptedMessage(
            header = data.copyOfRange(prefixSize, headerEnd),
            cipherText = data.copyOfRange(headerEnd, data.size),
        )
    }

    fun decodeKeyExchangeOrNull(data: ByteArray): KeyExchange? {
        if(data.size < KEY_PREFIX_SIZE) return null
        if(data[0] != TYPE_REQUEST && data[0] != TYPE_ACCEPT) return null

        if(data.size == SIGNED_KEY_EXCHANGE_SIZE &&
            data[1] == V2_MARKER && data[2] == V2_VERSION
        ) {
            return KeyExchange(
                type = data[0],
                publicKey = data.copyOfRange(3, 3 + X25519_PUBLIC_KEY_SIZE),
                identityPublicKey = data.copyOfRange(
                    3 + X25519_PUBLIC_KEY_SIZE,
                    3 + X25519_PUBLIC_KEY_SIZE * 2,
                ),
            )
        }

        val keyLength = data[1].toInt() and 0xff
        if(keyLength != X25519_PUBLIC_KEY_SIZE ||
            data.size != KEY_PREFIX_SIZE + keyLength
        ) return null

        return KeyExchange(data[0], data.copyOfRange(KEY_PREFIX_SIZE, data.size))
    }

    private const val LEGACY_MESSAGE_PREFIX_SIZE = 3
    private const val V2_MESSAGE_PREFIX_SIZE = 9
    private const val V2_MARKER: Byte = 0
    private const val V2_VERSION: Byte = 2
    private const val KEY_PREFIX_SIZE = 2
    private const val X25519_PUBLIC_KEY_SIZE = 32
    private const val ED25519_PUBLIC_KEY_SIZE = 32
    private const val ED25519_SIGNATURE_SIZE = 64
    private const val SIGNED_KEY_EXCHANGE_SIZE = 3 + X25519_PUBLIC_KEY_SIZE +
        ED25519_PUBLIC_KEY_SIZE + ED25519_SIGNATURE_SIZE
    private const val SERIALIZED_HEADER_SIZE = 40
    private const val MIN_CIPHER_SIZE = 48 // one AES block plus HMAC-SHA256
    private const val MAX_CIPHER_SIZE = 1024 * 1024
    private const val MAX_BASE64_TEXT_SIZE = 1_500_000
}
