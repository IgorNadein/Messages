package com.afkanerd.deku.attachments.cloud

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.getEncryptedBinaryData
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.removeEncryptedBinaryData
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.extensions.saveBinaryDataEncrypted

enum class CloudProvider(val wireCode: Int) {
    SELF_HOSTED(1),
    YANDEX_DISK(2),
    GOOGLE_DRIVE(3),
    ;

    companion object {
        fun fromWireCode(code: Int): CloudProvider? = entries.firstOrNull { it.wireCode == code }
    }
}

data class CloudStorageProfile(
    val provider: CloudProvider,
    val endpoint: String = "",
    val folder: String = "Messages",
)

data class CloudStorageCredentials(
    val profile: CloudStorageProfile,
    val accessToken: String,
)

/** Keeps only non-secret metadata in preferences; credentials stay Keystore-wrapped. */
class CloudStorageConfigStore(context: Context) {
    private val appContext = context.applicationContext
    private val preferences = appContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun profile(): CloudStorageProfile? {
        if(!preferences.getBoolean(KEY_CONFIGURED, false)) return null
        val provider = runCatching {
            CloudProvider.valueOf(preferences.getString(KEY_PROVIDER, null).orEmpty())
        }.getOrNull() ?: return null
        return CloudStorageProfile(
            provider = provider,
            endpoint = preferences.getString(KEY_ENDPOINT, "").orEmpty(),
            folder = preferences.getString(KEY_FOLDER, DEFAULT_FOLDER).orEmpty()
                .ifBlank { DEFAULT_FOLDER },
        ).takeIf(::isValidProfile)
    }

    fun isConfigured(): Boolean = profile() != null

    suspend fun credentials(): CloudStorageCredentials? {
        val profile = profile() ?: return null
        val tokenBytes = appContext.getEncryptedBinaryData(CREDENTIAL_ALIAS) ?: return null
        return try {
            val token = tokenBytes.toString(Charsets.UTF_8)
            token.takeIf(String::isNotBlank)?.let { CloudStorageCredentials(profile, it) }
        } finally {
            tokenBytes.fill(0)
        }
    }

    suspend fun save(profile: CloudStorageProfile, accessToken: String) {
        require(isValidProfile(profile)) { "Invalid cloud storage profile" }
        require(accessToken.isNotBlank()) { "Cloud access token is required" }
        val tokenBytes = accessToken.trim().toByteArray(Charsets.UTF_8)
        try {
            check(appContext.saveBinaryDataEncrypted(CREDENTIAL_ALIAS, tokenBytes)) {
                "Unable to protect cloud credentials"
            }
            preferences.edit(commit = true) {
                putString(KEY_PROVIDER, profile.provider.name)
                putString(KEY_ENDPOINT, profile.endpoint.trim().trimEnd('/'))
                putString(KEY_FOLDER, sanitizeFolder(profile.folder))
                putBoolean(KEY_CONFIGURED, true)
            }
        } finally {
            tokenBytes.fill(0)
        }
    }

    suspend fun clear() {
        appContext.removeEncryptedBinaryData(CREDENTIAL_ALIAS)
        preferences.edit(commit = true) { clear() }
    }

    private fun isValidProfile(profile: CloudStorageProfile): Boolean = when(profile.provider) {
        CloudProvider.SELF_HOSTED -> isHttps(profile.endpoint)
        CloudProvider.YANDEX_DISK, CloudProvider.GOOGLE_DRIVE -> true
    }

    private fun isHttps(value: String): Boolean = runCatching {
        val uri = Uri.parse(value.trim())
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun sanitizeFolder(value: String): String = value.trim()
        .replace(Regex("[\\\\/:*?\"<>|]+"), "_")
        .take(80)
        .ifBlank { DEFAULT_FOLDER }

    private companion object {
        const val PREFERENCES = "cloud_media_transport"
        const val CREDENTIAL_ALIAS = "deku_cloud_media_credentials_v1"
        const val KEY_CONFIGURED = "configured"
        const val KEY_PROVIDER = "provider"
        const val KEY_ENDPOINT = "endpoint"
        const val KEY_FOLDER = "folder"
        const val DEFAULT_FOLDER = "Messages"
    }
}
