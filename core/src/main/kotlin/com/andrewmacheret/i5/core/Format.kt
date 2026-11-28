package com.andrewmacheret.i5.core

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(Seattle.zone)
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US)
    private val shortDay = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US)

    /** "9:00 PM" in Seattle time */
    fun clock(at: Instant): String = clock.format(at)

    /** "Today", "Tomorrow" or "Thu" */
    fun dayName(at: Instant, now: Instant): String = when (Seattle.daysBetween(Seattle.day(now), Seattle.day(at))) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> weekday.format(Seattle.day(at))
    }

    /** "9:00 PM", "5:00 AM tomorrow", "8:00 AM Sat", or "8:00 AM Sat, Dec 12" once it's a week or more away */
    fun whenText(at: Instant, now: Instant): String {
        val diff = Seattle.daysBetween(Seattle.day(now), Seattle.day(at))
        if (diff >= 7 || diff <= -7) return "${clock(at)} ${shortDate(at)}"
        return when (val day = dayName(at, now)) {
            "Today" -> clock(at)
            "Tomorrow" -> "${clock(at)} tomorrow"
            else -> "${clock(at)} $day"
        }
    }

    /** "Thu, Nov 19" */
    fun shortDay(day: LocalDate): String = shortDay.format(day)

    fun shortDate(at: Instant): String = shortDay(Seattle.day(at))

    /** "4h 5m", "12m", "2d 3h" */
    fun countdown(to: Instant, from: Instant): String {
        val total = maxOf(0L, Math.floorDiv(to.toEpochMilli() - from.toEpochMilli(), 60_000L))
        val d = total / (24 * 60)
        val h = (total / 60) % 24
        val m = total % 60
        return when {
            d > 0 -> if (h > 0) "${d}d ${h}h" else "${d}d"
            h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
            else -> "${maxOf(m, 1)}m"
        }
    }

    /** A number the way JavaScript prints it, so 168.0 is "168" and 168.8 stays "168.8" */
    fun number(n: Double): String = if (n == Math.rint(n) && kotlin.math.abs(n) < 1e15) n.toLong().toString() else n.toString()
}
