package com.afkanerd.deku.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.RemoteListenerDraft
import com.afkanerd.deku.messages.domain.RemoteListenerSummary
import com.afkanerd.deku.messages.domain.RemoteListenerToggleResult
import com.afkanerd.deku.messages.domain.RemoteQueueDraft
import com.afkanerd.deku.messages.domain.RemoteQueueSummary
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RemoteListenerEditorState(
    val id: Long?,
    val draft: RemoteListenerDraft,
    val validationFailed: Boolean = false,
)

data class RemoteQueueEditorState(
    val id: Long?,
    val draft: RemoteQueueDraft,
    val validationFailed: Boolean = false,
)

data class RemoteListenersUiState(
    val items: List<RemoteListenerSummary> = emptyList(),
    val selectedListenerId: Long? = null,
    val queues: List<RemoteQueueSummary> = emptyList(),
    val editor: RemoteListenerEditorState? = null,
    val queueEditor: RemoteQueueEditorState? = null,
    val deleteCandidateId: Long? = null,
    val queueDeleteCandidateId: Long? = null,
    val pendingToggleId: Long? = null,
    val isBusy: Boolean = false,
    val notice: RemoteListenerNotice? = null,
)

enum class RemoteListenerNotice {
    FAILED,
    MISSING_QUEUES,
    PERMISSION_REQUIRED,
    ACTIVATED,
    DEACTIVATED,
}

class RemoteListenersViewModel(
    private val messageService: MessageService,
) : ViewModel() {
    private val _state = MutableStateFlow(RemoteListenersUiState())
    val state: StateFlow<RemoteListenersUiState> = _state.asStateFlow()
    private var queuesJob: Job? = null

    init {
        viewModelScope.launch {
            messageService.remoteListeners().collect { listeners ->
                val selectedId = _state.value.selectedListenerId
                    ?.takeIf { id -> listeners.any { it.id == id } }
                _state.value = _state.value.copy(
                    items = listeners,
                    selectedListenerId = selectedId,
                    queues = if(selectedId == null) emptyList() else _state.value.queues,
                )
            }
        }
    }

    fun create() {
        if(_state.value.isBusy) return
        _state.value = _state.value.copy(
            editor = RemoteListenerEditorState(null, RemoteListenerDraft()),
            notice = null,
        )
    }

    fun edit(id: Long) {
        if(_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, notice = null)
            val draft = runCatching { messageService.loadRemoteListenerDraft(id) }.getOrNull()
            _state.value = _state.value.copy(
                isBusy = false,
                editor = draft?.let { RemoteListenerEditorState(id, it) },
                notice = if(draft == null) RemoteListenerNotice.FAILED else null,
            )
        }
    }

    fun updateDraft(draft: RemoteListenerDraft) {
        val editor = _state.value.editor ?: return
        if(_state.value.isBusy) return
        _state.value = _state.value.copy(
            editor = editor.copy(draft = draft, validationFailed = false),
        )
    }

    fun dismissEditor() {
        if(_state.value.isBusy) return
        _state.value = _state.value.copy(editor = null, deleteCandidateId = null)
    }

    fun save() {
        val editor = _state.value.editor ?: return
        if(_state.value.isBusy) return
        if(!editor.draft.isValid()) {
            _state.value = _state.value.copy(
                editor = editor.copy(validationFailed = true),
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, notice = null)
            val succeeded = runCatching {
                messageService.saveRemoteListener(editor.id, editor.draft)
            }.getOrDefault(false)
            _state.value = _state.value.copy(
                isBusy = false,
                editor = if(succeeded) null else editor,
                notice = if(succeeded) null else RemoteListenerNotice.FAILED,
            )
        }
    }

    fun requestDelete(id: Long) {
        if(_state.value.isBusy || _state.value.items.none { it.id == id }) return
        _state.value = _state.value.copy(deleteCandidateId = id)
    }

    fun dismissDelete() {
        _state.value = _state.value.copy(deleteCandidateId = null)
    }

    fun confirmDelete() {
        val id = _state.value.deleteCandidateId ?: return
        if(_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, notice = null)
            val succeeded = runCatching { messageService.deleteRemoteListener(id) }
                .getOrDefault(false)
            _state.value = _state.value.copy(
                isBusy = false,
                editor = if(succeeded && _state.value.editor?.id == id) null
                    else _state.value.editor,
                selectedListenerId = if(succeeded && _state.value.selectedListenerId == id) null
                    else _state.value.selectedListenerId,
                deleteCandidateId = null,
                notice = if(succeeded) null else RemoteListenerNotice.FAILED,
            )
        }
    }

    fun toggle(id: Long) {
        if(_state.value.isBusy) return
        viewModelScope.launch { toggleInternal(id) }
    }

    fun onPermissionsResult(granted: Boolean) {
        val id = _state.value.pendingToggleId ?: return
        _state.value = _state.value.copy(
            pendingToggleId = null,
            notice = if(granted) null else RemoteListenerNotice.PERMISSION_REQUIRED,
        )
        if(granted) viewModelScope.launch { toggleInternal(id) }
    }

    private suspend fun toggleInternal(id: Long) {
        _state.value = _state.value.copy(isBusy = true, notice = null)
        val result = runCatching { messageService.toggleRemoteListener(id) }
            .getOrDefault(RemoteListenerToggleResult.FAILED)
        _state.value = _state.value.copy(
            isBusy = false,
            pendingToggleId = if(result == RemoteListenerToggleResult.PERMISSION_REQUIRED) id
                else null,
            notice = when(result) {
                RemoteListenerToggleResult.ACTIVATED -> RemoteListenerNotice.ACTIVATED
                RemoteListenerToggleResult.DEACTIVATED -> RemoteListenerNotice.DEACTIVATED
                RemoteListenerToggleResult.MISSING_QUEUES -> RemoteListenerNotice.MISSING_QUEUES
                RemoteListenerToggleResult.PERMISSION_REQUIRED ->
                    RemoteListenerNotice.PERMISSION_REQUIRED
                RemoteListenerToggleResult.FAILED -> RemoteListenerNotice.FAILED
            },
        )
    }

    fun openQueues(listenerId: Long) {
        if(_state.value.items.none { it.id == listenerId }) return
        queuesJob?.cancel()
        _state.value = _state.value.copy(
            selectedListenerId = listenerId,
            queues = emptyList(),
            notice = null,
        )
        queuesJob = viewModelScope.launch {
            messageService.remoteQueues(listenerId).collect { queues ->
                if(_state.value.selectedListenerId == listenerId) {
                    _state.value = _state.value.copy(queues = queues)
                }
            }
        }
    }

    fun closeQueues() {
        queuesJob?.cancel()
        queuesJob = null
        _state.value = _state.value.copy(
            selectedListenerId = null,
            queues = emptyList(),
            queueEditor = null,
            queueDeleteCandidateId = null,
        )
    }

    fun createQueue() {
        if(_state.value.selectedListenerId == null || _state.value.isBusy) return
        _state.value = _state.value.copy(
            queueEditor = RemoteQueueEditorState(null, RemoteQueueDraft()),
            notice = null,
        )
    }

    fun editQueue(id: Long) {
        if(_state.value.isBusy || _state.value.queues.none { it.id == id }) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, notice = null)
            val draft = runCatching { messageService.loadRemoteQueueDraft(id) }.getOrNull()
            _state.value = _state.value.copy(
                isBusy = false,
                queueEditor = draft?.let { RemoteQueueEditorState(id, it) },
                notice = if(draft == null) RemoteListenerNotice.FAILED else null,
            )
        }
    }

    fun updateQueueDraft(draft: RemoteQueueDraft) {
        val editor = _state.value.queueEditor ?: return
        if(_state.value.isBusy) return
        _state.value = _state.value.copy(
            queueEditor = editor.copy(draft = draft, validationFailed = false),
        )
    }

    fun updateQueueExchange(exchange: String) {
        val editor = _state.value.queueEditor ?: return
        if(_state.value.isBusy) return
        val updated = editor.draft.copy(exchange = exchange)
        _state.value = _state.value.copy(
            queueEditor = editor.copy(draft = updated, validationFailed = false),
        )
        viewModelScope.launch {
            val suggestions = runCatching {
                messageService.suggestRemoteBindings(exchange)
            }.getOrDefault(emptyList())
            val current = _state.value.queueEditor ?: return@launch
            if(current.draft.exchange != exchange || suggestions.isEmpty()) return@launch
            _state.value = _state.value.copy(
                queueEditor = current.copy(
                    draft = current.draft.copy(
                        sim1Binding = suggestions[0],
                        sim2Binding = suggestions.getOrNull(1).orEmpty(),
                    ),
                ),
            )
        }
    }

    fun dismissQueueEditor() {
        if(_state.value.isBusy) return
        _state.value = _state.value.copy(
            queueEditor = null,
            queueDeleteCandidateId = null,
        )
    }

    fun saveQueue() {
        val listenerId = _state.value.selectedListenerId ?: return
        val editor = _state.value.queueEditor ?: return
        if(_state.value.isBusy) return
        if(!editor.draft.isValid()) {
            _state.value = _state.value.copy(
                queueEditor = editor.copy(validationFailed = true),
            )
            return
        }
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, notice = null)
            val succeeded = runCatching {
                messageService.saveRemoteQueue(listenerId, editor.id, editor.draft)
            }.getOrDefault(false)
            _state.value = _state.value.copy(
                isBusy = false,
                queueEditor = if(succeeded) null else editor,
                notice = if(succeeded) null else RemoteListenerNotice.FAILED,
            )
        }
    }

    fun requestQueueDelete() {
        val id = _state.value.queueEditor?.id ?: return
        if(_state.value.isBusy) return
        _state.value = _state.value.copy(queueDeleteCandidateId = id)
    }

    fun dismissQueueDelete() {
        _state.value = _state.value.copy(queueDeleteCandidateId = null)
    }

    fun confirmQueueDelete() {
        val listenerId = _state.value.selectedListenerId ?: return
        val id = _state.value.queueDeleteCandidateId ?: return
        if(_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, notice = null)
            val succeeded = runCatching {
                messageService.deleteRemoteQueue(listenerId, id)
            }.getOrDefault(false)
            _state.value = _state.value.copy(
                isBusy = false,
                queueEditor = if(succeeded) null else _state.value.queueEditor,
                queueDeleteCandidateId = null,
                notice = if(succeeded) null else RemoteListenerNotice.FAILED,
            )
        }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }
}

class RemoteListenersViewModelFactory(
    private val messageService: MessageService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RemoteListenersViewModel(messageService) as T
}
