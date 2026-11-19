package com.andrewmacheret.i5.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class Change(val at: Instant, val to: LaneStatus)

data class Segment(val start: TimeOfDay, val end: TimeOfDay, val status: LaneStatus)

/** Works out the lane status at any moment from the schedule and its exceptions. */
class Engine(val info: ExpressLanesInfo) {
    fun status(at: Instant): LaneStatus {
        if (closure(at) != null) return LaneStatus.CLOSED
        val t = Seattle.time(at)
        return windows(Seattle.day(at)).firstOrNull { it.start <= t && t < it.end }?.status ?: LaneStatus.CLOSED
    }

    fun closure(at: Instant): Closure? = info.closures.firstOrNull { !at.isBefore(it.start) && at.isBefore(it.end) }

    /** The early closing time for a day, if it has a known one */
    fun earlyCloseTime(day: LocalDate): TimeOfDay? {
        val t = info.earlyClose(day)?.time ?: return null
        return if (t == TimeOfDay.MIDNIGHT) TimeOfDay.END_OF_DAY else t
    }

    fun windows(day: LocalDate): List<LaneWindow> {
        val list = if (Seattle.isWeekend(day)) info.schedule.weekend else info.schedule.weekday
        val cut = earlyCloseTime(day) ?: return list
        return list.mapNotNull { w ->
            if (w.start >= cut) null else w.copy(end = minOf(w.end, cut))
        }
    }

    /** The next few times the status flips, looking up to `days` ahead. */
    fun upcomingChanges(from: Instant, limit: Int = 5, days: Int = 8): List<Change> {
        val start = from.truncatedTo(ChronoUnit.MINUTES)
        var current = status(from)
        val out = mutableListOf<Change>()
        for (minute in 1..days * 24 * 60) {
            val at = start.plus(Duration.ofMinutes(minute.toLong()))
            val s = status(at)
            if (s != current) {
                out += Change(at, s)
                current = s
                if (out.size == limit) break
            }
        }
        return out
    }

    /**
     * Colored runs across one Seattle day, for the timeline bar. Steps through real
     * clock time and places each step by its wall time, so daylight saving days line up.
     */
    fun segments(day: LocalDate): List<Segment> {
        val end = Seattle.startOfDay(day.plusDays(1))
        var at = Seattle.startOfDay(day)
        val out = mutableListOf<Segment>()
        while (at.isBefore(end)) {
            val s = status(at)
            val t = Seattle.time(at)
            val stepEnd = TimeOfDay(minOf(t.minutes + 5, 24 * 60))
            val last = out.lastOrNull()
            when {
                last != null && last.status == s && last.end <= t -> out[out.size - 1] = last.copy(end = stepEnd)
                // Fall back repeats an hour. Skip the repeat.
                last != null && last.end > t -> Unit
                else -> out += Segment(t, stepEnd, s)
            }
            at = at.plus(Duration.ofMinutes(5))
        }
        return out
    }

    fun upcomingEarlyCloses(from: Instant, limit: Int = 6): List<EarlyClose> {
        val today = Seattle.day(from)
        val time = Seattle.time(from)
        return info.earlyCloses.filter { e ->
            e.day.isAfter(today) || (e.day == today && (e.time?.let { time < it } ?: true))
        }.take(limit)
    }

    fun upcomingClosures(from: Instant): List<Closure> = info.closures.filter { it.end.isAfter(from) }.sortedBy { it.start }
}
