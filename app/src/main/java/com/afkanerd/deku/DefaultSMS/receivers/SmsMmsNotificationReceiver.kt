package com.afkanerd.deku.DefaultSMS.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import com.afkanerd.deku.MainActivity
import com.afkanerd.deku.MessagesApplication
import com.afkanerd.deku.Router.ui.viewModels.GatewayServerViewModel
import com.afkanerd.deku.messages.domain.NotificationReplyRequest
import com.afkanerd.deku.messages.domain.SendResult
import com.afkanerd.deku.messages.service.NotificationReplyHandler
import com.afkanerd.deku.security.SecureMessageCodec
import com.afkanerd.deku.DefaultSMS.R as AppR
import com.afkanerd.lib_smsmms_android.R
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.EncryptionController
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.SavedEncryptedModes
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.getEncryptionModeStatesSync
import com.afkanerd.smswithoutborders.libsignal_doubleratchet.removeEncryptionRatchetStates
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.notify
import com.afkanerd.smswithoutborders_libsmsmms.receivers.SmsMmsActionsImpl
import com.afkanerd.smswithoutborders_libsmsmms.receivers.SmsTextReceivedReceiver
import com.afkanerd.smswithoutborders_libsmsmms.security.SECURE_TRANSPORT_TEXT_EXTRA
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsMmsNotificationReceiver: BroadcastReceiver() {
    private val cls = MainActivity::class.java
    override fun onReceive(context: Context?, intent: Intent?) {
        when(intent?.action) {
            SmsTextReceivedReceiver.SMS_SENT_BROADCAST_INTENT_LIB -> {
                val id = intent.getLongExtra("id", -1)
                val self = intent.getBooleanExtra("self", false)
                val type = intent.getStringExtra("type")
                val showNotification = intent.getBooleanExtra("showNotification", true)
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        context?.getDatabase()?.conversationsDao()
                            ?.getConversation(id)?.let { conversation ->
                            when(conversation.sms?.status) {
                                Telephony.Sms.STATUS_FAILED -> {
                                    notifyMessageFailedToSend(context, conversation)

                                    if(conversation.sms_data != null) {
                                        EncryptionController.markSessionBroken(
                                            context,
                                            conversation.sms?.address!!,
                                        )
                                    }
                                }
                                else -> {
                                    if(self) {
                                        intent.getStringExtra(SECURE_TRANSPORT_TEXT_EXTRA)?.let {
                                            EncryptionController.markOutboundSent(
                                                context,
                                                conversation.sms?.address!!,
                                                it,
                                            )
                                            conversation.secure_transport_text = null
                                            context.getDatabase().conversationsDao()
                                                ?.update(conversation)
                                        }
                                    } else {
                                        if(type == NotificationTxType.DATA.name) {
                                            processEncryptedContent(context, conversation)
                                        } else {
                                            processEncryptedMessage(context, conversation)?.let {
                                                conversation.sms?.body = it.displayText
                                                conversation.secure_transport_text =
                                                    it.failedTransportText
                                                context.getDatabase().conversationsDao()
                                                    ?.update(conversation)
                                            }
                                        }
                                    }

                                    GatewayServerViewModel().route(context, conversation)

                                    if(showNotification) {
                                        context.notify(
                                            conversation = conversation,
                                            cls = cls,
                                            self = self,
                                        )
                                    }
                                }
                            }
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }

            SmsMmsActionsImpl.NOTIFICATION_REPLY_ACTION_INTENT_ACTION_REPLAY -> {
                val safeContext = context ?: return
                val request = NotificationReplyRequest.create(
                    address = intent.getStringExtra("address"),
                    threadId = intent.getIntExtra("threadId", -1),
                    subscriptionId = intent.getLongExtra("subscriptionId", -1),
                    text = intent.getStringExtra("reply"),
                ) ?: return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        val application = safeContext.applicationContext as? MessagesApplication
                            ?: error("MessagesApplication is unavailable")
                        when(NotificationReplyHandler(application.messageService).send(request)) {
                            is SendResult.Sent -> Unit
                            is SendResult.BlockedBySecurity,
                            is SendResult.Failed -> withContext(Dispatchers.Main) {
                                Toast.makeText(
                                    safeContext,
                                    AppR.string.oneui_notification_reply_failed,
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        }
                    } catch(error: Throwable) {
                        Log.e(
                            "NotificationReply",
                            "Notification reply could not be queued",
                            error,
                        )
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
        }
    }

    private fun notifyMessageFailedToSend(context: Context, conversation: Conversations) {
        val content = context
            .getString(
                R.string
                    .message_failed_send_notification_description_a_message_failed_to_send_to) +
                " ${conversation.sms?.address}"

        context.notify(
            conversation = conversation,
            actions = false,
            text = content,
            cls = cls,
        )
    }

    private suspend fun processEncryptedContent(
        context: Context,
        conversation: Conversations
    ) {
        val data = conversation.sms_data!!
        try {
            SecureMessageCodec.decodeKeyExchangeOrNull(data)
                ?: throw SecurityException("Malformed secure key-exchange payload")
            EncryptionController.receiveRequest(
                context,
                conversation.sms?.address!!,
                data
            )
        } catch(e: Exception) {
            withContext(Dispatchers.Main) {
                Toast.makeText(context, e.message, Toast.LENGTH_LONG).show()
            }
        }
    }

    private data class ProcessedSecureMessage(
        val displayText: String,
        val failedTransportText: String? = null,
    )

    private suspend fun processEncryptedMessage(
        context: Context,
        conversation: Conversations,
    ) : ProcessedSecureMessage? {
        val transportText = conversation.sms?.body ?: return null
        if(SecureMessageCodec.decodeTextOrNull(transportText) == null) return null

        val failure = ProcessedSecureMessage(
            displayText = context.getString(AppR.string.security_decryption_failed),
            failedTransportText = transportText,
        )
        return try {
            val data = context.getEncryptionModeStatesSync(
                conversation.sms?.address!!
            ) ?: throw IllegalStateException("Secure mode state is missing")
            val saveData = SavedEncryptedModes.deserialize(data)
            check(saveData.mode == EncryptionController.SecureRequestMode.REQUEST_ACCEPTED) {
                "Secure session setup is not complete"
            }
            val plaintext = EncryptionController.decrypt(
                context,
                conversation.sms?.address!!,
                transportText,
            ) ?: throw SecurityException("Secure decryption returned no plaintext")
            ProcessedSecureMessage(plaintext)
        } catch(e: Exception) {
            Log.w("SecureMessage", "Incoming secure message could not be decrypted", e)
            conversation.sms?.address?.let {
                EncryptionController.markSessionBroken(context, it)
            }
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    context,
                    AppR.string.security_decryption_failed,
                    Toast.LENGTH_LONG,
                ).show()
            }
            failure
        }
    }
}
