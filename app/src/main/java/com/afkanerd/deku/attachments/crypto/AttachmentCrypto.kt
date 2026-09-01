package com.afkanerd.deku.attachments.crypto

import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.deku.attachments.protocol.SmsFrameCodec
import com.afkanerd.deku.attachments.protocol.SmsPacketType
import com.afkanerd.deku.attachments.protocol.TransferId
import com.afkanerd.deku.attachments.protocol.TransferLimits
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.GeneralSecurityException
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AttachmentCrypto {
    enum class Direction(val wireValue: Int) { INITIATOR_TO_RESPONDER(1), RESPONDER_TO_INITIATOR(2) }

    sealed interface DecryptResult {
        data class Success(val plaintext: ByteArray) : DecryptResult
        data object AuthenticationFailed : DecryptResult
    }

    private val nonce = ByteArray(12)

    fun generateMasterKey(random: SecureRandom = SecureRandom()): ByteArray = ByteArray(32).also(random::nextBytes)

    fun encrypt(
        masterKey: ByteArray,
        packetType: SmsPacketType,
        transferId: TransferId,
        index: Int,
        totalChunks: Int,
        plaintext: ByteArray,
        direction: Direction,
        flags: Int = 0,
    ): SmsFrame {
        require(masterKey.size == 32)
        require(packetType.encrypted)
        require(plaintext.size <= TransferLimits.CHUNK_PLAINTEXT_BYTES)
        val ciphertextLength = plaintext.size + TransferLimits.AEAD_TAG_BYTES
        val skeleton = SmsFrame(
            packetType = packetType, flags = flags, transferId = transferId,
            chunkIndex = index, totalChunks = totalChunks, payload = ByteArray(ciphertextLength),
        )
        val key = derivePacketKey(masterKey, skeleton, direction)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(SmsFrameCodec.headerBytes(skeleton, ciphertextLength))
            skeleton.copy(payload = cipher.doFinal(plaintext))
        } finally {
            key.fill(0)
        }
    }

    fun decrypt(masterKey: ByteArray, frame: SmsFrame, direction: Direction): DecryptResult {
        require(masterKey.size == 32)
        require(frame.packetType.encrypted)
        if (frame.payload.size < TransferLimits.AEAD_TAG_BYTES) return DecryptResult.AuthenticationFailed
        val key = derivePacketKey(masterKey, frame, direction)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
            cipher.updateAAD(SmsFrameCodec.headerBytes(frame))
            DecryptResult.Success(cipher.doFinal(frame.payload))
        } catch (_: AEADBadTagException) {
            DecryptResult.AuthenticationFailed
        } catch (_: GeneralSecurityException) {
            DecryptResult.AuthenticationFailed
        } finally {
            key.fill(0)
        }
    }

    private fun derivePacketKey(masterKey: ByteArray, frame: SmsFrame, direction: Direction): ByteArray {
        val salt = frame.transferId.toByteArray()
        val info = ByteBuffer.allocate(1 + 1 + 1 + 2 + 2).order(ByteOrder.BIG_ENDIAN)
            .put(frame.protocolVersion.toByte())
            .put(direction.wireValue.toByte())
            .put(frame.packetType.wireValue.toByte())
            .putShort(frame.chunkIndex.toShort())
            .putShort(frame.totalChunks.toShort())
            .array()
        return HkdfSha256.derive(masterKey, salt, info, 32)
    }
}
