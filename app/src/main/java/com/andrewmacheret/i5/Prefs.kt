package com.andrewmacheret.i5

import android.content.Context
import androidx.core.content.edit
import com.andrewmacheret.i5.core.Alerts
import com.andrewmacheret.i5.core.Assessment
import com.andrewmacheret.i5.core.LaneStatus
import java.time.Instant

/** Small settings and state that must survive the app being killed. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("i5", Context.MODE_PRIVATE)

    var notify: Boolean
        get() = sp.getBoolean("notify", false)
        set(v) = sp.edit { putBoolean("notify", v) }

    /** What the last sync saw, so the next one can tell what changed */
    var seen: Alerts.Seen?
        get() {
            val verdict = sp.getString("seenVerdict", null) ?: return null
            val status = sp.getString("seenStatus", null) ?: return null
            return runCatching {
                Alerts.Seen(
                    Assessment.Verdict.valueOf(verdict),
                    LaneStatus.valueOf(status),
                    sp.getLong("seenNextAt", 0).takeIf { it > 0 }?.let(Instant::ofEpochMilli),
                )
            }.getOrNull()
        }
        set(v) = sp.edit {
            if (v == null) {
                remove("seenVerdict")
                remove("seenStatus")
                remove("seenNextAt")
            } else {
                putString("seenVerdict", v.verdict.name)
                putString("seenStatus", v.scheduled.name)
                putLong("seenNextAt", v.nextAt?.toEpochMilli() ?: 0)
            }
        }

    /** Debug builds only. Pins the app to a made up moment for screenshots. */
    var scenario: String?
        get() = sp.getString("scenario", null)
        set(v) = sp.edit { if (v == null) remove("scenario") else putString("scenario", v) }
}
