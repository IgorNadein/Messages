package com.afkanerd.deku.attachments.transport

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import androidx.work.BackoffPolicy
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.attachments.protocol.SmsPacketType
import com.afkanerd.deku.attachments.storage.AttachmentTransferStatus
import com.afkanerd.deku.attachments.storage.ChunkTracker
import com.afkanerd.deku.attachments.reliability.AttachmentAckTimeoutWorker
import com.afkanerd.deku.attachments.AttachmentManager
import com.afkanerd.smswithoutborders_libsmsmms.receivers.isSuccessfulSmsCallback
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
        val callbackSuccessful = isSuccessfulSmsCallback(callbackResult)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val dao = Datastore.getDatastore(context).attachmentTransferDao()
                val transfer = dao.get(id) ?: return@launch
                val now = System.currentTimeMillis()
                if (intent.action == ACTION_DELIVERED) {
                    if (callbackSuccessful) {
                        dao.incrementDelivered(id, now)
                    }
                    return@launch
                }
                if (callbackSuccessful) {
                    val updated = when (type) {
                        SmsPacketType.TRANSFER_OFFER -> transfer.copy(
                            controlSequence = maxOf(transfer.controlSequence, index + 1),
                            smsSent = transfer.smsSent + 1,
                            retryCount = 0,
                            lastError = null,
                            updatedAt = now,
                        )
                        SmsPacketType.TRANSFER_CHUNK -> {
                            val sent = ChunkTracker.restore(transfer.totalChunks, transfer.sentBitmap)
                            sent.mark(index)
                            transfer.copy(
                                sentBitmap = sent.serialize(), smsSent = transfer.smsSent + 1,
                                retryCount = 0, lastError = null, updatedAt = now,
                            )
                        }
                        else -> transfer.copy(
                            smsSent = transfer.smsSent + 1,
                            retryCount = 0,
                            lastError = null,
                            updatedAt = now,
                        )
                    }
                    dao.update(updated)
                    if (type == SmsPacketType.TRANSFER_COMPLETE && transfer.outgoing &&
                        transfer.status == AttachmentTransferStatus.COMPLETED.name) {
                        AttachmentManager.get(context).finalizeSentCompletion(id)
                    }
                    if (type == SmsPacketType.TRANSFER_OFFER || type == SmsPacketType.TRANSFER_CHUNK) {
                        enqueue(context, id, PACING_MILLIS)
                    }
                    if (type == SmsPacketType.TRANSFER_CHUNK || type == SmsPacketType.NACK) {
                        AttachmentAckTimeoutWorker.enqueue(context, id)
                    }
                } else {
                    val retry = transfer.retryCount + 1
                    val currentStatus = AttachmentTransferStatus.valueOf(transfer.status)
                    dao.update(transfer.copy(
                        status = if (currentStatus.terminal) transfer.status
                            else if (retry >= MAX_RETRIES) AttachmentTransferStatus.FAILED.name
                            else AttachmentTransferStatus.RETRYING.name,
                        retryCount = retry,
                        lastError = smsError(callbackResult),
                        updatedAt = now,
                    ))
                    if (retry < MAX_RETRIES) enqueue(context, id, retryDelay(retry))
                }
            } finally {
                pending.finish()
            }
        }
    }

    private fun smsError(code: Int): String = when (code) {
        SmsManager.RESULT_ERROR_NO_SERVICE -> "No mobile service"
        SmsManager.RESULT_ERROR_RADIO_OFF -> "Mobile radio is off"
        SmsManager.RESULT_ERROR_LIMIT_EXCEEDED -> "SMS rate limit exceeded"
        SmsManager.RESULT_ERROR_SHORT_CODE_NOT_ALLOWED,
        SmsManager.RESULT_ERROR_SHORT_CODE_NEVER_ALLOWED -> "Destination rejected by SMS policy"
        else -> "SMS send failed ($code)"
    }

    companion object {
        const val ACTION_SENT = "com.afkanerd.deku.ATTACHMENT_SMS_SENT"
        const val ACTION_DELIVERED = "com.afkanerd.deku.ATTACHMENT_SMS_DELIVERED"
        const val EXTRA_TRANSFER_ID = "transfer_id"
        const val EXTRA_PACKET_TYPE = "packet_type"
        const val EXTRA_INDEX = "packet_index"
        private const val PACING_MILLIS = 1_500L
        private const val MAX_RETRIES = 8

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

        private fun retryDelay(retry: Int): Long = minOf(15L * 60_000L, 15_000L shl minOf(retry - 1, 5))
    }
}
