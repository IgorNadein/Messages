package com.afkanerd.deku.attachments.transport

import com.afkanerd.deku.attachments.protocol.SmsFrame

data class BinaryRoute(val address: String, val subscriptionId: Int)

sealed interface BinarySendResult {
    data object Dispatched : BinarySendResult
    data class Failed(val reason: String, val cause: Throwable? = null) : BinarySendResult
}

interface BinaryTransport {
    suspend fun send(frame: SmsFrame, route: BinaryRoute): BinarySendResult
}
