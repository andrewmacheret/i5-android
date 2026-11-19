package com.andrewmacheret.i5.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Everything the widget and detail screen show, worked out once. */
data class Snapshot(
    val now: Instant,
    val result: FetchResult,
    val check: Assessment,
    val changes: List<Change>,
    val today: List<Segment>,
    val tomorrow: List<Segment>,
    val tomorrowConcerns: List<Concern>,
    /** Days in the next week that may not follow the schedule */
    val doubtfulDays: Set<LocalDate>,
    val activeClosure: Closure?,
    val earlyCloseTonight: TimeOfDay?,
) {
    val next: Change? get() = changes.firstOrNull()
    val todayDay: LocalDate get() = Seattle.day(now)
    val engine: Engine get() = Engine(result.info)

    /** "Closes at 9:00 PM", "Opens southbound at 5:00 AM tomorrow" */
    val nextText: String
        get() {
            val n = next ?: return "No changes in the next week"
            return "${verb(check.scheduled, n.to)} at ${Format.whenText(n.at, now)}"
        }

    /** Holidays in the next two weeks */
    val upcomingHolidays: List<Pair<LocalDate, String>>
        get() = (0L..14L).mapNotNull { offset ->
            val d = todayDay.plusDays(offset)
            Holidays.name(d)?.let { d to it }
        }

    companion object {
        fun make(result: FetchResult, now: Instant, refreshing: Boolean = false): Snapshot {
            val engine = Engine(result.info)
            val today = Seattle.day(now)
            val tomorrow = today.plusDays(1)
            val doubtful = (0L..7L).map { today.plusDays(it) }
                .filter { Trust.dayConcerns(result.info, it, today).isNotEmpty() }
                .toSet()
            return Snapshot(
                now = now,
                result = result,
                check = Trust.assess(result, now, refreshing),
                changes = engine.upcomingChanges(now, limit = 4),
                today = engine.segments(today),
                tomorrow = engine.segments(tomorrow),
                tomorrowConcerns = Trust.dayConcerns(result.info, tomorrow, today),
                doubtfulDays = doubtful,
                activeClosure = engine.closure(now),
                earlyCloseTonight = engine.earlyCloseTime(today),
            )
        }

        fun verb(current: LaneStatus, to: LaneStatus): String = when {
            to == LaneStatus.CLOSED -> "Closes"
            current == LaneStatus.CLOSED -> "Opens ${to.title.lowercase()}"
            else -> "Switches ${to.title.lowercase()}"
        }
    }
}

/** Decides when to send a notification. Based on the Mac app's rules, with [waiting] added. */
object Alerts {
    data class Message(val title: String, val body: String)

    /** What the app last saw, saved between runs so it can spot a change */
    data class Seen(val verdict: Assessment.Verdict, val scheduled: LaneStatus, val nextAt: Instant?)

    /** A change older than this happened while the phone was asleep. Don't report old news. */
    val LATE_LIMIT: Duration = Duration.ofMinutes(10)

    /** Closed gaps shorter than this, like the 15 minute weekday switch, aren't worth a buzz */
    val SHORT_GAP: Duration = Duration.ofMinutes(30)

    fun seen(snap: Snapshot) = Seen(snap.check.verdict, snap.check.scheduled, snap.next?.at)

    /**
     * True when it's too soon to judge, so nothing is sent and the last [Seen] is kept:
     * - a refresh is running, or every problem is old data that a refresh should fix
     *   (this also keeps it quiet at boot and overnight when the network is asleep)
     * - the posted schedule says closed, so a "can't confirm" can wait until the lanes
     *   would open instead of buzzing at midnight
     */
    fun waiting(snap: Snapshot): Boolean {
        val c = snap.check
        return when (c.verdict) {
            Assessment.Verdict.CONFIDENT -> false
            Assessment.Verdict.CHECKING -> true
            Assessment.Verdict.UNCERTAIN -> c.concerns.all { it.fixedByRefresh } || c.scheduled == LaneStatus.CLOSED
        }
    }

    fun decide(old: Seen?, snap: Snapshot): Message? {
        if (old == null || waiting(snap)) return null
        val new = snap.check
        if (old.verdict == Assessment.Verdict.CONFIDENT && new.verdict == Assessment.Verdict.UNCERTAIN) {
            return cantConfirm(new)
        }
        if (old.verdict != Assessment.Verdict.CONFIDENT || new.verdict != Assessment.Verdict.CONFIDENT) return null
        if (old.scheduled == new.scheduled) return null
        val expected = old.nextAt ?: return null
        if (Duration.between(expected, snap.now) > LATE_LIMIT) return null
        val next = snap.next
        if (new.scheduled == LaneStatus.CLOSED && next != null && Duration.between(snap.now, next.at) < SHORT_GAP) return null
        return change(new.scheduled, next, snap.now)
    }

    fun change(status: LaneStatus, next: Change?, now: Instant): Message {
        val title = if (status == LaneStatus.CLOSED) "I-5 express lanes closed" else "I-5 express lanes now ${status.title.lowercase()}"
        val body = next?.let { "${Snapshot.verb(status, it.to)} at ${Format.whenText(it.at, now)}" } ?: ""
        return Message(title, body)
    }

    fun cantConfirm(check: Assessment) =
        Message(
            "Can't confirm the I-5 express lanes",
            (check.concerns.firstOrNull { !it.fixedByRefresh } ?: check.concerns.firstOrNull())?.title ?: "Open the app for details",
        )
}
