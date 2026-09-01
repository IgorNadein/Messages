package com.afkanerd.smswithoutborders.libsignal_doubleratchet

import android.content.Context
import android.util.Base64
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.dataStore
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.getEncryptedBinaryData
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.saveBinaryDataEncrypted
import com.google.crypto.tink.subtle.Ed25519Sign
import com.google.crypto.tink.subtle.Ed25519Verify
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.security.MessageDigest

enum class IdentityVerificationStatus {
    LEGACY_UNVERIFIED,
    UNVERIFIED,
    VERIFIED,
    KEY_CHANGED,
    REKEY_REQUIRED,
}

data class ContactIdentity(
    val status: IdentityVerificationStatus,
    val publicKey: ByteArray? = null,
    val candidatePublicKey: ByteArray? = null,
)

class IdentityKeyChangedException : SecurityException(
    "Security key for this contact has changed"
)

object IdentityKeyManager {
    suspend fun localPublicKey(context: Context): ByteArray {
        val keyPair = getOrCreateLocalKeyPair(context)
        return try {
            keyPair.publicKey.copyOf()
        } finally {
            keyPair.privateKey.fill(0)
        }
    }

    suspend fun sign(context: Context, message: ByteArray): ByteArray {
        val keyPair = getOrCreateLocalKeyPair(context)
        val privateKey = keyPair.privateKey.copyOf()
        return try {
            Ed25519Sign(privateKey).sign(message)
        } finally {
            privateKey.fill(0)
            keyPair.privateKey.fill(0)
        }
    }

    fun verify(publicKey: ByteArray, signature: ByteArray, message: ByteArray): Boolean {
        if(publicKey.size != KEY_SIZE || signature.size != SIGNATURE_SIZE) return false
        return try {
            Ed25519Verify(publicKey).verify(signature, message)
            true
        } catch (_: Exception) {
            false
        }
    }

    suspend fun recordRemoteIdentity(
        context: Context,
        address: String,
        publicKey: ByteArray,
    ): ContactIdentity {
        require(publicKey.size == KEY_SIZE) { "Invalid Ed25519 public key" }
        val existing = getContactIdentity(context, address)
        val update = evaluateRemoteIdentity(existing, publicKey)
        saveContactIdentity(context, address, update.identity)
        if(update.keyChanged) throw IdentityKeyChangedException()
        return update.identity
    }

    suspend fun recordLegacyIdentity(context: Context, address: String) {
        val existing = getContactIdentity(context, address)
        if(existing.publicKey == null) {
            saveContactIdentity(
                context,
                address,
                ContactIdentity(IdentityVerificationStatus.LEGACY_UNVERIFIED),
            )
        }
    }

    suspend fun getContactIdentity(context: Context, address: String): ContactIdentity {
        val encoded = context.dataStore.data.first()[contactPreferenceKey(address)]
            ?: return ContactIdentity(IdentityVerificationStatus.LEGACY_UNVERIFIED)
        return deserializeContact(encoded)
    }

    fun observeContactIdentity(context: Context, address: String): Flow<ContactIdentity> =
        context.dataStore.data.map { preferences ->
            preferences[contactPreferenceKey(address)]?.let(::deserializeContact)
                ?: ContactIdentity(IdentityVerificationStatus.LEGACY_UNVERIFIED)
        }

    suspend fun verifyContact(
        context: Context,
        address: String,
        scannedPublicKey: ByteArray,
    ): Boolean {
        val identity = getContactIdentity(context, address)
        val expected = identity.publicKey ?: return false
        if(identity.status == IdentityVerificationStatus.KEY_CHANGED ||
            !MessageDigest.isEqual(expected, scannedPublicKey)
        ) return false
        saveContactIdentity(
            context,
            address,
            identity.copy(status = IdentityVerificationStatus.VERIFIED),
        )
        return true
    }

    suspend fun acceptChangedIdentity(context: Context, address: String): Boolean {
        val identity = getContactIdentity(context, address)
        val candidate = identity.candidatePublicKey ?: return false
        saveContactIdentity(
            context,
            address,
            ContactIdentity(
                status = IdentityVerificationStatus.REKEY_REQUIRED,
                publicKey = candidate,
            ),
        )
        return true
    }

    suspend fun safetyNumber(context: Context, address: String): String? {
        val remote = getContactIdentity(context, address).publicKey ?: return null
        val local = localPublicKey(context)
        val ordered = if(compareUnsigned(local, remote) <= 0) local + remote else remote + local
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(SAFETY_NUMBER_DOMAIN + ordered)
        val digits = buildString {
            digest.forEach { append((it.toInt() and 0xff).toString().padStart(3, '0')) }
        }.take(60)
        return digits.chunked(5).joinToString(" ")
    }

    fun qrPayload(publicKey: ByteArray): String {
        require(publicKey.size == KEY_SIZE)
        return QR_PREFIX + Base64.encodeToString(publicKey, Base64.NO_WRAP or Base64.URL_SAFE)
    }

    fun parseQrPayload(value: String): ByteArray? {
        if(!value.startsWith(QR_PREFIX)) return null
        return try {
            Base64.decode(value.removePrefix(QR_PREFIX), Base64.NO_WRAP or Base64.URL_SAFE)
                .takeIf { it.size == KEY_SIZE }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun getOrCreateLocalKeyPair(context: Context): LocalIdentityKeyPair =
        localKeyMutex.withLock {
            context.getEncryptedBinaryData(LOCAL_IDENTITY_ALIAS)?.let { stored ->
                return@withLock deserializeLocalKey(stored)
            }

            val generated = Ed25519Sign.KeyPair.newKeyPair()
            val keyPair = LocalIdentityKeyPair(
                generated.publicKey,
                generated.privateKey,
            )
            check(context.saveBinaryDataEncrypted(
                LOCAL_IDENTITY_ALIAS,
                serializeLocalKey(keyPair),
            )) { "Unable to persist local identity key" }
            keyPair
        }

    private suspend fun saveContactIdentity(
        context: Context,
        address: String,
        identity: ContactIdentity,
    ) {
        context.dataStore.edit {
            it[contactPreferenceKey(address)] = serializeContact(identity)
        }
    }

    private fun serializeContact(identity: ContactIdentity): String = JSONObject().apply {
        put(FIELD_STATUS, identity.status.name)
        identity.publicKey?.let {
            put(FIELD_PUBLIC_KEY, Base64.encodeToString(it, Base64.NO_WRAP))
        }
        identity.candidatePublicKey?.let {
            put(FIELD_CANDIDATE_KEY, Base64.encodeToString(it, Base64.NO_WRAP))
        }
    }.toString()

    private fun deserializeContact(value: String): ContactIdentity {
        val input = JSONObject(value)
        return ContactIdentity(
            status = IdentityVerificationStatus.valueOf(input.getString(FIELD_STATUS)),
            publicKey = input.optString(FIELD_PUBLIC_KEY).takeIf(String::isNotEmpty)?.let {
                Base64.decode(it, Base64.NO_WRAP)
            },
            candidatePublicKey = input.optString(FIELD_CANDIDATE_KEY)
                .takeIf(String::isNotEmpty)?.let { Base64.decode(it, Base64.NO_WRAP) },
        )
    }

    private fun contactPreferenceKey(address: String) = stringPreferencesKey(
        "contact_identity_" + Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(address.encodeToByteArray()),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )
    )

    private fun serializeLocalKey(keyPair: LocalIdentityKeyPair): ByteArray =
        byteArrayOf(LOCAL_KEY_VERSION) + keyPair.publicKey + keyPair.privateKey

    private fun deserializeLocalKey(data: ByteArray): LocalIdentityKeyPair {
        require(data.size == 1 + KEY_SIZE * 2 && data[0] == LOCAL_KEY_VERSION) {
            "Corrupted local identity key"
        }
        return LocalIdentityKeyPair(
            data.copyOfRange(1, 1 + KEY_SIZE),
            data.copyOfRange(1 + KEY_SIZE, data.size),
        )
    }

    private fun compareUnsigned(first: ByteArray, second: ByteArray): Int {
        first.indices.forEach { index ->
            val comparison = (first[index].toInt() and 0xff)
                .compareTo(second[index].toInt() and 0xff)
            if(comparison != 0) return comparison
        }
        return 0
    }

    internal fun evaluateRemoteIdentity(
        existing: ContactIdentity,
        incomingPublicKey: ByteArray,
    ): IdentityUpdate {
        require(incomingPublicKey.size == KEY_SIZE) { "Invalid Ed25519 public key" }
        if(existing.publicKey == null) {
            return IdentityUpdate(
                ContactIdentity(
                    status = IdentityVerificationStatus.UNVERIFIED,
                    publicKey = incomingPublicKey.copyOf(),
                ),
                keyChanged = false,
            )
        }
        if(MessageDigest.isEqual(existing.publicKey, incomingPublicKey)) {
            return IdentityUpdate(
                if(existing.status == IdentityVerificationStatus.REKEY_REQUIRED) {
                    existing.copy(status = IdentityVerificationStatus.UNVERIFIED)
                } else existing,
                keyChanged = false,
            )
        }
        return IdentityUpdate(
            existing.copy(
                status = IdentityVerificationStatus.KEY_CHANGED,
                candidatePublicKey = incomingPublicKey.copyOf(),
            ),
            keyChanged = true,
        )
    }

    internal data class IdentityUpdate(
        val identity: ContactIdentity,
        val keyChanged: Boolean,
    )

    private data class LocalIdentityKeyPair(
        val publicKey: ByteArray,
        val privateKey: ByteArray,
    )

    private const val KEY_SIZE = 32
    private const val SIGNATURE_SIZE = 64
    private const val LOCAL_KEY_VERSION: Byte = 1
    private const val LOCAL_IDENTITY_ALIAS = "deku_permanent_ed25519_identity_v1"
    private const val FIELD_STATUS = "status"
    private const val FIELD_PUBLIC_KEY = "publicKey"
    private const val FIELD_CANDIDATE_KEY = "candidatePublicKey"
    private const val QR_PREFIX = "deku-identity:v1:"
    private val SAFETY_NUMBER_DOMAIN = "DekuSMS Safety Number v1".encodeToByteArray()
    private val localKeyMutex = Mutex()
}
