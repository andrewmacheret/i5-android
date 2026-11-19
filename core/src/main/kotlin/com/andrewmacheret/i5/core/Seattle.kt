package com.andrewmacheret.i5.core

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Seattle time helpers. The lanes run on Seattle time no matter where the phone is. */
object Seattle {
    val zone: ZoneId = ZoneId.of("America/Los_Angeles")

    fun day(at: Instant): LocalDate = at.atZone(zone).toLocalDate()

    fun time(at: Instant): TimeOfDay {
        val z = at.atZone(zone)
        return TimeOfDay.of(z.hour, z.minute)
    }

    /** The instant a Seattle wall clock shows `time` on `day`. END_OF_DAY means the next midnight. */
    fun instant(day: LocalDate, time: TimeOfDay): Instant {
        if (time.minutes >= 24 * 60) return startOfDay(day.plusDays(1))
        return day.atTime(LocalTime.of(time.minutes / 60, time.minutes % 60)).atZone(zone).toInstant()
    }

    fun startOfDay(day: LocalDate): Instant = day.atStartOfDay(zone).toInstant()

    fun isWeekend(day: LocalDate) = day.dayOfWeek == DayOfWeek.SATURDAY || day.dayOfWeek == DayOfWeek.SUNDAY

    fun daysBetween(a: LocalDate, b: LocalDate) = ChronoUnit.DAYS.between(a, b)
}
