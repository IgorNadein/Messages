package com.afkanerd.deku.messages.presentation

import androidx.paging.PagingData
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.AttachmentAction
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.AttachmentPrepareResult
import com.afkanerd.deku.messages.domain.PreparedAttachment
import com.afkanerd.deku.messages.domain.ConversationSecurityState
import com.afkanerd.deku.messages.domain.ConversationThread
import com.afkanerd.deku.messages.domain.ConversationFolder
import com.afkanerd.deku.messages.domain.ConversationThreadAction
import com.afkanerd.deku.messages.domain.ImportProgress
import com.afkanerd.deku.messages.domain.ImportResult
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.MessageRecipient
import com.afkanerd.deku.messages.domain.SecureSessionActionResult
import com.afkanerd.deku.messages.domain.SendResult
import com.afkanerd.deku.messages.domain.SimSubscription
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModelRegressionTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun draftIsRestoredAfterViewModelRecreation() = runTest(dispatcher) {
        val service = FakeMessageService(draft = "survives process restart")

        val viewModel = ConversationViewModel(service, ADDRESS, THREAD_ID)
        advanceUntilIdle()

        assertEquals("survives process restart", viewModel.state.value.draft)
    }

    @Test
    fun selectedSimIsUsedAndSuccessfulSendClearsPersistedDraft() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = ConversationViewModel(service, ADDRESS, THREAD_ID)
        advanceUntilIdle()

        viewModel.selectSubscription(SECOND_SIM)
        viewModel.updateDraft("multipart payload ".repeat(30))
        advanceUntilIdle()
        viewModel.send()
        advanceUntilIdle()

        assertEquals(SECOND_SIM, service.lastSentSubscriptionId)
        assertEquals(SECOND_SIM, service.selectedSubscriptionId)
        assertEquals("", service.savedDraft)
        assertEquals("", viewModel.state.value.draft)
    }

    @Test
    fun subsequentGroupMessageRemainsOneMmsInsteadOfCommaAddressedSms() =
        runTest(dispatcher) {
            val service = FakeMessageService()
            val groupAddress = "$ADDRESS,$SECOND_ADDRESS"
            val viewModel = ConversationViewModel(service, groupAddress, THREAD_ID)
            advanceUntilIdle()

            viewModel.updateDraft("next group message")
            viewModel.send()
            advanceUntilIdle()

            assertEquals(listOf(ADDRESS, SECOND_ADDRESS), service.lastMmsAddresses)
            assertEquals(0, service.textSendCalls)
            assertEquals("", viewModel.state.value.draft)
        }

    @Test
    fun blockedSecureSendKeepsDraftAndNeverFallsBack() = runTest(dispatcher) {
        val service = FakeMessageService(
            sendResult = SendResult.BlockedBySecurity("key changed"),
        )
        val viewModel = ConversationViewModel(service, ADDRESS, THREAD_ID)
        advanceUntilIdle()
        viewModel.updateDraft("do not leak me")
        advanceUntilIdle()

        viewModel.send()
        advanceUntilIdle()

        assertEquals("do not leak me", viewModel.state.value.draft)
        assertEquals(ConversationError.SECURITY_NOT_READY, viewModel.state.value.error)
        assertFalse(service.savedDraft.isNullOrBlank())
    }

    @Test
    fun decryptionFailureActionForcesKeyRenewal() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = ConversationViewModel(service, ADDRESS, THREAD_ID)
        advanceUntilIdle()

        viewModel.requestOrRepairSecureSession(forceRenewal = true)
        advanceUntilIdle()

        assertEquals(true, service.forcedRenewal)
    }

    @Test
    fun incomingSecureRequestUsesConversationSimWithoutForcedRenewal() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = ConversationViewModel(service, ADDRESS, THREAD_ID)
        advanceUntilIdle()

        viewModel.selectSubscription(SECOND_SIM)
        advanceUntilIdle()
        viewModel.requestOrRepairSecureSession(forceRenewal = false)
        advanceUntilIdle()

        assertEquals(false, service.forcedRenewal)
        assertEquals(SECOND_SIM, service.secureRequestSubscriptionId)
        assertEquals(THREAD_ID, service.secureRequestThreadId)
    }

    @Test
    fun keyChangeRequiresExplicitAcceptanceBeforeRepair() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = ConversationViewModel(service, ADDRESS, THREAD_ID)
        advanceUntilIdle()

        viewModel.acceptChangedIdentityAndRepair()
        advanceUntilIdle()

        assertEquals(true, service.changedIdentityAccepted)
    }

    @Test
    fun contactIdentityCanOnlyBeVerifiedThroughMatchingQrPayload() = runTest(dispatcher) {
        val service = FakeMessageService(identityVerificationSucceeds = true)
        val viewModel = ConversationViewModel(service, ADDRESS, THREAD_ID)
        advanceUntilIdle()

        viewModel.loadSecurityQrPayload()
        viewModel.verifyContactIdentity("deku-identity:matching-key")
        advanceUntilIdle()

        assertEquals("deku-identity:local-key", viewModel.state.value.securityQrPayload)
        assertEquals("deku-identity:matching-key", service.lastVerifiedQrPayload)
        assertEquals(
            IdentityVerificationResult.VERIFIED,
            viewModel.state.value.identityVerificationResult,
        )
    }

    @Test
    fun contactsAreExplainedAfterImportAndCanBeSkipped() = runTest(dispatcher) {
        val service = FakeMessageService(
            contactAccess = false,
            contactPromptCompleted = false,
        )
        val viewModel = InboxViewModel(service)
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.showContactsPrompt)
        viewModel.completeContactsPrompt()

        assertEquals(false, viewModel.state.value.showContactsPrompt)
        assertEquals(true, viewModel.state.value.showReady)
        assertEquals(true, service.contactPromptCompleted)
    }

    @Test
    fun readyStepSurvivesLifecycleResumeAfterContactsDialog() = runTest(dispatcher) {
        val service = FakeMessageService(
            contactAccess = false,
            contactPromptCompleted = false,
        )
        val viewModel = InboxViewModel(service)
        advanceUntilIdle()

        assertEquals(1, service.importCalls)
        viewModel.completeContactsPrompt()
        assertEquals(true, viewModel.state.value.showReady)

        // ActivityResult and lifecycle ON_RESUME can both arrive after the system dialog.
        viewModel.onResume()
        advanceUntilIdle()

        assertEquals(true, viewModel.state.value.showReady)
        assertEquals(1, service.importCalls)
    }

    @Test
    fun cachedMessageStoreNeverShowsPreparingPaneOrReimportsOnResume() =
        runTest(dispatcher) {
            val service = FakeMessageService(messageStoreReady = true)

            val viewModel = InboxViewModel(service)
            advanceUntilIdle()
            viewModel.onResume()
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isImporting)
            assertEquals(null, viewModel.state.value.importProgress)
            assertEquals(0, service.importCalls)
        }

    @Test
    fun inboxFoldersAndLegacyThreadActionsRemainAvailableAfterUiMigration() =
        runTest(dispatcher) {
            val service = FakeMessageService()
            val viewModel = InboxViewModel(service)
            backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
                viewModel.threads.collect {}
            }
            advanceUntilIdle()

            viewModel.selectFolder(ConversationFolder.ARCHIVED)
            advanceUntilIdle()
            assertEquals(ConversationFolder.ARCHIVED, service.lastConversationFolder)

            viewModel.onResume()
            advanceUntilIdle()
            assertEquals(ConversationFolder.ARCHIVED, viewModel.state.value.folder)

            viewModel.performThreadAction(THREAD_ID, ConversationThreadAction.UNARCHIVE)
            advanceUntilIdle()
            assertEquals(
                THREAD_ID to ConversationThreadAction.UNARCHIVE,
                service.lastThreadAction,
            )

            viewModel.markAllRead()
            advanceUntilIdle()
            assertEquals(true, service.markAllReadCalled)
            assertEquals(false, viewModel.state.value.threadActionFailed)

            viewModel.exportMessages("content://regression/messages.json")
            advanceUntilIdle()
            assertEquals("content://regression/messages.json", service.lastExportDestination)
            assertEquals(true, viewModel.state.value.exportResult)
        }

    @Test
    fun threadDeletionRequiresConfirmationAndDispatchesExactlyOnce() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = InboxViewModel(service)
        val thread = ConversationThread(
            id = THREAD_ID,
            address = ADDRESS,
            displayName = "Delete regression",
            avatarUri = null,
            snippet = "must remain until confirmed",
            timestampMillis = 1L,
            unreadCount = 0,
            isPinned = false,
            isMuted = false,
        )
        advanceUntilIdle()

        viewModel.performThreadAction(THREAD_ID, ConversationThreadAction.DELETE)
        advanceUntilIdle()
        assertEquals(emptyList<Pair<Int, ConversationThreadAction>>(), service.threadActions)

        viewModel.requestThreadDeletion(thread)
        assertEquals(THREAD_ID, viewModel.state.value.pendingThreadDeletion?.id)
        viewModel.cancelThreadDeletion()
        advanceUntilIdle()
        assertEquals(null, viewModel.state.value.pendingThreadDeletion)
        assertEquals(emptyList<Pair<Int, ConversationThreadAction>>(), service.threadActions)

        viewModel.requestThreadDeletion(thread)
        viewModel.confirmThreadDeletion()
        viewModel.confirmThreadDeletion()
        advanceUntilIdle()

        assertEquals(
            listOf(THREAD_ID to ConversationThreadAction.DELETE),
            service.threadActions,
        )
        assertEquals(null, viewModel.state.value.pendingThreadDeletion)
        assertEquals(false, viewModel.state.value.threadActionFailed)
    }

    @Test
    fun contactDetailsPreserveSimSecurityAndBlockActions() = runTest(dispatcher) {
        val service = FakeMessageService(contactBlocked = true)
        val viewModel = ContactDetailsViewModel(service, ADDRESS)
        advanceUntilIdle()

        assertEquals(FIRST_SIM, viewModel.state.value.header?.subscriptionId)
        assertEquals("00001 00002", viewModel.state.value.fingerprint)
        assertEquals(true, viewModel.state.value.isBlocked)

        viewModel.selectSubscription(SECOND_SIM)
        viewModel.toggleBlocked()
        viewModel.toggleMuted()
        advanceUntilIdle()

        assertEquals(SECOND_SIM, service.selectedSubscriptionId)
        assertEquals(false, service.contactBlocked)
        assertEquals(false, viewModel.state.value.isBlocked)
        assertEquals(THREAD_ID to ConversationThreadAction.MUTE, service.lastThreadAction)
        assertEquals(true, viewModel.state.value.header?.isMuted)
    }

    @Test
    fun searchIsDebouncedAndPreservesContactScope() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = MessagesSearchViewModel(service, ADDRESS)
        backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) {
            viewModel.results.collect {}
        }
        advanceUntilIdle()

        viewModel.updateQuery("s")
        advanceUntilIdle()
        assertEquals(null, service.lastSearchQuery)

        viewModel.updateQuery("secure")
        advanceUntilIdle()

        assertEquals("secure", service.lastSearchQuery)
        assertEquals(ADDRESS, service.lastSearchAddress)
    }

    @Test
    fun sharedTextBlockedBySecurePolicyBecomesDraftWithoutPlaintextFallback() =
        runTest(dispatcher) {
            val service = FakeMessageService(
                sendResult = SendResult.BlockedBySecurity("session unavailable"),
            )
            val viewModel = NewMessageViewModel(service, "private text", SECOND_SIM)
            advanceUntilIdle()

            viewModel.selectRecipient(MessageRecipient(1, ADDRESS, "Contact", null))
            assertEquals(null, viewModel.state.value.destination)
            assertEquals(null, service.lastSentSubscriptionId)

            viewModel.createConversation()
            advanceUntilIdle()

            assertEquals("private text", service.savedDraft)
            assertEquals(SECOND_SIM, service.lastSentSubscriptionId)
            assertEquals("private text", viewModel.state.value.destination?.preservedText)
        }

    @Test
    fun selectingRecipientNeverAutoSendsSharedText() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = NewMessageViewModel(service, "shared draft", null)
        advanceUntilIdle()

        viewModel.selectRecipient(MessageRecipient(1, ADDRESS, "Contact", null))
        advanceUntilIdle()

        assertEquals(null, service.lastSentSubscriptionId)
        assertEquals(null, service.savedDraft)
        assertEquals(null, viewModel.state.value.destination)
        assertEquals("shared draft", viewModel.state.value.draftText)
        assertEquals(ADDRESS, viewModel.state.value.selectedRecipient?.address)
    }

    @Test
    fun textEnteredBeforeChoosingRecipientIsPreservedAsDraft() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = NewMessageViewModel(service, null, null)
        advanceUntilIdle()

        viewModel.updateDraft("typed before recipient")
        viewModel.selectRecipient(MessageRecipient(1, ADDRESS, "Contact", null))
        assertEquals(null, viewModel.state.value.destination)
        assertEquals(null, service.lastSentSubscriptionId)

        viewModel.createConversation()
        advanceUntilIdle()

        assertEquals(FIRST_SIM, service.lastSentSubscriptionId)
        assertEquals(null, service.savedDraft)
        assertEquals(ADDRESS, viewModel.state.value.destination?.address)
        assertEquals(null, viewModel.state.value.destination?.preservedText)
    }

    @Test
    fun multipleRecipientsCreateOneGroupMmsThreadOnSelectedSim() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = NewMessageViewModel(service, null, SECOND_SIM)
        advanceUntilIdle()

        viewModel.selectRecipient(MessageRecipient(1, ADDRESS, "First", null))
        viewModel.selectRecipient(MessageRecipient(2, SECOND_ADDRESS, "Second", null))
        viewModel.updateDraft("group message")
        viewModel.createConversation()
        advanceUntilIdle()

        assertEquals(listOf(ADDRESS, SECOND_ADDRESS), service.lastMmsAddresses)
        assertEquals(SECOND_SIM, service.lastSentSubscriptionId)
        assertEquals(0, service.textSendCalls)
        assertEquals("$ADDRESS,$SECOND_ADDRESS", viewModel.state.value.destination?.address)
        assertEquals(2, viewModel.state.value.selectedRecipients.size)
    }

    @Test
    fun recipientPickerCanToggleAndCancelAWorkingSelection() = runTest(dispatcher) {
        val service = FakeMessageService()
        val viewModel = NewMessageViewModel(service, null, SECOND_SIM)
        advanceUntilIdle()
        val first = MessageRecipient(1, ADDRESS, "First", null)
        val second = MessageRecipient(2, SECOND_ADDRESS, "Second", null)

        viewModel.toggleRecipient(first)
        viewModel.toggleRecipient(second)
        assertEquals(listOf(ADDRESS, SECOND_ADDRESS), viewModel.state.value.selectedRecipients.map {
            it.address
        })

        viewModel.toggleRecipient(first)
        assertEquals(listOf(SECOND_ADDRESS), viewModel.state.value.selectedRecipients.map {
            it.address
        })

        viewModel.replaceRecipients(listOf(first))
        assertEquals(listOf(ADDRESS), viewModel.state.value.selectedRecipients.map { it.address })
    }

    @Test
    fun editingGroupStartsWithExactCurrentMembersAndDoesNotSendUntilConfirmed() =
        runTest(dispatcher) {
            val service = FakeMessageService()
            val viewModel = NewMessageViewModel(
                messageService = service,
                initialText = null,
                initialSubscriptionId = SECOND_SIM,
                initialAddresses = "$ADDRESS,$SECOND_ADDRESS,$ADDRESS",
            )
            advanceUntilIdle()

            assertEquals(
                listOf(ADDRESS, SECOND_ADDRESS),
                viewModel.state.value.selectedRecipients.map(MessageRecipient::address),
            )
            assertEquals(0, service.textSendCalls)
            assertEquals(null, service.lastMmsAddresses)

            viewModel.updateDraft("new membership thread")
            viewModel.createConversation()
            advanceUntilIdle()

            assertEquals(listOf(ADDRESS, SECOND_ADDRESS), service.lastMmsAddresses)
            assertEquals(SECOND_SIM, service.lastSentSubscriptionId)
        }

    private class FakeMessageService(
        var draft: String = "",
        var sendResult: SendResult = SendResult.Sent(1),
        private val contactAccess: Boolean = true,
        var contactPromptCompleted: Boolean = true,
        var contactBlocked: Boolean = false,
        private val identityVerificationSucceeds: Boolean = false,
        var messageStoreReady: Boolean = false,
    ) : MessageService {
        var savedDraft: String? = null
        var selectedSubscriptionId: Long? = null
        var lastSentSubscriptionId: Long? = null
        var lastMmsAddresses: List<String>? = null
        var textSendCalls = 0
        var forcedRenewal: Boolean? = null
        var secureRequestSubscriptionId: Long? = null
        var secureRequestThreadId: Int? = null
        var changedIdentityAccepted = false
        var lastSearchQuery: String? = null
        var lastSearchAddress: String? = null
        var lastConversationFolder: ConversationFolder? = null
        var lastThreadAction: Pair<Int, ConversationThreadAction>? = null
        val threadActions = mutableListOf<Pair<Int, ConversationThreadAction>>()
        var markAllReadCalled = false
        var lastVerifiedQrPayload: String? = null
        var lastExportDestination: String? = null
        var importCalls = 0

        override fun isDefaultSmsApp() = true
        override fun hasContactAccess() = contactAccess
        override fun isContactPromptCompleted() = contactPromptCompleted
        override fun completeContactPrompt() {
            contactPromptCompleted = true
        }
        override fun isMessageStoreReady() = messageStoreReady
        override fun conversationThreads(): Flow<PagingData<ConversationThread>> =
            flowOf(PagingData.empty())
        override fun conversationThreads(
            folder: ConversationFolder,
        ): Flow<PagingData<ConversationThread>> {
            lastConversationFolder = folder
            return flowOf(PagingData.empty())
        }
        override suspend fun updateConversationThread(
            threadId: Int,
            action: ConversationThreadAction,
        ): Boolean {
            lastThreadAction = threadId to action
            threadActions += threadId to action
            return true
        }
        override suspend fun markAllConversationsRead(): Boolean {
            markAllReadCalled = true
            return true
        }
        override suspend fun exportMessages(destinationUri: String): Boolean {
            lastExportDestination = destinationUri
            return true
        }
        override fun searchThreads(
            query: String,
            address: String?,
        ): Flow<PagingData<ConversationThread>> {
            lastSearchQuery = query
            lastSearchAddress = address
            return flowOf(PagingData.empty())
        }
        override suspend fun searchRecipients(query: String): List<MessageRecipient> =
            listOf(MessageRecipient(1, ADDRESS, "Contact", null))
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
        ) = AttachmentPrepareResult.Queued
        override suspend fun importIfNeeded(onProgress: suspend (ImportProgress) -> Unit):
            ImportResult {
            importCalls++
            messageStoreReady = true
            return ImportResult.AlreadyReady
        }
        override suspend fun conversationHeader(address: String, threadId: Int?) =
            ConversationHeader(
                threadId = threadId ?: THREAD_ID,
                address = address,
                displayName = address,
                avatarUri = null,
                subscriptionId = FIRST_SIM,
                subscriptions = listOf(
                    SimSubscription(FIRST_SIM, "SIM 1", 0),
                    SimSubscription(SECOND_SIM, "SIM 2", 1),
                ),
                securityState = ConversationSecurityState.PLAIN,
            )
        override suspend fun conversationHeader(
            addresses: List<String>,
            threadId: Int?,
        ) = conversationHeader(addresses.joinToString(","), threadId)
        override suspend fun loadDraft(threadId: Int) = draft
        override suspend fun saveDraft(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ) {
            savedDraft = text
            draft = text
        }
        override suspend fun selectSubscription(address: String, subscriptionId: Long) {
            selectedSubscriptionId = subscriptionId
        }
        override suspend fun isContactBlocked(address: String) = contactBlocked
        override suspend fun setContactBlocked(address: String, blocked: Boolean): Boolean {
            contactBlocked = blocked
            return true
        }
        override suspend fun sendText(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ): SendResult {
            textSendCalls++
            lastSentSubscriptionId = subscriptionId
            return sendResult
        }
        override suspend fun sendMms(
            addresses: List<String>,
            threadId: Int,
            subscriptionId: Long,
            text: String,
        ): SendResult {
            lastMmsAddresses = addresses
            lastSentSubscriptionId = subscriptionId
            return sendResult
        }
        override suspend fun requestOrRepairSecureSession(
            address: String,
            threadId: Int,
            subscriptionId: Long,
            forceRenewal: Boolean,
        ): SecureSessionActionResult {
            forcedRenewal = forceRenewal
            secureRequestSubscriptionId = subscriptionId
            secureRequestThreadId = threadId
            return SecureSessionActionResult.RequestSent
        }
        override suspend fun securityFingerprint(address: String) = "00001 00002"
        override suspend fun localSecurityQrPayload() = "deku-identity:local-key"
        override suspend fun verifyContactIdentity(address: String, qrPayload: String): Boolean {
            lastVerifiedQrPayload = qrPayload
            return identityVerificationSucceeds
        }
        override suspend fun acceptChangedIdentityAndRepair(
            address: String,
            threadId: Int,
            subscriptionId: Long,
        ): SecureSessionActionResult {
            changedIdentityAccepted = true
            return SecureSessionActionResult.RequestSent
        }
    }

    private companion object {
        const val ADDRESS = "+79990000000"
        const val SECOND_ADDRESS = "+79990000001"
        const val THREAD_ID = 77
        const val FIRST_SIM = 10L
        const val SECOND_SIM = 20L
    }
}
