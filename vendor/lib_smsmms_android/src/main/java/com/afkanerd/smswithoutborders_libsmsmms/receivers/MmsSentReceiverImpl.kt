package com.afkanerd.smswithoutborders_libsmsmms.receivers

import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.lib_smsmms_android.R
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendNotificationBroadcast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MmsSentReceiverImpl: BroadcastReceiver() {
    @SuppressLint("Range")
    override fun onReceive(context: Context, intent: Intent) {
        val uri = intent.getStringExtra(EXTRA_CONTENT_URI)
        val filepath = intent.getStringExtra(EXTRA_FILE_PATH)
        val id = intent.getLongExtra(EXTRA_ORIGINAL_RESENT_MESSAGE_ID, -1)
        val callbackResult = resultCode
        Log.i(CALLBACK_LOG_TAG, "MMS sent callback result=$callbackResult id=$id")

        val successful = callbackResult == Activity.RESULT_OK
        val messageBox = if (successful) {
            Telephony.Mms.MESSAGE_BOX_SENT
        } else {
            val msg = context.getString(R.string.unknown_error_sending_mms)
            Toast.makeText(context, msg + callbackResult, Toast.LENGTH_LONG).show()
            Telephony.Mms.MESSAGE_BOX_FAILED
        }

        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                context.getDatabase().conversationsDao()
                    ?.getConversation(id)
                    ?.let { conversation ->
                        conversation.sms?.status = if(successful) {
                            Telephony.Sms.STATUS_NONE
                        } else {
                            Telephony.Sms.STATUS_FAILED
                        }
                        conversation.sms?.type = if(successful) {
                            Telephony.Sms.MESSAGE_TYPE_SENT
                        } else {
                            Telephony.Sms.MESSAGE_TYPE_FAILED
                        }
                        conversation.mms = conversation.mms?.copy(msg_box = messageBox)
                        conversation.mms_filepath = filepath
                        context.getDatabase().conversationsDao()?.update(conversation)

                        if(!successful) {
                            context.sendNotificationBroadcast(
                                conversation,
                                type = NotificationTxType.MMS,
                            )
                        }
                    }
            } catch(error: Throwable) {
                Log.e(CALLBACK_LOG_TAG, "Unable to persist MMS sent status", error)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_CONTENT_URI = "content_uri"
        private const val EXTRA_FILE_PATH = "file_path"
        private const val CALLBACK_LOG_TAG = "MmsStatusCallback"
        const val EXTRA_ORIGINAL_RESENT_MESSAGE_ID = "original_message_id"
    }
}
