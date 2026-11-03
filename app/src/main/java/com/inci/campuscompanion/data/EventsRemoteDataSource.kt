package com.inci.campuscompanion.data

class EventsRemoteDataSource(private val api: CampusApi) {
    suspend fun getEvents(page: Int, pageSize: Int): List<EventDto> = api.getEvents(page, pageSize)
}
