package com.afkanerd.smswithoutborders_libsmsmms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.core.net.toUri
import com.afkanerd.smswithoutborders_libsmsmms.data.data.models.MmsParser
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.insertMms
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendNotificationBroadcast
import com.klinker.android.send_message.MmsReceivedReceiver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundMmsHandlerRegistry

class MmsReceivedReceiverImpl: MmsReceivedReceiver() {
    override fun onMessageReceived(context: Context?, contentUri: Uri?) {
        context?.let { context ->
            processIncomingMms(context, contentUri)
        }
    }

    fun processIncomingMms(
        context: Context,
        contentUri: Uri?,
    ) {
        contentUri?.let { uri ->
            CoroutineScope(Dispatchers.IO).launch {
                if(InboundMmsHandlerRegistry.consume(context.applicationContext, uri)) {
                    // Internal encrypted parts must not remain visible as unknown
                    // attachments when the user temporarily switches SMS apps.
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    return@launch
                }
                context.contentResolver?.query(
                    uri,
                    null,
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if(cursor.moveToFirst()) {
                        MmsParser.parse(context, cursor)?.let{ conversation ->
                            // Android already persisted the received MMS at contentUri.
                            // Only mirror it into the app database; inserting an SMS row
                            // here duplicated/corrupted MMS threads on some OEM providers.
                            context.insertMms(conversation)
                            context.getDatabase().threadsDao()?.get(conversation.sms?.thread_id!!)
                                ?.let {
                                    if(!it.isMute)
                                        context.sendNotificationBroadcast(
                                            conversation,
                                            type = NotificationTxType.MMS
                                        )
                                }
                        }
                    }
                }
            }
        }
    }

    override fun onError(p0: Context?, p1: String?) {
        Log.e("MmsReceivedReceiver", p1 ?: "Unknown MMS receive error")
    }
}
