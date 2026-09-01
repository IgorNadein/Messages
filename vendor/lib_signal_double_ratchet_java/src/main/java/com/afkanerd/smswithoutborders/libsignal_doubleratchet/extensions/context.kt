package com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions

import android.content.Context
import android.util.Base64
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SecurityAES
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SecurityCurve25519
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SecurityRSA
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.security.MessageDigest
import java.security.KeyPair
import java.security.KeyStore
import java.security.KeyStoreException
import java.security.NoSuchAlgorithmException
import java.security.UnrecoverableEntryException
import java.security.cert.CertificateException
import javax.crypto.spec.SecretKeySpec
import org.json.JSONArray
import org.json.JSONObject

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "secure_comms")

@Throws(
    KeyStoreException::class,
    CertificateException::class,
    IOException::class,
    NoSuchAlgorithmException::class
)
fun Context.isAvailableInKeystore(keystoreAlias: String?): Boolean {
    /*
         * Load the Android KeyStore instance using the
         * AndroidKeyStore provider to list the currently stored entries.
         */

    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    return keyStore.containsAlias(keystoreAlias)
}

/**
 * Pair<PublicKey, PrivateKey>
 */
suspend fun Context.getKeypairValues(address: String): Pair<ByteArray?, ByteArray?> {
    getEncryptedBinaryData(address + SESSION_KEYPAIR_V2_SUFFIX)?.let { encoded ->
        require(encoded.size == SESSION_KEYPAIR_ENCODED_SIZE && encoded[0] == SESSION_KEYPAIR_VERSION) {
            "Corrupted session key pair"
        }
        return Pair(
            encoded.copyOfRange(1, 1 + SESSION_KEY_SIZE),
            encoded.copyOfRange(1 + SESSION_KEY_SIZE, encoded.size),
        )
    }

    val keyValue = stringSetPreferencesKey(address + "_keypair")
    val keypairSet = dataStore.data.first()[keyValue] ?: return Pair(null, null)
    require(keypairSet.size == 2) { "Corrupted legacy session key pair" }
    val encryptionKeyPair = getKeypairFromKeystore(address)
        ?: throw IllegalStateException("Missing legacy session wrapping key")
    val decrypted = keypairSet.map { value ->
        SecurityRSA.decrypt(
            encryptionKeyPair.private,
            Base64.decode(value, Base64.DEFAULT),
        ) ?: throw IllegalStateException("Unable to decrypt legacy session key")
    }

    val ordered = sequenceOf(
        Pair(decrypted[0], decrypted[1]),
        Pair(decrypted[1], decrypted[0]),
    ).firstOrNull { (publicKey, privateKey) ->
        publicKey.size == SESSION_KEY_SIZE &&
            privateKey.size == SESSION_KEY_SIZE &&
            MessageDigest.isEqual(SecurityCurve25519(privateKey).generateKey(), publicKey)
    } ?: throw IllegalStateException("Legacy session key pair is inconsistent")

    val result = Pair(ordered.first.copyOf(), ordered.second.copyOf())
    try {
        setKeypairValues(address, ordered.first, ordered.second)
        return result
    } finally {
        decrypted.forEach { it.fill(0) }
    }
}

suspend fun Context.setKeypairValues(
    address: String,
    publicKey: ByteArray,
    privateKey: ByteArray,
) {
    require(publicKey.size == SESSION_KEY_SIZE && privateKey.size == SESSION_KEY_SIZE) {
        "Invalid X25519 key pair"
    }
    require(MessageDigest.isEqual(SecurityCurve25519(privateKey).generateKey(), publicKey)) {
        "Inconsistent X25519 key pair"
    }
    val encoded = byteArrayOf(SESSION_KEYPAIR_VERSION) + publicKey + privateKey
    check(saveBinaryDataEncrypted(address + SESSION_KEYPAIR_V2_SUFFIX, encoded)) {
        "Unable to store session key pair"
    }
}

suspend fun Context.removeSessionKeypairValues(address: String) {
    removeEncryptedBinaryData(address + SESSION_KEYPAIR_V2_SUFFIX)
    dataStore.edit { preferences ->
        preferences.remove(stringSetPreferencesKey(address + "_keypair"))
    }
    deleteKeystoreEntry(address)
}

@Throws(
    KeyStoreException::class,
    CertificateException::class,
    IOException::class,
    NoSuchAlgorithmException::class,
    UnrecoverableEntryException::class
)
fun Context.getKeypairFromKeystore(keystoreAlias: String): KeyPair? {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)

    val entry = keyStore.getEntry(keystoreAlias, null)
    if (entry is KeyStore.PrivateKeyEntry) {
        val privateKey = entry.privateKey
        val publicKey = keyStore.getCertificate(keystoreAlias).publicKey
        return KeyPair(publicKey, privateKey)
    }
    return null
}

data class SavedBinaryData(
    val key: ByteArray,
    val algorithm: String,
    val data: ByteArray,
)

private fun SavedBinaryData.serialize(): String = JSONObject().apply {
    put("schema", 2)
    put("wrappedKey", Base64.encodeToString(key, Base64.NO_WRAP))
    put("algorithm", algorithm)
    put("ciphertext", Base64.encodeToString(data, Base64.NO_WRAP))
}.toString()

private fun deserializeSavedBinaryData(value: String): SavedBinaryData {
    val input = JSONObject(value)
    if(input.optInt("schema", 0) == 2) {
        return SavedBinaryData(
            key = Base64.decode(input.getString("wrappedKey"), Base64.NO_WRAP),
            algorithm = input.getString("algorithm"),
            data = Base64.decode(input.getString("ciphertext"), Base64.NO_WRAP),
        )
    }

    // Compatibility with the original Gson representation, where ByteArray fields
    // were encoded as JSON arrays of signed byte values.
    return SavedBinaryData(
        key = input.getJSONArray("key").toByteArray(),
        algorithm = input.getString("algorithm"),
        data = input.getJSONArray("data").toByteArray(),
    )
}

private fun JSONArray.toByteArray(): ByteArray = ByteArray(length()) { index ->
    getInt(index).toByte()
}

/**
 *  Would overwrite anything with the same Keystore Alias
 */
@Throws
suspend fun Context.saveBinaryDataEncrypted(
    keystoreAlias: String,
    data: ByteArray,
) : Boolean {
    val keyValue = stringPreferencesKey(keystoreAlias)

    val aesGcmKey = SecurityAES.generateSecretKey(256)
    val encryptedData = SecurityAES.encryptAESGCM(data, aesGcmKey)
    val encryptionPublicKey = getKeypairFromKeystore(keystoreAlias)?.public
        ?: SecurityRSA.generateKeyPair(keystoreAlias)
        ?: throw IllegalStateException("Unable to create state wrapping key")

    var saved = false
    dataStore.edit { secureComms->
        val rawAesKey = aesGcmKey.encoded
        try {
            SecurityRSA.encrypt(encryptionPublicKey, rawAesKey)?.let { key ->
                secureComms[keyValue] = SavedBinaryData(
                        key = key,
                        algorithm = aesGcmKey.algorithm,
                        data = encryptedData,
                    ).serialize()
                saved = true
            }
        } catch(e: Exception) {
            throw e
        } finally {
            rawAesKey.fill(0)
        }
    }
    return saved
}

@Throws
suspend fun Context.getEncryptedBinaryData(keystoreAlias: String): ByteArray? {
    val keyValue = stringPreferencesKey(keystoreAlias)
    val data = dataStore.data.first()[keyValue]
    if(data == null) return null

    val savedBinaryData = deserializeSavedBinaryData(data)

    return try {
        val encryptionPublicKey = getKeypairFromKeystore(keystoreAlias)
        val rawAesKey = SecurityRSA.decrypt(
            encryptionPublicKey?.private,
            savedBinaryData.key,
        ) ?: throw IllegalStateException("Unable to unwrap encrypted state")
        try {
            SecurityAES.decryptAESGCM(
                savedBinaryData.data,
                SecretKeySpec(rawAesKey, savedBinaryData.algorithm),
            )
        } finally {
            rawAesKey.fill(0)
        }
    } catch(e: Exception) {
        throw e
    }
}

suspend fun Context.removeEncryptedBinaryData(keystoreAlias: String) {
    dataStore.edit { preferences ->
        preferences.remove(stringPreferencesKey(keystoreAlias))
    }
    deleteKeystoreEntry(keystoreAlias)
}

private fun deleteKeystoreEntry(alias: String) {
    val keyStore = KeyStore.getInstance("AndroidKeyStore")
    keyStore.load(null)
    if(keyStore.containsAlias(alias)) keyStore.deleteEntry(alias)
}

private const val SESSION_KEYPAIR_V2_SUFFIX = "_session_keypair_v2"
private const val SESSION_KEYPAIR_VERSION: Byte = 1
private const val SESSION_KEY_SIZE = 32
private const val SESSION_KEYPAIR_ENCODED_SIZE = 1 + SESSION_KEY_SIZE * 2
