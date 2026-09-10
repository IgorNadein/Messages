package com.afkanerd.deku.security

import android.content.Context
import android.util.Base64
import com.afkanerd.deku.messages.domain.SecureMessageTransport
import java.security.MessageDigest

/** Global user choice plus per-contact completion of the mandatory first legacy SMS. */
object SecureMessageTransportPreference {
    fun selected(context: Context): SecureMessageTransport = runCatching {
        SecureMessageTransport.valueOf(
            preferences(context).getString(KEY_SELECTED, null)
                ?: SecureMessageTransport.STANDARD_SMS.name
        )
    }.getOrDefault(SecureMessageTransport.STANDARD_SMS)

    fun setSelected(context: Context, transport: SecureMessageTransport) {
        val prefs = preferences(context)
        val previous = selected(context)
        val editor = prefs.edit().putString(KEY_SELECTED, transport.name)
        if(previous != transport) {
            prefs.all.keys.filter { it.startsWith(KEY_BOOTSTRAP_PREFIX) }
                .forEach(editor::remove)
        }
        editor.apply()
    }

    fun selected(context: Context, subscriptionId: Long): SecureMessageTransport = runCatching {
        SecureMessageTransport.valueOf(
            preferences(context).getString(subscriptionKey(subscriptionId), null)
                ?: selected(context).name
        )
    }.getOrDefault(SecureMessageTransport.STANDARD_SMS)

    fun setSelected(
        context: Context,
        subscriptionId: Long,
        transport: SecureMessageTransport,
    ) {
        val prefs = preferences(context)
        val previous = selected(context, subscriptionId)
        val editor = prefs.edit().putString(subscriptionKey(subscriptionId), transport.name)
        if(previous != transport) {
            prefs.all.keys.filter { it.startsWith(bootstrapPrefix(subscriptionId)) }
                .forEach(editor::remove)
        }
        editor.apply()
    }

    fun shouldUseData(context: Context, address: String): Boolean =
        selected(context) == SecureMessageTransport.DATA_SMS &&
            preferences(context).getBoolean(bootstrapKey(address), false)

    fun shouldUseData(context: Context, address: String, subscriptionId: Long): Boolean =
        selected(context, subscriptionId) == SecureMessageTransport.DATA_SMS &&
            preferences(context).getBoolean(bootstrapKey(address, subscriptionId), false)

    fun markFirstLegacyMessageComplete(context: Context, address: String) {
        if(selected(context) != SecureMessageTransport.DATA_SMS) return
        preferences(context).edit().putBoolean(bootstrapKey(address), true).apply()
    }

    fun markFirstLegacyMessageComplete(
        context: Context,
        address: String,
        subscriptionId: Long,
    ) {
        if(selected(context, subscriptionId) != SecureMessageTransport.DATA_SMS) return
        preferences(context).edit()
            .putBoolean(bootstrapKey(address, subscriptionId), true)
            .apply()
    }

    fun resetPeer(context: Context, address: String) {
        preferences(context).edit().remove(bootstrapKey(address)).apply()
    }

    fun resetPeer(context: Context, address: String, subscriptionId: Long) {
        preferences(context).edit().remove(bootstrapKey(address, subscriptionId)).apply()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private fun bootstrapKey(address: String): String = KEY_BOOTSTRAP_PREFIX +
        Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(address.encodeToByteArray()),
            Base64.NO_WRAP or Base64.URL_SAFE,
        )

    private fun bootstrapKey(address: String, subscriptionId: Long): String =
        bootstrapPrefix(subscriptionId) + addressHash(address)

    private fun bootstrapPrefix(subscriptionId: Long): String =
        KEY_BOOTSTRAP_PREFIX + subscriptionId + "_"

    private fun subscriptionKey(subscriptionId: Long): String =
        KEY_SELECTED_SUBSCRIPTION_PREFIX + subscriptionId

    private fun addressHash(address: String): String = Base64.encodeToString(
        MessageDigest.getInstance("SHA-256").digest(address.encodeToByteArray()),
        Base64.NO_WRAP or Base64.URL_SAFE,
    )

    private const val PREFERENCES_NAME = "secure_message_transport"
    private const val KEY_SELECTED = "selected"
    private const val KEY_SELECTED_SUBSCRIPTION_PREFIX = "selected_subscription_"
    private const val KEY_BOOTSTRAP_PREFIX = "first_legacy_complete_"
}
