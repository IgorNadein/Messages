package com.afkanerd.deku.security

import android.content.Context
import android.provider.Telephony
import android.util.Base64
import android.util.Log
import com.afkanerd.deku.DefaultSMS.R
import com.afkanerd.deku.messages.domain.SecureMessageTransport
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsMmsNatives
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getThreadId
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.makeE16PhoneNumber
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendNotificationBroadcast
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.settingsGetKeepMessagesArchived
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundDataSmsHandler

/** Consumes encrypted message envelopes before the library's legacy Data-SMS storage path. */
class SecureDataSmsHandler : InboundDataSmsHandler {
    override suspend fun consume(
        context: Context,
        address: String,
        subscriptionId: Int,
        payload: ByteArray,
    ): Boolean {
        if(payload.firstOrNull() != SecureMessageCodec.TYPE_MESSAGE) return false
        if(SecureMessageCodec.decodeMessageOrNull(payload) == null) return true
        if(SecureMessageTransportPreference.selected(context, subscriptionId.toLong()) !=
            SecureMessageTransport.DATA_SMS
        ) {
            Log.w(TAG, "Secure Data SMS ignored because the transport is disabled")
            return true
        }

        val normalizedAddress = runCatching { context.makeE16PhoneNumber(address) }
            .getOrDefault(address)
        val channelAddress = SecureChannelId.storageAddress(
            normalizedAddress,
            subscriptionId.toLong(),
        )
        val transportText = Base64.encodeToString(payload, Base64.NO_WRAP)
        val body = try {
            check(SecureSessionStatusResolver.resolve(
                context,
                normalizedAddress,
                subscriptionId.toLong(),
            ) ==
                SecureSessionStatus.SECURE_ESTABLISHED
            ) { "Secure session is not established" }
            EncryptionController.decryptBytes(context, channelAddress, payload)
                ?.decodeToString()
                ?: throw SecurityException("Secure Data SMS returned no plaintext")
        } catch(error: Exception) {
            Log.w(TAG, "Secure Data SMS could not be decrypted", error)
            EncryptionController.markSessionBroken(context, channelAddress)
            context.getString(R.string.security_decryption_failed)
        }

        val now = System.currentTimeMillis()
        val conversation = Conversations(
            sms = SmsMmsNatives.Sms(
                thread_id = context.getThreadId(normalizedAddress),
                address = normalizedAddress,
                date = now,
                date_sent = now,
                read = 0,
                status = Telephony.Sms.STATUS_NONE,
                type = Telephony.Sms.MESSAGE_TYPE_INBOX,
                body = body,
                sub_id = subscriptionId.toLong(),
            ),
            sms_data = payload,
            secure_transport_text = transportText,
        )
        context.getDatabase().conversationsDao()
            ?.insert(conversation, context.settingsGetKeepMessagesArchived)
            ?.let { conversation.id = it }
        SecureMessageTransportPreference.markFirstLegacyMessageComplete(
            context,
            normalizedAddress,
            subscriptionId.toLong(),
        )
        val thread = context.getDatabase().threadsDao()?.get(conversation.sms?.thread_id!!)
        context.sendNotificationBroadcast(
            conversation,
            type = NotificationTxType.DATA,
            showNotification = thread?.isMute != true,
        )
        return true
    }

    private companion object {
        const val TAG = "SecureDataSms"
    }
}
