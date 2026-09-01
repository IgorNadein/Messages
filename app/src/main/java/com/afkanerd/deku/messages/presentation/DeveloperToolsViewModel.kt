package com.afkanerd.deku.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afkanerd.deku.messages.domain.DeveloperToolsService
import com.afkanerd.deku.messages.domain.NativeMessageImportSummary
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DeveloperToolsUiState(
    val isBusy: Boolean = false,
    val showClearConfirmation: Boolean = false,
    val showClearNativeConfirmation: Boolean = false,
    val importSummary: NativeMessageImportSummary? = null,
    val result: DeveloperToolsResult? = null,
)

enum class DeveloperToolsResult {
    SAMPLE_NOTIFICATION_CREATED,
    LOCAL_HISTORY_CLEARED,
    NATIVE_DATABASE_EXPORTED,
    NATIVE_DATABASE_IMPORTED,
    NATIVE_DATABASE_CLEARED,
    FAILED,
}

class DeveloperToolsViewModel(
    private val service: DeveloperToolsService,
) : ViewModel() {
    private val _state = MutableStateFlow(DeveloperToolsUiState())
    val state: StateFlow<DeveloperToolsUiState> = _state.asStateFlow()

    fun triggerSampleMmsNotification() = runOperation(
        success = DeveloperToolsResult.SAMPLE_NOTIFICATION_CREATED,
        operation = service::triggerSampleMmsNotification,
    )

    /** Opening the dialog must never mutate message storage. */
    fun requestClearLocalHistory() {
        if(_state.value.isBusy) return
        _state.value = _state.value.copy(
            showClearConfirmation = true,
            showClearNativeConfirmation = false,
            result = null,
        )
    }

    fun dismissClearLocalHistory() {
        _state.value = _state.value.copy(showClearConfirmation = false)
    }

    fun confirmClearLocalHistory() {
        if(!_state.value.showClearConfirmation || _state.value.isBusy) return
        _state.value = _state.value.copy(showClearConfirmation = false)
        runOperation(
            success = DeveloperToolsResult.LOCAL_HISTORY_CLEARED,
            operation = service::clearLocalMessageHistory,
        )
    }

    fun clearResult() {
        _state.value = _state.value.copy(result = null, importSummary = null)
    }

    fun exportNativeMessageDatabase(destinationUri: String) = runOperation(
        success = DeveloperToolsResult.NATIVE_DATABASE_EXPORTED,
        operation = { service.exportNativeMessageDatabase(destinationUri) },
    )

    fun importNativeMessageDatabase(sourceUri: String) {
        if(_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, result = null, importSummary = null)
            val summary = runCatching {
                service.importNativeMessageDatabase(sourceUri)
            }.getOrNull()
            _state.value = _state.value.copy(
                isBusy = false,
                importSummary = summary,
                result = if(summary != null) DeveloperToolsResult.NATIVE_DATABASE_IMPORTED
                    else DeveloperToolsResult.FAILED,
            )
        }
    }

    fun requestClearNativeMessageDatabase() {
        if(_state.value.isBusy) return
        _state.value = _state.value.copy(
            showClearConfirmation = false,
            showClearNativeConfirmation = true,
            result = null,
        )
    }

    fun dismissClearNativeMessageDatabase() {
        _state.value = _state.value.copy(showClearNativeConfirmation = false)
    }

    fun confirmClearNativeMessageDatabase() {
        if(!_state.value.showClearNativeConfirmation || _state.value.isBusy) return
        _state.value = _state.value.copy(showClearNativeConfirmation = false)
        runOperation(
            success = DeveloperToolsResult.NATIVE_DATABASE_CLEARED,
            operation = service::clearNativeMessageDatabase,
        )
    }

    private fun runOperation(
        success: DeveloperToolsResult,
        operation: suspend () -> Boolean,
    ) {
        if(_state.value.isBusy) return
        viewModelScope.launch {
            _state.value = _state.value.copy(isBusy = true, result = null, importSummary = null)
            val succeeded = runCatching { operation() }.getOrDefault(false)
            _state.value = _state.value.copy(
                isBusy = false,
                result = if(succeeded) success else DeveloperToolsResult.FAILED,
            )
        }
    }
}

class DeveloperToolsViewModelFactory(
    private val service: DeveloperToolsService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        DeveloperToolsViewModel(service) as T
}
