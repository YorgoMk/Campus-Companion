package com.inci.campuscompanion.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class EventsDao {
    @Query("SELECT * FROM events ORDER BY position")
    abstract fun observeEvents(): Flow<List<CachedEvent>>

    @Query("DELETE FROM events")
    abstract suspend fun deleteEvents()

    @Insert
    abstract suspend fun insertEvents(events: List<CachedEvent>)

    @Transaction
    open suspend fun replaceEvents(events: List<CachedEvent>) {
        deleteEvents()
        insertEvents(events)
    }
}
