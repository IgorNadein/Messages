package com.afkanerd.deku.messages.presentation

import com.afkanerd.deku.messages.domain.DeveloperToolsService
import com.afkanerd.deku.messages.domain.NativeMessageImportSummary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
class DeveloperToolsViewModelRegressionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun clearHistoryRequiresExplicitConfirmation() = runTest(dispatcher) {
        val service = FakeDeveloperToolsService()
        val viewModel = DeveloperToolsViewModel(service)

        viewModel.requestClearLocalHistory()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.showClearConfirmation)
        assertEquals(0, service.clearCalls)

        viewModel.dismissClearLocalHistory()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.showClearConfirmation)
        assertEquals(0, service.clearCalls)

        viewModel.requestClearLocalHistory()
        viewModel.confirmClearLocalHistory()
        advanceUntilIdle()

        assertEquals(1, service.clearCalls)
        assertEquals(
            DeveloperToolsResult.LOCAL_HISTORY_CLEARED,
            viewModel.state.value.result,
        )
    }

    @Test
    fun sampleNotificationPreservesLegacyCommand() = runTest(dispatcher) {
        val service = FakeDeveloperToolsService()
        val viewModel = DeveloperToolsViewModel(service)

        viewModel.triggerSampleMmsNotification()
        advanceUntilIdle()

        assertEquals(1, service.notificationCalls)
        assertEquals(
            DeveloperToolsResult.SAMPLE_NOTIFICATION_CREATED,
            viewModel.state.value.result,
        )
    }

    @Test
    fun nativeDatabaseExportAndImportPreserveDocumentUrisAndImportCounts() = runTest(dispatcher) {
        val service = FakeDeveloperToolsService()
        val viewModel = DeveloperToolsViewModel(service)

        viewModel.exportNativeMessageDatabase("content://documents/export.json")
        advanceUntilIdle()

        assertEquals("content://documents/export.json", service.exportUri)
        assertEquals(DeveloperToolsResult.NATIVE_DATABASE_EXPORTED, viewModel.state.value.result)

        viewModel.clearResult()
        viewModel.importNativeMessageDatabase("content://documents/import.json")
        advanceUntilIdle()

        assertEquals("content://documents/import.json", service.importUri)
        assertEquals(DeveloperToolsResult.NATIVE_DATABASE_IMPORTED, viewModel.state.value.result)
        assertEquals(NativeMessageImportSummary(2, 3, 4), viewModel.state.value.importSummary)
    }

    @Test
    fun clearingNativeDatabaseRequiresItsOwnExplicitConfirmation() = runTest(dispatcher) {
        val service = FakeDeveloperToolsService()
        val viewModel = DeveloperToolsViewModel(service)

        viewModel.requestClearNativeMessageDatabase()
        assertTrue(viewModel.state.value.showClearNativeConfirmation)
        assertEquals(0, service.clearNativeCalls)

        viewModel.dismissClearNativeMessageDatabase()
        assertFalse(viewModel.state.value.showClearNativeConfirmation)
        assertEquals(0, service.clearNativeCalls)

        viewModel.requestClearNativeMessageDatabase()
        viewModel.confirmClearNativeMessageDatabase()
        advanceUntilIdle()

        assertEquals(1, service.clearNativeCalls)
        assertEquals(DeveloperToolsResult.NATIVE_DATABASE_CLEARED, viewModel.state.value.result)
    }

    private class FakeDeveloperToolsService : DeveloperToolsService {
        var notificationCalls = 0
        var clearCalls = 0
        var clearNativeCalls = 0
        var exportUri: String? = null
        var importUri: String? = null

        override suspend fun triggerSampleMmsNotification(): Boolean {
            notificationCalls++
            return true
        }

        override suspend fun clearLocalMessageHistory(): Boolean {
            clearCalls++
            return true
        }

        override suspend fun exportNativeMessageDatabase(destinationUri: String): Boolean {
            exportUri = destinationUri
            return true
        }

        override suspend fun importNativeMessageDatabase(
            sourceUri: String,
        ): NativeMessageImportSummary {
            importUri = sourceUri
            return NativeMessageImportSummary(2, 3, 4)
        }

        override suspend fun clearNativeMessageDatabase(): Boolean {
            clearNativeCalls++
            return true
        }
    }
}
