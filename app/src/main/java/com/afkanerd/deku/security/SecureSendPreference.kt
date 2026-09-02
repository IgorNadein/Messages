package com.afkanerd.deku.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest

/** User choice for future messages. Session and identity material remain untouched. */
object SecureSendPreference {
    fun isEnabled(context: Context, address: String, subscriptionId: Long): Boolean =
        preferences(context).getBoolean(key(address, subscriptionId), false)

    fun setEnabled(context: Context, address: String, subscriptionId: Long, enabled: Boolean) {
        preferences(context).edit().putBoolean(key(address, subscriptionId), enabled).apply()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun key(address: String, subscriptionId: Long): String =
        "encrypt_future_" + Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(
                SecureChannelId.storageAddress(address, subscriptionId).encodeToByteArray()
            ),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )

    private const val PREFERENCES_NAME = "secure_send_preferences"
}
