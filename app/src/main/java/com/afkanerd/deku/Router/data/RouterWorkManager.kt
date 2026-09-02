package com.afkanerd.deku.Router.data

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.afkanerd.deku.Datastore
import com.afkanerd.deku.Modules.Network
import com.afkanerd.deku.Router.data.models.FTP
import com.afkanerd.deku.Router.Models.RouterHandler
import com.afkanerd.deku.Router.Models.RouterItem
import com.afkanerd.deku.Router.data.models.SMTP
import com.afkanerd.deku.security.SecureSessionStatus
import com.afkanerd.deku.security.SecureSessionStatusResolver
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.getDatabase
import com.sun.mail.util.MailConnectException
import kotlinx.coroutines.runBlocking

class RouterWorkManager (context: Context, workerParams: WorkerParameters)
    : Worker(context, workerParams) {
    override fun doWork(): Result {
        val gatewayServerId = inputData.getLong(GATEWAY_SERVER_ID, -1)
        val conversationId = inputData.getString(CONVERSATION_ID) ?: return Result.failure()

        val datastore = Datastore.getDatastore(applicationContext)
        val gatewayServer = datastore.gatewayServerDAO()[gatewayServerId.toString()]
            ?: return Result.failure()
        val conversation = applicationContext.getDatabase()
            .conversationsDao()?.getConversation(conversationId.toLong())
            ?: return Result.failure()

        val address = conversation.sms?.address ?: return Result.failure()
        val sessionStatus = runBlocking {
            SecureSessionStatusResolver.resolve(
                applicationContext,
                address,
                conversation.sms?.sub_id ?: -1,
            )
        }
        if(sessionStatus != SecureSessionStatus.PLAIN) {
            // Re-check at execution time: work may have been queued before the
            // conversation became secure.
            return Result.failure()
        }

        val routerItem = RouterItem(conversation.sms!!)
        routerItem.tag = gatewayServer.tag

        val jsonStringBody = routerItem.serializeJson()
        when(gatewayServer.protocol) {
            SMTP.PROTOCOL -> {
                try {
                    RouterHandler.routeSmtpMessages(jsonStringBody, gatewayServer)
                } catch (e: Exception) {
                    if (e is MailConnectException) { return Result.retry() }
                    return Result.failure()
                }
            }
            FTP.PROTOCOL -> {
                try {
                    RouterHandler.routeFTPMessages(jsonStringBody, gatewayServer)
                } catch (e: Exception) {
                    Log.w(javaClass.name, "FTP routing failed")
                    return Result.failure()
                }
            }
            else -> {
                return try {
                    when(Network.jsonRequestPost(gatewayServer.URL!!,
                        jsonStringBody)
                        .response.statusCode
                    ) {
                        in 500..600 -> Result.retry()
                        in 200..299 -> Result.success()
                        else -> Result.failure()
                    }
                } catch(e: IllegalArgumentException) {
                    Result.failure()
                } catch(e: Exception) {
                    Log.w(javaClass.name, "HTTPS routing failed")
                    Result.retry()
                }
            }
        }

        return Result.success()
    }

    companion object {
        var GATEWAY_SERVER_ID = "GATEWAY_SERVER_ID"
        var CONVERSATION_ID = "CONVERSATION_ID"
    }
}
