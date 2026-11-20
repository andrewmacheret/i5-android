package com.andrewmacheret.i5.core

import java.time.Instant
import java.time.LocalDate

enum class LaneStatus(val title: String, val letter: String) {
    NORTHBOUND("Northbound", "N"),
    SOUTHBOUND("Southbound", "S"),
    CLOSED("Closed", "✕"),
    UNKNOWN("Unknown", "?");

    val isOpen: Boolean get() = this == NORTHBOUND || this == SOUTHBOUND
}

/** Minutes since midnight, 0..1440. Use `TimeOfDay.of(hour, minute)` for clock times. */
data class TimeOfDay(val minutes: Int) : Comparable<TimeOfDay> {
    override fun compareTo(other: TimeOfDay) = minutes.compareTo(other.minutes)

    /** "5 AM", "11:15 AM", "Midnight" */
    val label: String
        get() {
            val m = minutes % (24 * 60)
            if (m == 0) return "Midnight"
            if (m == 12 * 60) return "Noon"
            val h = m / 60
            val mm = m % 60
            val h12 = if (h % 12 == 0) 12 else h % 12
            val ampm = if (h < 12) "AM" else "PM"
            return if (mm == 0) "$h12 $ampm" else "%d:%02d %s".format(h12, mm, ampm)
        }

    companion object {
        val MIDNIGHT = TimeOfDay(0)
        val END_OF_DAY = TimeOfDay(24 * 60)
        fun of(hour: Int, minute: Int = 0): TimeOfDay {
            require(hour in 0..24 && minute in 0..59)
            return TimeOfDay(hour * 60 + minute)
        }
    }
}

/** An open window. Any time not covered by a window is closed. */
data class LaneWindow(val status: LaneStatus, val start: TimeOfDay, val end: TimeOfDay)

data class WeeklySchedule(val weekday: List<LaneWindow>, val weekend: List<LaneWindow>) {
    companion object {
        /** The schedule WSDOT posted as of Nov 2026. Used when the page can't be read. */
        val BUILT_IN = WeeklySchedule(
            weekday = listOf(
                LaneWindow(LaneStatus.SOUTHBOUND, TimeOfDay.of(5), TimeOfDay.of(11)),
                LaneWindow(LaneStatus.NORTHBOUND, TimeOfDay.of(11, 15), TimeOfDay.of(23)),
            ),
            weekend = listOf(
                LaneWindow(LaneStatus.SOUTHBOUND, TimeOfDay.of(8), TimeOfDay.of(13, 30)),
                LaneWindow(LaneStatus.NORTHBOUND, TimeOfDay.of(13, 45), TimeOfDay.of(23)),
            ),
        )
    }
}

/** A full closure over a date range, like a weekend-long closure. */
data class Closure(val title: String, val start: Instant, val end: Instant)

/** The days something applies to. `last == null` means it has no end date. */
data class DayRange(val first: LocalDate, val last: LocalDate?) {
    fun contains(day: LocalDate) = !day.isBefore(first) && (last == null || !day.isAfter(last))
    fun isOverBefore(today: LocalDate) = last != null && last.isBefore(today)
}

data class SpecialEvent(
    val title: String,
    val detail: String,
    val link: String?,
    /** Days the event mentions. Null when it has no dates we could read. */
    val days: DayRange? = null,
    /** Set when we could read exact start and end times for a full closure. */
    val closure: Closure? = null,
)

data class Advisory(val text: String, val link: String?, val days: DayRange?) {
    /** True when the notice only talks about days that are already over. */
    fun isOverBefore(today: LocalDate) = days?.isOverBefore(today) ?: false
}

/** `time` is null when the page didn't say what time. */
data class EarlyClose(val day: LocalDate, val time: TimeOfDay?)

data class ExpressLanesInfo(
    val schedule: WeeklySchedule = WeeklySchedule.BUILT_IN,
    /** False when the page's schedule couldn't be read and the built-in one is used. */
    val scheduleFromPage: Boolean = false,
    val earlyCloses: List<EarlyClose> = emptyList(),
    val events: List<SpecialEvent> = emptyList(),
    val advisories: List<Advisory> = emptyList(),
    /** Bits of the page that looked important but couldn't be read */
    val warnings: List<String> = emptyList(),
) {
    val closures: List<Closure> get() = events.mapNotNull { it.closure }

    fun earlyClose(day: LocalDate) = earlyCloses.firstOrNull { it.day == day }

    companion object {
        val BUILT_IN = ExpressLanesInfo()
    }
}

object Sources {
    const val EXPRESS_LANES = "https://wsdot.wa.gov/travel/roads-bridges/express-lanes"
    const val REVIVE_I5 = "https://wsdot.wa.gov/construction-planning/search-projects/revive-i-5-preserving-vital-freeway"
    const val TRAVEL_MAP = "https://wsdot.com/travel/real-time/map/"
    const val WSDOT_TRAFFIC_X = "https://x.com/wsdot_traffic"
    const val PRIVACY = "https://github.com/andrewmacheret/i5-android/blob/main/PRIVACY.md"
}
