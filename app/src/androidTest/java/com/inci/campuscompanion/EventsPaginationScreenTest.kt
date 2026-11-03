package com.inci.campuscompanion

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.inci.campuscompanion.model.CampusEvent
import com.inci.campuscompanion.ui.EventsScreen
import com.inci.campuscompanion.ui.EventsUiState
import com.inci.campuscompanion.ui.LoadMoreState
import com.inci.campuscompanion.ui.theme.CampusCompanionTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class EventsPaginationScreenTest {
    @get:Rule val compose = createComposeRule()
    private val cached = listOf(CampusEvent("1", "Cached event", "date", "Campus"))

    private fun show(state: EventsUiState, onLoadMore: () -> Unit = {}) {
        compose.setContent {
            CampusCompanionTheme {
                EventsScreen(state, onRefresh = {}, onLoadMore = onLoadMore, onBack = {})
            }
        }
    }

    @Test fun loadMoreButtonReportsIntent() {
        var clicks = 0
        show(EventsUiState(events = cached, canLoadMore = true)) { clicks++ }
        compose.onNodeWithText("Load more").performClick()
        assertEquals(1, clicks)
    }

    @Test fun loadingMoreKeepsCachedEventsVisible() {
        show(EventsUiState(events = cached, canLoadMore = true, loadMore = LoadMoreState(loading = true)))
        compose.onNodeWithText("Cached event").assertIsDisplayed()
        compose.onNodeWithText("Loading more events...").assertIsDisplayed()
        compose.onNodeWithText("Load more").assertDoesNotExist()
    }

    @Test fun failedPageOffersRetryWithoutReplacingContent() {
        var retries = 0
        show(EventsUiState(events = cached, canLoadMore = true,
            loadMore = LoadMoreState(errorMessage = "Page failed"))) { retries++ }
        compose.onNodeWithText("Cached event").assertIsDisplayed()
        compose.onNodeWithText("Page failed").assertIsDisplayed()
        compose.onNodeWithText("Retry loading more").performClick()
        assertEquals(1, retries)
    }

    @Test fun completedFeedShowsEndInsteadOfLoadMore() {
        show(EventsUiState(events = cached))
        compose.onNodeWithText("All events loaded.").assertIsDisplayed()
        compose.onNodeWithText("Load more").assertDoesNotExist()
    }
}
