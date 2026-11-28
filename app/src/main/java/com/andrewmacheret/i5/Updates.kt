package com.andrewmacheret.i5

import android.content.Context
import android.net.ConnectivityManager
import java.time.Duration
import java.time.Instant

/**
 * How often the app asks the server on its own. Tapping refresh always asks.
 * "Wi-Fi" means any network Android says isn't metered, so a phone hotspot counts as mobile data.
 */
object Updates {
    val WIFI_CHOICES = listOf(15, 30, 60)
    val MOBILE_CHOICES = listOf(15, 30, 60, 0)

    /** One check is about 30 KB from the server plus about 5 KB to connect */
    private const val KB_PER_CHECK = 35

    fun metered(context: Context): Boolean =
        context.getSystemService(ConnectivityManager::class.java).isActiveNetworkMetered

    /** Null means only when asked */
    fun interval(context: Context, metered: Boolean = metered(context)): Duration? {
        val prefs = Prefs(context)
        val minutes = if (metered) prefs.mobileMinutes else prefs.wifiMinutes
        return if (minutes > 0) Duration.ofMinutes(minutes.toLong()) else null
    }

    /** True if data downloaded at `loadedAt` is old enough to download again on the current network */
    fun due(context: Context, loadedAt: Instant?, now: Instant = Instant.now()): Boolean {
        val every = interval(context) ?: return false
        val age = loadedAt?.let { Duration.between(it, now) } ?: return true
        // Periodic work can run a little early, so don't skip a run over a minute or two
        return age.isNegative || age >= every.multipliedBy(4).dividedBy(5)
    }

    /** Opening the app checks unless it just did. On mobile data it waits for the mobile setting instead. */
    fun dueOnOpen(context: Context, loadedAt: Instant?, now: Instant = Instant.now()): Boolean {
        if (metered(context)) return due(context, loadedAt, now)
        val age = loadedAt?.let { Duration.between(it, now) } ?: return true
        return age.isNegative || age >= Duration.ofMinutes(2)
    }

    fun label(minutes: Int): String = when {
        minutes == 0 -> "Off"
        minutes < 60 -> "$minutes min"
        else -> "${minutes / 60} hr"
    }

    /** Most data a month at this setting, if Android never delays a check */
    fun monthlyMegabytes(minutes: Int): Int =
        if (minutes == 0) 0 else ((24 * 60 / minutes) * 30 * KB_PER_CHECK + 500) / 1000
}
