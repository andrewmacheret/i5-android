package com.andrewmacheret.i5

import android.app.Application
import kotlinx.coroutines.launch

class I5App : Application() {
    override fun onCreate() {
        super.onCreate()
        Lanes.init(this)
        Notifier.createChannel(this)
        Work.schedulePeriodic(this)
        // Android cancels the app's alarms when exact alarms get turned off, so set the next one on every start
        Lanes.scope.launch { Sync.run(this@I5App) }
    }
}
