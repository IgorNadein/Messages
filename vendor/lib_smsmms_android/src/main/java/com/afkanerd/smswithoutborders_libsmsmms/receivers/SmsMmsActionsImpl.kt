package com.afkanerd.smswithoutborders_libsmsmms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.app.RemoteInput
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.ContextSmsSender
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.SmsManager
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.cancelNotification
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getCustomizationProperties
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDefaultSimSubscription
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendNotificationBroadcast
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendSms
import com.afkanerd.smswithoutborders_libsmsmms.receivers.SmsTextReceivedReceiver.Companion.SMS_SENT_BROADCAST_INTENT_LIB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.lang.Exception

class SmsMmsActionsImpl : BroadcastReceiver() {
    companion object {
        const val NOTIFICATION_REPLY_ACTION_KEY = "NOTIFICATION_REPLY_ACTION_KEY"

        const val NOTIFICATION_MARK_AS_READ_ACTION_INTENT_ACTION =
            "NOTIFICATION_MARK_AS_READ_ACTION_INTENT_ACTION"

        const val NOTIFICATION_REPLY_ACTION_INTENT_ACTION =
            "NOTIFICATION_REPLY_ACTION_INTENT_ACTION"

        const val NOTIFICATION_REPLY_ACTION_INTENT_ACTION_REPLAY =
            "com.afkanerd.deku.NOTIFICATION_REPLY_ACTION_INTENT_ACTION_REPLAY"

        const val NOTIFICATION_MUTE_ACTION_INTENT_ACTION = "NOTIFICATION_MUTE_ACTION_INTENT_ACTION"
        private const val CALLBACK_LOG_TAG = "SmsNotificationAction"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if(intent.action == null) return
        when(intent.action) {
            NOTIFICATION_REPLY_ACTION_INTENT_ACTION -> {
                extractNotificationReplyCommand(
                    intent = intent,
                    defaultSubscriptionId = context.getDefaultSimSubscription(),
                )?.let { command ->

                    val properties = context.getCustomizationProperties()
                    val broadcast = properties
                        .getProperty("broadcast_notifications_reply_actions").toBoolean()

                    if(broadcast) {
                        context.sendBroadcast(
                            Intent(NOTIFICATION_REPLY_ACTION_INTENT_ACTION_REPLAY).apply{
                                putExtra("address", command.address)
                                putExtra("threadId", command.threadId)
                                putExtra("subscriptionId", command.subscriptionId)
                                putExtra("reply", command.text)
                                setPackage(context.packageName)
                            }
                        )
                    } else {
                        val pending = goAsync()
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                SmsManager(ContextSmsSender()).sendSms(
                                    context = context,
                                    text = command.text,
                                    address = command.address,
                                    subscriptionId = command.subscriptionId,
                                    threadId = command.threadId,
                                ){ conversation ->
                                    conversation?.let {
                                        context.sendNotificationBroadcast(
                                            conversation,
                                            self = true,
                                            type = NotificationTxType.TEXT,
                                        )
                                    }
                                }
                            } catch(e: Exception) {
                                Log.e(CALLBACK_LOG_TAG, "Notification reply could not be sent", e)
                            } finally {
                                pending.finish()
                            }
                        }
                    }
                }
            }
            NOTIFICATION_MARK_AS_READ_ACTION_INTENT_ACTION -> {
                val id = intent.getLongExtra("id", -1)
                val threadId = intent.getIntExtra("thread_id", -1)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        context.getDatabase().conversationsDao()?.getConversation(id)
                            ?.let {
                                it.sms?.read = 1
                                context.getDatabase().conversationsDao()?.update(it)
                            }
                    } catch (e: Exception) {
                        Log.e(CALLBACK_LOG_TAG, "Notification message could not be marked read", e)
                    } finally {
                        pending.finish()
                    }
                }
                context.cancelNotification(threadId)
            }
            NOTIFICATION_MUTE_ACTION_INTENT_ACTION -> {
                val threadId = intent.getIntExtra("thread_id", -1)
                val pending = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        context.getDatabase().threadsDao()?.setMute(true, threadId)
                    } catch (e: Exception) {
                        Log.e(CALLBACK_LOG_TAG, "Notification thread could not be muted", e)
                    } finally {
                        pending.finish()
                    }
                }
                context.cancelNotification(threadId)
            }
        }
    }

}

internal fun resolveNotificationReplySubscription(
    notificationSubscriptionId: Long?,
    defaultSubscriptionId: Long?,
): Long? = notificationSubscriptionId
    ?.takeIf { it >= 0L }
    ?: defaultSubscriptionId?.takeIf { it >= 0L }

data class NotificationReplyCommand(
    val address: String,
    val threadId: Int,
    val subscriptionId: Long,
    val text: String,
)

/** Extracts Android RemoteInput without dispatching or touching the SMS transport. */
fun extractNotificationReplyCommand(
    intent: Intent,
    defaultSubscriptionId: Long?,
): NotificationReplyCommand? {
    if(intent.action != SmsMmsActionsImpl.NOTIFICATION_REPLY_ACTION_INTENT_ACTION) return null
    val remoteInput = RemoteInput.getResultsFromIntent(intent) ?: return null
    val address = intent.getStringExtra("address")
        ?.trim()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    val threadId = intent.getIntExtra("thread_id", -1).takeIf { it >= 0 } ?: return null
    val subscriptionId = resolveNotificationReplySubscription(
        notificationSubscriptionId = intent
            .takeIf { it.hasExtra("sub_id") }
            ?.getLongExtra("sub_id", -1L),
        defaultSubscriptionId = defaultSubscriptionId,
    ) ?: return null
    val text = remoteInput
        .getCharSequence(SmsMmsActionsImpl.NOTIFICATION_REPLY_ACTION_KEY)
        ?.toString()
        ?.takeIf(String::isNotEmpty)
        ?: return null
    return NotificationReplyCommand(address, threadId, subscriptionId, text)
}
