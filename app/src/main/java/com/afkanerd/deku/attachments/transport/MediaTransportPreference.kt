package com.afkanerd.deku.attachments.transport

import android.content.Context
import com.afkanerd.deku.messages.domain.MediaTransport

/** Persists only the sender's preference. Inbound handlers remain enabled for every transport. */
object MediaTransportPreference {
    fun selected(context: Context): MediaTransport = runCatching {
        MediaTransport.valueOf(
            preferences(context).getString(KEY_SELECTED, null) ?: MediaTransport.MMS.name
        )
    }.getOrDefault(MediaTransport.MMS)

    fun setSelected(context: Context, transport: MediaTransport) {
        preferences(context).edit().putString(KEY_SELECTED, transport.name).apply()
    }

    fun selected(context: Context, subscriptionId: Long): MediaTransport = runCatching {
        MediaTransport.valueOf(
            preferences(context).getString(subscriptionKey(subscriptionId), null)
                ?: selected(context).name
        )
    }.getOrDefault(MediaTransport.MMS)

    fun setSelected(context: Context, subscriptionId: Long, transport: MediaTransport) {
        preferences(context).edit()
            .putString(subscriptionKey(subscriptionId), transport.name)
            .apply()
    }

    fun replaceAll(context: Context, from: MediaTransport, to: MediaTransport) {
        val prefs = preferences(context)
        val editor = prefs.edit()
        prefs.all.forEach { (key, value) ->
            if((key == KEY_SELECTED || key.startsWith(KEY_SELECTED_SUBSCRIPTION_PREFIX)) &&
                value == from.name
            ) editor.putString(key, to.name)
        }
        editor.apply()
    }

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "media_transport"
    private const val KEY_SELECTED = "selected"
    private const val KEY_SELECTED_SUBSCRIPTION_PREFIX = "selected_subscription_"

    private fun subscriptionKey(subscriptionId: Long): String =
        KEY_SELECTED_SUBSCRIPTION_PREFIX + subscriptionId
}
