package com.afkanerd.deku.messages.presentation

import com.afkanerd.deku.messages.TestMessageService
import com.afkanerd.deku.messages.domain.RemoteListenerDraft
import com.afkanerd.deku.messages.domain.RemoteListenerProtocol
import com.afkanerd.deku.messages.domain.RemoteListenerSummary
import com.afkanerd.deku.messages.domain.RemoteListenerToggleResult
import com.afkanerd.deku.messages.domain.RemoteQueueDraft
import com.afkanerd.deku.messages.domain.RemoteQueueSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteListenersViewModelRegressionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun nonNumericPortIsEditableAndRejectedWithoutCrashingOrPersisting() =
        runTest(dispatcher) {
            val service = FakeRemoteListenerService()
            val viewModel = RemoteListenersViewModel(service)
            viewModel.create()
            val editor = requireNotNull(viewModel.state.value.editor)

            viewModel.updateDraft(editor.draft.copy(
                host = "relay.example.org",
                username = "user",
                password = "secret",
                port = "",
            ))
            viewModel.save()
            advanceUntilIdle()

            assertNull(service.savedListener)
            assertTrue(viewModel.state.value.editor?.validationFailed == true)
        }

    @Test
    fun editUsesStoredProtocolAndCurrentFieldValues() = runTest(dispatcher) {
        val service = FakeRemoteListenerService(
            loadedListener = validDraft().copy(protocol = RemoteListenerProtocol.AMQPS),
        )
        val viewModel = RemoteListenersViewModel(service)

        viewModel.edit(4)
        advanceUntilIdle()
        val edited = requireNotNull(viewModel.state.value.editor).draft.copy(port = "5671")
        viewModel.updateDraft(edited)
        viewModel.save()
        advanceUntilIdle()

        assertEquals(RemoteListenerProtocol.AMQPS, service.savedListener?.protocol)
        assertEquals("5671", service.savedListener?.port)
    }

    @Test
    fun listenerDeletionRequiresExplicitConfirmation() = runTest(dispatcher) {
        val service = FakeRemoteListenerService()
        val viewModel = RemoteListenersViewModel(service)
        advanceUntilIdle()

        viewModel.requestDelete(4)
        assertEquals(0, service.listenerDeleteCalls)
        viewModel.dismissDelete()
        assertEquals(0, service.listenerDeleteCalls)
        viewModel.requestDelete(4)
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(1, service.listenerDeleteCalls)
        assertEquals(4L, service.deletedListenerId)
    }

    @Test
    fun permissionResultRetriesOnlyThePendingActivation() = runTest(dispatcher) {
        val service = FakeRemoteListenerService().apply {
            toggleResults += RemoteListenerToggleResult.PERMISSION_REQUIRED
            toggleResults += RemoteListenerToggleResult.ACTIVATED
        }
        val viewModel = RemoteListenersViewModel(service)
        advanceUntilIdle()

        viewModel.toggle(4)
        advanceUntilIdle()
        assertEquals(4L, viewModel.state.value.pendingToggleId)
        assertEquals(1, service.toggleCalls)

        viewModel.onPermissionsResult(true)
        advanceUntilIdle()

        assertEquals(2, service.toggleCalls)
        assertNull(viewModel.state.value.pendingToggleId)
        assertEquals(RemoteListenerNotice.ACTIVATED, viewModel.state.value.notice)
    }

    @Test
    fun queueSaveAndDeleteRemainScopedToSelectedListener() = runTest(dispatcher) {
        val service = FakeRemoteListenerService()
        val viewModel = RemoteListenersViewModel(service)
        advanceUntilIdle()
        viewModel.openQueues(4)
        advanceUntilIdle()
        viewModel.createQueue()
        viewModel.updateQueueDraft(RemoteQueueDraft("sms", "sms.ru.250", "sms.ru.251"))
        viewModel.saveQueue()
        advanceUntilIdle()

        assertEquals(4L, service.savedQueueListenerId)
        assertEquals("sms.ru.251", service.savedQueue?.sim2Binding)

        viewModel.editQueue(8)
        advanceUntilIdle()
        viewModel.requestQueueDelete()
        assertEquals(0, service.queueDeleteCalls)
        viewModel.confirmQueueDelete()
        advanceUntilIdle()

        assertEquals(1, service.queueDeleteCalls)
        assertEquals(4L, service.deletedQueueListenerId)
        assertEquals(8L, service.deletedQueueId)
        assertFalse(viewModel.state.value.isBusy)
    }

    @Test
    fun exchangeSuggestionsPopulateBothSimBindingsWithoutSwappingThem() =
        runTest(dispatcher) {
            val service = FakeRemoteListenerService()
            val viewModel = RemoteListenersViewModel(service)
            advanceUntilIdle()
            viewModel.openQueues(4)
            viewModel.createQueue()

            viewModel.updateQueueExchange("sms")
            advanceUntilIdle()

            val draft = requireNotNull(viewModel.state.value.queueEditor).draft
            assertEquals("sms.ru.250", draft.sim1Binding)
            assertEquals("sms.ru.251", draft.sim2Binding)
        }

    private fun validDraft() = RemoteListenerDraft(
        host = "relay.example.org",
        username = "user",
        password = "secret",
        virtualHost = "/",
    )

    private class FakeRemoteListenerService(
        private val loadedListener: RemoteListenerDraft? = null,
    ) : TestMessageService() {
        private val listeners = MutableStateFlow(listOf(summary()))
        private val queues = MutableStateFlow(listOf(queue()))
        val toggleResults = ArrayDeque<RemoteListenerToggleResult>()
        var savedListener: RemoteListenerDraft? = null
        var listenerDeleteCalls = 0
        var deletedListenerId: Long? = null
        var toggleCalls = 0
        var savedQueueListenerId: Long? = null
        var savedQueue: RemoteQueueDraft? = null
        var queueDeleteCalls = 0
        var deletedQueueListenerId: Long? = null
        var deletedQueueId: Long? = null

        override fun remoteListeners(): Flow<List<RemoteListenerSummary>> = listeners
        override suspend fun loadRemoteListenerDraft(id: Long): RemoteListenerDraft? =
            loadedListener
        override suspend fun saveRemoteListener(
            id: Long?,
            draft: RemoteListenerDraft,
        ): Boolean {
            savedListener = draft
            return true
        }
        override suspend fun deleteRemoteListener(id: Long): Boolean {
            listenerDeleteCalls++
            deletedListenerId = id
            return true
        }
        override suspend fun toggleRemoteListener(id: Long): RemoteListenerToggleResult {
            toggleCalls++
            return if(toggleResults.isEmpty()) RemoteListenerToggleResult.FAILED
                else toggleResults.removeFirst()
        }
        override fun remoteQueues(listenerId: Long): Flow<List<RemoteQueueSummary>> = queues
        override suspend fun loadRemoteQueueDraft(id: Long): RemoteQueueDraft? =
            RemoteQueueDraft("sms", "sms.ru.250", "sms.ru.251")
        override suspend fun suggestRemoteBindings(exchange: String): List<String> =
            listOf("$exchange.ru.250", "$exchange.ru.251")
        override suspend fun saveRemoteQueue(
            listenerId: Long,
            id: Long?,
            draft: RemoteQueueDraft,
        ): Boolean {
            savedQueueListenerId = listenerId
            savedQueue = draft
            return true
        }
        override suspend fun deleteRemoteQueue(listenerId: Long, id: Long): Boolean {
            queueDeleteCalls++
            deletedQueueListenerId = listenerId
            deletedQueueId = id
            return true
        }

        companion object {
            private fun summary() = RemoteListenerSummary(
                id = 4,
                displayName = "Office",
                host = "relay.example.org",
                username = "user",
                port = 5672,
                virtualHost = "/",
                protocol = RemoteListenerProtocol.AMQP,
                activated = false,
                connected = false,
            )

            private fun queue() = RemoteQueueSummary(
                id = 8,
                listenerId = 4,
                exchange = "sms",
                sim1Binding = "sms.ru.250",
                sim2Binding = "sms.ru.251",
            )
        }
    }
}
