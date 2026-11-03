package com.inci.campuscompanion

import com.inci.campuscompanion.data.CachedEvent
import com.inci.campuscompanion.data.EventsDao
import com.inci.campuscompanion.data.EventPagination
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import com.inci.campuscompanion.data.CampusApi
import com.inci.campuscompanion.data.EventDto
import com.inci.campuscompanion.data.EventsRemoteDataSource
import com.inci.campuscompanion.data.EventsRepository
import java.io.IOException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EventsRepositoryTest {
    private fun repository(load: suspend () -> List<EventDto>) = EventsRepository(
        EventsRemoteDataSource(object : CampusApi {
            override suspend fun getEvents(page: Int, limit: Int) = load()
        }),
        MemoryEventsDao()
    )


    @Test fun refreshStoresMappedEventsInApiOrder() = runBlocking {
        val cache = MemoryEventsDao()
        val api = object : CampusApi {
            override suspend fun getEvents(page: Int, limit: Int) = listOf(
                EventDto("2", "Second ID first", "date", "Campus"),
                EventDto("1", "First ID second", "date")
            )
        }
        val repo = EventsRepository(EventsRemoteDataSource(api), cache)
        repo.refreshEvents()
        assertEquals(listOf("2", "1"), repo.events.first().map { it.id })
        assertEquals(listOf(0, 1), cache.rows.value.map { it.position })
        assertEquals("TBA", repo.events.first().last().locationLabel)
    }

    @Test fun failedRefreshPreservesCachedContent() = runBlocking {
        val cache = MemoryEventsDao()
        val old = listOf(CachedEvent("1", "Saved event", "date", "Campus", 0))
        cache.rows.value = old
        val api = object : CampusApi {
            override suspend fun getEvents(page: Int, limit: Int): List<EventDto> = throw IOException("Offline")
        }
        try {
            EventsRepository(EventsRemoteDataSource(api), cache).refreshEvents()
            throw AssertionError("Expected connection failure")
        } catch (expected: IOException) {
            assertEquals(old, cache.rows.value)
        }
    }

    @Test fun emptySuccessfulRefreshClearsCache() = runBlocking {
        val cache = MemoryEventsDao()
        cache.rows.value = listOf(CachedEvent("1", "Old event", "date", "Campus", 0))
        val api = object : CampusApi { override suspend fun getEvents(page: Int, limit: Int) = emptyList<EventDto>() }
        val repo = EventsRepository(EventsRemoteDataSource(api), cache)
        repo.refreshEvents()
        assertTrue(repo.events.first().isEmpty())
    }

    @Test fun duplicateIdsDoNotReplaceValidCache() = runBlocking {
        val cache = MemoryEventsDao()
        val old = listOf(CachedEvent("old", "Saved event", "date", "Campus", 0))
        cache.rows.value = old
        val api = object : CampusApi {
            override suspend fun getEvents(page: Int, limit: Int) = listOf(
                EventDto("1", "First", "date"), EventDto("1", "Duplicate", "date")
            )
        }
        try {
            EventsRepository(EventsRemoteDataSource(api), cache).refreshEvents()
            throw AssertionError("Expected invalid data failure")
        } catch (expected: IllegalArgumentException) {
            assertEquals(old, cache.rows.value)
        }
    }

    @Test fun decodesActualApiFieldNamesAndIgnoresUnusedFields() {
        val dto = Json { ignoreUnknownKeys = true }.decodeFromString<EventDto>(
            """{"id":"1","name":"Meetup","startingDate":"2026-10-20","address":"Campus","avatar":"unused"}"""
        )
        assertEquals("Meetup", dto.name)
        assertEquals("Campus", dto.address)
    }

    @Test(expected = SerializationException::class)
    fun rejectsMissingRequiredApiFields() {
        Json.decodeFromString<EventDto>("""{"id":"1"}""")
    }

    @Test fun mapsApiFieldsAndMissingLocation() = runBlocking {
        val event = repository {
            listOf(EventDto("1", "Campus meetup", "2026-10-20T12:00:00Z"))
        }.getEvents().single()
        assertEquals("1", event.id)
        assertEquals("Campus meetup", event.title)
        assertEquals("2026-10-20T12:00:00Z", event.timeLabel)
        assertEquals("TBA", event.locationLabel)
    }

    @Test fun preservesEmptyResponse() = runBlocking {
        assertTrue(repository { emptyList() }.getEvents().isEmpty())
    }

    @Test fun connectionFailureIsNotEmptyContent() = runBlocking {
        try {
            repository { throw IOException("Offline") }.getEvents()
            throw AssertionError("Expected connection failure")
        } catch (expected: IOException) {
            assertEquals("Offline", expected.message)
        }
    }

    @Test fun rejectsInvalidEventData() = runBlocking {
        try {
            repository { listOf(EventDto("1", "", "date")) }.getEvents()
            throw AssertionError("Expected invalid data failure")
        } catch (expected: IllegalArgumentException) {
            assertEquals("Invalid event data", expected.message)
        }
    }
}
