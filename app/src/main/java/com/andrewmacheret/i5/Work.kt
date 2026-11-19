package com.andrewmacheret.i5

import android.content.Context
import android.util.Log
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val failed = try {
            Lanes.refresh().error != null
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("I5", "refresh failed", e)
            true
        }
        // WorkManager backs off between tries, starting at 30 seconds
        return if (failed && runAttemptCount < 3) Result.retry() else Result.success()
    }
}

object Work {
    private val online = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

    /** Every 15 minutes keeps data well inside the 2 hour freshness limit */
    fun schedulePeriodic(context: Context) {
        val request = PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES)
            .setConstraints(online)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.UPDATE, request)
    }

    /**
     * One refresh as soon as there's a network. WorkManager keeps the app alive for it,
     * unlike a coroutine started from a widget tap. Replacing skips any retry wait.
     */
    fun refreshSoon(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(online).build()
        WorkManager.getInstance(context).enqueueUniqueWork("refresh-now", ExistingWorkPolicy.REPLACE, request)
    }
}
