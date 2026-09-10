package com.afkanerd.deku.attachments.crypto

import com.afkanerd.deku.attachments.protocol.AttachmentProtocolFlags
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.deku.attachments.protocol.TransferLimits
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** A single opaque MMS part. The header is authenticated but intentionally not encrypted. */
object MmsMediaContainer {
    data class Header(
        val flags: Int,
        val transferId: TransferId,
        val partIndex: Int,
        val totalParts: Int,
        val plaintextBytes: Int,
    )

    data class DecryptedPart(val header: Header, val plaintext: ByteArray)

    sealed interface DecodeResult {
        data class Success(val part: DecryptedPart) : DecodeResult
        data class Rejected(val reason: String) : DecodeResult
        data object AuthenticationFailed : DecodeResult
    }

    fun encrypt(
        masterKey: ByteArray,
        flags: Int,
        transferId: TransferId,
        partIndex: Int,
        totalParts: Int,
        plaintext: ByteArray,
    ): ByteArray {
        require(masterKey.size == KEY_BYTES)
        validate(flags, partIndex, totalParts, plaintext.size)
        val header = Header(flags, transferId, partIndex, totalParts, plaintext.size)
        val headerBytes = encodeHeader(header)
        val key = derivePartKey(masterKey, header)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, NONCE))
            cipher.updateAAD(headerBytes)
            headerBytes + cipher.doFinal(plaintext)
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(masterKey: ByteArray, encoded: ByteArray): DecodeResult {
        if(masterKey.size != KEY_BYTES) return DecodeResult.Rejected("Invalid media key")
        if(encoded.size !in (HEADER_BYTES + TAG_BYTES + 1)..MAX_ENCODED_PART_BYTES) {
            return DecodeResult.Rejected("MMS media part length is out of bounds")
        }
        val header = decodeHeader(encoded) ?: return DecodeResult.Rejected("Invalid MMS media header")
        val validation = runCatching {
            validate(header.flags, header.partIndex, header.totalParts, header.plaintextBytes)
            require(encoded.size == HEADER_BYTES + header.plaintextBytes + TAG_BYTES)
        }
        if(validation.isFailure) return DecodeResult.Rejected("Invalid MMS media metadata")
        val key = derivePartKey(masterKey, header)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, NONCE))
            cipher.updateAAD(encoded.copyOfRange(0, HEADER_BYTES))
            DecodeResult.Success(
                DecryptedPart(header, cipher.doFinal(encoded, HEADER_BYTES, encoded.size - HEADER_BYTES))
            )
        } catch (_: AEADBadTagException) {
            DecodeResult.AuthenticationFailed
        } catch (_: GeneralSecurityException) {
            DecodeResult.AuthenticationFailed
        } finally {
            key.fill(0)
        }
    }

    fun inspect(encoded: ByteArray): Header? {
        if(encoded.size !in (HEADER_BYTES + TAG_BYTES + 1)..MAX_ENCODED_PART_BYTES) return null
        val header = decodeHeader(encoded) ?: return null
        return runCatching {
            validate(header.flags, header.partIndex, header.totalParts, header.plaintextBytes)
            require(encoded.size == HEADER_BYTES + header.plaintextBytes + TAG_BYTES)
            header
        }.getOrNull()
    }

    private fun encodeHeader(header: Header): ByteArray = ByteBuffer.allocate(HEADER_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(MAGIC)
        .put(VERSION.toByte())
        .put(header.flags.toByte())
        .put(header.transferId.toByteArray())
        .putInt(header.partIndex)
        .putInt(header.totalParts)
        .putInt(header.plaintextBytes)
        .array()

    private fun decodeHeader(encoded: ByteArray): Header? = runCatching {
        val input = ByteBuffer.wrap(encoded, 0, HEADER_BYTES).order(ByteOrder.BIG_ENDIAN)
        require(input.int == MAGIC && (input.get().toInt() and 0xff) == VERSION)
        Header(
            flags = input.get().toInt() and 0xff,
            transferId = TransferId.fromBytes(ByteArray(TransferId.BYTE_LENGTH).also(input::get)),
            partIndex = input.int,
            totalParts = input.int,
            plaintextBytes = input.int,
        )
    }.getOrNull()

    private fun validate(flags: Int, partIndex: Int, totalParts: Int, plaintextBytes: Int) {
        require(AttachmentProtocolFlags.isSupported(flags))
        require(AttachmentProtocolFlags.usesMmsPayload(flags))
        require(totalParts in 1..TransferLimits.MAX_TOTAL_CHUNKS)
        require(partIndex in 0 until totalParts)
        require(plaintextBytes in 1..TransferLimits.MMS_PART_PLAINTEXT_BYTES)
    }

    private fun derivePartKey(masterKey: ByteArray, header: Header): ByteArray {
        val infoPrefix = "deku-mms-media-part-v1".toByteArray(Charsets.US_ASCII)
        val info = ByteBuffer.allocate(infoPrefix.size + 1 + 4 + 4)
            .order(ByteOrder.BIG_ENDIAN)
            .put(infoPrefix)
            .put(header.flags.toByte())
            .putInt(header.partIndex)
            .putInt(header.totalParts)
            .array()
        return HkdfSha256.derive(masterKey, header.transferId.toByteArray(), info, KEY_BYTES)
    }

    private const val MAGIC = 0x444b4d4d // DKMM
    private const val VERSION = 1
    private const val KEY_BYTES = 32
    private const val TAG_BYTES = 16
    private const val HEADER_BYTES = 34
    const val MAX_ENCODED_PART_BYTES = HEADER_BYTES + TransferLimits.MMS_PART_PLAINTEXT_BYTES + TAG_BYTES
    private val NONCE = ByteArray(12)
}
