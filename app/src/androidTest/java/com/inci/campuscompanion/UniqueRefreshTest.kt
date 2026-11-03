package com.inci.campuscompanion

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.NetworkType
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.testing.WorkManagerTestInitHelper
import com.inci.campuscompanion.work.EventsRefreshScheduler
import com.inci.campuscompanion.work.RefreshStatus
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class UniqueRefreshTest {
    @Test fun repeatedRefreshKeepsOneConstrainedQueuedRequest() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        val workManager = WorkManager.getInstance(context)
        val scheduler = EventsRefreshScheduler(workManager)
        try {
            scheduler.refresh().result.get(10, TimeUnit.SECONDS)
            scheduler.refresh().result.get(10, TimeUnit.SECONDS)
            val requests = workManager.getWorkInfosForUniqueWork(EventsRefreshScheduler.WORK_NAME)
                .get(10, TimeUnit.SECONDS)
            assertEquals(1, requests.size)
            assertEquals(WorkInfo.State.ENQUEUED, requests.single().state)
            assertEquals(NetworkType.CONNECTED, requests.single().constraints.requiredNetworkType)
            assertEquals(RefreshStatus.Queued, scheduler.state.first().status)
        } finally {
            workManager.cancelUniqueWork(EventsRefreshScheduler.WORK_NAME).result.get(10, TimeUnit.SECONDS)
        }
    }
}
