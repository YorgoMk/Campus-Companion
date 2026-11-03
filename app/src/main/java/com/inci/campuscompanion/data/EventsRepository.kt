package com.inci.campuscompanion.data

import com.inci.campuscompanion.model.CampusEvent
import kotlinx.coroutines.flow.map

class EventsRepository(
    private val remote: EventsRemoteDataSource,
    private val cache: EventsDao
) {
    val events = cache.observeEvents().map { rows ->
        rows.map { CampusEvent(it.id, it.title, it.timeLabel, it.locationLabel) }
    }

    suspend fun getEvents(): List<CampusEvent> = remote.getEvents().map { dto ->
        require(dto.id.isNotBlank() && dto.name.isNotBlank() && dto.startingDate.isNotBlank()) {
            "Invalid event data"
        }
        CampusEvent(
            id = dto.id,
            title = dto.name,
            timeLabel = dto.startingDate,
            locationLabel = dto.address ?: "TBA"
        )
    }

    suspend fun refreshEvents() {
        val events = getEvents()
        require(events.map { it.id }.distinct().size == events.size) { "Duplicate event IDs" }
        cache.replaceEvents(events.mapIndexed { index, event ->
            CachedEvent(event.id, event.title, event.timeLabel, event.locationLabel, index)
        })
    }
}
