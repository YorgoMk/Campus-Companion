package com.inci.campuscompanion.work

import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import kotlinx.coroutines.flow.map

enum class RefreshStatus { Idle, Queued, Refreshing, Current, Failed }

data class RefreshState(
    val status: RefreshStatus = RefreshStatus.Idle,
    val errorMessage: String? = null,
    val requestId: String? = null
)

class EventsRefreshScheduler(private val workManager: WorkManager) {
    val state = workManager.getWorkInfosForUniqueWorkFlow(WORK_NAME).map { infos ->
        val info = infos.firstOrNull { !it.state.isFinished } ?: infos.firstOrNull()
        RefreshState(
            status = when (info?.state) {
                WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> RefreshStatus.Queued
                WorkInfo.State.RUNNING -> RefreshStatus.Refreshing
                WorkInfo.State.SUCCEEDED -> RefreshStatus.Current
                WorkInfo.State.FAILED -> RefreshStatus.Failed
                else -> RefreshStatus.Idle
            },
            errorMessage = info?.outputData?.getString(RefreshEventsWorker.ERROR_MESSAGE),
            requestId = info?.id?.toString()
        )
    }

    fun refresh() = workManager.enqueueUniqueWork(
        WORK_NAME,
        ExistingWorkPolicy.KEEP,
        OneTimeWorkRequestBuilder<RefreshEventsWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .build()
    )

    companion object {
        const val WORK_NAME = "refresh-campus-events"
    }
}
