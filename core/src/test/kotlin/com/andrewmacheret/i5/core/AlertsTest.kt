package com.andrewmacheret.i5.core

import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertsTest {
    // The server's real timeline from Dec 1, 2026: the regular schedule, closing early at 9 p.m. on Wed, Dec 2,
    // and a weekend closure from Fri, Dec 4 whose start time is approximate
    private val base = fixture("confident").status.copy(live = null)

    /** A snapshot at `at`, with the data read 5 minutes earlier so it's always fresh */
    private fun snap(at: Instant, status: ServerStatus = base, refreshing: Boolean = false) =
        Snapshot.make(status.copy(fetchedAt = at.minus(Duration.ofMinutes(5))), at, refreshing)

    private fun change(from: String, to: String) = Alerts.decide(Alerts.seen(snap(t(from))), snap(t(to)))

    @Test fun reportsADirectionChangeOnTime() {
        assertEquals(Alerts.Message("I-5 express lanes now southbound", "Closes at 11:00 AM"), change("2026-12-02T04:50", "2026-12-02T05:00"))
    }

    @Test fun skipsOldNews() {
        // The phone slept through the 5 a.m. switch and woke at 6:30
        assertNull(change("2026-12-02T04:50", "2026-12-02T06:30"))
    }

    @Test fun skipsTheShortClosedGap() {
        assertNull(change("2026-12-02T10:55", "2026-12-02T11:00"))
        assertEquals("I-5 express lanes now northbound", change("2026-12-02T11:00", "2026-12-02T11:15")?.title)
    }

    @Test fun reportsAnEarlyClosure() {
        assertEquals(Alerts.Message("I-5 express lanes closed", "Opens southbound at 5:00 AM tomorrow"), change("2026-12-02T20:30", "2026-12-02T21:00"))
    }

    @Test fun reportsLosingConfidenceWhileTheLanesAreOpen() {
        // The closure starts "as early as 11 p.m.", so the server can't vouch for the two hours before it
        assertEquals(
            Alerts.Message("Can't confirm the I-5 express lanes", "Weekend-long full closure: start time is approximate"),
            change("2026-12-04T20:50", "2026-12-04T21:00"),
        )
    }

    @Test fun holdsACantConfirmUntilTheLanesWouldOpen() {
        val p = published(doubts = listOf(doubt("2026-11-19T00:00", "2026-11-20T00:00", title = "No word yet on Thanksgiving", source = "holiday")))
        val st = status(t("2026-11-18T22:00"), p)
        fun at(s: String) = Snapshot.make(st.copy(fetchedAt = t(s).minus(Duration.ofMinutes(5))), t(s))
        val seen = Alerts.seen(at("2026-11-18T22:00"))
        // The doubt starts at midnight, but the lanes are closed until 5 a.m.
        val midnight = at("2026-11-19T00:00")
        assertTrue(Alerts.waiting(midnight))
        assertNull(Alerts.decide(seen, midnight))
        assertEquals(
            Alerts.Message("Can't confirm the I-5 express lanes", "No word yet on Thanksgiving"),
            Alerts.decide(seen, at("2026-11-19T05:00")),
        )
    }

    @Test fun staysQuietWhenOnlyTheDataIsOld() {
        // At boot or overnight with no network, old data alone doesn't buzz
        val before = snap(t("2026-12-02T06:00"))
        val after = Snapshot.make(base.copy(fetchedAt = t("2026-12-02T06:00")), t("2026-12-02T09:00"))
        assertEquals(Assessment.Verdict.UNCERTAIN, after.check.verdict)
        assertTrue(Alerts.waiting(after))
        assertNull(Alerts.decide(Alerts.seen(before), after))
    }

    @Test fun staysQuietWhileCheckingOrDownloading() {
        val old = base.copy(fetchedAt = t("2026-12-02T01:00"))
        val before = Snapshot.make(old, t("2026-12-02T04:55"))
        val after = Snapshot.make(old, t("2026-12-02T05:00"), refreshing = true)
        assertEquals(Assessment.Verdict.CHECKING, after.check.verdict)
        assertNull(Alerts.decide(Alerts.seen(before), after))
        assertNull(Alerts.decide(null, after))
        // The server reading a new page is quiet too
        val checking = fixture("checking")
        val c = checking.snap()
        assertEquals(Assessment.Verdict.CHECKING, c.check.verdict)
        assertNull(Alerts.decide(Alerts.seen(snap(checking.at)), c))
    }

    @Test fun leavesTheEndOfTheTimelineOutOfTheMessage() {
        assertEquals(Alerts.Message("I-5 express lanes now southbound", ""), Alerts.change(LaneStatus.SOUTHBOUND, Change(t("2026-11-20T00:00"), LaneStatus.UNKNOWN), t("2026-11-19T05:00")))
    }
}
