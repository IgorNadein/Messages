package com.afkanerd.deku.messages.service

import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.NotificationReplyRequest
import com.afkanerd.deku.messages.domain.SendResult

/** Stateless background entry point; encryption remains enforced by the transport policy. */
class NotificationReplyHandler(
    private val messageService: MessageService,
) {
    suspend fun send(request: NotificationReplyRequest): SendResult =
        messageService.sendNotificationReply(
            address = request.address,
            threadId = request.threadId,
            subscriptionId = request.subscriptionId,
            text = request.text,
        )
}
