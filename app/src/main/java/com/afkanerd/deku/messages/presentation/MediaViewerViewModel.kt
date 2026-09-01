package com.afkanerd.deku.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afkanerd.deku.messages.domain.MessageService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MediaViewerUiState(
    val isSaving: Boolean = false,
    val notice: MediaViewerNotice? = null,
)

enum class MediaViewerNotice {
    SAVED,
    FAILED,
}

class MediaViewerViewModel(
    private val messageService: MessageService,
    private val sourceUri: String,
) : ViewModel() {
    private val _state = MutableStateFlow(MediaViewerUiState())
    val state: StateFlow<MediaViewerUiState> = _state.asStateFlow()

    fun save(destinationUri: String?) {
        if(destinationUri.isNullOrBlank() || _state.value.isSaving) return
        viewModelScope.launch {
            _state.value = MediaViewerUiState(isSaving = true)
            val succeeded = runCatching {
                messageService.saveMedia(sourceUri, destinationUri)
            }.getOrDefault(false)
            _state.value = MediaViewerUiState(
                notice = if(succeeded) MediaViewerNotice.SAVED else MediaViewerNotice.FAILED,
            )
        }
    }

    fun clearNotice() {
        _state.value = _state.value.copy(notice = null)
    }
}

class MediaViewerViewModelFactory(
    private val messageService: MessageService,
    private val sourceUri: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MediaViewerViewModel(messageService, sourceUri) as T
}
