package com.afkanerd.smswithoutborders_libsmsmms.transport

import android.content.Context

/** Collapses callbacks from all multipart text or Data-SMS fragments into one status update. */
internal object DataSmsPartStatusTracker {
    private val lock = Any()

    fun shouldFinalize(
        context: Context,
        messageId: Long,
        stage: String,
        partIndex: Int,
        partCount: Int,
        successful: Boolean,
    ): Boolean = synchronized(lock) {
        if(partCount <= 1) return@synchronized true
        if(partIndex !in 0 until partCount) return@synchronized false

        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        cleanup(prefs)
        val base = "$stage.$messageId"
        if(prefs.getBoolean("$base.done", false)) return@synchronized false

        if(!successful) {
            prefs.edit()
                .putBoolean("$base.done", true)
                .putLong("$base.time", System.currentTimeMillis())
                .remove("$base.parts")
                .apply()
            return@synchronized true
        }

        val completed = prefs.getStringSet("$base.parts", emptySet())
            .orEmpty()
            .toMutableSet()
        completed += partIndex.toString()
        val done = completed.size >= partCount
        prefs.edit()
            .putStringSet("$base.parts", completed)
            .putBoolean("$base.done", done)
            .putLong("$base.time", System.currentTimeMillis())
            .apply()
        done
    }

    private fun cleanup(prefs: android.content.SharedPreferences) {
        val now = System.currentTimeMillis()
        val staleBases = prefs.all.keys.asSequence()
            .filter { it.endsWith(".time") }
            .filter { now - prefs.getLong(it, now) > TTL_MILLIS }
            .map { it.removeSuffix(".time") }
            .toList()
        if(staleBases.isEmpty()) return
        prefs.edit().also { editor ->
            staleBases.forEach { base ->
                editor.remove("$base.time")
                editor.remove("$base.done")
                editor.remove("$base.parts")
            }
        }.apply()
    }

    private const val PREFS = "data_sms_part_status_v1"
    private const val TTL_MILLIS = 7 * 24 * 60 * 60 * 1000L
}
