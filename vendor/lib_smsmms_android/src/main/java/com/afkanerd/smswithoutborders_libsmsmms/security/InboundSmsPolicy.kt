package com.afkanerd.smswithoutborders_libsmsmms.security

import android.content.Context

/** A text SMS as received from Android, before it is exposed to the app database. */
data class InboundSms(
    val address: String,
    val transportText: String,
)

/**
 * The body safe to expose in UI and an optional transport copy retained only in
 * the app's encrypted database.
 */
data class ProcessedInboundSms(
    val displayText: String,
    val secureTransportText: String? = null,
)

fun interface InboundSmsPolicy {
    suspend fun evaluate(context: Context, message: InboundSms): ProcessedInboundSms
}

object InboundSmsPolicyRegistry {
    private val passThrough = InboundSmsPolicy { _, message ->
        ProcessedInboundSms(displayText = message.transportText)
    }

    @Volatile
    var policy: InboundSmsPolicy = passThrough

    suspend fun evaluate(context: Context, message: InboundSms): ProcessedInboundSms =
        policy.evaluate(context, message)
}
