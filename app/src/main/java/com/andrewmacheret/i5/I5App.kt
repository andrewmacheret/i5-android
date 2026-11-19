package com.andrewmacheret.i5

import android.app.Application

class I5App : Application() {
    override fun onCreate() {
        super.onCreate()
        Lanes.init(this)
        Notifier.createChannel(this)
        Work.schedulePeriodic(this)
    }
}
