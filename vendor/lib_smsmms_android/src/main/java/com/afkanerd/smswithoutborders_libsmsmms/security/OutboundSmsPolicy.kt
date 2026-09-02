package com.afkanerd.smswithoutborders_libsmsmms.security

import android.content.Context

/**
 * Last policy boundary before a message is inserted into Telephony and handed to
 * Android's SmsManager. Applications embedding this library can transform or
 * reject every outbound SMS, including notification replies, shares and retries.
 */
data class OutboundSms(
    val address: String,
    val displayText: String,
    val transportText: String = displayText,
    val transportData: ByteArray? = null,
    val retryTransportText: String? = null,
    val forcePlainText: Boolean = false,
    val subscriptionId: Long = -1,
)

sealed interface OutboundSmsDecision {
    data class Allow(val message: OutboundSms) : OutboundSmsDecision
    data class Block(val reason: String, val cause: Throwable? = null) : OutboundSmsDecision
}

fun interface OutboundSmsPolicy {
    suspend fun evaluate(context: Context, message: OutboundSms): OutboundSmsDecision
}

object OutboundSmsPolicyRegistry {
    private val allowAll = OutboundSmsPolicy { _, message ->
        OutboundSmsDecision.Allow(message)
    }

    @Volatile
    var policy: OutboundSmsPolicy = allowAll

    suspend fun evaluate(context: Context, message: OutboundSms): OutboundSmsDecision =
        policy.evaluate(context, message)
}

class OutboundSmsBlockedException(
    message: String,
    cause: Throwable? = null,
) : SecurityException(message, cause)

const val SECURE_TRANSPORT_TEXT_EXTRA =
    "com.afkanerd.smswithoutborders_libsmsmms.SECURE_TRANSPORT_TEXT"

const val SECURE_RETRY_TRANSPORT_TEXT_EXTRA =
    "com.afkanerd.smswithoutborders_libsmsmms.SECURE_RETRY_TRANSPORT_TEXT"

/** Internal opt-out used only after the UI has confirmed an unencrypted resend. */
const val FORCE_PLAIN_TEXT_EXTRA =
    "com.afkanerd.smswithoutborders_libsmsmms.FORCE_PLAIN_TEXT"
