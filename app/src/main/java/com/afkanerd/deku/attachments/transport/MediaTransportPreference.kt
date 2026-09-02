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

    private fun preferences(context: Context) = context.applicationContext
        .getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    private const val PREFERENCES_NAME = "media_transport"
    private const val KEY_SELECTED = "selected"
}
