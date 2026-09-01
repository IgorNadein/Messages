package com.afkanerd.deku.messages.service

import com.afkanerd.deku.Router.Models.RouterHandler
import com.afkanerd.deku.messages.domain.RoutingHistoryItem
import com.afkanerd.deku.messages.domain.RoutingHistoryState
import com.afkanerd.smswithoutborders_libsmsmms.data.entities.Conversations

internal object RoutingHistoryMapper {
    data class RouteTags(val messageId: Long, val gatewayId: Long)

    fun parseTags(tags: Set<String>): RouteTags? {
        val messageId = tags.asSequence()
            .filter { it.startsWith(RouterHandler.TAG_GATEWAY_SERVER_MESSAGE_ID) }
            .mapNotNull { it.substringAfter(':', "").toLongOrNull() }
            .firstOrNull()
            ?: return null
        val gatewayId = tags.asSequence()
            .filter { it.startsWith(RouterHandler.TAG_GATEWAY_SERVER_ID) }
            .mapNotNull { it.substringAfter(':', "").toLongOrNull() }
            .firstOrNull()
            ?: return null
        return RouteTags(messageId, gatewayId)
    }

    fun toItem(
        workId: String,
        tags: Set<String>,
        workerState: String,
        conversation: Conversations?,
        displayName: String? = null,
    ): RoutingHistoryItem? {
        val route = parseTags(tags) ?: return null
        val sms = conversation?.sms ?: return null
        val address = sms.address?.takeIf(String::isNotBlank) ?: return null
        return RoutingHistoryItem(
            workId = workId,
            messageId = route.messageId,
            gatewayId = route.gatewayId,
            address = address,
            displayName = displayName?.takeIf(String::isNotBlank) ?: address,
            body = sms.body.orEmpty(),
            timestampMillis = sms.date,
            state = RoutingHistoryState.fromWorkerName(workerState),
        )
    }
}
