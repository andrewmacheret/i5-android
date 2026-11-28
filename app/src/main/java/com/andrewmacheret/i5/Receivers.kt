package com.andrewmacheret.i5

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.andrewmacheret.i5.core.Trust
import com.andrewmacheret.i5.widget.DrawNow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Runs a suspend block without the system killing the receiver halfway. */
private fun BroadcastReceiver.async(block: suspend () -> Unit) {
    val pending = goAsync()
    Lanes.scope.launch {
        try {
            block()
        } finally {
            pending.finish()
        }
    }
}

/** The alarm set by [Alarms]. */
class TickReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        Sync.run(context)
        try {
            DrawNow.all(context)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w("I5", "direct widget draw failed", e)
        }
        // Data just went stale, so try for fresh data right away
        val snap = Lanes.snapshot()
        val stale = Trust.staleAt(snap.status)?.let { !it.isAfter(snap.now) } ?: true
        if (stale && !Lanes.inScenario) Work.refreshSoon(context)
    }
}

/** Boot, app updates and clock changes. */
class SystemEventsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) = async {
        Log.i("I5", "system event: ${intent.action}")
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                Work.schedulePeriodic(context)
                Work.refreshSoon(context)
            }
        }
        Sync.run(context)
    }
}
