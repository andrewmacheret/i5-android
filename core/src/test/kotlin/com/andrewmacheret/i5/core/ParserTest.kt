package com.andrewmacheret.i5.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ParserTest {
    private val now = seattle(2026, 11, 18, 16, 55)

    @Test fun readsWeeklySchedule() {
        val info = Parser.parse(fixture(), now)
        assertTrue(info.scheduleFromPage)
        assertEquals(WeeklySchedule.BUILT_IN, info.schedule)
        assertEquals(emptyList(), info.warnings)
    }

    @Test fun readsEarlyCloses() {
        val info = Parser.parse(fixture(), now)
        assertEquals(19, info.earlyCloses.size)
        assertTrue(info.earlyCloses.all { it.time == t(21) })
        assertEquals(day(2026, 11, 16), info.earlyCloses.first().day)
        // January rolls into next year
        assertEquals(day(2027, 1, 6), info.earlyCloses.last().day)
    }

    @Test fun earlyCloseWithItsOwnTime() {
        val html = page(
            special = """
            <h3>Express lanes closing early</h3><p>The I-5 express lanes will close at 9 p.m. on the dates below.</p>
            <ul><li>Monday, Nov. 30</li><li>Tuesday, Dec. 1 at 8 p.m.</li></ul>
            """,
        )
        assertEquals(
            listOf(EarlyClose(day(2026, 11, 30), t(21)), EarlyClose(day(2026, 12, 1), t(20))),
            Parser.parse(html, now).earlyCloses,
        )
    }

    @Test fun earlyCloseWithoutTime() {
        val html = page(special = "<h3>Express lanes closing early</h3><p>The lanes will close early.</p><ul><li>Monday, Nov. 30</li></ul>")
        assertEquals(listOf(EarlyClose(day(2026, 11, 30), null)), Parser.parse(html, now).earlyCloses)
    }

    @Test fun readsWeekendClosure() {
        val e = Parser.parse(fixture(), now).events.first()
        val c = assertNotNull(e.closure)
        assertEquals(seattle(2026, 12, 4, 23), c.start)
        assertEquals(seattle(2026, 12, 7, 4), c.end)
        assertEquals(DayRange(day(2026, 12, 4), day(2026, 12, 7)), e.days)
        assertTrue(e.link?.contains("ship-canal-bridge") == true)
    }

    @Test fun closureWithoutTimesIsNotGuessed() {
        val html = page(special = "<h3>Full express lanes closure</h3><p>The lanes will be closed Dec. 12 and Dec. 13.</p>")
        val e = Parser.parse(html, now).events.first()
        assertNull(e.closure)
        assertEquals(DayRange(day(2026, 12, 12), day(2026, 12, 13)), e.days)
    }

    @Test fun dateFirstClosure() {
        val html = page(special = "<h3>Weekend closure</h3><p>Closed from Friday, Jan. 8 at 11 p.m. to Monday, Jan. 11 at 5 a.m.</p>")
        val c = assertNotNull(Parser.parse(html, now).events.first().closure)
        assertEquals(seattle(2027, 1, 8, 23), c.start)
        assertEquals(seattle(2027, 1, 11, 5), c.end)
    }

    @Test fun readsAdvisoryDates() {
        val a = Parser.parse(fixture(), now).advisories.first()
        assertTrue(a.text.startsWith("Please note"))
        assertEquals(DayRange(day(2026, 11, 16), day(2026, 11, 17)), a.days)
        assertTrue(a.isOverBefore(day(2026, 11, 18)))
        assertFalse(a.isOverBefore(day(2026, 11, 17)))
    }

    @Test fun untilFurtherNoticeHasNoEnd() {
        val html = page(schedule = "<p>As of Nov. 16 the express lanes are closed until further notice.</p>$DEFAULT_SCHEDULE")
        val a = Parser.parse(html, now).advisories.first()
        assertEquals(DayRange(day(2026, 11, 16), null), a.days)
        assertFalse(a.isOverBefore(day(2026, 12, 25)))
    }

    @Test fun asOfNoticeHasNoEnd() {
        // Still on the page means still in effect, even days later
        val html = page(schedule = "<p>As of 4 p.m. Monday, Nov. 16, the express lanes are closed for emergency panel repair.</p>$DEFAULT_SCHEDULE")
        val a = Parser.parse(html, now).advisories.first()
        assertEquals(DayRange(day(2026, 11, 16), null), a.days)
    }

    @Test fun readsDateRanges() {
        assertEquals(listOf(day(2026, 12, 4), day(2026, 12, 7)), Dates.all("Weekend closure, Dec. 4-7", now))
        assertEquals(listOf(day(2026, 12, 4), day(2026, 12, 7)), Dates.all("Dec. 4 – 7", now))
        // A time after a dash isn't a date
        assertEquals(listOf(day(2026, 11, 16)), Dates.all("Monday, Nov. 16 - 4 p.m.", now))
        assertEquals(listOf(day(2026, 11, 16)), Dates.all("Nov. 16 - 4:30", now))
    }

    @Test fun readsEveryClosureInAnEvent() {
        val html = page(
            special = "<h3>Weekend closures</h3><p>Closed 11 p.m. Friday, Dec. 4 to 4 a.m. Monday, Dec. 7, " +
                "and again 11 p.m. Friday, Dec. 11 to 4 a.m. Monday, Dec. 14.</p>",
        )
        val events = Parser.parse(html, now).events
        assertEquals(listOf(seattle(2026, 12, 4, 23), seattle(2026, 12, 11, 23)), events.mapNotNull { it.closure?.start })
        assertEquals(2, events.size)
    }

    @Test fun flagsDatesNoClosureCovers() {
        val html = page(
            special = "<h3>Weekend closures</h3><p>Closed 11 p.m. Friday, Dec. 4 to 4 a.m. Monday, Dec. 7. " +
                "Another closure is planned Dec. 18-21.</p>",
        )
        val events = Parser.parse(html, now).events
        assertEquals(2, events.size)
        val loose = events.single { it.closure == null }
        assertEquals(DayRange(day(2026, 12, 18), day(2026, 12, 21)), loose.days)
        val during = seattle(2026, 12, 19, 10)
        assertEquals(
            Assessment.Verdict.UNCERTAIN,
            Trust.assess(FetchResult(Parser.parse(html, during), DataSource.Live(during)), during).verdict,
        )
    }

    @Test fun flagsUnknownPageParts() {
        val html = page(schedule = "$DEFAULT_SCHEDULE<h3>Holidays</h3><ul><li>Southbound only, all day</li></ul>")
        val info = Parser.parse(html, now)
        assertTrue(info.scheduleFromPage)
        assertTrue(info.warnings.any { it.contains("Holidays") })
        assertTrue(info.warnings.any { it.contains("Southbound only, all day") })
    }

    @Test fun flagsUnknownSections() {
        val withNew = page().replace("<h2>Special events schedule</h2>", "<h2>Holiday schedule</h2><p>Dec. 25</p><h2>Special events schedule</h2>")
        assertTrue(Parser.parse(withNew, now).warnings.any { it.contains("Holiday schedule") })
    }

    @Test fun onlyReadsTheExpressLanesPart() {
        // The footer's "Nov. 1" must not become a notice
        val info = Parser.parse(page(), now)
        assertEquals(emptyList(), info.advisories)
        assertEquals(emptyList(), info.warnings)
    }

    @Test fun windowFormats() {
        assertEquals(LaneWindow(LaneStatus.NORTHBOUND, t(13, 45), t(23)), Parser.window("Northbound - 1:45 to 11 p.m."))
        assertEquals(LaneWindow(LaneStatus.SOUTHBOUND, t(10), t(14)), Parser.window("Southbound - 10 to 2 p.m."))
        assertEquals(LaneWindow(LaneStatus.SOUTHBOUND, TimeOfDay.MIDNIGHT, TimeOfDay.END_OF_DAY), Parser.window("Southbound - 24 hours a day"))
        assertEquals(LaneWindow(LaneStatus.NORTHBOUND, t(11), TimeOfDay.END_OF_DAY), Parser.window("Northbound - 11 a.m. to midnight"))
        assertEquals(LaneWindow(LaneStatus.SOUTHBOUND, t(5), t(11)), Parser.window("Southbound – 5 a.m. to 11 a.m."))
        assertNull(Parser.window("Closed: 11 p.m. - 5 a.m."))
    }

    @Test fun garbageFallsBackToBuiltIn() {
        val info = Parser.parse("<html><p>nothing here</p></html>", now)
        assertFalse(info.scheduleFromPage)
        assertEquals(WeeklySchedule.BUILT_IN, info.schedule)
        assertTrue(info.warnings.isNotEmpty())
    }

    @Test fun timeLabels() {
        assertEquals("5 AM", t(5).label)
        assertEquals("11:15 AM", t(11, 15).label)
        assertEquals("1:30 PM", t(13, 30).label)
        assertEquals("Noon", t(12).label)
        assertEquals("Midnight", TimeOfDay.END_OF_DAY.label)
    }
}
