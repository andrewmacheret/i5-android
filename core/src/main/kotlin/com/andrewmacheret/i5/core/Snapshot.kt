package com.andrewmacheret.i5.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** Everything the widget and detail screen show, worked out once. */
data class Snapshot(
    val now: Instant,
    val today: LocalDate,
    val status: ServerStatus,
    val check: Assessment,
    val changes: List<Change>,
    val todaySegments: List<DaySegment>,
    val tomorrowSegments: List<DaySegment>,
    val tomorrowDoubts: List<Doubt>,
    /** Days in the next week with something the server can't vouch for */
    val doubtfulDays: Set<LocalDate>,
    /** The app is downloading a new copy right now */
    val refreshing: Boolean = false,
    /** Why the app's last download failed, if it did */
    val loadError: String? = null,
) {
    val next: Change? get() = changes.firstOrNull()

    /** "Closes at 9:00 PM" */
    val nextText: String
        get() {
            val n = next ?: return "No changes coming up"
            if (n.to == LaneStatus.UNKNOWN) return "Schedule unknown after ${Format.whenText(n.at, now)}"
            return "${verb(check.scheduled, n.to)} at ${Format.whenText(n.at, now)}"
        }

    /** True when the lanes flip within 15 minutes */
    val changingSoon: Boolean
        get() = next?.let { it.to != LaneStatus.UNKNOWN && Duration.between(now, it.at) <= Duration.ofMinutes(15) } ?: false

    companion object {
        private val ALL_UNKNOWN = listOf(DaySegment(TimeOfDay.MIDNIGHT, TimeOfDay.END_OF_DAY, LaneStatus.UNKNOWN))

        fun make(status: ServerStatus, now: Instant, refreshing: Boolean = false, loadError: String? = null): Snapshot {
            val p = status.published
            val today = Seattle.day(now)
            val tomorrow = today.plusDays(1)
            val doubtful = mutableSetOf<LocalDate>()
            if (p != null) {
                for (i in 0L..7L) {
                    val d = today.plusDays(i)
                    val from = maxOf(now, Seattle.startOfDay(d))
                    if (Timeline.doubtsBetween(p, from, Seattle.startOfDay(d.plusDays(1))).isNotEmpty()) doubtful += d
                }
            }
            return Snapshot(
                now = now,
                today = today,
                status = status,
                check = Trust.assess(status, now, refreshing),
                changes = p?.let { Timeline.upcomingChanges(it, now, 4) } ?: emptyList(),
                todaySegments = p?.let { Timeline.daySegments(it, today) } ?: ALL_UNKNOWN,
                tomorrowSegments = p?.let { Timeline.daySegments(it, tomorrow) } ?: ALL_UNKNOWN,
                tomorrowDoubts = p?.let { Timeline.doubtsBetween(it, Seattle.startOfDay(tomorrow), Seattle.startOfDay(tomorrow.plusDays(1))) } ?: emptyList(),
                doubtfulDays = doubtful,
                refreshing = refreshing,
                loadError = loadError,
            )
        }

        /** "Closes", "Opens northbound", "Switches southbound" */
        fun verb(current: LaneStatus, to: LaneStatus): String = when {
            to == LaneStatus.UNKNOWN -> "Schedule unknown"
            to == LaneStatus.CLOSED -> "Closes"
            current == LaneStatus.CLOSED || current == LaneStatus.UNKNOWN -> "Opens ${to.title.lowercase()}"
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
     * - a refresh or a server read is running, or every problem is missing or old data that a refresh should fix
     *   (this also keeps it quiet at boot and overnight when the network is asleep)
     * - the timeline says closed, so a "can't confirm" can wait until the lanes
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
        if (new.scheduled == LaneStatus.CLOSED && next != null && next.to != LaneStatus.UNKNOWN &&
            Duration.between(snap.now, next.at) < SHORT_GAP
        ) return null
        return change(new.scheduled, next, snap.now)
    }

    fun change(status: LaneStatus, next: Change?, now: Instant): Message {
        val title = if (status == LaneStatus.CLOSED) "I-5 express lanes closed" else "I-5 express lanes now ${status.title.lowercase()}"
        val body = next?.takeIf { it.to != LaneStatus.UNKNOWN }?.let { "${Snapshot.verb(status, it.to)} at ${Format.whenText(it.at, now)}" } ?: ""
        return Message(title, body)
    }

    fun cantConfirm(check: Assessment) =
        Message(
            "Can't confirm the I-5 express lanes",
            (check.concerns.firstOrNull { !it.fixedByRefresh } ?: check.concerns.firstOrNull())?.title ?: "Open the app for details",
        )
}
