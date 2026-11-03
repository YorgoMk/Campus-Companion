package com.inci.campuscompanion.work

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.inci.campuscompanion.data.EventsDependencies
import com.inci.campuscompanion.data.EventsRepository
import java.io.IOException
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

class RefreshEventsWorker(
    appContext: Context,
    params: WorkerParameters,
    private val repository: EventsRepository
) : CoroutineWorker(appContext, params) {
    constructor(appContext: Context, params: WorkerParameters) : this(
        appContext, params, EventsDependencies.repository(appContext)
    )

    override suspend fun doWork(): Result = try {
        repository.refreshEvents()
        Result.success()
    } catch (error: CancellationException) {
        throw error
    } catch (error: IOException) {
        Result.retry()
    } catch (error: HttpException) {
        if (error.code() in 500..599 || error.code() == 408 || error.code() == 429) {
            Result.retry()
        } else {
            Result.failure(workDataOf(ERROR_MESSAGE to "The request was rejected (HTTP ${error.code()})."))
        }
    } catch (error: Exception) {
        Log.e("RefreshEventsWorker", "Unable to refresh events", error)
        Result.failure(workDataOf(ERROR_MESSAGE to "Unable to refresh event data. Please retry."))
    }

    companion object {
        const val ERROR_MESSAGE = "refresh-error"
    }
}
