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

fun interface InboundTextSmsHandler {
    /** Return true to keep an application protocol envelope out of ordinary SMS storage. */
    suspend fun consume(context: Context, address: String, subscriptionId: Int, text: String): Boolean
}

object InboundTextSmsHandlerRegistry {
    @Volatile
    var handler: InboundTextSmsHandler? = null

    suspend fun consume(context: Context, address: String, subscriptionId: Int, text: String): Boolean =
        handler?.consume(context, address, subscriptionId, text) == true
}
