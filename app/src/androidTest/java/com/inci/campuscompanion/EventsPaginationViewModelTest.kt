package com.inci.campuscompanion

import android.content.Context
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.inci.campuscompanion.data.AppDatabase
import com.inci.campuscompanion.data.CampusApi
import com.inci.campuscompanion.data.EventDto
import com.inci.campuscompanion.data.EventsRemoteDataSource
import com.inci.campuscompanion.data.EventsRepository
import com.inci.campuscompanion.ui.EventsViewModel
import com.inci.campuscompanion.work.EventsRefreshScheduler
import com.inci.campuscompanion.work.RefreshStatus
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EventsPaginationViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: AppDatabase
    private lateinit var workManager: WorkManager
    private lateinit var scheduler: EventsRefreshScheduler

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        workManager = WorkManager.getInstance(context)
        scheduler = EventsRefreshScheduler(workManager)
    }

    @After fun tearDown() {
        workManager.cancelUniqueWork(EventsRefreshScheduler.WORK_NAME).result.get(10, TimeUnit.SECONDS)
        db.close()
    }

    private fun page(number: Int, count: Int = 10) = (1..count).map {
        val id = (number - 1) * 10 + it
        EventDto(id.toString(), "Event $id", "date")
    }

    private fun repository(load: suspend (Int) -> List<EventDto>) = EventsRepository(
        EventsRemoteDataSource(object : CampusApi {
            override suspend fun getEvents(page: Int, limit: Int) = load(page)
        }), db.eventsDao()
    )

    private suspend fun withViewModel(repo: EventsRepository, test: suspend (EventsViewModel) -> Unit) = coroutineScope {
        val store = ViewModelStore()
        val vm = ViewModelProvider(store, EventsViewModel.Factory(repo, scheduler))[EventsViewModel::class.java]
        val collection = launch(Dispatchers.Default) { vm.uiState.collect {} }
        try {
            withTimeout(5000) { vm.uiState.first { it.canLoadMore } }
            test(vm)
        } finally {
            collection.cancel()
            InstrumentationRegistry.getInstrumentation().runOnMainSync { store.clear() }
        }
    }

    @Test fun duplicateClicksDoNotRepeatPendingPageAndFailureCanBeRetried() = runBlocking {
        val started = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val attempts = AtomicInteger()
        val repo = repository {
            if (it == 1) page(1) else {
                if (attempts.incrementAndGet() == 1) {
                    started.complete(Unit)
                    release.await()
                    throw IOException("Offline")
                }
                page(2, 3)
            }
        }
        repo.refreshEvents()
        withViewModel(repo) { vm ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.loadMore(); vm.loadMore() }
            withTimeout(5000) { started.await() }
            assertEquals(1, attempts.get())
            val loading = withTimeout(5000) { vm.uiState.first { it.loadMore.loading } }
            assertEquals(10, loading.events.size)
            release.complete(Unit)
            val failed = withTimeout(5000) { vm.uiState.first { it.loadMore.errorMessage != null } }
            assertEquals(10, failed.events.size)
            assertTrue(failed.canLoadMore)
            assertNotNull(failed.loadMore.errorMessage)
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.loadMore() }
            val loaded = withTimeout(5000) { vm.uiState.first { it.events.size == 13 && !it.loadMore.loading } }
            assertEquals(null, loaded.loadMore.errorMessage)
            assertEquals(2, attempts.get())
        }
    }

    @Test fun queuedRefreshPreventsLoadingAnotherPage() = runBlocking {
        val requests = AtomicInteger()
        val repo = repository { requests.incrementAndGet(); page(it) }
        repo.refreshEvents()
        withViewModel(repo) { vm ->
            InstrumentationRegistry.getInstrumentation().runOnMainSync { vm.refresh(); vm.loadMore() }
            withTimeout(5000) { vm.uiState.first { it.refresh.status == RefreshStatus.Queued } }
            assertEquals(1, requests.get())
            assertEquals(10, vm.uiState.value.events.size)
        }
    }
}
