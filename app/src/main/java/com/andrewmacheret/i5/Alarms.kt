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

    fun scheduleNext(context: Context, snap: Snapshot) {
        val at = nextWake(snap) ?: return
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = PendingIntent.getBroadcast(
            context, 0,
            Intent(context, TickReceiver::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        // A second late so the new minute has started
        val ms = at.toEpochMilli() + 1_000
        val exact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || am.canScheduleExactAlarms()
        try {
            if (exact) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            } else {
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
            }
        } catch (_: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi)
        }
    }

    fun exactAllowed(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() ?: false
    }
}
