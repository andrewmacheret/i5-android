package com.andrewmacheret.i5.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

/**
 * Holidays when WSDOT may run the lanes differently. WSDOT's page doesn't list
 * holiday schedules, so on these days the app says it can't confirm.
 */
object Holidays {
    fun name(day: LocalDate): String? {
        val y = day.year
        val thanksgiving = nth(4, DayOfWeek.THURSDAY, 11, y)
        val list = listOf(
            observed(LocalDate.of(y, 1, 1)) to "New Year's Day",
            nth(3, DayOfWeek.MONDAY, 1, y) to "Martin Luther King Jr. Day",
            nth(3, DayOfWeek.MONDAY, 2, y) to "Presidents Day",
            LocalDate.of(y, 5, 1).with(TemporalAdjusters.lastInMonth(DayOfWeek.MONDAY)) to "Memorial Day",
            observed(LocalDate.of(y, 6, 19)) to "Juneteenth",
            observed(LocalDate.of(y, 7, 4)) to "Independence Day",
            nth(1, DayOfWeek.MONDAY, 9, y) to "Labor Day",
            observed(LocalDate.of(y, 11, 11)) to "Veterans Day",
            thanksgiving to "Thanksgiving",
            thanksgiving.plusDays(1) to "Day after Thanksgiving",
            LocalDate.of(y, 12, 24) to "Christmas Eve",
            observed(LocalDate.of(y, 12, 25)) to "Christmas",
            LocalDate.of(y, 12, 31) to "New Year's Eve",
            // The real date too, when the holiday is observed on another day
            LocalDate.of(y, 1, 1) to "New Year's Day",
            LocalDate.of(y, 6, 19) to "Juneteenth",
            LocalDate.of(y, 7, 4) to "Independence Day",
            LocalDate.of(y, 11, 11) to "Veterans Day",
            LocalDate.of(y, 12, 25) to "Christmas",
        )
        // Next year's New Year's Day can be observed on Dec 31 of this year
        if (day == observed(LocalDate.of(y + 1, 1, 1))) return "New Year's Day"
        return list.firstOrNull { it.first == day }?.second
    }

    private fun nth(n: Int, dow: DayOfWeek, month: Int, year: Int): LocalDate =
        LocalDate.of(year, month, 1).with(TemporalAdjusters.dayOfWeekInMonth(n, dow))

    /** Saturday holidays move to Friday, Sunday ones to Monday */
    private fun observed(day: LocalDate): LocalDate = when (day.dayOfWeek) {
        DayOfWeek.SATURDAY -> day.minusDays(1)
        DayOfWeek.SUNDAY -> day.plusDays(1)
        else -> day
    }
}
