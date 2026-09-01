package com.afkanerd.deku.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.afkanerd.deku.messages.domain.ConversationHeader
import com.afkanerd.deku.messages.domain.ConversationGroup
import com.afkanerd.deku.messages.domain.AppSettingsService
import com.afkanerd.deku.messages.domain.AppSettingsSnapshot
import com.afkanerd.deku.messages.domain.BooleanSetting
import com.afkanerd.deku.messages.domain.AttachmentAction
import com.afkanerd.deku.messages.domain.AttachmentTransfer
import com.afkanerd.deku.messages.domain.AttachmentPrepareResult
import com.afkanerd.deku.messages.domain.PreparedAttachment
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
import com.afkanerd.deku.messages.domain.ThemeMode
import com.afkanerd.deku.messages.service.GroupMessagePolicy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

data class InboxUiState(
    val roleRequired: Boolean = true,
    val isImporting: Boolean = false,
    val importProgress: ImportProgress? = null,
    val showContactsPrompt: Boolean = false,
    val showReady: Boolean = false,
    val error: InboxError? = null,
    val folder: ConversationFolder = ConversationFolder.INBOX,
    val selectedGroupId: String? = null,
    val groupActionInProgress: Boolean = false,
    val groupActionFailed: Boolean = false,
    val threadActionInProgress: Boolean = false,
    val threadActionFailed: Boolean = false,
    val pendingThreadDeletions: List<ConversationThread> = emptyList(),
    val exportResult: Boolean? = null,
) {
    val pendingThreadDeletion: ConversationThread?
        get() = pendingThreadDeletions.singleOrNull()
}

enum class InboxError {
    IMPORT_FAILED,
}

@OptIn(ExperimentalCoroutinesApi::class)
class InboxViewModel(
    private val messageService: MessageService,
) : ViewModel() {
    private val _state = MutableStateFlow(
        InboxUiState(roleRequired = !messageService.isDefaultSmsApp())
    )
    val state: StateFlow<InboxUiState> = _state.asStateFlow()

    val threads: Flow<PagingData<ConversationThread>> = state
        .map { it.folder to it.selectedGroupId }
        .distinctUntilChanged()
        .flatMapLatest { (folder, groupId) ->
            messageService.conversationThreads(folder, groupId)
        }
        .cachedIn(viewModelScope)

    val allInboxThreads: Flow<PagingData<ConversationThread>> =
        messageService.conversationThreads(ConversationFolder.INBOX).cachedIn(viewModelScope)

    val unreadMessageCount: Flow<Int> = messageService.unreadMessageCount()
    val conversationGroups: Flow<List<ConversationGroup>> = messageService.conversationGroups()

    init {
        if(messageService.isDefaultSmsApp() && !messageService.isMessageStoreReady()) {
            importMessages()
        }
    }

    fun onResume() {
        val roleRequired = !messageService.isDefaultSmsApp()
        _state.value = _state.value.copy(
            roleRequired = roleRequired,
            showReady = _state.value.showReady && !roleRequired,
        )
        if(!roleRequired &&
            !messageService.isMessageStoreReady() &&
            !_state.value.showContactsPrompt &&
            !_state.value.showReady
        ) {
            importMessages()
        }
    }

    fun retryImport() = importMessages()

    fun completeContactsPrompt() {
        messageService.completeContactPrompt()
        _state.value = _state.value.copy(
            showContactsPrompt = false,
            showReady = true,
        )
    }

    fun finishOnboarding() {
        _state.value = _state.value.copy(showReady = false)
    }

    fun selectFolder(folder: ConversationFolder) {
        _state.value = _state.value.copy(
            folder = folder,
            selectedGroupId = null,
            threadActionFailed = false,
        )
    }

    fun selectGroup(groupId: String?) {
        _state.value = _state.value.copy(
            folder = ConversationFolder.INBOX,
            selectedGroupId = groupId,
            groupActionFailed = false,
        )
    }

    fun createConversationGroup(name: String, threadIds: Set<Int>) {
        if(_state.value.groupActionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                groupActionInProgress = true,
                groupActionFailed = false,
            )
            val id = messageService.createConversationGroup(name, threadIds)
            _state.value = _state.value.copy(
                selectedGroupId = id ?: _state.value.selectedGroupId,
                groupActionInProgress = false,
                groupActionFailed = id == null,
            )
        }
    }

    fun deleteConversationGroup(id: String) {
        if(_state.value.groupActionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                groupActionInProgress = true,
                groupActionFailed = false,
            )
            val succeeded = messageService.deleteConversationGroup(id)
            _state.value = _state.value.copy(
                selectedGroupId = if(succeeded && _state.value.selectedGroupId == id) null
                    else _state.value.selectedGroupId,
                groupActionInProgress = false,
                groupActionFailed = !succeeded,
            )
        }
    }

    fun updateConversationGroup(id: String, name: String, threadIds: Set<Int>) {
        if(_state.value.groupActionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                groupActionInProgress = true,
                groupActionFailed = false,
            )
            val succeeded = messageService.updateConversationGroup(id, name, threadIds)
            _state.value = _state.value.copy(
                groupActionInProgress = false,
                groupActionFailed = !succeeded,
            )
        }
    }

    fun performThreadAction(threadId: Int, action: ConversationThreadAction) {
        performThreadActions(setOf(threadId), action)
    }

    fun performThreadActions(threadIds: Set<Int>, action: ConversationThreadAction) {
        if(action == ConversationThreadAction.DELETE || threadIds.isEmpty()) return
        dispatchThreadActions(threadIds, action)
    }

    fun requestThreadDeletion(thread: ConversationThread) {
        requestThreadDeletion(listOf(thread))
    }

    fun requestThreadDeletion(threads: List<ConversationThread>) {
        if(_state.value.threadActionInProgress) return
        _state.value = _state.value.copy(
            pendingThreadDeletions = threads.distinctBy(ConversationThread::id),
            threadActionFailed = false,
        )
    }

    fun cancelThreadDeletion() {
        if(_state.value.threadActionInProgress) return
        _state.value = _state.value.copy(pendingThreadDeletions = emptyList())
    }

    fun confirmThreadDeletion() {
        val threadIds = _state.value.pendingThreadDeletions.mapTo(linkedSetOf()) { it.id }
        if(threadIds.isEmpty()) return
        _state.value = _state.value.copy(pendingThreadDeletions = emptyList())
        dispatchThreadActions(threadIds, ConversationThreadAction.DELETE)
    }

    private fun dispatchThreadActions(threadIds: Set<Int>, action: ConversationThreadAction) {
        if(_state.value.threadActionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                threadActionInProgress = true,
                threadActionFailed = false,
            )
            val succeeded = threadIds.fold(true) { allSucceeded, threadId ->
                messageService.updateConversationThread(threadId, action) && allSucceeded
            }
            _state.value = _state.value.copy(
                threadActionInProgress = false,
                threadActionFailed = !succeeded,
            )
        }
    }

    fun markAllRead() {
        if(_state.value.threadActionInProgress) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                threadActionInProgress = true,
                threadActionFailed = false,
            )
            val succeeded = messageService.markAllConversationsRead()
            _state.value = _state.value.copy(
                threadActionInProgress = false,
                threadActionFailed = !succeeded,
            )
        }
    }

    fun exportMessages(destinationUri: String) {
        viewModelScope.launch {
            _state.value = _state.value.copy(exportResult = null)
            _state.value = _state.value.copy(
                exportResult = messageService.exportMessages(destinationUri),
            )
        }
    }

    fun clearExportResult() {
        _state.value = _state.value.copy(exportResult = null)
    }

    private fun importMessages() {
        if(messageService.isMessageStoreReady()) {
            _state.value = _state.value.copy(
                roleRequired = false,
                isImporting = false,
                importProgress = null,
            )
            return
        }
        if(_state.value.isImporting ||
            _state.value.showContactsPrompt ||
            _state.value.showReady ||
            !messageService.isDefaultSmsApp()
        ) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                roleRequired = false,
                isImporting = true,
                error = null,
            )
            when(messageService.importIfNeeded { progress ->
                _state.value = _state.value.copy(importProgress = progress)
            }) {
                ImportResult.RoleRequired -> _state.value = InboxUiState(roleRequired = true)
                ImportResult.AlreadyReady,
                is ImportResult.Imported -> {
                    val showContacts = !messageService.hasContactAccess() &&
                        !messageService.isContactPromptCompleted()
                    _state.value = InboxUiState(
                        roleRequired = false,
                        showContactsPrompt = showContacts,
                        folder = _state.value.folder,
                    )
                }
                is ImportResult.Failed -> _state.value = _state.value.copy(
                    isImporting = false,
                    error = InboxError.IMPORT_FAILED,
                )
            }
        }
    }
}

data class ConversationUiState(
    val header: ConversationHeader? = null,
    val draft: String = "",
    val isSending: Boolean = false,
    val isRequestingSecureSession: Boolean = false,
    val securityFingerprint: String? = null,
    val securityQrPayload: String? = null,
    val identityVerificationResult: IdentityVerificationResult? = null,
    val error: ConversationError? = null,
)

enum class IdentityVerificationResult {
    VERIFIED,
    FAILED,
}

enum class ConversationError {
    SEND_FAILED,
    SECURITY_NOT_READY,
    SECURE_REQUEST_FAILED,
}

@OptIn(ExperimentalCoroutinesApi::class)
class ConversationViewModel(
    private val messageService: MessageService,
    private val address: String,
    initialThreadId: Int?,
    initialText: String? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ConversationUiState(draft = initialText.orEmpty())
    )
    val state: StateFlow<ConversationUiState> = _state.asStateFlow()
    val attachments: Flow<List<AttachmentTransfer>> = messageService.attachmentTransfers(address)
    private var draftSaveJob: Job? = null

    private val activeThreadId = MutableStateFlow(initialThreadId)
    val timeline: Flow<PagingData<TimelineItem>> = activeThreadId
        .flatMapLatest { threadId ->
            if(threadId == null) flowOf(PagingData.empty())
            else messageService.timeline(threadId)
        }
        .cachedIn(viewModelScope)

    init {
        refreshHeader()
    }

    fun refreshHeader() {
        viewModelScope.launch {
            runCatching { messageService.conversationHeader(address, activeThreadId.value) }
                .onSuccess { header ->
                    activeThreadId.value = header.threadId
                    val restoredDraft = if(_state.value.draft.isBlank()) {
                        messageService.loadDraft(header.threadId)
                    } else {
                        _state.value.draft
                    }
                    _state.value = _state.value.copy(
                        header = header,
                        draft = restoredDraft,
                        securityFingerprint = if(header.isGroupConversation) null
                            else messageService.securityFingerprint(header.address),
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(error = ConversationError.SEND_FAILED)
                }
        }
    }

    fun updateDraft(value: String) {
        _state.value = _state.value.copy(draft = value, error = null)
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(DRAFT_SAVE_DEBOUNCE_MILLIS)
            saveDraftSnapshot()
        }
    }

    fun persistDraft() {
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch { saveDraftSnapshot() }
    }

    fun selectSubscription(subscriptionId: Long) {
        val header = _state.value.header ?: return
        if(header.subscriptionId == subscriptionId) return
        _state.value = _state.value.copy(
            header = header.copy(subscriptionId = subscriptionId),
        )
        viewModelScope.launch {
            messageService.selectSubscription(header.address, subscriptionId)
            saveDraftSnapshot()
        }
    }

    fun send() {
        val snapshot = _state.value
        val header = snapshot.header ?: return
        val body = snapshot.draft.trim()
        if(body.isEmpty() || snapshot.isSending) return

        viewModelScope.launch {
            _state.value = snapshot.copy(isSending = true, error = null)
            val recipients = header.participants
                .map(MessageRecipient::address)
                .ifEmpty { GroupMessagePolicy.recipients(header.address) }
            val sendResult = if(recipients.size > 1) {
                messageService.sendMms(
                    addresses = recipients,
                    threadId = header.threadId,
                    subscriptionId = header.subscriptionId,
                    text = body,
                )
            } else {
                messageService.sendText(
                    address = header.address,
                    threadId = header.threadId,
                    subscriptionId = header.subscriptionId,
                    text = body,
                )
            }
            when(sendResult) {
                is SendResult.Sent -> {
                    messageService.saveDraft(
                        address = header.address,
                        threadId = header.threadId,
                        subscriptionId = header.subscriptionId,
                        text = "",
                    )
                    _state.value = _state.value.copy(
                        draft = "",
                        isSending = false,
                    )
                }
                is SendResult.BlockedBySecurity -> {
                    _state.value = _state.value.copy(
                        isSending = false,
                        error = ConversationError.SECURITY_NOT_READY,
                    )
                    refreshHeader()
                }
                is SendResult.Failed -> _state.value = _state.value.copy(
                    isSending = false,
                    error = ConversationError.SEND_FAILED,
                )
            }
        }
    }

    fun requestOrRepairSecureSession(forceRenewal: Boolean = false) {
        val header = _state.value.header ?: return
        if(_state.value.isRequestingSecureSession) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isRequestingSecureSession = true,
                error = null,
            )
            when(messageService.requestOrRepairSecureSession(
                address = header.address,
                threadId = header.threadId,
                subscriptionId = header.subscriptionId,
                forceRenewal = forceRenewal,
            )) {
                SecureSessionActionResult.RequestSent -> {
                    _state.value = _state.value.copy(isRequestingSecureSession = false)
                    refreshHeader()
                }
                is SecureSessionActionResult.Failed -> _state.value = _state.value.copy(
                    isRequestingSecureSession = false,
                    error = ConversationError.SECURE_REQUEST_FAILED,
                )
            }
        }
    }

    fun loadSecurityQrPayload() {
        if(_state.value.securityQrPayload != null) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                securityQrPayload = messageService.localSecurityQrPayload(),
            )
        }
    }

    fun verifyContactIdentity(qrPayload: String) {
        val header = _state.value.header ?: return
        viewModelScope.launch {
            val verified = messageService.verifyContactIdentity(header.address, qrPayload)
            _state.value = _state.value.copy(
                identityVerificationResult = if(verified) {
                    IdentityVerificationResult.VERIFIED
                } else {
                    IdentityVerificationResult.FAILED
                },
            )
            if(verified) refreshHeader()
        }
    }

    fun clearIdentityVerificationResult() {
        _state.value = _state.value.copy(identityVerificationResult = null)
    }

    fun acceptChangedIdentityAndRepair() {
        val header = _state.value.header ?: return
        if(_state.value.isRequestingSecureSession) return
        viewModelScope.launch {
            _state.value = _state.value.copy(
                isRequestingSecureSession = true,
                error = null,
            )
            when(messageService.acceptChangedIdentityAndRepair(
                address = header.address,
                threadId = header.threadId,
                subscriptionId = header.subscriptionId,
            )) {
                SecureSessionActionResult.RequestSent -> {
                    _state.value = _state.value.copy(isRequestingSecureSession = false)
                    refreshHeader()
                }
                is SecureSessionActionResult.Failed -> _state.value = _state.value.copy(
                    isRequestingSecureSession = false,
                    error = ConversationError.SECURE_REQUEST_FAILED,
                )
            }
        }
    }

    fun clearError() {
        _state.value = _state.value.copy(error = null)
    }

    fun performAttachmentAction(transferId: String, action: AttachmentAction) {
        viewModelScope.launch {
            runCatching { messageService.performAttachmentAction(transferId, action) }
                .onFailure {
                    _state.value = _state.value.copy(error = ConversationError.SEND_FAILED)
                }
        }
    }

    suspend fun prepareAttachment(attachment: PreparedAttachment): Boolean {
        val header = _state.value.header ?: return false
        return when(messageService.prepareAttachment(
            address = header.address,
            subscriptionId = header.subscriptionId,
            attachment = attachment,
        )) {
            AttachmentPrepareResult.Queued -> true
            is AttachmentPrepareResult.Failed -> {
                _state.value = _state.value.copy(error = ConversationError.SEND_FAILED)
                false
            }
        }
    }

    private suspend fun saveDraftSnapshot() {
        val snapshot = _state.value
        val header = snapshot.header ?: return
        messageService.saveDraft(
            address = header.address,
            threadId = header.threadId,
            subscriptionId = header.subscriptionId,
            text = snapshot.draft,
        )
    }

    private companion object {
        const val DRAFT_SAVE_DEBOUNCE_MILLIS = 400L
    }
}

class InboxViewModelFactory(
    private val messageService: MessageService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(InboxViewModel::class.java))
        return InboxViewModel(messageService) as T
    }
}

class SettingsViewModel(
    private val settingsService: AppSettingsService,
) : ViewModel() {
    private val _state = MutableStateFlow(settingsService.snapshot())
    val state: StateFlow<AppSettingsSnapshot> = _state.asStateFlow()

    fun setTheme(mode: ThemeMode) {
        _state.value = settingsService.setTheme(mode)
    }

    fun setLanguage(tag: String) {
        _state.value = settingsService.setLanguage(tag)
    }

    fun setBoolean(setting: BooleanSetting, enabled: Boolean) {
        _state.value = settingsService.setBoolean(setting, enabled)
    }
}

class SettingsViewModelFactory(
    private val settingsService: AppSettingsService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(SettingsViewModel::class.java))
        return SettingsViewModel(settingsService) as T
    }
}

data class ContactDetailsUiState(
    val header: ConversationHeader? = null,
    val fingerprint: String? = null,
    val isBlocked: Boolean = false,
    val isLoading: Boolean = true,
    val operationFailed: Boolean = false,
    val photoCount: Int = 0,
    val voiceCount: Int = 0,
    val fileCount: Int = 0,
)

class ContactDetailsViewModel(
    private val messageService: MessageService,
    private val address: String,
    private val threadId: Int? = null,
) : ViewModel() {
    private val _state = MutableStateFlow(ContactDetailsUiState())
    val state: StateFlow<ContactDetailsUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            messageService.attachmentTransfers(address).collect { transfers ->
                _state.value = _state.value.copy(
                    photoCount = transfers.count { it.kind == com.afkanerd.deku.messages.domain.AttachmentKind.PHOTO },
                    voiceCount = transfers.count { it.kind == com.afkanerd.deku.messages.domain.AttachmentKind.VOICE },
                    fileCount = transfers.count { it.kind == com.afkanerd.deku.messages.domain.AttachmentKind.FILE },
                )
            }
        }
        viewModelScope.launch {
            runCatching {
                val header = messageService.conversationHeader(address, threadId)
                Triple(
                    header,
                    if(header.isGroupConversation) null else messageService.securityFingerprint(address),
                    if(header.isGroupConversation) false else messageService.isContactBlocked(address),
                )
            }.onSuccess { (header, fingerprint, blocked) ->
                _state.value = _state.value.copy(
                    header = header,
                    fingerprint = fingerprint,
                    isBlocked = blocked,
                    isLoading = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(isLoading = false, operationFailed = true)
            }
        }
    }

    fun selectSubscription(subscriptionId: Long) {
        val header = _state.value.header ?: return
        _state.value = _state.value.copy(header = header.copy(subscriptionId = subscriptionId))
        viewModelScope.launch { messageService.selectSubscription(header.address, subscriptionId) }
    }

    fun toggleBlocked() {
        val header = _state.value.header ?: return
        val next = !_state.value.isBlocked
        viewModelScope.launch {
            if(messageService.setContactBlocked(header.address, next)) {
                _state.value = _state.value.copy(isBlocked = next, operationFailed = false)
            } else {
                _state.value = _state.value.copy(operationFailed = true)
            }
        }
    }

    fun toggleMuted() {
        val header = _state.value.header ?: return
        val action = if(header.isMuted) {
            ConversationThreadAction.UNMUTE
        } else {
            ConversationThreadAction.MUTE
        }
        viewModelScope.launch {
            if(messageService.updateConversationThread(header.threadId, action)) {
                _state.value = _state.value.copy(
                    header = header.copy(isMuted = !header.isMuted),
                    operationFailed = false,
                )
            } else {
                _state.value = _state.value.copy(operationFailed = true)
            }
        }
    }
}

class ContactDetailsViewModelFactory(
    private val messageService: MessageService,
    private val address: String,
    private val threadId: Int? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ContactDetailsViewModel::class.java))
        return ContactDetailsViewModel(messageService, address, threadId) as T
    }
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MessagesSearchViewModel(
    private val messageService: MessageService,
    private val address: String?,
) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val results: Flow<PagingData<ConversationThread>> = _query
        .debounce(SEARCH_DEBOUNCE_MILLIS)
        .distinctUntilChanged()
        .flatMapLatest { value ->
            if(value.length < MINIMUM_QUERY_LENGTH) flowOf(PagingData.empty())
            else messageService.searchThreads(value, address)
        }
        .cachedIn(viewModelScope)

    fun updateQuery(value: String) {
        _query.value = value
    }

    fun clear() {
        _query.value = ""
    }

    private companion object {
        const val MINIMUM_QUERY_LENGTH = 2
        const val SEARCH_DEBOUNCE_MILLIS = 180L
    }
}

class MessagesSearchViewModelFactory(
    private val messageService: MessageService,
    private val address: String?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(MessagesSearchViewModel::class.java))
        return MessagesSearchViewModel(messageService, address) as T
    }
}

data class NewMessageDestination(
    val address: String,
    val threadId: Int,
    val preservedText: String? = null,
)

data class NewMessageUiState(
    val query: String = "",
    val draftText: String = "",
    val selectedRecipients: List<MessageRecipient> = emptyList(),
    val recipients: List<MessageRecipient> = emptyList(),
    val subscriptions: List<SimSubscription> = emptyList(),
    val selectedSubscriptionId: Long? = null,
    val isLoading: Boolean = true,
    val isCreating: Boolean = false,
    val destination: NewMessageDestination? = null,
    val operationFailed: Boolean = false,
) {
    val selectedRecipient: MessageRecipient?
        get() = selectedRecipients.firstOrNull()
}

class NewMessageViewModel(
    private val messageService: MessageService,
    private val initialText: String?,
    private val initialSubscriptionId: Long?,
    initialAddresses: String? = null,
) : ViewModel() {
    private val availableSubscriptions = messageService.activeSimSubscriptions()
    private val initialRecipients = GroupMessagePolicy.recipients(initialAddresses.orEmpty())
        .mapIndexed { index, address ->
            MessageRecipient(
                id = Long.MIN_VALUE + index,
                address = address,
                displayName = address,
                avatarUri = null,
                isDirectEntry = true,
            )
        }
    private val _state = MutableStateFlow(
        NewMessageUiState(
            draftText = initialText.orEmpty(),
            selectedRecipients = initialRecipients,
            subscriptions = availableSubscriptions,
            selectedSubscriptionId = initialSubscriptionId
                ?: availableSubscriptions.firstOrNull()?.id,
        )
    )
    val state: StateFlow<NewMessageUiState> = _state.asStateFlow()
    private var searchJob: Job? = null

    init {
        search("")
    }

    fun updateQuery(value: String) {
        _state.value = _state.value.copy(query = value, operationFailed = false)
        search(value)
    }

    fun updateDraft(value: String) {
        _state.value = _state.value.copy(draftText = value, operationFailed = false)
    }

    fun selectSubscription(subscriptionId: Long) {
        if(_state.value.subscriptions.none { it.id == subscriptionId }) return
        _state.value = _state.value.copy(selectedSubscriptionId = subscriptionId)
    }

    fun selectRecipient(recipient: MessageRecipient) {
        val current = _state.value
        val selected = current.selectedRecipients
        if(selected.any { it.address == recipient.address }) return
        _state.value = current.copy(
            selectedRecipients = selected + recipient,
            query = "",
            operationFailed = false,
        )
        if(current.query.isNotEmpty()) search("")
    }

    fun toggleRecipient(recipient: MessageRecipient) {
        if(_state.value.selectedRecipients.any { it.address == recipient.address }) {
            removeRecipient(recipient)
        } else {
            selectRecipient(recipient)
        }
    }

    fun replaceRecipients(recipients: List<MessageRecipient>) {
        _state.value = _state.value.copy(
            selectedRecipients = recipients.distinctBy(MessageRecipient::address),
            query = "",
            operationFailed = false,
        )
        search("")
    }

    fun removeRecipient(recipient: MessageRecipient) {
        val current = _state.value
        _state.value = current.copy(
            selectedRecipients = current.selectedRecipients.filterNot {
                it.address == recipient.address
            },
            query = "",
            operationFailed = false,
        )
        if(current.query.isNotEmpty()) search("")
    }

    fun clearRecipient() {
        _state.value = _state.value.copy(selectedRecipients = emptyList())
    }

    fun createConversation() {
        val snapshot = _state.value
        val recipients = snapshot.selectedRecipients.takeIf { it.isNotEmpty() }
            ?: return
        val textToSend = snapshot.draftText.takeIf(String::isNotBlank) ?: return
        if(snapshot.destination != null || snapshot.isCreating) return
        _state.value = snapshot.copy(isCreating = true, operationFailed = false)
        viewModelScope.launch {
            runCatching {
                val addresses = recipients.map(MessageRecipient::address)
                val header = messageService.conversationHeader(addresses, null)
                val subscription = snapshot.selectedSubscriptionId ?: header.subscriptionId
                val sendResult = if(addresses.size == 1) {
                    messageService.sendText(
                        address = header.address,
                        threadId = header.threadId,
                        subscriptionId = subscription,
                        text = textToSend,
                    )
                } else {
                    messageService.sendMms(
                        addresses = addresses,
                        threadId = header.threadId,
                        subscriptionId = subscription,
                        text = textToSend,
                    )
                }
                when(sendResult) {
                    is SendResult.Sent -> NewMessageDestination(
                        address = header.address,
                        threadId = header.threadId,
                    )
                    is SendResult.BlockedBySecurity -> {
                        messageService.saveDraft(
                            address = header.address,
                            threadId = header.threadId,
                            subscriptionId = subscription,
                            text = textToSend,
                        )
                        NewMessageDestination(
                            address = header.address,
                            threadId = header.threadId,
                            preservedText = textToSend,
                        )
                    }
                    is SendResult.Failed -> throw IllegalStateException("Message was not sent")
                }
            }.onSuccess { destination ->
                _state.value = _state.value.copy(
                    destination = destination,
                    isCreating = false,
                )
            }.onFailure {
                _state.value = _state.value.copy(
                    isCreating = false,
                    operationFailed = true,
                )
            }
        }
    }

    fun consumeDestination() {
        _state.value = _state.value.copy(destination = null)
    }

    suspend fun prepareAttachment(attachment: PreparedAttachment): Boolean {
        val snapshot = _state.value
        val recipients = snapshot.selectedRecipients.takeIf(List<MessageRecipient>::isNotEmpty)
            ?: return false
        if(snapshot.destination != null || snapshot.isCreating) return false
        _state.value = snapshot.copy(isCreating = true, operationFailed = false)
        return runCatching {
            val addresses = recipients.map(MessageRecipient::address)
            val header = messageService.conversationHeader(addresses, null)
            val subscriptionId = snapshot.selectedSubscriptionId ?: header.subscriptionId
            when(
                messageService.prepareAttachment(
                    address = header.address,
                    subscriptionId = subscriptionId,
                    attachment = attachment,
                )
            ) {
                AttachmentPrepareResult.Queued -> {
                    _state.value = _state.value.copy(
                        isCreating = false,
                        destination = NewMessageDestination(
                            address = header.address,
                            threadId = header.threadId,
                            preservedText = snapshot.draftText.takeIf(String::isNotBlank),
                        ),
                    )
                    true
                }
                is AttachmentPrepareResult.Failed -> false
            }
        }.getOrElse { false }.also { queued ->
            if(!queued) {
                _state.value = _state.value.copy(
                    isCreating = false,
                    operationFailed = true,
                )
            }
        }
    }

    private fun search(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true)
            delay(RECIPIENT_SEARCH_DEBOUNCE_MILLIS)
            runCatching { messageService.searchRecipients(query) }
                .onSuccess { recipients ->
                    val knownByAddress = recipients.associateBy { it.address }
                    val selected = _state.value.selectedRecipients.map { selectedRecipient ->
                        knownByAddress[selectedRecipient.address] ?: selectedRecipient
                    }
                    _state.value = _state.value.copy(
                        selectedRecipients = selected,
                        recipients = recipients,
                        isLoading = false,
                    )
                }
                .onFailure {
                    _state.value = _state.value.copy(
                        recipients = emptyList(),
                        isLoading = false,
                        operationFailed = true,
                    )
                }
        }
    }

    private companion object {
        const val RECIPIENT_SEARCH_DEBOUNCE_MILLIS = 140L
    }
}

class NewMessageViewModelFactory(
    private val messageService: MessageService,
    private val initialText: String?,
    private val initialSubscriptionId: Long?,
    private val initialAddresses: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(NewMessageViewModel::class.java))
        return NewMessageViewModel(
            messageService,
            initialText,
            initialSubscriptionId,
            initialAddresses,
        ) as T
    }
}

class ConversationViewModelFactory(
    private val messageService: MessageService,
    private val address: String,
    private val threadId: Int?,
    private val initialText: String? = null,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        require(modelClass.isAssignableFrom(ConversationViewModel::class.java))
        return ConversationViewModel(messageService, address, threadId, initialText) as T
    }
}
