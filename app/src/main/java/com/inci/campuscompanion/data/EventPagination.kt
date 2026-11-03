package com.inci.campuscompanion.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "event_pagination")
data class EventPagination(
    @PrimaryKey val id: Int = 1,
    val nextPage: Int? = null,
    val generation: Long = 0
)
