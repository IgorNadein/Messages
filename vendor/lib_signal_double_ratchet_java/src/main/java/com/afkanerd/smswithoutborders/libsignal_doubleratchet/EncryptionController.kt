package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import android.content.Context
import android.util.Base64
import android.widget.Toast
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.dataStore
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.getEncryptedBinaryData
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.getKeypairValues
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.removeEncryptedBinaryData
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.removeSessionKeypairValues
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.saveBinaryDataEncrypted
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.setKeypairValues
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.libsignal.Headers
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.libsignal.Ratchets
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.libsignal.States
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.json.JSONObject
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

object EncryptionController {

    @Serializable
    enum class SecureRequestMode {
        REQUEST_NONE,
        REQUEST_REQUESTED,
        REQUEST_RECEIVED,
        REQUEST_ACCEPTED,
        REQUEST_BROKEN,
    }

    enum class MessageRequestType(val code: Byte) {
        TYPE_REQUEST(0x01.toByte()),
        TYPE_ACCEPT(0x02.toByte()),
        TYPE_MESSAGE(0x03.toByte());

        companion object {
            fun fromCode(code: Byte): MessageRequestType? =
                entries.find { it.code == code } // Kotlin 1.9+, use values() before that

            fun fromMessage(message: ByteArray): MessageRequestType? =
                message.firstOrNull()?.let(::fromCode)
        }
    }

    enum class SessionRole {
        INITIATOR,
        RESPONDER,
    }

    private fun extractMessage(data: ByteArray) : Pair<Headers, ByteArray> {
        require(data.size >= LEGACY_MESSAGE_PREFIX_SIZE) { "Truncated encrypted payload" }
        require(MessageRequestType.fromCode(data[0]) == MessageRequestType.TYPE_MESSAGE) {
            "Unexpected encrypted payload type"
        }

        val prefixSize: Int
        val lenHeader: Int
        val lenMessage: Int
        if(data[1] == V2_MARKER) {
            require(data.size >= V2_MESSAGE_PREFIX_SIZE) { "Truncated v2 encrypted payload" }
            require(data[2] == V2_VERSION) { "Unsupported encrypted payload version" }
            val lengths = ByteBuffer.wrap(data, 3, 6).order(ByteOrder.BIG_ENDIAN)
            lenHeader = lengths.short.toInt() and 0xffff
            lenMessage = lengths.int
            prefixSize = V2_MESSAGE_PREFIX_SIZE
        } else {
            lenHeader = data[1].toInt() and 0xff
            lenMessage = data[2].toInt() and 0xff
            prefixSize = LEGACY_MESSAGE_PREFIX_SIZE
        }

        require(lenHeader == SERIALIZED_HEADER_SIZE) { "Invalid encrypted header length" }
        require(lenMessage >= MIN_CIPHER_TEXT_SIZE) { "Invalid ciphertext length" }
        val expectedSize = prefixSize.toLong() + lenHeader.toLong() + lenMessage.toLong()
        require(expectedSize == data.size.toLong()) { "Encrypted payload length mismatch" }

        val headerEnd = prefixSize + lenHeader
        val header = data.copyOfRange(prefixSize, headerEnd)
        val message = data.copyOfRange(headerEnd, data.size)
        return Pair(Headers.deSerializeHeader(header), message)
    }

    private suspend fun formatRequestPublicKey(
        context: Context,
        publicKey: ByteArray,
        type: MessageRequestType
    ) : ByteArray = SignedKeyExchangeCodec.encode(context, type, publicKey)

    @OptIn(ExperimentalUnsignedTypes::class)
    private fun formatMessage(
        header: Headers,
        cipherText: ByteArray
    ) : ByteArray {
        val serializedHeader = header.serialized
        require(serializedHeader.size == SERIALIZED_HEADER_SIZE) { "Invalid ratchet header" }
        require(cipherText.size >= MIN_CIPHER_TEXT_SIZE) { "Invalid ciphertext" }

        if(cipherText.size <= UByte.MAX_VALUE.toInt()) {
            val mn = ubyteArrayOf(MessageRequestType.TYPE_MESSAGE.code.toUByte())
            val lenHeader = ubyteArrayOf(serializedHeader.size.toUByte())
            val lenMessage = ubyteArrayOf(cipherText.size.toUByte())
            return (mn + lenHeader + lenMessage).toByteArray() +
                serializedHeader + cipherText
        }

        val prefix = ByteBuffer.allocate(V2_MESSAGE_PREFIX_SIZE)
            .order(ByteOrder.BIG_ENDIAN)
            .put(MessageRequestType.TYPE_MESSAGE.code)
            .put(V2_MARKER)
            .put(V2_VERSION)
            .putShort(serializedHeader.size.toShort())
            .putInt(cipherText.size)
            .array()
        return prefix + serializedHeader + cipherText
    }

    suspend fun sendRequest(
        context: Context,
        address: String,
        mode: SecureRequestMode,
    ): ByteArray = lockFor(address).withLock {
        try {
            val publicKey = generateIdentityPublicKeys(context, address)

            var type: MessageRequestType? = null
            val role: SessionRole
            val mode = when(mode) {
                SecureRequestMode.REQUEST_RECEIVED -> {
                    type = MessageRequestType.TYPE_ACCEPT
                    role = SessionRole.RESPONDER
                    SecureRequestMode.REQUEST_ACCEPTED
                }
                else -> {
                    type = MessageRequestType.TYPE_REQUEST
                    role = SessionRole.INITIATOR
                    SecureRequestMode.REQUEST_REQUESTED
                }
            }

            context.setEncryptionModeStates(address, mode, role = role)
            return formatRequestPublicKey(context, publicKey, type)
        } catch (e: Exception) {
            throw e
        }
    }

    /**
     * Discards only ephemeral session material and starts a signed key exchange.
     * The permanent Ed25519 identity and the contact's verification status remain
     * intact, so a session repair cannot silently replace a verified identity.
     */
    suspend fun renewSession(context: Context, address: String): ByteArray =
        lockFor(address).withLock {
            context.removeEncryptedBinaryData(address + RATCHET_SUFFIX)
            context.removeSessionKeypairValues(address)
            context.removeEncryptionModeStates(address)

            val publicKey = generateIdentityPublicKeys(context, address)
            context.setEncryptionModeStates(
                address,
                SecureRequestMode.REQUEST_REQUESTED,
                role = SessionRole.INITIATOR,
            )
            formatRequestPublicKey(context, publicKey, MessageRequestType.TYPE_REQUEST)
        }

    /**
     * Fail closed after a ratchet/authentication error. Session material is kept
     * only for diagnostics and must never be used for another outbound message.
     * A signed renewal is required to leave this state.
     */
    suspend fun markSessionBroken(context: Context, address: String) {
        lockFor(address).withLock {
            context.setEncryptionModeStates(address, SecureRequestMode.REQUEST_BROKEN)
        }
    }

    suspend fun receiveRequest(
        context: Context,
        address: String,
        publicKey: ByteArray,
    ) : ByteArray? = lockFor(address).withLock {
        val keyExchange = SignedKeyExchangeCodec.decode(context, address, publicKey)
        val type = keyExchange.type
        val sessionPublicKey = keyExchange.sessionPublicKey
        try {
            val mode = when(type) {
                MessageRequestType.TYPE_REQUEST -> {
                    val currentMode = context.getEncryptionModeStatesSync(address)
                        ?.let(SavedEncryptedModes::deserialize)
                    val localSessionPublicKey = context.getKeypairValues(address).first
                    if(currentMode?.mode == SecureRequestMode.REQUEST_REQUESTED &&
                        localSessionPublicKey != null &&
                        keepInitiatorOnSimultaneousRequest(
                            localSessionPublicKey,
                            sessionPublicKey,
                        )
                    ) {
                        context.setEncryptionModeStates(
                            address,
                            SecureRequestMode.REQUEST_REQUESTED,
                            role = SessionRole.INITIATOR,
                        )
                        return@withLock sessionPublicKey
                    }
                    context.removeEncryptionRatchetStates(address, showFeedback = false)
                    SecureRequestMode.REQUEST_RECEIVED
                }
                MessageRequestType.TYPE_ACCEPT -> {
                    context.removeEncryptionRatchetStates(address, showFeedback = false)
                    SecureRequestMode.REQUEST_ACCEPTED
                }
                else -> return@withLock null
            }
            context.setEncryptionModeStates(
                address,
                mode,
                sessionPublicKey,
                role = when(type) {
                    MessageRequestType.TYPE_REQUEST -> SessionRole.RESPONDER
                    MessageRequestType.TYPE_ACCEPT -> SessionRole.INITIATOR
                    else -> null
                },
            )
        } catch (e: Exception) {
            throw e
        }
        sessionPublicKey
    }

    @Throws
    private suspend fun generateIdentityPublicKeys(
        context: Context,
        address: String
    ): ByteArray {
        try {
            val libSigCurve25519 = SecurityCurve25519()
            val publicKey = libSigCurve25519.generateKey()
            context.setKeypairValues(address, publicKey, libSigCurve25519.privateKey)
            return publicKey
        } catch (e: Exception) {
            throw e
        }
    }

    @Throws
    suspend fun decrypt(
        context: Context,
        address: String,
        text: String
    ): String? = lockFor(address).withLock {
        val data = Base64.decode(text, Base64.DEFAULT)
        decryptBytesLocked(context, address, data)?.decodeToString()
    }

    /**
     * Encrypts an opaque binary message with the established Double Ratchet session.
     * The returned envelope is already transport-safe binary and must be persisted by
     * the caller before transmission so retries reuse these exact bytes.
     */
    suspend fun encryptBytes(
        context: Context,
        address: String,
        plaintext: ByteArray,
    ): ByteArray? = lockFor(address).withLock {
        encryptBytesLocked(context, address, plaintext)
    }

    /** Decrypts and authenticates an opaque binary Double Ratchet envelope. */
    suspend fun decryptBytes(
        context: Context,
        address: String,
        envelope: ByteArray,
    ): ByteArray? = lockFor(address).withLock {
        decryptBytesLocked(context, address, envelope)
    }

    private suspend fun decryptBytesLocked(
        context: Context,
        address: String,
        data: ByteArray,
    ): ByteArray? {
        if(data.isEmpty()) return null
        if(MessageRequestType.fromCode(data[0]) != MessageRequestType.TYPE_MESSAGE)
            return null

        val payload = try { extractMessage(data) } catch(e: Exception) {
            throw e
        }

        val modeStates = context.getEncryptionModeStatesSync(address)
        val savedMode = SavedEncryptedModes.deserialize(modeStates)
        val publicKey = savedMode.publicKey

        if(publicKey == null) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.missing_public_key),
                    Toast.LENGTH_LONG).show()
            }
            return null
        }

        val publicKeyBytes = Base64.decode(publicKey, Base64.DEFAULT)

        val keystore = address + "_ratchet_state"
        val envelope = RatchetEnvelope.deserialize(context.getEncryptedBinaryData(keystore))

        var state: States?
        if(envelope.ratchetState == null) {
            requireInitialDecryptRole(savedMode.role)
            state = States()
            val sk = context.calculateSharedSecret(address, publicKeyBytes)
            val keypair = context.getKeypairValues(address) //public private

            Ratchets.ratchetInitBob(
                state,
                sk,
                android.util.Pair(keypair.second, keypair.first)
            )
        }
        else state = States(envelope.ratchetState)

        val keypair = context.getKeypairValues(address)
        val decrypted: ByteArray
        try {
            decrypted = Ratchets.ratchetDecrypt(
                state,
                payload.first,
                payload.second,
                keypair.first
            )
            check(context.saveBinaryDataEncrypted(
                keystore,
                envelope.copy(ratchetState = state.serializedStates).serialize(),
            )) { "Unable to persist decrypted ratchet state" }
        } catch(e: Exception) {
            throw e
        }
        return decrypted
    }

    @Throws
    suspend fun encrypt(
        context: Context,
        address: String,
        text: String,
        retryTransportText: String? = null,
    ) : String? = lockFor(address).withLock {
        val modeStates = context.getEncryptionModeStatesSync(address)
        val savedMode = SavedEncryptedModes.deserialize(modeStates)
        val publicKey = savedMode.publicKey

        if(publicKey == null) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.missing_public_key),
                    Toast.LENGTH_LONG).show()
            }
            return null
        }

        val publicKeyBytes = Base64.decode(publicKey, Base64.DEFAULT)

        val keystore = address + "_ratchet_state"
        val envelope = RatchetEnvelope.deserialize(context.getEncryptedBinaryData(keystore))
        if(retryTransportText != null) {
            envelope.pending.firstOrNull {
                it.plaintext == text && it.transportText == retryTransportText
            }?.let {
                return@withLock it.transportText
            }
            throw SecurityException("Encrypted retry state is missing or does not match")
        }

        val message = encryptBytesLocked(context, address, text.encodeToByteArray()) ?: return@withLock null
        val transportText = Base64.encodeToString(message, Base64.NO_WRAP)
        val updatedEnvelope = RatchetEnvelope.deserialize(
            context.getEncryptedBinaryData(address + RATCHET_SUFFIX)
        )
        val pending = (updatedEnvelope.pending + PendingCipherText(text, transportText))
            .takeLast(MAX_PENDING_MESSAGES)
        check(context.saveBinaryDataEncrypted(
            address + RATCHET_SUFFIX,
            updatedEnvelope.copy(pending = pending).serialize(),
        )) { "Unable to persist encrypted retry metadata" }
        transportText
    }

    private suspend fun encryptBytesLocked(
        context: Context,
        address: String,
        plaintext: ByteArray,
    ): ByteArray? {
        require(plaintext.isNotEmpty()) { "Cannot ratchet-encrypt an empty binary message" }
        val modeStates = context.getEncryptionModeStatesSync(address)
        val savedMode = SavedEncryptedModes.deserialize(modeStates)
        val publicKey = savedMode.publicKey

        if(publicKey == null) {
            CoroutineScope(Dispatchers.Main).launch {
                Toast.makeText(
                    context,
                    context.getString(R.string.missing_public_key),
                    Toast.LENGTH_LONG).show()
            }
            return null
        }

        val publicKeyBytes = Base64.decode(publicKey, Base64.DEFAULT)

        val keystore = address + RATCHET_SUFFIX
        val envelope = RatchetEnvelope.deserialize(context.getEncryptedBinaryData(keystore))
        val state: States
        if(envelope.ratchetState == null) {
            requireInitialEncryptRole(savedMode.role)
            state = States()
            val sk = context.calculateSharedSecret(address, publicKeyBytes)
            Ratchets.ratchetInitAlice(state, sk, publicKeyBytes)
        }
        else state = States(envelope.ratchetState)

        val ratchetOutput = Ratchets.ratchetEncrypt(state,
            plaintext, publicKeyBytes)

        return try {
            val message = formatMessage(
                ratchetOutput.first,
                ratchetOutput.second
            )
            check(context.saveBinaryDataEncrypted(
                keystore,
                envelope.copy(ratchetState = state.serializedStates).serialize(),
            )) { "Unable to persist encrypted ratchet state" }
            message
        } catch(e: Exception) {
            throw e
        }
    }

    suspend fun markOutboundSent(context: Context, address: String, transportText: String) {
        lockFor(address).withLock {
            val keystore = address + "_ratchet_state"
            val envelope = RatchetEnvelope.deserialize(context.getEncryptedBinaryData(keystore))
            val pending = envelope.pending.filterNot { it.transportText == transportText }
            if(pending.size != envelope.pending.size) {
                check(context.saveBinaryDataEncrypted(
                    keystore,
                    envelope.copy(pending = pending).serialize(),
                )) { "Unable to persist secure send acknowledgement" }
            }
        }
    }

    suspend fun isLocallySignedKeyExchange(context: Context, data: ByteArray): Boolean {
        val exchange = try {
            SignedKeyExchangeCodec.decodeAndVerify(data)
        } catch (_: Exception) {
            return false
        }
        val embeddedIdentity = exchange.identityPublicKey ?: return false
        return MessageDigest.isEqual(
            embeddedIdentity,
            IdentityKeyManager.localPublicKey(context),
        )
    }

    internal fun keepInitiatorOnSimultaneousRequest(
        localSessionPublicKey: ByteArray,
        remoteSessionPublicKey: ByteArray,
    ): Boolean {
        require(localSessionPublicKey.size == X25519_PUBLIC_KEY_SIZE)
        require(remoteSessionPublicKey.size == X25519_PUBLIC_KEY_SIZE)
        for(index in localSessionPublicKey.indices) {
            val comparison = (localSessionPublicKey[index].toInt() and 0xff)
                .compareTo(remoteSessionPublicKey[index].toInt() and 0xff)
            if(comparison != 0) return comparison < 0
        }
        throw SecurityException("Simultaneous requests used the same session key")
    }

    internal fun requireInitialEncryptRole(role: SessionRole?) {
        check(role == SessionRole.INITIATOR) {
            "Secure session is waiting for the initiator's first message"
        }
    }

    internal fun requireInitialDecryptRole(role: SessionRole?) {
        check(role == SessionRole.RESPONDER) {
            "Initiator ratchet state is missing; renew the secure session"
        }
    }

    private fun lockFor(address: String): Mutex =
        sessionLocks.computeIfAbsent(address) { Mutex() }

    private const val X25519_PUBLIC_KEY_SIZE = 32
    private const val SERIALIZED_HEADER_SIZE = 40
    private const val MIN_CIPHER_TEXT_SIZE = 48
    private const val LEGACY_MESSAGE_PREFIX_SIZE = 3
    private const val V2_MESSAGE_PREFIX_SIZE = 9
    private const val V2_MARKER: Byte = 0
    private const val V2_VERSION: Byte = 2
    private const val MAX_PENDING_MESSAGES = 100
    private const val RATCHET_SUFFIX = "_ratchet_state"
    private val sessionLocks = ConcurrentHashMap<String, Mutex>()
}

private suspend fun Context.calculateSharedSecret(
    address: String,
    publicKey: ByteArray
): ByteArray? {
    val keypair = getKeypairValues(address) //public private
    keypair.second?.let { privateKey ->
        val libSigCurve25519 = SecurityCurve25519(privateKey)
        return libSigCurve25519.calculateSharedSecret(publicKey)
    }
    return null
}

data class SavedEncryptedModes(
    var mode: EncryptionController.SecureRequestMode,
    var publicKey: String? = null,
    var role: EncryptionController.SessionRole? = null,
) {
    fun serialize(): String = JSONObject().apply {
        put(FIELD_MODE, mode.name)
        publicKey?.let { put(FIELD_PUBLIC_KEY, it) }
        role?.let { put(FIELD_ROLE, it.name) }
    }.toString()

    companion object {
        private const val FIELD_MODE = "mode"
        private const val FIELD_PUBLIC_KEY = "publicKey"
        private const val FIELD_ROLE = "role"

        fun deserialize(value: String?): SavedEncryptedModes {
            require(!value.isNullOrBlank()) { "Secure mode state is missing" }
            val input = JSONObject(value)
            val mode = EncryptionController.SecureRequestMode.valueOf(
                input.getString(FIELD_MODE)
            )
            val publicKey = input.optString(FIELD_PUBLIC_KEY).takeIf { it.isNotEmpty() }
            val role = input.optString(FIELD_ROLE).takeIf { it.isNotEmpty() }?.let {
                EncryptionController.SessionRole.valueOf(it)
            }
            return SavedEncryptedModes(mode, publicKey, role)
        }
    }
}

private suspend fun Context.setEncryptionModeStates(
    address: String,
    mode: EncryptionController.SecureRequestMode,
    publicKey: ByteArray? = null,
    role: EncryptionController.SessionRole? = null,
) {
    val keyValue = stringPreferencesKey(address + "_mode_states")
    dataStore.edit { secureComms ->
        // Make a mutable copy of existing state
        val currentState = secureComms[keyValue] ?: ""
        val savedEncryptedModes = if(currentState.isNotEmpty()) SavedEncryptedModes
            .deserialize(currentState)
            .apply { this.mode = mode }
        else SavedEncryptedModes(mode = mode)

        publicKey?.let { publicKey ->
            savedEncryptedModes.publicKey =
                Base64.encodeToString(publicKey, Base64.NO_WRAP)
        }
        role?.let { savedEncryptedModes.role = it }

        secureComms[keyValue] = savedEncryptedModes.serialize()
    }
}

suspend fun Context.removeEncryptionRatchetStates(
    address: String,
    showFeedback: Boolean = true,
) {
    removeEncryptedBinaryData(address + "_ratchet_state")
    if(showFeedback) {
        withContext(Dispatchers.Main) {
            Toast.makeText(
                this@removeEncryptionRatchetStates,
                getString(R.string.ratchet_states_removed),
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}

suspend fun Context.removeEncryptionModeStates(address: String) {
    val keyValue = stringPreferencesKey(address + "_mode_states")
    dataStore.edit { secureComms ->
        secureComms.remove(keyValue)
    }
}

fun Context.getEncryptionRatchetStates(address: String): Flow<String?> {
    val keyValue = stringPreferencesKey(address + "_ratchet_state")
    return dataStore.data.map { it[keyValue] }
}

suspend fun Context.getEncryptionModeStatesSync(address: String): String? {
    val keyValue = stringPreferencesKey(address + "_mode_states")
    return dataStore.data.first()[keyValue]
}

fun Context.getEncryptionModeStates(address: String): Flow<String?> {
    val keyValue = stringPreferencesKey(address + "_mode_states")
    return dataStore.data.map { it[keyValue] }
}
