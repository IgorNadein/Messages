package com.afkanerd.deku.messages.service

import com.afkanerd.deku.RemoteListeners.Models.RemoteListeners
import com.afkanerd.deku.RemoteListeners.Models.RemoteListenersQueues
import com.afkanerd.deku.messages.domain.RemoteListenerDraft
import com.afkanerd.deku.messages.domain.RemoteListenerProtocol
import com.afkanerd.deku.messages.domain.RemoteListenerSummary
import com.afkanerd.deku.messages.domain.RemoteQueueDraft
import com.afkanerd.deku.messages.domain.RemoteQueueSummary

object RemoteListenerConfigurationMapper {
    fun toSummary(entity: RemoteListeners, connected: Boolean): RemoteListenerSummary =
        RemoteListenerSummary(
            id = entity.id,
            displayName = entity.friendlyConnectionName.orEmpty(),
            host = entity.hostUrl.orEmpty(),
            username = entity.username.orEmpty(),
            port = entity.port,
            virtualHost = entity.virtualHost.orEmpty(),
            protocol = entity.protocol.toProtocol(),
            activated = entity.activated,
            connected = connected,
        )

    fun toDraft(entity: RemoteListeners): RemoteListenerDraft = RemoteListenerDraft(
        host = entity.hostUrl.orEmpty(),
        username = entity.username.orEmpty(),
        password = entity.password.orEmpty(),
        displayName = entity.friendlyConnectionName.orEmpty(),
        virtualHost = entity.virtualHost.orEmpty().ifBlank { "/" },
        port = entity.port.takeIf { it in 1..65535 }
            ?.toString()
            ?: RemoteListenerDraft.DEFAULT_AMQP_PORT.toString(),
        protocol = entity.protocol.toProtocol(),
    )

    fun toEntity(
        existing: RemoteListeners?,
        draft: RemoteListenerDraft,
        nowMillis: Long,
    ): RemoteListeners = require(draft.isValid()) { "Invalid remote listener draft" }.let {
        (existing ?: RemoteListeners()).apply {
            if(existing == null) date = nowMillis
            hostUrl = draft.host.trim()
            username = draft.username.trim()
            password = draft.password
            friendlyConnectionName = draft.displayName.trim()
            virtualHost = draft.virtualHost.trim()
            port = requireNotNull(draft.port.toIntOrNull())
            protocol = draft.protocol.name.lowercase()
        }
    }

    fun toQueueSummary(entity: RemoteListenersQueues): RemoteQueueSummary = RemoteQueueSummary(
        id = entity.id,
        listenerId = entity.gatewayClientId,
        exchange = entity.name.orEmpty(),
        sim1Binding = entity.binding1Name.orEmpty(),
        sim2Binding = entity.binding2Name.orEmpty(),
    )

    fun toQueueDraft(entity: RemoteListenersQueues): RemoteQueueDraft = RemoteQueueDraft(
        exchange = entity.name.orEmpty(),
        sim1Binding = entity.binding1Name.orEmpty(),
        sim2Binding = entity.binding2Name.orEmpty(),
    )

    fun toQueueEntity(
        id: Long,
        listenerId: Long,
        draft: RemoteQueueDraft,
    ): RemoteListenersQueues = require(draft.isValid()) { "Invalid remote queue draft" }.let {
        RemoteListenersQueues().apply {
            this.id = id
            gatewayClientId = listenerId
            name = draft.exchange.trim()
            binding1Name = draft.sim1Binding.trim()
            binding2Name = draft.sim2Binding.trim()
        }
    }

    private fun String?.toProtocol(): RemoteListenerProtocol =
        if(equals("amqps", ignoreCase = true)) RemoteListenerProtocol.AMQPS
        else RemoteListenerProtocol.AMQP
}
