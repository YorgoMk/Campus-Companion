package com.inci.campuscompanion

import com.inci.campuscompanion.data.CampusApi
import com.inci.campuscompanion.data.EventDto
import com.inci.campuscompanion.data.EventsRemoteDataSource
import com.inci.campuscompanion.data.EventsRepository
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EventsPaginationTest {
    private val cache = MemoryEventsDao()
    private val requests = mutableListOf<Pair<Int, Int>>()
    private fun page(number: Int, count: Int = 10) = (1..count).map {
        val id = (number - 1) * 10 + it
        EventDto(id.toString(), "Event $id", "date", "Campus")
    }
    private fun repository(load: suspend (Int) -> List<EventDto>) = EventsRepository(
        EventsRemoteDataSource(object : CampusApi {
            override suspend fun getEvents(page: Int, limit: Int): List<EventDto> {
                requests += page to limit
                return load(page)
            }
        }), cache
    )

    @Test fun refreshAndAppendUseCorrectPageAndLimit() = runBlocking {
        val repo = repository { page(it) }
        repo.refreshEvents()
        assertEquals(2, cache.getPagination()?.nextPage)
        repo.loadNextPage()
        assertEquals(listOf(1 to 10, 2 to 10), requests)
        assertEquals((1..20).map { it.toString() }, repo.events.first().map { it.id })
        assertEquals(3, cache.getPagination()?.nextPage)
    }

    @Test fun shortLastPageEndsPagination() = runBlocking {
        val repo = repository { if (it == 1) page(1) else page(2, 3) }
        repo.refreshEvents(); repo.loadNextPage(); repo.loadNextPage()
        assertEquals(13, repo.events.first().size)
        assertFalse(repo.feed.first().canLoadMore)
        assertEquals(2, requests.size)
    }

    @Test fun emptyLastPageKeepsExistingEventsAndMarksEnd() = runBlocking {
        val repo = repository { if (it == 1) page(1) else emptyList() }
        repo.refreshEvents(); repo.loadNextPage(); repo.loadNextPage()
        assertEquals(10, repo.events.first().size)
        assertFalse(repo.feed.first().canLoadMore)
        assertEquals(2, requests.size)
    }

    @Test fun failedAppendDoesNotAdvanceCursorAndRetryRequestsSamePage() = runBlocking {
        var failed = false
        val repo = repository {
            if (it == 1) page(1) else if (!failed) { failed = true; throw IOException("Offline") }
            else page(2, 3)
        }
        repo.refreshEvents()
        try { repo.loadNextPage(); throw AssertionError("Expected failure") }
        catch (expected: IOException) {
            assertEquals(10, repo.events.first().size)
            assertEquals(2, cache.getPagination()?.nextPage)
            assertTrue(repo.feed.first().canLoadMore)
        }
        repo.loadNextPage()
        assertEquals(listOf(1 to 10, 2 to 10, 2 to 10), requests)
        assertEquals(13, repo.events.first().size)
    }

    @Test fun refreshResetsLoadedPagesAndCursor() = runBlocking {
        val repo = repository { page(it) }
        repo.refreshEvents(); repo.loadNextPage(); repo.refreshEvents()
        assertEquals(10, repo.events.first().size)
        assertEquals(2, cache.getPagination()?.nextPage)
        assertEquals(2L, cache.getPagination()?.generation)
        assertEquals(listOf(1 to 10, 2 to 10, 1 to 10), requests)
    }

    @Test fun overlappingPagesDoNotDuplicateEvents() = runBlocking {
        val repo = repository { if (it == 1) page(1) else listOf(page(1).last()) + page(2, 9) }
        repo.refreshEvents(); repo.loadNextPage()
        val ids = repo.events.first().map { it.id }
        assertEquals(19, ids.size)
        assertEquals(ids.distinct(), ids)
    }

    @Test fun repeatedFullPageStopsInsteadOfLoopingForever() = runBlocking {
        val repo = repository { page(1) }
        repo.refreshEvents(); repo.loadNextPage(); repo.loadNextPage()
        assertEquals(10, repo.events.first().size)
        assertFalse(repo.feed.first().canLoadMore)
        assertEquals(2, requests.size)
    }

    @Test fun newRepositoryResumesSavedCursor() = runBlocking {
        repository { page(it) }.refreshEvents()
        val reopened = repository { page(it, 2) }
        assertTrue(reopened.hasCachedFeed())
        reopened.loadNextPage()
        assertEquals(listOf(1 to 10, 2 to 10), requests)
        assertEquals(12, reopened.events.first().size)
    }

    @Test fun refreshAndAppendAreSerialized() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val repo = repository {
            if (it == 2) { started.complete(Unit); release.await() }
            page(it)
        }
        repo.refreshEvents()
        val append = async { repo.loadNextPage() }
        started.await()
        val refresh = async { repo.refreshEvents() }
        release.complete(Unit)
        append.await(); refresh.await()
        assertEquals(10, repo.events.first().size)
        assertEquals(2, cache.getPagination()?.nextPage)
        assertEquals(2L, cache.getPagination()?.generation)
    }

    @Test fun oversizedPageDoesNotChangeCacheOrCursor() = runBlocking {
        val repo = repository { if (it == 1) page(1) else page(2, 11) }
        repo.refreshEvents()
        try { repo.loadNextPage(); throw AssertionError("Expected invalid page") }
        catch (expected: IllegalArgumentException) {
            assertEquals(10, repo.events.first().size)
            assertEquals(2, cache.getPagination()?.nextPage)
        }
    }
}
