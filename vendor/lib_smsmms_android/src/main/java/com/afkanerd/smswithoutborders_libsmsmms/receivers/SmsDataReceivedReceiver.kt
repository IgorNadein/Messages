package com.afkanerd.smswithoutborders_libsmsmms.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.NotificationTxType
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.registerIncomingDataSms
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendNotificationBroadcast
import com.afkanerd.smswithoutborders_libsmsmms.transport.InboundDataSmsHandlerRegistry
import com.afkanerd.smswithoutborders_libsmsmms.transport.DataSmsFragmentCodec
import com.afkanerd.smswithoutborders_libsmsmms.transport.DataSmsFragmentStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

//import org.bouncycastle.operator.OperatorCreationException;
class SmsDataReceivedReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context?, intent: Intent?) {
        if(context == null || intent == null) return

        if(intent.action == Telephony.Sms.Intents.DATA_SMS_RECEIVED_ACTION) {
            val pendingResult = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    val address = messages.firstOrNull()?.displayOriginatingAddress.orEmpty()
                    val subscriptionId = intent.extras?.getInt("subscription", -1) ?: -1
                    var payload = messages.fold(ByteArray(0)) { accumulated, message ->
                        accumulated + (message.userData ?: ByteArray(0))
                    }
                    when(val decoded = DataSmsFragmentCodec.decode(payload)) {
                        is DataSmsFragmentCodec.DecodeResult.Success -> {
                            when(val stored = DataSmsFragmentStore.accept(
                                context,
                                address,
                                decoded.frame,
                            )) {
                                is DataSmsFragmentStore.Result.Complete -> payload = stored.payload
                                DataSmsFragmentStore.Result.Pending,
                                DataSmsFragmentStore.Result.Rejected -> return@launch
                            }
                        }
                        DataSmsFragmentCodec.DecodeResult.Rejected -> return@launch
                        DataSmsFragmentCodec.DecodeResult.NotFragment -> Unit
                    }
                    if (address.isNotBlank() && payload.isNotEmpty() &&
                        InboundDataSmsHandlerRegistry.consume(
                            context.applicationContext,
                            address,
                            subscriptionId,
                            payload,
                        )
                    ) return@launch

                    val conversation = context.registerIncomingDataSms(
                        address = address,
                        subscriptionId = subscriptionId,
                        payload = payload,
                        dateSent = messages.minOfOrNull { it.timestampMillis }
                            ?: System.currentTimeMillis(),
                    )
                    context.getDatabase().threadsDao()?.get(conversation.sms?.thread_id!!)?.let {
                        if(!it.isMute) context.sendNotificationBroadcast(
                            conversation, type = NotificationTxType.DATA)
                    }
                } finally {
                    pendingResult.finish()
                }
            }
        }
    }
}
