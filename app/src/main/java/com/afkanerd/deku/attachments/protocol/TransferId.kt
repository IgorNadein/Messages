package com.afkanerd.deku.attachments.protocol

import java.security.SecureRandom

class TransferId private constructor(private val value: ByteArray) {
    fun toByteArray(): ByteArray = value.copyOf()

    fun toHex(): String = value.joinToString("") { "%02x".format(it.toInt() and 0xff) }

    override fun equals(other: Any?): Boolean =
        other is TransferId && value.contentEquals(other.value)

    override fun hashCode(): Int = value.contentHashCode()

    override fun toString(): String = toHex()

    companion object {
        const val BYTE_LENGTH: Int = 16

        fun random(random: SecureRandom = SecureRandom()): TransferId =
            TransferId(ByteArray(BYTE_LENGTH).also(random::nextBytes))

        fun fromBytes(bytes: ByteArray): TransferId {
            require(bytes.size == BYTE_LENGTH) { "transferId must be $BYTE_LENGTH bytes" }
            return TransferId(bytes.copyOf())
        }

        fun fromHex(value: String): TransferId {
            require(value.length == BYTE_LENGTH * 2 && value.all { it.isHexDigit() }) {
                "Invalid transferId hex"
            }
            return fromBytes(ByteArray(BYTE_LENGTH) { index ->
                value.substring(index * 2, index * 2 + 2).toInt(16).toByte()
            })
        }

        private fun Char.isHexDigit(): Boolean =
            this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'
    }
}
