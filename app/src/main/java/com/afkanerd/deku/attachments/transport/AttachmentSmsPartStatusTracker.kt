package com.afkanerd.deku.attachments.transport

import android.content.Context

/** Collapses callbacks from multipart compatible-SMS segments into one frame callback. */
internal object AttachmentSmsPartStatusTracker {
    private val lock = Any()

    fun shouldFinalize(
        context: Context,
        key: String,
        partIndex: Int,
        partCount: Int,
        successful: Boolean,
    ): Boolean = synchronized(lock) {
        if(partIndex !in 0 until partCount) return@synchronized false
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cleanupExpired(prefs, System.currentTimeMillis())
        if(prefs.getBoolean("$key.done", false)) return@synchronized false
        val now = System.currentTimeMillis()
        if(!successful) {
            prefs.edit()
                .putBoolean("$key.done", true)
                .putLong("$key.updated", now)
                .remove("$key.parts")
                .apply()
            return@synchronized true
        }
        val parts = prefs.getStringSet("$key.parts", emptySet()).orEmpty().toMutableSet()
        parts += partIndex.toString()
        val complete = parts.size >= partCount
        prefs.edit()
            .putStringSet("$key.parts", parts)
            .putBoolean("$key.done", complete)
            .putLong("$key.updated", now)
            .apply()
        complete
    }

    private fun cleanupExpired(
        prefs: android.content.SharedPreferences,
        now: Long,
    ) {
        val expiredBases = prefs.all.keys
            .asSequence()
            .filter { it.endsWith(UPDATED_SUFFIX) }
            .filter { now - prefs.getLong(it, now) > ENTRY_TTL_MILLIS }
            .map { it.removeSuffix(UPDATED_SUFFIX) }
            .toList()
        if(expiredBases.isEmpty()) return
        prefs.edit().apply {
            expiredBases.forEach { base ->
                remove("$base.done")
                remove("$base.parts")
                remove("$base.updated")
            }
        }.apply()
    }

    private const val PREFS = "attachment_text_sms_parts_v1"
    private const val UPDATED_SUFFIX = ".updated"
    private const val ENTRY_TTL_MILLIS = 24L * 60L * 60L * 1_000L
}
