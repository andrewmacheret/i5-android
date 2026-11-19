package com.andrewmacheret.i5

import android.content.Context
import android.util.Log
import androidx.glance.appwidget.updateAll
import com.andrewmacheret.i5.core.Alerts
import com.andrewmacheret.i5.widget.LanesWidget
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Runs after anything changes: new data, a lane change, midnight, the clock moving.
 * Sets the next wake up, sends any notification and redraws the widget.
 */
object Sync {
    private val lock = Mutex()

    suspend fun run(context: Context) = lock.withLock {
        guard("sync") {
            Lanes.load()
            val snap = Lanes.snapshot()
            if (!Lanes.inScenario) {
                // First, so a failed notification can't skip the next wake up
                Alarms.scheduleNext(context, snap)
                val prefs = Prefs(context)
                val message = Alerts.decide(prefs.seen, snap)
                if (!Alerts.waiting(snap)) prefs.seen = Alerts.seen(snap)
                if (message != null && prefs.notify) Notifier.post(context, message)
            }
        }
        guard("widget update") {
            Lanes.bump()
            LanesWidget().updateAll(context)
        }
    }

    private suspend fun guard(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("I5", "$what failed", e)
        }
    }
}
