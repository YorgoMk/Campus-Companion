package com.inci.campuscompanion.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.inci.campuscompanion.data.EventsRepository
import com.inci.campuscompanion.model.CampusEvent
import com.inci.campuscompanion.work.EventsRefreshScheduler
import com.inci.campuscompanion.work.RefreshState
import com.inci.campuscompanion.work.RefreshStatus
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class LoadMoreState(
    val loading: Boolean = false,
    val errorMessage: String? = null
)

data class EventsUiState(
    val events: List<CampusEvent> = emptyList(),
    val refresh: RefreshState = RefreshState(),
    val canLoadMore: Boolean = false,
    val loadMore: LoadMoreState = LoadMoreState(),
    val refreshPending: Boolean = false
)

class EventsViewModel(
    private val repository: EventsRepository,
    private val scheduler: EventsRefreshScheduler
) : ViewModel() {
    private data class PendingRefresh(val previousRequestId: String?)
    private val refreshRequest = MutableStateFlow<PendingRefresh?>(null)
    private val loadMore = MutableStateFlow(LoadMoreState())

    val uiState = combine(repository.feed, scheduler.state, loadMore, refreshRequest) { feed, refresh, more, pending ->
        EventsUiState(
            feed.events, refresh, feed.canLoadMore, more,
            refreshPending = pending != null && pending.previousRequestId == refresh.requestId
        )
    }.onEach { state ->
        val pending = refreshRequest.value
        if (pending != null && state.refresh.requestId != null &&
            pending.previousRequestId != state.refresh.requestId) {
            refreshRequest.compareAndSet(pending, null)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), EventsUiState())

    init {
        viewModelScope.launch {
            if (!repository.hasCachedFeed()) refresh()
        }
    }

    fun refresh() {
        val state = uiState.value
        if (loadMore.value.loading || state.refresh.status == RefreshStatus.Queued ||
            state.refresh.status == RefreshStatus.Refreshing) return
        loadMore.value = LoadMoreState()
        refreshRequest.value = PendingRefresh(state.refresh.requestId)
        scheduler.refresh()
    }

    fun loadMore() {
        val state = uiState.value
        val pending = refreshRequest.value
        if ((pending != null && pending.previousRequestId == state.refresh.requestId) ||
            loadMore.value.loading || !state.canLoadMore ||
            state.refresh.status == RefreshStatus.Queued ||
            state.refresh.status == RefreshStatus.Refreshing) return
        loadMore.value = LoadMoreState(loading = true)
        viewModelScope.launch {
            try {
                repository.loadNextPage()
                loadMore.value = LoadMoreState()
            } catch (error: CancellationException) {
                loadMore.value = LoadMoreState()
                throw error
            } catch (error: IOException) {
                loadMore.value = LoadMoreState(errorMessage = "Unable to load more events. Check your connection and retry.")
            } catch (error: HttpException) {
                loadMore.value = LoadMoreState(errorMessage = "Unable to load more events (HTTP ${error.code()}). Please retry.")
            } catch (error: Exception) {
                loadMore.value = LoadMoreState(errorMessage = "Unable to read the next page. Please retry.")
            }
        }
    }

    companion object {
        fun Factory(repository: EventsRepository, scheduler: EventsRefreshScheduler): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    EventsViewModel(repository, scheduler) as T
            }
    }
}
