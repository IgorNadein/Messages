package com.afkanerd.deku.messages.service

import com.afkanerd.deku.RemoteListeners.Models.RemoteListeners
import com.afkanerd.deku.RemoteListeners.Models.RemoteListenersQueues
import com.afkanerd.deku.messages.domain.RemoteListenerDraft
import com.afkanerd.deku.messages.domain.RemoteListenerProtocol
import com.afkanerd.deku.messages.domain.RemoteQueueDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteListenerConfigurationMapperTest {
    @Test
    fun editingPreservesRuntimeAndAdvancedPersistentState() {
        val existing = RemoteListeners().apply {
            id = 7
            date = 11
            activated = true
            state = RemoteListeners.STATE_CONNECTED
            connectionTimeout = 44
            prefetch_count = 3
            heartbeat = 55
        }
        val draft = validDraft().copy(
            displayName = "Office relay",
            protocol = RemoteListenerProtocol.AMQPS,
            port = "5671",
        )

        val mapped = RemoteListenerConfigurationMapper.toEntity(existing, draft, 999)

        assertEquals(7L, mapped.id)
        assertEquals(11L, mapped.date)
        assertTrue(mapped.activated)
        assertEquals(RemoteListeners.STATE_CONNECTED, mapped.state)
        assertEquals(44, mapped.connectionTimeout)
        assertEquals(3, mapped.prefetch_count)
        assertEquals(55, mapped.heartbeat)
        assertEquals("amqps", mapped.protocol)
        assertEquals(5671, mapped.port)
    }

    @Test
    fun invalidStoredPortGetsAnEditableSafeDefault() {
        val entity = RemoteListeners().apply {
            hostUrl = "relay.example.org"
            username = "user"
            password = "secret"
            virtualHost = "/"
            port = 0
        }

        val draft = RemoteListenerConfigurationMapper.toDraft(entity)

        assertEquals("5672", draft.port)
    }

    @Test
    fun invalidPortsNeverReachEntityMapping() {
        assertFalse(validDraft().copy(port = "").isValid())
        assertFalse(validDraft().copy(port = "not-a-number").isValid())
        assertFalse(validDraft().copy(port = "65536").isValid())
        assertTrue(validDraft().copy(port = "65535").isValid())
    }

    @Test
    fun queueRoundTripKeepsBothSimBindings() {
        val entity = RemoteListenerConfigurationMapper.toQueueEntity(
            id = 9,
            listenerId = 7,
            draft = RemoteQueueDraft("sms", "sms.ru.250", "sms.ru.251"),
        )

        val summary = RemoteListenerConfigurationMapper.toQueueSummary(entity)

        assertEquals(9L, summary.id)
        assertEquals(7L, summary.listenerId)
        assertEquals("sms.ru.250", summary.sim1Binding)
        assertEquals("sms.ru.251", summary.sim2Binding)
    }

    private fun validDraft() = RemoteListenerDraft(
        host = "relay.example.org",
        username = "user",
        password = "secret",
        virtualHost = "/",
    )
}
