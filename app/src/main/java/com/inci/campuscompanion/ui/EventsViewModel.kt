package com.inci.campuscompanion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inci.campuscompanion.data.EventsRepository
import com.inci.campuscompanion.model.CampusEvent
import com.inci.campuscompanion.work.EventsRefreshScheduler
import com.inci.campuscompanion.work.RefreshState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class EventsUiState(
    val events: List<CampusEvent> = emptyList(),
    val refresh: RefreshState = RefreshState()
)

class EventsViewModel(
    repository: EventsRepository,
    private val scheduler: EventsRefreshScheduler
) : ViewModel() {
    val uiState = combine(repository.events, scheduler.state) { events, refresh ->
        EventsUiState(events, refresh)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EventsUiState())

    init { refresh() }

    fun refresh() { scheduler.refresh() }

    companion object {
        fun Factory(repository: EventsRepository, scheduler: EventsRefreshScheduler): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EventsViewModel(repository, scheduler) as T
            }
    }
}
