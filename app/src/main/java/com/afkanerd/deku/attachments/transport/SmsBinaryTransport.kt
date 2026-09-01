package com.afkanerd.deku.attachments.transport

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import com.afkanerd.deku.attachments.protocol.SmsFrame
import com.afkanerd.deku.attachments.protocol.SmsFrameCodec

class SmsBinaryTransport(private val context: Context) : BinaryTransport {
    override suspend fun send(frame: SmsFrame, route: BinaryRoute): BinarySendResult {
        val bytes = try {
            SmsFrameCodec.encode(frame)
        } catch (error: Exception) {
            return BinarySendResult.Failed("Invalid attachment frame", error)
        }
        if (bytes.size > MAX_APPLICATION_BYTES) {
            return BinarySendResult.Failed("Attachment frame exceeds conservative SMS limit")
        }
        val statusIntent = Intent(context, AttachmentSmsStatusReceiver::class.java).apply {
            action = AttachmentSmsStatusReceiver.ACTION_SENT
            data = Uri.Builder().scheme("deku-attachment").authority("sent")
                .appendPath(frame.transferId.toHex())
                .appendPath(frame.packetType.wireValue.toString())
                .appendPath(frame.chunkIndex.toString())
                .build()
            putExtra(AttachmentSmsStatusReceiver.EXTRA_TRANSFER_ID, frame.transferId.toHex())
            putExtra(AttachmentSmsStatusReceiver.EXTRA_PACKET_TYPE, frame.packetType.wireValue)
            putExtra(AttachmentSmsStatusReceiver.EXTRA_INDEX, frame.chunkIndex)
        }
        val sent = PendingIntent.getBroadcast(
            context,
            statusIntent.data.hashCode(),
            statusIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val deliveredIntent = Intent(context, AttachmentSmsStatusReceiver::class.java).apply {
            action = AttachmentSmsStatusReceiver.ACTION_DELIVERED
            data = Uri.Builder().scheme("deku-attachment").authority("delivered")
                .appendPath(frame.transferId.toHex())
                .appendPath(frame.packetType.wireValue.toString())
                .appendPath(frame.chunkIndex.toString())
                .build()
            putExtra(AttachmentSmsStatusReceiver.EXTRA_TRANSFER_ID, frame.transferId.toHex())
            putExtra(AttachmentSmsStatusReceiver.EXTRA_PACKET_TYPE, frame.packetType.wireValue)
            putExtra(AttachmentSmsStatusReceiver.EXTRA_INDEX, frame.chunkIndex)
        }
        val delivered = PendingIntent.getBroadcast(
            context,
            deliveredIntent.data.hashCode(),
            deliveredIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return try {
            smsManager(route.subscriptionId).sendDataMessage(
                route.address,
                null,
                DESTINATION_PORT,
                bytes,
                sent,
                delivered,
            )
            BinarySendResult.Dispatched
        } catch (error: Exception) {
            BinarySendResult.Failed("SmsManager rejected attachment frame", error)
        }
    }

    private fun smsManager(subscriptionId: Int): SmsManager =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }

    companion object {
        const val MAX_APPLICATION_BYTES: Int = 120
        const val DESTINATION_PORT: Short = 8200
    }
}
