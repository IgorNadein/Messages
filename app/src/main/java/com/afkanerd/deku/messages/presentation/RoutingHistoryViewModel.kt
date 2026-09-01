package com.afkanerd.deku.messages.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.afkanerd.deku.messages.domain.MessageService
import com.afkanerd.deku.messages.domain.RoutingHistoryItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

data class RoutingHistoryUiState(
    val items: List<RoutingHistoryItem> = emptyList(),
    val isLoading: Boolean = true,
    val failed: Boolean = false,
)

class RoutingHistoryViewModel(
    private val messageService: MessageService,
) : ViewModel() {
    private val _state = MutableStateFlow(RoutingHistoryUiState())
    val state: StateFlow<RoutingHistoryUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            messageService.routingHistory()
                .catch {
                    _state.value = _state.value.copy(isLoading = false, failed = true)
                }
                .collect { items ->
                    _state.value = RoutingHistoryUiState(
                        items = items,
                        isLoading = false,
                    )
                }
        }
    }
}

class RoutingHistoryViewModelFactory(
    private val messageService: MessageService,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        RoutingHistoryViewModel(messageService) as T
}
