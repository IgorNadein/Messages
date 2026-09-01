package com.afkanerd.deku.messages.presentation

import androidx.paging.PagingData
import com.afkanerd.deku.messages.domain.AttachmentAction
import com.afkanerd.deku.messages.domain.AttachmentPrepareResult
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.ConversationThread
import com.afkanerd.deku.messages.domain.GatewayDraft
import com.afkanerd.deku.messages.domain.GatewayProtocol
import com.afkanerd.deku.messages.domain.GatewaySummary
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
import kotlinx.coroutines.flow.MutableStateFlow
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GatewayViewModelRegressionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun smtpFieldsAreEditableAndSaveUsesTheirCurrentValues() = runTest(dispatcher) {
        val service = FakeGatewayMessageService()
        val viewModel = GatewayViewModel(service)
        viewModel.create(GatewayProtocol.SMTP)

        val edited = requireNotNull(viewModel.state.value.editor).draft.copy(
            smtpHost = "smtp.example.org",
            smtpUsername = "user",
            smtpPassword = "secret",
            smtpRecipient = "to@example.org",
            smtpFrom = "from@example.org",
            smtpSubject = "subject",
            smtpPort = "465",
        )
        viewModel.updateDraft(edited)
        assertEquals(edited, viewModel.state.value.editor?.draft)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(edited, service.savedDraft)
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun invalidSmtpNeverReachesPersistence() = runTest(dispatcher) {
        val service = FakeGatewayMessageService()
        val viewModel = GatewayViewModel(service)
        viewModel.create(GatewayProtocol.SMTP)

        viewModel.save()
        advanceUntilIdle()

        assertNull(service.savedDraft)
        assertTrue(viewModel.state.value.editor?.validationFailed == true)
        assertEquals("587", viewModel.state.value.editor?.draft?.smtpPort)
    }

    @Test
    fun editingStoredSmtpKeepsTheSmtpEditorType() = runTest(dispatcher) {
        val service = FakeGatewayMessageService(
            loadedDraft = validSmtpDraft(),
        )
        val viewModel = GatewayViewModel(service)

        viewModel.edit(12)
        advanceUntilIdle()

        assertEquals(GatewayProtocol.SMTP, viewModel.state.value.editor?.draft?.protocol)
        assertEquals(12L, viewModel.state.value.editor?.id)
    }

    @Test
    fun deletionRequiresConfirmationAndDispatchesOnce() = runTest(dispatcher) {
        val service = FakeGatewayMessageService(loadedDraft = validSmtpDraft())
        val viewModel = GatewayViewModel(service)
        viewModel.edit(22)
        advanceUntilIdle()

        viewModel.requestDelete()
        assertEquals(22L, viewModel.state.value.deleteCandidateId)
        assertEquals(0, service.deleteCalls)

        viewModel.dismissDelete()
        assertEquals(0, service.deleteCalls)

        viewModel.requestDelete()
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertEquals(1, service.deleteCalls)
        assertEquals(22L, service.deletedId)
        assertNull(viewModel.state.value.editor)
        assertFalse(viewModel.state.value.isBusy)
    }

    private fun validSmtpDraft() = GatewayDraft(
        protocol = GatewayProtocol.SMTP,
        smtpHost = "smtp.example.org",
        smtpUsername = "user",
        smtpPassword = "secret",
        smtpRecipient = "to@example.org",
        smtpFrom = "from@example.org",
    )

    private class FakeGatewayMessageService(
        private val loadedDraft: GatewayDraft? = null,
    ) : MessageService {
        private val gateways = MutableStateFlow<List<GatewaySummary>>(emptyList())
        var savedDraft: GatewayDraft? = null
        var deletedId: Long? = null
        var deleteCalls = 0

        override fun gatewayConfigurations(): Flow<List<GatewaySummary>> = gateways
        override suspend fun loadGatewayDraft(id: Long): GatewayDraft? = loadedDraft
        override suspend fun saveGateway(id: Long?, draft: GatewayDraft): Boolean {
            savedDraft = draft
            return true
        }
        override suspend fun deleteGateway(id: Long): Boolean {
            deleteCalls++
            deletedId = id
            return true
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
        override suspend fun conversationHeader(address: String, threadId: Int?) =
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
