package com.andrewmacheret.i5

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.andrewmacheret.i5.core.Alerts
import kotlinx.coroutines.launch

/**
 * Debug builds only. Drive the app from adb:
 *
 *   adb shell am broadcast -n com.andrewmacheret.i5/.DebugReceiver --es scenario north
 *   adb shell am broadcast -n com.andrewmacheret.i5/.DebugReceiver --es scenario live
 *   adb shell am broadcast -n com.andrewmacheret.i5/.DebugReceiver --es notify change
 *   adb shell am broadcast -n com.andrewmacheret.i5/.DebugReceiver --ez tick true
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        Lanes.scope.launch {
            try {
                intent.getStringExtra("scenario")?.let { name ->
                    val prefs = Prefs(context)
                    prefs.scenario = name.takeIf { it in Scenarios.names }
                    // Forget what the app saw before, so going back to live doesn't alert about old news
                    if (prefs.scenario == null) prefs.seen = null
                    Log.i("I5", "scenario: ${prefs.scenario ?: "live"}")
                    Sync.run(context)
                }
                // Runs the same code as the lane change alarm
                if (intent.hasExtra("tick")) context.sendBroadcast(Intent(context, TickReceiver::class.java))
                when (intent.getStringExtra("notify")) {
                    "change" -> Notifier.post(context, Alerts.Message("I-5 express lanes now northbound", "Closes at 11:00 PM"))
                    "closed" -> Notifier.post(context, Alerts.Message("I-5 express lanes closed", "Opens southbound at 5:00 AM tomorrow"))
                    "cantconfirm" -> Notifier.post(context, Alerts.Message("Can't confirm the I-5 express lanes", "Thanksgiving may run a holiday schedule"))
                }
            } finally {
                pending.finish()
            }
        }
    }
}
