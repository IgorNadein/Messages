package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import android.content.Context

internal data class SignedKeyExchange(
    val type: EncryptionController.MessageRequestType,
    val sessionPublicKey: ByteArray,
    val identityPublicKey: ByteArray?,
)

internal object SignedKeyExchangeCodec {
    suspend fun encode(
        context: Context,
        type: EncryptionController.MessageRequestType,
        sessionPublicKey: ByteArray,
    ): ByteArray {
        require(type != EncryptionController.MessageRequestType.TYPE_MESSAGE)
        require(sessionPublicKey.size == KEY_SIZE)
        val identityPublicKey = IdentityKeyManager.localPublicKey(context)
        val signedData = signatureInput(type.code, sessionPublicKey, identityPublicKey)
        val signature = IdentityKeyManager.sign(context, signedData)
        require(signature.size == SIGNATURE_SIZE)
        return assembleSigned(type, sessionPublicKey, identityPublicKey, signature)
    }

    suspend fun decode(
        context: Context,
        address: String,
        data: ByteArray,
    ): SignedKeyExchange {
        val decoded = decodeAndVerify(data)
        if(decoded.identityPublicKey == null) {
            throw SecurityException(
                "Unsigned legacy key exchange is no longer accepted; update both devices"
            )
        } else {
            IdentityKeyManager.recordRemoteIdentity(context, address, decoded.identityPublicKey)
        }
        return decoded
    }

    internal fun decodeAndVerify(data: ByteArray): SignedKeyExchange {
        val type = data.firstOrNull()?.let(EncryptionController.MessageRequestType::fromCode)
            ?: throw SecurityException("Unknown key-exchange type")
        require(type != EncryptionController.MessageRequestType.TYPE_MESSAGE)

        if(data.size == LEGACY_SIZE) {
            val length = data[1].toInt() and 0xff
            require(length == KEY_SIZE)
            return SignedKeyExchange(type, data.copyOfRange(2, data.size), null)
        }
        require(data.size == V2_SIZE && data[1] == V2_MARKER && data[2] == V2_VERSION) {
            "Malformed signed key-exchange payload"
        }
        val sessionPublicKey = data.copyOfRange(3, 3 + KEY_SIZE)
        val identityPublicKey = data.copyOfRange(3 + KEY_SIZE, 3 + KEY_SIZE * 2)
        val signature = data.copyOfRange(3 + KEY_SIZE * 2, data.size)
        require(IdentityKeyManager.verify(
            identityPublicKey,
            signature,
            signatureInput(type.code, sessionPublicKey, identityPublicKey),
        )) { "Invalid key-exchange identity signature" }
        return SignedKeyExchange(type, sessionPublicKey, identityPublicKey)
    }

    internal fun signatureInput(type: Byte, sessionKey: ByteArray, identityKey: ByteArray) =
        SIGNATURE_DOMAIN + byteArrayOf(type, V2_VERSION) + sessionKey + identityKey

    internal fun assembleSigned(
        type: EncryptionController.MessageRequestType,
        sessionPublicKey: ByteArray,
        identityPublicKey: ByteArray,
        signature: ByteArray,
    ): ByteArray {
        require(type != EncryptionController.MessageRequestType.TYPE_MESSAGE)
        require(sessionPublicKey.size == KEY_SIZE)
        require(identityPublicKey.size == KEY_SIZE)
        require(signature.size == SIGNATURE_SIZE)
        return byteArrayOf(type.code, V2_MARKER, V2_VERSION) +
            sessionPublicKey + identityPublicKey + signature
    }

    private const val KEY_SIZE = 32
    private const val SIGNATURE_SIZE = 64
    private const val LEGACY_SIZE = 34
    private const val V2_SIZE = 3 + KEY_SIZE + KEY_SIZE + SIGNATURE_SIZE
    private const val V2_MARKER: Byte = 0
    private const val V2_VERSION: Byte = 2
    private val SIGNATURE_DOMAIN = "DekuSMS signed key exchange v2".encodeToByteArray()
}
