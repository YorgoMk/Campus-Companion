package com.inci.campuscompanion

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import com.inci.campuscompanion.data.AppDatabase
import com.inci.campuscompanion.data.CachedEvent
import com.inci.campuscompanion.data.CampusApi
import com.inci.campuscompanion.data.EventDto
import com.inci.campuscompanion.data.EventsRemoteDataSource
import com.inci.campuscompanion.data.EventsRepository
import com.inci.campuscompanion.work.RefreshEventsWorker
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType
import okhttp3.ResponseBody
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import retrofit2.HttpException
import retrofit2.Response

@RunWith(AndroidJUnit4::class)
class EventsPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private val saved = CachedEvent("saved", "Saved event", "date", "Campus", 0)

    @Before fun openDatabase() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After fun closeDatabase() { db.close() }

    private fun repository(load: suspend () -> List<EventDto>) = EventsRepository(
        EventsRemoteDataSource(object : CampusApi {
            override suspend fun getEvents() = load()
        }), db.eventsDao()
    )

    private fun worker(repo: EventsRepository): RefreshEventsWorker =
        TestListenableWorkerBuilder<RefreshEventsWorker>(context)
            .setWorkerFactory(object : WorkerFactory() {
                override fun createWorker(context: Context, name: String, params: WorkerParameters): ListenableWorker? =
                    if (name == RefreshEventsWorker::class.java.name) RefreshEventsWorker(context, params, repo) else null
            }).build()

    @Test fun workerSuccessWritesEventsToRoom() = runBlocking {
        val result = worker(repository { listOf(EventDto("1", "Meetup", "date")) }).doWork()
        assertEquals(ListenableWorker.Result.success(), result)
        assertEquals("Meetup", db.eventsDao().observeEvents().first().single().title)
    }

    @Test fun temporaryNetworkFailureRetriesAndRetainsCache() = runBlocking {
        db.eventsDao().replaceEvents(listOf(saved))
        val result = worker(repository { throw IOException("Offline") }).doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
        assertEquals(listOf(saved), db.eventsDao().observeEvents().first())
    }

    @Test fun serverOutageRetries() = runBlocking {
        val result = worker(repository { throw httpError(503) }).doWork()
        assertEquals(ListenableWorker.Result.retry(), result)
    }

    @Test fun rejectedRequestFailsWithUsefulMessage() = runBlocking {
        val result = worker(repository { throw httpError(400) }).doWork()
        assertEquals(
            ListenableWorker.Result.failure(androidx.work.workDataOf(
                RefreshEventsWorker.ERROR_MESSAGE to "The request was rejected (HTTP 400)."
            )), result
        )
    }

    @Test fun invalidDataFailsAndRetainsCache() = runBlocking {
        db.eventsDao().replaceEvents(listOf(saved))
        val result = worker(repository { listOf(EventDto("1", "", "date")) }).doWork()
        assertEquals(ListenableWorker.Result.failure(androidx.work.workDataOf(
            RefreshEventsWorker.ERROR_MESSAGE to "Unable to refresh event data. Please retry."
        )), result)
        assertEquals(listOf(saved), db.eventsDao().observeEvents().first())
    }

    @Test fun cancellationIsNotReportedAsFailureOrSuccess() {
        assertThrows(CancellationException::class.java) {
            runBlocking { worker(repository { throw CancellationException("Stopped") }).doWork() }
        }
    }

    @Test fun emptySuccessfulResponseClearsCache() = runBlocking {
        db.eventsDao().replaceEvents(listOf(saved))
        assertEquals(ListenableWorker.Result.success(), worker(repository { emptyList() }).doWork())
        assertTrue(db.eventsDao().observeEvents().first().isEmpty())
    }

    @Test fun failedCacheTransactionRollsBackDeletion() = runBlocking {
        db.eventsDao().replaceEvents(listOf(saved))
        try {
            db.eventsDao().replaceEvents(listOf(saved, saved))
            throw AssertionError("Expected duplicate primary key")
        } catch (expected: android.database.sqlite.SQLiteConstraintException) {
            assertEquals(listOf(saved), db.eventsDao().observeEvents().first())
        }
    }

    @Test fun eventCacheSurvivesReopeningDatabase() = runBlocking {
        val name = "events-test-${UUID.randomUUID()}"
        val first = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try { first.eventsDao().replaceEvents(listOf(saved)) } finally { first.close() }
        val reopened = Room.databaseBuilder(context, AppDatabase::class.java, name).build()
        try { assertEquals(listOf(saved), reopened.eventsDao().observeEvents().first()) }
        finally { reopened.close(); context.deleteDatabase(name) }
    }

    @Test fun migrationPreservesExistingTasks() = runBlocking {
        val name = "migration-test-${UUID.randomUUID()}"
        context.openOrCreateDatabase(name, Context.MODE_PRIVATE, null).use { old ->
            old.execSQL("CREATE TABLE tasks (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT NOT NULL, description TEXT NOT NULL, isCompleted INTEGER NOT NULL)")
            old.execSQL("INSERT INTO tasks VALUES (1, 'Keep this task', 'Existing data', 0)")
            old.version = 1
        }
        val upgraded = Room.databaseBuilder(context, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_1_2).build()
        try {
            assertEquals("Keep this task", upgraded.taskDao().getTasks().first().single().title)
            assertTrue(upgraded.eventsDao().observeEvents().first().isEmpty())
        } finally { upgraded.close(); context.deleteDatabase(name) }
    }

    private fun httpError(code: Int) = HttpException(
        Response.error<List<EventDto>>(code, ResponseBody.create(MediaType.get("application/json"), "{}"))
    )
}
