package com.inci.campuscompanion.data

import android.content.Context

object EventsDependencies {
    @Volatile private var repository: EventsRepository? = null

    fun repository(context: Context): EventsRepository = repository ?: synchronized(this) {
        repository ?: EventsRepository(
            EventsRemoteDataSource(CampusApi.create()),
            AppDatabase.getDatabase(context.applicationContext).eventsDao()
        ).also { repository = it }
    }
}
