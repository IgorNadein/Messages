package com.afkanerd.deku.attachments.crypto

import com.afkanerd.deku.attachments.protocol.AttachmentProtocolFlags
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.deku.attachments.protocol.TransferLimits
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Streaming, opaque cloud object. The small routing header is authenticated as AEAD AAD. */
object CloudMediaContainer {
    data class Header(
        val flags: Int,
        val transferId: TransferId,
        val plaintextBytes: Long,
        val nonce: ByteArray,
    )

    fun encrypt(
        masterKey: ByteArray,
        flags: Int,
        transferId: TransferId,
        source: File,
        destination: File,
    ) {
        require(masterKey.size == KEY_BYTES)
        require(AttachmentProtocolFlags.isSupported(flags) && AttachmentProtocolFlags.usesCloudPayload(flags))
        require(source.isFile && source.length() in 1..TransferLimits.MAX_MMS_TRANSFER_BYTES.toLong())
        val nonce = ByteArray(NONCE_BYTES).also(SecureRandom()::nextBytes)
        val header = Header(flags, transferId, source.length(), nonce)
        val headerBytes = encodeHeader(header)
        val key = deriveKey(masterKey, transferId, flags)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(headerBytes)
            FileOutputStream(destination).use { rawOutput ->
                rawOutput.write(headerBytes)
                FileInputStream(source).use { input ->
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    while(true) {
                        val count = input.read(buffer)
                        if(count < 0) break
                        cipher.update(buffer, 0, count)?.let { rawOutput.write(it) }
                    }
                }
                rawOutput.write(cipher.doFinal())
                rawOutput.fd.sync()
            }
            check(destination.length() == HEADER_BYTES + source.length() + TAG_BYTES)
        } catch(error: Exception) {
            destination.delete()
            throw error
        } finally {
            key.fill(0)
            nonce.fill(0)
        }
    }

    /** Decrypts into a caller-owned temporary file. The destination is removed on any failure. */
    fun decrypt(
        masterKey: ByteArray,
        expectedTransferId: TransferId,
        expectedFlags: Int,
        source: File,
        destination: File,
    ) {
        require(masterKey.size == KEY_BYTES)
        require(source.isFile && source.length() in (HEADER_BYTES + TAG_BYTES + 1L)..MAX_ENCODED_BYTES)
        val headerBytes = ByteArray(HEADER_BYTES)
        val header = FileInputStream(source).use { input ->
            input.readFully(headerBytes)
            decodeHeader(headerBytes)
        }
        require(header.transferId == expectedTransferId)
        require(header.flags == expectedFlags)
        require(source.length() == HEADER_BYTES + header.plaintextBytes + TAG_BYTES)
        val key = deriveKey(masterKey, header.transferId, header.flags)
        try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, header.nonce))
            cipher.updateAAD(headerBytes)
            FileInputStream(source).use { rawInput ->
                rawInput.skipFully(HEADER_BYTES.toLong())
                FileOutputStream(destination).use { output ->
                    val buffer = ByteArray(STREAM_BUFFER_BYTES)
                    while(true) {
                        val count = rawInput.read(buffer)
                        if(count < 0) break
                        cipher.update(buffer, 0, count)?.let { output.write(it) }
                    }
                    output.write(cipher.doFinal())
                    output.fd.sync()
                }
            }
            check(destination.length() == header.plaintextBytes)
        } catch(error: Exception) {
            destination.delete()
            throw error
        } finally {
            key.fill(0)
            headerBytes.fill(0)
            header.nonce.fill(0)
        }
    }

    private fun encodeHeader(header: Header): ByteArray = ByteBuffer.allocate(HEADER_BYTES)
        .order(ByteOrder.BIG_ENDIAN)
        .putInt(MAGIC)
        .put(VERSION.toByte())
        .put(header.flags.toByte())
        .put(header.transferId.toByteArray())
        .putLong(header.plaintextBytes)
        .put(header.nonce)
        .array()

    private fun decodeHeader(bytes: ByteArray): Header {
        val input = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        require(input.int == MAGIC)
        require((input.get().toInt() and 0xff) == VERSION)
        val flags = input.get().toInt() and 0xff
        require(AttachmentProtocolFlags.isSupported(flags) && AttachmentProtocolFlags.usesCloudPayload(flags))
        val transferId = TransferId.fromBytes(ByteArray(TransferId.BYTE_LENGTH).also(input::get))
        val plaintextBytes = input.long
        require(plaintextBytes in 1..TransferLimits.MAX_MMS_TRANSFER_BYTES.toLong())
        return Header(flags, transferId, plaintextBytes, ByteArray(NONCE_BYTES).also(input::get))
    }

    private fun deriveKey(masterKey: ByteArray, transferId: TransferId, flags: Int): ByteArray =
        HkdfSha256.derive(
            masterKey,
            transferId.toByteArray(),
            "deku-cloud-media-v1:$flags".toByteArray(Charsets.US_ASCII),
            KEY_BYTES,
        )

    private fun FileInputStream.readFully(destination: ByteArray) {
        var offset = 0
        while(offset < destination.size) {
            val count = read(destination, offset, destination.size - offset)
            require(count > 0) { "Truncated cloud media header" }
            offset += count
        }
    }

    private fun FileInputStream.skipFully(bytes: Long) {
        var remaining = bytes
        while(remaining > 0) {
            val skipped = skip(remaining)
            if(skipped > 0) {
                remaining -= skipped
            } else {
                require(read() >= 0) { "Truncated cloud media object" }
                remaining--
            }
        }
    }

    const val HEADER_BYTES = 42
    const val TAG_BYTES = 16
    const val MAX_ENCODED_BYTES =
        0L + HEADER_BYTES + TransferLimits.MAX_MMS_TRANSFER_BYTES + TAG_BYTES
    private const val MAGIC = 0x444b434c // DKCL
    private const val VERSION = 1
    private const val KEY_BYTES = 32
    private const val NONCE_BYTES = 12
    private const val STREAM_BUFFER_BYTES = 32 * 1024
}
