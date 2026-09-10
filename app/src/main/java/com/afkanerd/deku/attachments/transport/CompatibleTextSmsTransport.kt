package com.afkanerd.deku.attachments.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.smsStatusPendingIntentFlags
import java.util.UUID

/** Sends the same authenticated attachment frame using ordinary text SMS segments. */
class CompatibleTextSmsTransport(private val context: Context) : BinaryTransport {
    override suspend fun send(frame: SmsFrame, route: BinaryRoute): BinarySendResult {
        val text = runCatching { CompatibleTextSmsCodec.encode(frame) }
            .getOrElse { return BinarySendResult.Failed("Invalid attachment frame", it) }
        return try {
            val manager = smsManager(route.subscriptionId)
            val parts = manager.divideMessage(text).ifEmpty { arrayListOf(text) }
            // PendingIntent identity does not include extras. Give every physical dispatch its
            // own identity so a retry of the same protocol frame cannot reuse stale callbacks.
            val callbackToken = UUID.randomUUID().toString()
            val sent = ArrayList<PendingIntent>(parts.size)
            val delivered = ArrayList<PendingIntent>(parts.size)
            parts.indices.forEach { partIndex ->
                sent += statusIntent(frame, callbackToken, partIndex, parts.size, delivered = false)
                delivered += statusIntent(frame, callbackToken, partIndex, parts.size, delivered = true)
            }
            if(parts.size == 1) {
                manager.sendTextMessage(route.address, null, text, sent.single(), delivered.single())
            } else {
                manager.sendMultipartTextMessage(route.address, null, parts, sent, delivered)
            }
            BinarySendResult.Dispatched
        } catch(error: Exception) {
            BinarySendResult.Failed("SmsManager rejected compatible text attachment frame", error)
        }
    }

    private fun statusIntent(
        frame: SmsFrame,
        callbackToken: String,
        partIndex: Int,
        partCount: Int,
        delivered: Boolean,
    ): PendingIntent {
        val intent = Intent(context, AttachmentSmsStatusReceiver::class.java).apply {
            action = if(delivered) AttachmentSmsStatusReceiver.ACTION_DELIVERED
            else AttachmentSmsStatusReceiver.ACTION_SENT
            data = Uri.Builder().scheme("deku-attachment-text")
                .authority(if(delivered) "delivered" else "sent")
                .appendPath(frame.transferId.toHex())
                .appendPath(frame.packetType.wireValue.toString())
                .appendPath(frame.chunkIndex.toString())
                .appendPath(callbackToken)
                .appendPath(partIndex.toString())
                .build()
            putExtra(AttachmentSmsStatusReceiver.EXTRA_TRANSFER_ID, frame.transferId.toHex())
            putExtra(AttachmentSmsStatusReceiver.EXTRA_PACKET_TYPE, frame.packetType.wireValue)
            putExtra(AttachmentSmsStatusReceiver.EXTRA_INDEX, frame.chunkIndex)
            putExtra(AttachmentSmsStatusReceiver.EXTRA_TEXT_CALLBACK_TOKEN, callbackToken)
            putExtra(AttachmentSmsStatusReceiver.EXTRA_TEXT_PART_INDEX, partIndex)
            putExtra(AttachmentSmsStatusReceiver.EXTRA_TEXT_PART_COUNT, partCount)
        }
        return PendingIntent.getBroadcast(
            context,
            intent.data.hashCode(),
            intent,
            smsStatusPendingIntentFlags(),
        )
    }

    private fun smsManager(subscriptionId: Int): SmsManager =
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
}
