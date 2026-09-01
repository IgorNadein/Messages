package com.afkanerd.deku.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afkanerd.deku.messages.domain.GatewayDraft
import com.afkanerd.deku.messages.domain.GatewayProtocol
import com.afkanerd.deku.messages.domain.GatewaySummary
import com.afkanerd.deku.messages.domain.MessageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GatewayEditorState(
    val id: Long?,
    val draft: GatewayDraft,
    val validationFailed: Boolean = false,
)

data class GatewayUiState(
    val items: List<GatewaySummary> = emptyList(),
    val editor: GatewayEditorState? = null,
    val deleteCandidateId: Long? = null,
    val isBusy: Boolean = false,
    val notice: GatewayNotice? = null,
)

enum class GatewayNotice {
    FAILED,
    UNSUPPORTED_PROTOCOL,
}

class GatewayViewModel(
    private val messageService: MessageService,
) : ViewModel() {
    private val _state = MutableStateFlow(GatewayUiState())
    val state: StateFlow<GatewayUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            messageService.gatewayConfigurations().collect { gateways ->
                _state.value = _state.value.copy(items = gateways)
            }
        }
    }

    fun create(protocol: GatewayProtocol) {
        if(protocol == GatewayProtocol.FTP || _state.value.isBusy) return
        _state.value = _state.value.copy(
            editor = GatewayEditorState(id = null, draft = GatewayDraft(protocol)),
            notice = null,
        )
    }

    fun edit(id: Long) {
        if(_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, notice = null)
            val draft = runCatching { messageService.loadGatewayDraft(id) }.getOrNull()
            _state.value = if(draft == null) {
                _state.value.copy(
                    isBusy = false,
                    notice = GatewayNotice.UNSUPPORTED_PROTOCOL,
                )
            } else {
                _state.value.copy(
                    isBusy = false,
                    editor = GatewayEditorState(id = id, draft = draft),
                )
            }
        }
    }

    fun updateDraft(draft: GatewayDraft) {
        val editor = _state.value.editor ?: return
        if(_state.value.isBusy || draft.protocol != editor.draft.protocol) return
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
                messageService.saveGateway(editor.id, editor.draft)
            }.getOrDefault(false)
            _state.value = _state.value.copy(
                isBusy = false,
                editor = if(succeeded) null else editor,
                notice = if(succeeded) null else GatewayNotice.FAILED,
            )
        }
    }

    fun requestDelete() {
        val id = _state.value.editor?.id ?: return
        if(_state.value.isBusy) return
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
            val succeeded = runCatching { messageService.deleteGateway(id) }
                .getOrDefault(false)
            _state.value = _state.value.copy(
                isBusy = false,
                editor = if(succeeded) null else _state.value.editor,
                deleteCandidateId = null,
                notice = if(succeeded) null else GatewayNotice.FAILED,
            )
        }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }
}

class GatewayViewModelFactory(
    private val messageService: MessageService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        GatewayViewModel(messageService) as T
}
