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
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val asked = inputData.getBoolean(ASKED, false)
        if (!asked && !Updates.due(applicationContext, Lanes.load().loadedAt)) {
            Log.i("I5", "skipped a check, the data is new enough for this network")
            return Result.success()
        }
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

    companion object {
        /** Someone tapped refresh, so skip the settings check */
        const val ASKED = "asked"
    }
}

object Work {
    private fun network(type: NetworkType) = Constraints.Builder().setRequiredNetworkType(type).build()

    /**
     * One job for Wi-Fi and one for mobile data, each at its own setting.
     * Any of them keeps data inside the 2 hour freshness limit, with room for a missed run.
     */
    fun schedulePeriodic(context: Context) {
        val prefs = Prefs(context)
        val manager = WorkManager.getInstance(context)
        manager.enqueueUniquePeriodicWork("refresh", ExistingPeriodicWorkPolicy.UPDATE, periodic(prefs.wifiMinutes, NetworkType.UNMETERED))
        if (prefs.mobileMinutes > 0) {
            manager.enqueueUniquePeriodicWork("refresh-mobile", ExistingPeriodicWorkPolicy.UPDATE, periodic(prefs.mobileMinutes, NetworkType.METERED))
        } else {
            manager.cancelUniqueWork("refresh-mobile")
        }
    }

    private fun periodic(minutes: Int, type: NetworkType) =
        PeriodicWorkRequestBuilder<RefreshWorker>(minutes.toLong(), TimeUnit.MINUTES).setConstraints(network(type)).build()

    /**
     * One refresh as soon as there's a network, on any network. For taps on refresh.
     * WorkManager keeps the app alive for it, unlike a coroutine started from a widget tap.
     * Replacing skips any retry wait.
     */
    fun refreshNow(context: Context) {
        val request = OneTimeWorkRequestBuilder<RefreshWorker>()
            .setConstraints(network(NetworkType.CONNECTED))
            .setInputData(workDataOf(RefreshWorker.ASKED to true))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork("refresh-now", ExistingWorkPolicy.REPLACE, request)
    }

    /** One refresh soon, if the settings allow one on the network there is. With mobile data off it waits for Wi-Fi. */
    fun refreshSoon(context: Context) {
        val type = if (Prefs(context).mobileMinutes > 0) NetworkType.CONNECTED else NetworkType.UNMETERED
        val request = OneTimeWorkRequestBuilder<RefreshWorker>().setConstraints(network(type)).build()
        WorkManager.getInstance(context).enqueueUniqueWork("refresh-soon", ExistingWorkPolicy.REPLACE, request)
    }
}
