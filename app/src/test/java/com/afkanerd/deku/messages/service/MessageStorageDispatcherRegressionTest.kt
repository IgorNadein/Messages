package com.afkanerd.deku.messages.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MessageStorageDispatcherRegressionTest {
    @Test
    fun pagingEnrichmentNeverRunsBlockingStorageReadOnCollectorThread() = runTest {
        val collectorThread = Thread.currentThread()
        val storageThread = MessageStorageDispatcher.read { Thread.currentThread() }

        assertNotEquals(collectorThread, storageThread)
    }
}
