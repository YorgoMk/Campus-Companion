package com.inci.campuscompanion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EventsDao {
    @Query("SELECT * FROM events ORDER BY position")
    abstract fun observeEvents(): Flow<List<CachedEvent>>

    @Query("SELECT id FROM events")
    abstract suspend fun getEventIds(): List<String>

    @Query("SELECT * FROM event_pagination WHERE id = 1")
    abstract fun observePagination(): Flow<EventPagination?>

    @Query("SELECT * FROM event_pagination WHERE id = 1")
    abstract suspend fun getPagination(): EventPagination?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract suspend fun savePagination(pagination: EventPagination)

    @Query("DELETE FROM events")
    abstract suspend fun deleteEvents()

    @Insert
    abstract suspend fun insertEvents(events: List<CachedEvent>)

    @Transaction
    open suspend fun replaceEvents(events: List<CachedEvent>, nextPage: Int? = null) {
        val generation = (getPagination()?.generation ?: 0) + 1
        deleteEvents()
        insertEvents(events)
        savePagination(EventPagination(nextPage = nextPage, generation = generation))
    }

    @Transaction
    open suspend fun appendPage(
        events: List<CachedEvent>,
        expected: EventPagination,
        hasAnotherPage: Boolean
    ) {
        if (getPagination() != expected) return
        val existingIds = getEventIds().toSet()
        val newEvents = events.filterNot { it.id in existingIds }
        insertEvents(newEvents)
        savePagination(expected.copy(
            nextPage = if (hasAnotherPage && newEvents.isNotEmpty()) {
                requireNotNull(expected.nextPage) + 1
            } else null
        ))
    }
}
