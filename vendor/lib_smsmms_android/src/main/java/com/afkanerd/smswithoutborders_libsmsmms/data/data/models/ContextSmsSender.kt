package com.afkanerd.smswithoutborders_libsmsmms.data.data.models

import android.content.Context
import android.os.Bundle
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations
import com.afkanerd.smswithoutborders_libsmsmms.extensions.context.sendSms
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Backend sender for non-UI entry points such as notification actions and workers. */
class ContextSmsSender(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    private val onFailure: (Throwable) -> Unit = {},
) : SmsSender {
    override fun sendSms(
        context: Context,
        text: String,
        address: String,
        subscriptionId: Long,
        threadId: Int,
        data: ByteArray?,
        bundle: Bundle,
        callback: (Conversations?) -> Unit,
    ) {
        scope.launch {
            try {
                context.sendSms(
                    text = text,
                    address = address,
                    threadId = threadId,
                    subscriptionId = subscriptionId,
                    data = data,
                    bundle = bundle,
                )?.let(callback)
            } catch(error: CancellationException) {
                throw error
            } catch(error: Throwable) {
                onFailure(error)
            }
        }
    }
}
