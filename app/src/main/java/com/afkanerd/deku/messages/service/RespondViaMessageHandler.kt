package com.afkanerd.deku.messages.service

import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.RespondViaMessageRequest
import com.afkanerd.deku.messages.domain.SendResult

/** Stateless adapter for Android's locked-screen quick-response contract. */
class RespondViaMessageHandler(
    private val messageService: MessageService,
) {
    suspend fun send(request: RespondViaMessageRequest): SendResult {
        val header = messageService.conversationHeader(request.address, null)
        return messageService.sendText(
            address = header.address,
            threadId = header.threadId,
            subscriptionId = header.subscriptionId,
            text = request.text,
        )
    }
}
