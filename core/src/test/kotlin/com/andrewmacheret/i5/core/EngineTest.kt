package com.andrewmacheret.i5.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EngineTest {
    private val engine = Engine(
        ExpressLanesInfo(
            earlyCloses = listOf(EarlyClose(day(2026, 11, 18), t(21)), EarlyClose(day(2026, 11, 19), null)),
            events = listOf(
                SpecialEvent(
                    "Weekend closure", "", null,
                    closure = Closure("Weekend closure", seattle(2026, 12, 4, 23), seattle(2026, 12, 7, 4)),
                ),
            ),
        ),
    )
    private val closed = LaneStatus.CLOSED
    private val south = LaneStatus.SOUTHBOUND
    private val north = LaneStatus.NORTHBOUND

    @Test fun passedEarlyCloseDropsOffTheList() {
        assertEquals(day(2026, 11, 18), engine.upcomingEarlyCloses(seattle(2026, 11, 18, 20)).first().day)
        assertEquals(day(2026, 11, 19), engine.upcomingEarlyCloses(seattle(2026, 11, 18, 22)).first().day)
    }

    @Test fun earlyCloseWithUnknownTimeDoesNotCut() {
        // Trust flags this day. The engine just keeps the normal schedule.
        assertEquals(north, engine.status(seattle(2026, 11, 19, 22)))
    }

    @Test fun daylightSavingDays() {
        // Nov 1, 2026 falls back and Mar 8, 2026 springs forward. Both are Sundays.
        for (d in listOf(day(2026, 11, 1), day(2026, 3, 8))) {
            val segs = engine.segments(d)
            assertEquals(listOf(closed, south, closed, north, closed), segs.map { it.status }, "on $d")
            assertEquals(Segment(t(8), t(13, 30), south), segs[1])
            assertEquals(Segment(t(13, 45), t(23), north), segs[3])
            assertEquals(TimeOfDay.END_OF_DAY, segs.last().end)
        }
    }

    @Test fun weekday() {
        // Thu Nov 19, 2026
        assertEquals(closed, engine.status(seattle(2026, 11, 19, 4, 59)))
        assertEquals(south, engine.status(seattle(2026, 11, 19, 5)))
        assertEquals(closed, engine.status(seattle(2026, 11, 19, 11, 5)))
        assertEquals(north, engine.status(seattle(2026, 11, 19, 11, 15)))
        assertEquals(north, engine.status(seattle(2026, 11, 19, 22, 59)))
        assertEquals(closed, engine.status(seattle(2026, 11, 19, 23)))
    }

    @Test fun weekend() {
        // Sat Nov 21, 2026
        assertEquals(closed, engine.status(seattle(2026, 11, 21, 7, 59)))
        assertEquals(south, engine.status(seattle(2026, 11, 21, 8)))
        assertEquals(closed, engine.status(seattle(2026, 11, 21, 13, 40)))
        assertEquals(north, engine.status(seattle(2026, 11, 21, 14)))
    }

    @Test fun earlyClose() {
        assertEquals(north, engine.status(seattle(2026, 11, 18, 20, 59)))
        assertEquals(closed, engine.status(seattle(2026, 11, 18, 21)))
    }

    @Test fun fullClosure() {
        // Sat Dec 5 would normally be southbound at 10 a.m.
        assertEquals(closed, engine.status(seattle(2026, 12, 5, 10)))
        assertEquals(south, engine.status(seattle(2026, 12, 7, 5)))
        assertTrue(engine.upcomingClosures(seattle(2026, 12, 7, 5)).isEmpty())
    }

    @Test fun upcomingChanges() {
        val changes = engine.upcomingChanges(seattle(2026, 11, 18, 16, 55), limit = 3)
        assertEquals(listOf(closed, south, closed), changes.map { it.to })
        assertEquals(seattle(2026, 11, 18, 21), changes[0].at)
        assertEquals(seattle(2026, 11, 19, 5), changes[1].at)
    }

    @Test fun timeline() {
        val segs = engine.segments(day(2026, 11, 19))
        assertEquals(listOf(closed, south, closed, north, closed), segs.map { it.status })
        assertEquals(Segment(t(5), t(11), south), segs[1])
    }

    @Test fun earlyCloseShowsInTimeline() {
        val segs = engine.segments(day(2026, 11, 18))
        assertEquals(Segment(t(11, 15), t(21), north), segs[3])
    }

    @Test fun countdownText() {
        val now = seattle(2026, 11, 18, 16, 55)
        assertEquals("4h 5m", Format.countdown(seattle(2026, 11, 18, 21), now))
        assertEquals("5m", Format.countdown(seattle(2026, 11, 18, 17), now))
        assertEquals("2d 1h", Format.countdown(seattle(2026, 11, 20, 17, 55), now))
        assertEquals("5:00 AM tomorrow", Format.whenText(seattle(2026, 11, 19, 5), now))
        assertEquals("9:00 PM", Format.whenText(seattle(2026, 11, 18, 21), now))
        assertEquals("8:00 AM Sat", Format.whenText(seattle(2026, 11, 21, 8), now))
    }
}
