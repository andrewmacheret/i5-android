package com.andrewmacheret.i5.core

import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AlertsTest {
    private val result = FetchResult(Parser.parse(fixture(), seattle(2026, 11, 18, 10)), DataSource.Live(seattle(2026, 11, 18, 10)))

    /** A snapshot at `at`, with the data read 5 minutes earlier so it's always fresh */
    private fun snap(at: java.time.Instant) =
        Snapshot.make(result.copy(source = DataSource.Live(at.minus(Duration.ofMinutes(5)))), at)

    @Test fun reportsDirectionChangeOnTime() {
        val before = snap(seattle(2026, 11, 19, 4, 50))
        val after = snap(seattle(2026, 11, 19, 5, 0))
        val msg = Alerts.decide(Alerts.seen(before), after)
        assertEquals(Alerts.Message("I-5 express lanes now southbound", "Closes at 11:00 AM"), msg)
    }

    @Test fun skipsOldNews() {
        // The phone slept through the 5 a.m. switch and woke at 6:30
        val before = snap(seattle(2026, 11, 19, 4, 50))
        val after = snap(seattle(2026, 11, 19, 6, 30))
        assertNull(Alerts.decide(Alerts.seen(before), after))
    }

    @Test fun skipsShortClosedGap() {
        val before = snap(seattle(2026, 11, 19, 10, 55))
        val after = snap(seattle(2026, 11, 19, 11, 0))
        assertNull(Alerts.decide(Alerts.seen(before), after))
        val north = snap(seattle(2026, 11, 19, 11, 15))
        assertEquals("I-5 express lanes now northbound", Alerts.decide(Alerts.seen(after), north)?.title)
    }

    @Test fun reportsEarlyClosure() {
        val before = snap(seattle(2026, 11, 18, 20, 30))
        val after = snap(seattle(2026, 11, 18, 21, 0))
        assertEquals(Alerts.Message("I-5 express lanes closed", "Opens southbound at 5:00 AM tomorrow"), Alerts.decide(Alerts.seen(before), after))
    }

    @Test fun reportsLosingConfidenceWhenLanesWouldOpen() {
        val before = snap(seattle(2026, 11, 25, 22))
        val seen = Alerts.seen(before)
        // Thanksgiving starts at midnight, but the lanes are posted closed until 5 a.m.
        val midnight = snap(seattle(2026, 11, 26, 0, 0))
        assertTrue(Alerts.waiting(midnight))
        assertNull(Alerts.decide(seen, midnight))
        assertEquals(
            Alerts.Message("Can't confirm the I-5 express lanes", "Thanksgiving may run a holiday schedule"),
            Alerts.decide(seen, snap(seattle(2026, 11, 26, 5, 0))),
        )
    }

    @Test fun quietWhenOnlyTheDataIsOld() {
        // At boot or overnight with no network, old data alone doesn't buzz
        val before = snap(seattle(2026, 11, 19, 6))
        val old = result.copy(source = DataSource.Live(seattle(2026, 11, 19, 6)))
        val after = Snapshot.make(old, seattle(2026, 11, 19, 9))
        assertEquals(Assessment.Verdict.UNCERTAIN, after.check.verdict)
        assertTrue(Alerts.waiting(after))
        assertNull(Alerts.decide(Alerts.seen(before), after))
    }

    @Test fun quietWhileUncertainOrChecking() {
        val stale = result.copy(source = DataSource.Live(seattle(2026, 11, 19, 1)))
        val before = Snapshot.make(stale, seattle(2026, 11, 19, 4, 55))
        val after = Snapshot.make(stale, seattle(2026, 11, 19, 5, 0), refreshing = true)
        assertEquals(Assessment.Verdict.CHECKING, after.check.verdict)
        assertNull(Alerts.decide(Alerts.seen(before), after))
        assertNull(Alerts.decide(null, after))
    }

    @Test fun snapshotText() {
        val s = snap(seattle(2026, 11, 18, 16, 55))
        assertEquals("Closes at 9:00 PM", s.nextText)
        assertEquals(t(21), s.earlyCloseTonight)
        assertEquals("Thanksgiving", s.upcomingHolidays.first().second)
        // The week ahead from Friday Nov 20 covers Thanksgiving and the day after
        assertEquals(setOf(day(2026, 11, 26), day(2026, 11, 27)), snap(seattle(2026, 11, 20, 12)).doubtfulDays)
    }
}
