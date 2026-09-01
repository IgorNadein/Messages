package com.afkanerd.deku.messages.presentation

import com.afkanerd.deku.messages.TestMessageService
import com.afkanerd.deku.messages.domain.RoutingHistoryItem
import com.afkanerd.deku.messages.domain.RoutingHistoryState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RoutingHistoryViewModelRegressionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun workManagerHistoryIsSubscribedExactlyOnceForTheScreenLifetime() = runTest(dispatcher) {
        val service = FakeRoutingHistoryService()
        val viewModel = RoutingHistoryViewModel(service)
        advanceUntilIdle()

        repeat(5) { viewModel.state.value }

        assertEquals(1, service.subscriptionCount)
        assertFalse(viewModel.state.value.isLoading)
        assertEquals("work-1", viewModel.state.value.items.single().workId)
    }

    @Test
    fun sourceFailureBecomesAnEmptyRecoverableStateInsteadOfCrashing() = runTest(dispatcher) {
        val viewModel = RoutingHistoryViewModel(object : TestMessageService() {
            override fun routingHistory(): Flow<List<RoutingHistoryItem>> = flow {
                error("WorkManager unavailable")
            }
        })
        advanceUntilIdle()

        assertFalse(viewModel.state.value.isLoading)
        assertTrue(viewModel.state.value.failed)
        assertTrue(viewModel.state.value.items.isEmpty())
    }

    private class FakeRoutingHistoryService : TestMessageService() {
        var subscriptionCount = 0

        override fun routingHistory(): Flow<List<RoutingHistoryItem>> = flow {
            subscriptionCount++
            emit(listOf(item()))
            awaitCancellation()
        }

        private fun item() = RoutingHistoryItem(
            workId = "work-1",
            messageId = 42,
            gatewayId = 8,
            address = "+15551234567",
            displayName = "Alice",
            body = "Forward me",
            timestampMillis = 1_700_000_000_000,
            state = RoutingHistoryState.RUNNING,
        )
    }
}
