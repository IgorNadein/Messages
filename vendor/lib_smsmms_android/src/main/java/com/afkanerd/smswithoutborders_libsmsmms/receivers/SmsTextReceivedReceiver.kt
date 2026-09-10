package com.afkanerd.smswithoutborders_libsmsmms.receivers

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.telephony.SmsMessage
import android.util.Log
import androidx.core.net.toUri
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.DATA_PART_COUNT_EXTRA
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.DATA_PART_INDEX_EXTRA
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.isSecondaryUser
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.registerIncomingSms
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendNotificationBroadcast
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.updateSms
import com.afkanerd.smswithoutborders_libsmsmms.security.SECURE_TRANSPORT_TEXT_EXTRA
import com.afkanerd.smswithoutborders_libsmsmms.transport.DataSmsPartStatusTracker
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundTextSmsHandlerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsTextReceivedReceiver : BroadcastReceiver() {
    companion object {
        var SMS_SENT_BROADCAST_INTENT = "com.afkanerd.deku.SMS_SENT_BROADCAST_INTENT"
        var SMS_DELIVERED_BROADCAST_INTENT = "com.afkanerd.deku.SMS_DELIVERED_BROADCAST_INTENT"
        var DATA_SENT_BROADCAST_INTENT = "com.afkanerd.deku.DATA_SENT_BROADCAST_INTENT"
        var DATA_DELIVERED_BROADCAST_INTENT = "com.afkanerd.deku.DATA_DELIVERED_BROADCAST_INTENT"

        var SMS_SENT_BROADCAST_INTENT_LIB = "com.afkanerd.deku.SMS_SENT_BROADCAST_INTENT_LIB"
        private const val CALLBACK_LOG_TAG = "SmsStatusCallback"
    }

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Telephony.Sms.Intents.SMS_DELIVER_ACTION, Telephony.Sms.Intents.SMS_RECEIVED_ACTION -> {
                if (resultCode == Activity.RESULT_OK) {
                    if(intent.action == Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
                        !context.isSecondaryUser()) {
                        return
                    }
                    val pendingResult = goAsync()
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                            val address = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
                            val subscriptionId = intent.extras?.getInt("subscription", -1) ?: -1
                            val text = messages.joinToString(separator = "") { it.messageBody.orEmpty() }
                            if(address.isNotBlank() && text.isNotEmpty() &&
                                InboundTextSmsHandlerRegistry.consume(
                                    context.applicationContext,
                                    address,
                                    subscriptionId,
                                    text,
                                )
                            ) return@launch
                            val conversation = context.registerIncomingSms(intent)
                            val thread = context.getDatabase().threadsDao()
                                ?.get(conversation.sms?.thread_id!!)
                            context.sendNotificationBroadcast(
                                conversation,
                                type = NotificationTxType.TEXT,
                                showNotification = thread?.isMute != true,
                            )
                        } finally {
                            pendingResult.finish()
                        }
                    }
                }
            }
            SMS_SENT_BROADCAST_INTENT, DATA_SENT_BROADCAST_INTENT -> {
                val pendingResult = goAsync()
                val callbackResult = resultCode
                val callbackSuccessful = isSuccessfulSmsCallback(callbackResult)
                Log.i(
                    CALLBACK_LOG_TAG,
                    "sent callback action=${intent.action} result=$callbackResult " +
                        "id=${intent.getLongExtra("id", -1)} " +
                        "part=${intent.getIntExtra(DATA_PART_INDEX_EXTRA, 0)}/" +
                        intent.getIntExtra(DATA_PART_COUNT_EXTRA, 1),
                )
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val id = intent.getLongExtra("id", -1)
                        val uri = intent.getStringExtra("uri")?.toUri()
                        if(intent.getIntExtra(DATA_PART_COUNT_EXTRA, 1) > 1 &&
                            !DataSmsPartStatusTracker.shouldFinalize(
                                context = context,
                                messageId = id,
                                stage = "sent",
                                partIndex = intent.getIntExtra(DATA_PART_INDEX_EXTRA, 0),
                                partCount = intent.getIntExtra(DATA_PART_COUNT_EXTRA, 1),
                                successful = callbackSuccessful,
                            )
                        ) return@launch

                        context.getDatabase().conversationsDao()
                            ?.getConversation(id)
                            ?.let { conversation ->
                            if (callbackSuccessful) {
                                if(conversation.sms?.status != Telephony.Sms.STATUS_COMPLETE &&
                                    conversation.sms?.status != Telephony.Sms.STATUS_FAILED
                                ) {
                                    conversation.sms?.status = Telephony.Sms.STATUS_NONE
                                    conversation.sms?.type = Telephony.Sms.MESSAGE_TYPE_SENT
                                    conversation.sms?.error_code = null
                                }
                            } else {
                                conversation.sms?.status = Telephony.Sms.STATUS_FAILED
                                conversation.sms?.type = Telephony.Sms.MESSAGE_TYPE_FAILED
                                conversation.sms?.error_code = callbackResult
                            }
                            try {
                                context.updateSms(uri, conversation)
                                context.sendNotificationBroadcast(
                                    conversation = conversation,
                                    type = if(intent.action == SMS_SENT_BROADCAST_INTENT) {
                                        NotificationTxType.TEXT
                                    } else {
                                        NotificationTxType.DATA
                                    },
                                    self = true,
                                    secureTransportText = intent.getStringExtra(
                                        SECURE_TRANSPORT_TEXT_EXTRA
                                    ),
                                )
                            } catch(e: Exception) {
                                Log.e(CALLBACK_LOG_TAG, "Unable to persist sent SMS status", e)
                            }
                            }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            SMS_DELIVERED_BROADCAST_INTENT, DATA_DELIVERED_BROADCAST_INTENT -> {
                val pendingResult = goAsync()
                val callbackResult = resultCode
                val reportStatus = readDeliveryReportStatus(intent)
                val reportOutcome = classifyDeliveryReportStatus(reportStatus)
                val callbackSuccessful = resolveDeliveryCallbackOutcome(
                    reportOutcome,
                    callbackResult,
                )
                Log.i(
                    CALLBACK_LOG_TAG,
                    "delivered callback action=${intent.action} result=$callbackResult " +
                        "tpStatus=${reportStatus ?: "missing"} outcome=$reportOutcome " +
                        "id=${intent.getLongExtra("id", -1)} " +
                        "part=${intent.getIntExtra(DATA_PART_INDEX_EXTRA, 0)}/" +
                        intent.getIntExtra(DATA_PART_COUNT_EXTRA, 1),
                )
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        // Missing or temporary TP-Status is not proof of delivery. Some Samsung
                        // firmwares invoke this callback with RESULT_ERROR_NONE but omit the PDU;
                        // retain one check instead of manufacturing a successful delivery.
                        if(callbackSuccessful == null) return@launch
                        val id = intent.getLongExtra("id", -1)
                        val uri = intent.getStringExtra("uri")?.toUri()
                        if(intent.getIntExtra(DATA_PART_COUNT_EXTRA, 1) > 1 &&
                            !DataSmsPartStatusTracker.shouldFinalize(
                                context = context,
                                messageId = id,
                                stage = "delivered",
                                partIndex = intent.getIntExtra(DATA_PART_INDEX_EXTRA, 0),
                                partCount = intent.getIntExtra(DATA_PART_COUNT_EXTRA, 1),
                                successful = callbackSuccessful,
                            )
                        ) return@launch

                        context.getDatabase().conversationsDao()
                            ?.getConversation(id)
                            ?.let { conversation ->
                            if (callbackSuccessful) {
                                conversation.sms?.status = Telephony.Sms.STATUS_COMPLETE
                                conversation.sms?.type = Telephony.Sms.MESSAGE_TYPE_SENT
                                conversation.sms?.error_code = null
                            } else {
                                conversation.sms?.status = Telephony.Sms.STATUS_FAILED
                                conversation.sms?.type = Telephony.Sms.MESSAGE_TYPE_FAILED
                                conversation.sms?.error_code = reportStatus ?: callbackResult
                            }
                            try {
                                context.updateSms(uri, conversation)
                                if(conversation.sms?.status == Telephony.Sms.STATUS_FAILED)
                                    context.sendNotificationBroadcast(
                                        conversation,
                                        if(intent.action == SMS_DELIVERED_BROADCAST_INTENT)
                                            NotificationTxType.TEXT else NotificationTxType.DATA
                                    )
                            } catch(e: Exception) {
                                Log.e(CALLBACK_LOG_TAG, "Unable to persist delivered SMS status", e)
                            }
                            }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }

    }

}

/**
 * Android historically documented [Activity.RESULT_OK] for successful SMS callbacks, while
 * current telephony implementations may return SmsManager.RESULT_ERROR_NONE (zero) instead.
 * Keep the value local because the framework constant was only added in API 30 while the
 * library still supports API 24; the callback result itself is stable across those versions.
 */
fun isSuccessfulSmsCallback(resultCode: Int): Boolean =
    resultCode == Activity.RESULT_OK || resultCode == SMS_RESULT_ERROR_NONE

enum class SmsDeliveryReportOutcome {
    DELIVERED,
    PENDING,
    FAILED,
    UNKNOWN,
}

/** Classifies the GSM TP-Status ranges returned by [SmsMessage.getStatus]. */
fun classifyDeliveryReportStatus(status: Int?): SmsDeliveryReportOutcome = when {
    status == null || status < 0 -> SmsDeliveryReportOutcome.UNKNOWN
    status == Telephony.Sms.STATUS_COMPLETE -> SmsDeliveryReportOutcome.DELIVERED
    status < Telephony.Sms.STATUS_PENDING -> SmsDeliveryReportOutcome.UNKNOWN
    status < Telephony.Sms.STATUS_FAILED -> SmsDeliveryReportOutcome.PENDING
    status <= MAX_STANDARD_TP_STATUS -> SmsDeliveryReportOutcome.FAILED
    else -> SmsDeliveryReportOutcome.UNKNOWN
}

fun resolveDeliveryCallbackOutcome(
    reportOutcome: SmsDeliveryReportOutcome,
    callbackResult: Int,
): Boolean? = when(reportOutcome) {
    SmsDeliveryReportOutcome.DELIVERED -> true
    SmsDeliveryReportOutcome.FAILED -> false
    SmsDeliveryReportOutcome.PENDING -> null
    SmsDeliveryReportOutcome.UNKNOWN -> if(isSuccessfulSmsCallback(callbackResult)) {
        null
    } else {
        false
    }
}

@Suppress("DEPRECATION")
fun readDeliveryReportStatus(intent: Intent): Int? {
    val pdu = intent.getByteArrayExtra("pdu") ?: return null
    val format = intent.getStringExtra("format")
    return runCatching {
        val message = if(format.isNullOrBlank()) {
            SmsMessage.createFromPdu(pdu)
        } else {
            SmsMessage.createFromPdu(pdu, format)
        }
        message?.status
    }.getOrNull()
}

private const val SMS_RESULT_ERROR_NONE = 0
private const val MAX_STANDARD_TP_STATUS = 0x7f
