package com.afkanerd.deku.messages

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.afkanerd.deku.MessagesApplication
import com.afkanerd.deku.messages.domain.RespondViaMessageRequest
import com.afkanerd.deku.messages.service.RespondViaMessageHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class RespondViaMessageService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val request = if(intent?.action == ACTION_RESPOND_VIA_MESSAGE) {
            RespondViaMessageRequest.create(
                dataUri = intent.dataString,
                text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
            )
        } else null
        if(request == null) {
            stopSelfResult(startId)
            return START_NOT_STICKY
        }

        val messageService = (application as MessagesApplication).messageService
        serviceScope.launch {
            runCatching {
                RespondViaMessageHandler(messageService).send(request)
            }
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
    }
}
