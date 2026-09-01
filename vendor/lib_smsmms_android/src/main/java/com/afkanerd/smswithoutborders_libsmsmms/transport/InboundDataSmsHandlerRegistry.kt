package com.afkanerd.smswithoutborders_libsmsmms.transport

import android.content.Context

fun interface InboundDataSmsHandler {
    /** Return true when the packet belongs to the handler and must not enter legacy SMS storage. */
    suspend fun consume(context: Context, address: String, subscriptionId: Int, payload: ByteArray): Boolean
}

object InboundDataSmsHandlerRegistry {
    @Volatile
    var handler: InboundDataSmsHandler? = null

    suspend fun consume(context: Context, address: String, subscriptionId: Int, payload: ByteArray): Boolean =
        handler?.consume(context, address, subscriptionId, payload) == true
}
