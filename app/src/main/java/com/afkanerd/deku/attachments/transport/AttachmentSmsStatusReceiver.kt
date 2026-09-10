package com.afkanerd.deku.attachments.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.afkanerd.deku.attachments.protocol.SmsPacketType
import com.afkanerd.deku.attachments.AttachmentManager
import com.afkanerd.smswithoutborders_libsmsmms.receivers.isSuccessfulSmsCallback
import com.afkanerd.smswithoutborders_libsmsmms.receivers.classifyDeliveryReportStatus
import com.afkanerd.smswithoutborders_libsmsmms.receivers.readDeliveryReportStatus
import com.afkanerd.smswithoutborders_libsmsmms.receivers.resolveDeliveryCallbackOutcome
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class AttachmentSmsStatusReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_SENT && intent.action != ACTION_DELIVERED) return
        val id = intent.getStringExtra(EXTRA_TRANSFER_ID) ?: return
        val type = SmsPacketType.fromWireValue(intent.getIntExtra(EXTRA_PACKET_TYPE, -1)) ?: return
        val index = intent.getIntExtra(EXTRA_INDEX, -1)
        val callbackResult = resultCode
        val deliveredCallback = intent.action == ACTION_DELIVERED
        val reportStatus = if(deliveredCallback) readDeliveryReportStatus(intent) else null
        val reportOutcome = if(deliveredCallback) {
            classifyDeliveryReportStatus(reportStatus)
        } else null
        val callbackSuccessful = if(deliveredCallback) {
            resolveDeliveryCallbackOutcome(requireNotNull(reportOutcome), callbackResult)
        } else {
            isSuccessfulSmsCallback(callbackResult)
        }
        // A missing or temporary TP-Status is not proof of delivery. A later status report or
        // the bounded receiver-acknowledgement timeout can settle the transfer.
        if(callbackSuccessful == null) return
        val partIndex = intent.getIntExtra(EXTRA_TEXT_PART_INDEX, 0)
        val partCount = intent.getIntExtra(EXTRA_TEXT_PART_COUNT, 1)
        val callbackToken = intent.getStringExtra(EXTRA_TEXT_CALLBACK_TOKEN)
        val callbackKey = "${intent.action}.$id.${type.wireValue}.$index.$callbackToken"
        if(callbackToken != null && !AttachmentSmsPartStatusTracker.shouldFinalize(
                context = context,
                key = callbackKey,
                partIndex = partIndex,
                partCount = partCount,
                successful = callbackSuccessful,
            )
        ) return
        if(!callbackSuccessful) {
            Log.w(
                "AttachmentSmsStatus",
                "SMS callback failed: transfer=$id type=$type index=$index " +
                    "delivered=$deliveredCallback result=$callbackResult " +
                    "tpStatus=${reportStatus ?: "missing"} outcome=${reportOutcome ?: "sent"}",
            )
        }
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AttachmentManager.get(context).onSmsTransportStatus(
                    transferIdHex = id,
                    packetType = type,
                    packetIndex = index,
                    deliveredCallback = deliveredCallback,
                    successful = callbackSuccessful,
                    resultCode = reportStatus ?: callbackResult,
                )
            } catch(error: Throwable) {
                Log.e("AttachmentSmsStatus", "Unable to persist SMS transport callback", error)
                enqueue(context, id, CALLBACK_RECOVERY_MILLIS)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_SENT = "com.afkanerd.deku.ATTACHMENT_SMS_SENT"
        const val ACTION_DELIVERED = "com.afkanerd.deku.ATTACHMENT_SMS_DELIVERED"
        const val EXTRA_TRANSFER_ID = "transfer_id"
        const val EXTRA_PACKET_TYPE = "packet_type"
        const val EXTRA_INDEX = "packet_index"
        const val EXTRA_TEXT_CALLBACK_TOKEN = "text_callback_token"
        const val EXTRA_TEXT_PART_INDEX = "text_part_index"
        const val EXTRA_TEXT_PART_COUNT = "text_part_count"
        private const val CALLBACK_RECOVERY_MILLIS = 30_000L

        fun enqueue(context: Context, transferId: String, delayMillis: Long = 0) {
            val request = OneTimeWorkRequestBuilder<AttachmentSendWorker>()
                .setInputData(Data.Builder().putString(EXTRA_TRANSFER_ID, transferId).build())
                .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                "attachment-send-$transferId",
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
