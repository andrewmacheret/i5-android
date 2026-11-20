package com.andrewmacheret.i5

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.andrewmacheret.i5.core.Seattle
import com.andrewmacheret.i5.core.Snapshot
import com.andrewmacheret.i5.core.Trust
import java.time.Instant

/** Wakes the app at the next moment the widget needs to change. */
object Alarms {
    /** The lanes changing, Seattle midnight (new day, new rules) or the data going stale */
    fun nextWake(snap: Snapshot): Instant? {
        val candidates = listOfNotNull(
            snap.next?.at,
            Seattle.startOfDay(snap.todayDay.plusDays(1)),
            Trust.staleAt(snap.result),
        )
        return candidates.filter { it.isAfter(snap.now) }.minOrNull()
    }

    /** Android's shortest window for an alarm that isn't exact */
    private const val WINDOW_MS = 10 * 60 * 1000L

    fun scheduleNext(context: Context, snap: Snapshot) {
        val at = nextWake(snap) ?: return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        // A second late so the new minute has started
        val ms = at.toEpochMilli() + 1_000
        if (exactAllowed(context)) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, tick(context, 0))
                am.cancel(tick(context, 1))
                return
            } catch (_: SecurityException) {
                // Permission taken away just now, so fall through
            }
        }
        // A plain alarm can come up to an hour late.
        // The window keeps it within 10 minutes while the phone is awake.
        // The second alarm still gets through when the phone is idle.
        am.setWindow(AlarmManager.RTC_WAKEUP, ms, WINDOW_MS, tick(context, 0))
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, tick(context, 1))
    }

    private fun tick(context: Context, id: Int) = PendingIntent.getBroadcast(
        context, id,
        Intent(context, TickReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun exactAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }
}
