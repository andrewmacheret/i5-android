package com.andrewmacheret.i5.core

import java.time.Instant
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

object Format {
    private val clock = DateTimeFormatter.ofPattern("h:mm a", Locale.US).withZone(Seattle.zone)
    private val weekday = DateTimeFormatter.ofPattern("EEE", Locale.US).withZone(Seattle.zone)
    private val shortDate = DateTimeFormatter.ofPattern("EEE, MMM d", Locale.US).withZone(Seattle.zone)

    fun clock(at: Instant): String = clock.format(at)

    /** "Today", "Tomorrow" or "Thu" */
    fun dayName(at: Instant, now: Instant): String = when (Seattle.daysBetween(Seattle.day(now), Seattle.day(at))) {
        0L -> "Today"
        1L -> "Tomorrow"
        else -> weekday.format(at)
    }

    /** "9:00 PM", "5:00 AM tomorrow", "8:00 AM Sat" */
    fun whenText(at: Instant, now: Instant): String = when (val day = dayName(at, now)) {
        "Today" -> clock(at)
        "Tomorrow" -> "${clock(at)} tomorrow"
        else -> "${clock(at)} $day"
    }

    fun shortDate(at: Instant): String = shortDate.format(at)

    fun shortDate(day: LocalDate): String = shortDate(Seattle.instant(day, TimeOfDay.of(12)))

    /** "4h 5m", "12m", "2d 3h" */
    fun countdown(to: Instant, from: Instant): String {
        val total = maxOf(0L, (to.epochSecond - from.epochSecond) / 60)
        val d = total / (24 * 60)
        val h = (total / 60) % 24
        val m = total % 60
        return when {
            d > 0 -> if (h > 0) "${d}d ${h}h" else "${d}d"
            h > 0 -> if (m > 0) "${h}h ${m}m" else "${h}h"
            else -> "${maxOf(m, 1)}m"
        }
    }
}
