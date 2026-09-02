package com.afkanerd.deku.security

import android.content.Context
import android.util.Base64
import java.security.MessageDigest

/** User choice for future messages. Session and identity material remain untouched. */
object SecureSendPreference {
    fun isEnabled(context: Context, address: String): Boolean = preferences(context)
        .getBoolean(key(address), true)

    fun setEnabled(context: Context, address: String, enabled: Boolean) {
        preferences(context).edit().putBoolean(key(address), enabled).apply()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun key(address: String): String = "encrypt_future_" + Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(address.encodeToByteArray()),
        Base64.NO_WRAP or Base64.URL_SAFE,
    )

    private const val PREFERENCES_NAME = "secure_send_preferences"
}
