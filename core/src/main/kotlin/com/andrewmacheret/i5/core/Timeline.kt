package com.andrewmacheret.i5.core

import java.time.Instant
import java.time.LocalDate

data class Change(val at: Instant, val to: LaneStatus)

/**
 * Questions about the timeline the server published. No schedule logic lives here.
 * The server works out the segments, so the app only looks things up.
 */
object Timeline {
    private const val HOUR_MS = 60 * 60_000L

    private fun indexAt(p: Published, t: Instant): Int {
        var lo = 0
        var hi = p.segments.size - 1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            val s = p.segments[mid]
            when {
                t < s.start -> hi = mid - 1
                t >= s.end -> lo = mid + 1
                else -> return mid
            }
        }
        return -1
    }

    /** The segment covering a moment */
    fun segmentAt(p: Published, t: Instant): Segment? = p.segments.getOrNull(indexAt(p, t))

    /** Unknown outside the published range */
    fun statusAt(p: Published, t: Instant): LaneStatus = segmentAt(p, t)?.status ?: LaneStatus.UNKNOWN

    fun doubtsAt(p: Published, t: Instant): List<Doubt> = p.doubts.filter { it.start <= t && t < it.end }

    fun doubtsBetween(p: Published, start: Instant, end: Instant): List<Doubt> = p.doubts.filter { it.start < end && it.end > start }

    /** The next few times the status flips, after `from` */
    fun upcomingChanges(p: Published, from: Instant, limit: Int = 5): List<Change> {
        val out = mutableListOf<Change>()
        var current = statusAt(p, from)
        for (s in p.segments) {
            if (s.start <= from) continue
            if (s.status != current) {
                out += Change(s.start, s.status)
                current = s.status
                if (out.size == limit) break
            }
        }
        // Past the end of the published range nothing is known
        if (out.size < limit && p.horizon.end > from && current != LaneStatus.UNKNOWN) out += Change(p.horizon.end, LaneStatus.UNKNOWN)
        return out
    }

    /** The last time the status flipped at or before `t`, or null if it hasn't within the published range */
    fun lastChange(p: Published, t: Instant): Instant? {
        val i = indexAt(p, t)
        if (i < 0) return null
        val status = p.segments[i].status
        for (j in i downTo 1) {
            if (p.segments[j - 1].status != status) return p.segments[j].start
        }
        return null
    }

    /**
     * Colored runs across one Seattle day, for the timeline bar. Placed by wall clock time,
     * so daylight saving days still fit a 24 hour bar. Fall back's repeated hour is drawn once.
     */
    fun daySegments(p: Published, day: LocalDate): List<DaySegment> {
        val start = Seattle.startOfDay(day)
        val end = Seattle.startOfDay(day.plusDays(1))
        val out = mutableListOf<DaySegment>()
        fun push(from: Int, to: Int, status: LaneStatus) {
            if (to <= from) return
            val last = out.lastOrNull()
            val a = if (last != null && last.end.minutes > from) last.end.minutes else from
            if (to <= a) return
            if (last != null && last.status == status && last.end.minutes == a) out[out.lastIndex] = last.copy(end = TimeOfDay(to))
            else out += DaySegment(TimeOfDay(a), TimeOfDay(to), status)
        }
        fun wall(t: Instant) = if (t >= end) 24 * 60 else Seattle.time(t).minutes
        var t = start
        if (start < p.horizon.start) {
            push(0, wall(minOf(p.horizon.start, end)), LaneStatus.UNKNOWN)
            t = p.horizon.start
        }
        for (s in p.segments) {
            if (s.end <= t || s.start >= end) continue
            val a = maxOf(s.start, t)
            val b = minOf(s.end, end)
            // Wall time can jump at a clock change, so walk across it an hour at a time
            var x = a
            while (x < b) {
                val next = minOf(b, Instant.ofEpochMilli((Math.floorDiv(x.toEpochMilli(), HOUR_MS) + 1) * HOUR_MS))
                push(Seattle.time(x).minutes, wall(next).takeIf { it != 0 } ?: (24 * 60), s.status)
                x = next
            }
            t = b
        }
        if (t < end) push(wall(t), 24 * 60, LaneStatus.UNKNOWN)
        return out
    }
}
