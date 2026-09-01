package com.afkanerd.deku.messages.presentation

import androidx.paging.PagingData
import com.afkanerd.deku.messages.domain.AttachmentAction
import com.afkanerd.deku.messages.domain.AttachmentPrepareResult
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.ConversationThread
import com.afkanerd.deku.messages.domain.ImportProgress
import com.afkanerd.deku.messages.domain.ImportResult
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.PreparedAttachment
import com.afkanerd.deku.messages.domain.SecureSessionActionResult
import com.afkanerd.deku.messages.domain.SendResult
import com.afkanerd.deku.messages.domain.TimelineItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MediaViewerViewModelRegressionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun cancelledDocumentPickerDoesNotStartAWrite() = runTest(dispatcher) {
        val service = FakeMediaMessageService()
        val viewModel = MediaViewerViewModel(service, "content://source")

        viewModel.save(null)
        advanceUntilIdle()

        assertNull(service.destination)
        assertEquals(MediaViewerUiState(), viewModel.state.value)
    }

    @Test
    fun saveDelegatesToServiceAndReportsCompletion() = runTest(dispatcher) {
        val service = FakeMediaMessageService(succeeds = true)
        val viewModel = MediaViewerViewModel(service, "content://source")

        viewModel.save("content://destination")
        advanceUntilIdle()

        assertEquals("content://source", service.source)
        assertEquals("content://destination", service.destination)
        assertEquals(MediaViewerNotice.SAVED, viewModel.state.value.notice)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun failedCopyIsReportedWithoutLeavingBusyState() = runTest(dispatcher) {
        val viewModel = MediaViewerViewModel(
            FakeMediaMessageService(succeeds = false),
            "content://source",
        )

        viewModel.save("content://destination")
        advanceUntilIdle()

        assertEquals(MediaViewerNotice.FAILED, viewModel.state.value.notice)
        assertFalse(viewModel.state.value.isSaving)
    }

    private class FakeMediaMessageService(
        private val succeeds: Boolean = true,
    ) : MessageService {
        var source: String? = null
        var destination: String? = null

        override suspend fun saveMedia(sourceUri: String, destinationUri: String): Boolean {
            source = sourceUri
            destination = destinationUri
            return succeeds
        }

        override fun isDefaultSmsApp() = true
        override fun hasContactAccess() = true
        override fun isContactPromptCompleted() = true
        override fun completeContactPrompt() = Unit
        override fun conversationThreads(): Flow<PagingData<ConversationThread>> =
            flowOf(PagingData.empty())
        override fun timeline(threadId: Int): Flow<PagingData<TimelineItem>> =
            flowOf(PagingData.empty())
        override fun attachmentTransfers(address: String): Flow<List<AttachmentTransfer>> =
            flowOf(emptyList())
        override suspend fun performAttachmentAction(
            transferId: String,
            action: AttachmentAction,
        ) = Unit
        override suspend fun prepareAttachment(
            address: String,
            subscriptionId: Long,
            attachment: PreparedAttachment,
        ): AttachmentPrepareResult = AttachmentPrepareResult.Queued
        override suspend fun importIfNeeded(
            onProgress: suspend (ImportProgress) -> Unit,
        ): ImportResult = ImportResult.AlreadyReady
        override suspend fun conversationHeader(address: String, threadId: Int?): ConversationHeader =
            error("Not used")
        override suspend fun loadDraft(threadId: Int) = ""
        override suspend fun saveDraft(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ) = Unit
        override suspend fun selectSubscription(address: String, subscriptionId: Long) = Unit
        override suspend fun sendText(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ): SendResult = SendResult.Failed("Not used")
        override suspend fun requestOrRepairSecureSession(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            forceRenewal: Boolean,
        ): SecureSessionActionResult = SecureSessionActionResult.Failed("Not used")
        override suspend fun securityFingerprint(address: String): String? = null
        override suspend fun acceptChangedIdentityAndRepair(
            address: String,
            threadId: Int,
            subscriptionId: Long,
        ): SecureSessionActionResult = SecureSessionActionResult.Failed("Not used")
    }
}
