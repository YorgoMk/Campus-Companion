package com.inci.campuscompanion

import com.inci.campuscompanion.data.CachedEvent
import com.inci.campuscompanion.data.EventPagination
import com.inci.campuscompanion.data.EventsDao
import kotlinx.coroutines.flow.MutableStateFlow

internal class MemoryEventsDao : EventsDao() {
    val rows = MutableStateFlow<List<CachedEvent>>(emptyList())
    val pagination = MutableStateFlow<EventPagination?>(null)
    override fun observePagination() = pagination
    override suspend fun getPagination() = pagination.value
    override suspend fun savePagination(pagination: EventPagination) { this.pagination.value = pagination }
    override suspend fun getEventIds() = rows.value.map { it.id }
    override fun observeEvents() = rows
    override suspend fun deleteEvents() { rows.value = emptyList() }
    override suspend fun insertEvents(events: List<CachedEvent>) { rows.value = (rows.value + events).sortedBy { it.position } }
}
