package com.andrewmacheret.i5.core

import java.io.IOException
import java.nio.file.Files
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TrustTest {
    private val now = seattle(2026, 11, 18, 16, 55)
    private val confident = Assessment.Verdict.CONFIDENT
    private val uncertain = Assessment.Verdict.UNCERTAIN
    private val checking = Assessment.Verdict.CHECKING

    private fun live(html: String, at: Instant = now) = FetchResult(Parser.parse(html, at), DataSource.Live(at))

    @Test fun todaysRealPageIsConfident() {
        // The old Monday notice is over, so it doesn't count
        val check = Trust.assess(live(fixture()), now)
        assertEquals(confident, check.verdict)
        assertEquals(LaneStatus.NORTHBOUND, check.shown)
    }

    @Test fun noticeForTodayBlocksConfidence() {
        val at = seattle(2026, 11, 17, 8)
        val check = Trust.assess(live(fixture(), at), at)
        assertEquals(uncertain, check.verdict)
        assertNull(check.shown)
        assertTrue(check.concerns.any { it.title == "WSDOT posted a notice" })
    }

    @Test fun undatedNoticeBlocksConfidence() {
        val html = page(schedule = "<p>The express lanes are closed for an emergency repair.</p>$DEFAULT_SCHEDULE")
        assertEquals(uncertain, Trust.assess(live(html), now).verdict)
    }

    @Test fun holidayBlocksConfidence() {
        val tg = seattle(2026, 11, 26, 10)
        val check = Trust.assess(live(fixture(), tg), tg)
        assertEquals(uncertain, check.verdict)
        assertTrue(check.concerns.first().title.contains("Thanksgiving"))
    }

    @Test fun closureWithUnclearTimesBlocksThoseDays() {
        val html = page(special = "<h3>Full express lanes closure</h3><p>The lanes will be closed Dec. 12 and Dec. 13.</p>")
        val during = seattle(2026, 12, 12, 10)
        assertEquals(uncertain, Trust.assess(live(html, during), during).verdict)
        val before = seattle(2026, 12, 10, 10)
        assertEquals(confident, Trust.assess(live(html, before), before).verdict)
    }

    @Test fun earlyCloseWithoutTimeBlocksThatDay() {
        val html = page(special = "<h3>Express lanes closing early</h3><p>Early closures.</p><ul><li>Wednesday, Nov. 18</li></ul>")
        assertEquals(uncertain, Trust.assess(live(html), now).verdict)
    }

    @Test fun staleData() {
        val info = Parser.parse(fixture(), now)
        val old = FetchResult(info, DataSource.Cached(now.minus(Duration.ofHours(3))))
        assertEquals(uncertain, Trust.assess(old, now).verdict)
        assertEquals("Data is 3h old", Trust.assess(old, now).concerns.first().title)
        // While a refresh is running it says checking instead
        assertEquals(checking, Trust.assess(old, now, refreshing = true).verdict)
        val fresh = FetchResult(info, DataSource.Cached(now.minus(Duration.ofMinutes(30))))
        assertEquals(confident, Trust.assess(fresh, now).verdict)
        assertEquals(now.plus(Duration.ofMinutes(90)), Trust.staleAt(fresh))
    }

    @Test fun builtInNeverConfident() {
        val r = FetchResult(ExpressLanesInfo.BUILT_IN, DataSource.BuiltIn)
        assertEquals(uncertain, Trust.assess(r, now).verdict)
        assertEquals(checking, Trust.assess(r, now, refreshing = true).verdict)
        assertNull(Trust.staleAt(r))
    }

    @Test fun changedPageNeverConfident() {
        val r = FetchResult(Parser.parse(fixture(), now), DataSource.Cached(now), pageChanged = true)
        assertEquals(uncertain, Trust.assess(r, now, refreshing = true).verdict)
    }

    @Test fun unknownPagePartsBlockConfidence() {
        val html = page(schedule = "$DEFAULT_SCHEDULE<h3>Holidays</h3><ul><li>Southbound only</li></ul>")
        assertEquals(uncertain, Trust.assess(live(html), now).verdict)
    }

    @Test fun brokenScheduleBlocksConfidence() {
        val bad = WeeklySchedule(
            weekday = listOf(LaneWindow(LaneStatus.SOUTHBOUND, t(5), t(12)), LaneWindow(LaneStatus.NORTHBOUND, t(11), t(23))),
            weekend = WeeklySchedule.BUILT_IN.weekend,
        )
        assertNotNull(Trust.scheduleProblem(bad))
        assertNull(Trust.scheduleProblem(WeeklySchedule.BUILT_IN))
        assertNotNull(Trust.scheduleProblem(bad.copy(weekday = emptyList())))
    }

    @Test fun holidays() {
        assertEquals("Thanksgiving", Holidays.name(day(2026, 11, 26)))
        assertEquals("Day after Thanksgiving", Holidays.name(day(2026, 11, 27)))
        assertEquals("Memorial Day", Holidays.name(day(2026, 5, 25)))
        assertEquals("Labor Day", Holidays.name(day(2026, 9, 7)))
        // July 4, 2026 is a Saturday, observed Friday the 3rd
        assertEquals("Independence Day", Holidays.name(day(2026, 7, 3)))
        assertEquals("Independence Day", Holidays.name(day(2026, 7, 4)))
        assertEquals("Christmas Eve", Holidays.name(day(2026, 12, 24)))
        // Jan 1, 2028 is a Saturday, observed Friday Dec 31, 2027
        assertEquals("New Year's Day", Holidays.name(day(2027, 12, 31)))
        assertNull(Holidays.name(day(2026, 11, 18)))
    }

    @Test fun fetcherKeepsLastGoodCopy() {
        val dir = Files.createTempDirectory("i5").toFile()
        val good = Fetcher(dir) { fixture() }.load(now)
        assertIs<DataSource.Live>(good.source)

        // The network fails, so the saved copy is used
        val offline = Fetcher(dir) { throw IOException("offline") }.load(now.plusSeconds(60))
        assertIs<DataSource.Cached>(offline.source)
        assertEquals("offline", offline.error)

        // The page changes into something unreadable. Keep the old copy and say so.
        val changed = Fetcher(dir) { "<html>new site</html>" }.load(now.plusSeconds(120))
        assertIs<DataSource.Cached>(changed.source)
        assertTrue(changed.pageChanged)
        assertTrue(changed.info.scheduleFromPage)
        assertEquals(uncertain, Trust.assess(changed, now.plusSeconds(120)).verdict)
        dir.deleteRecursively()
    }

    @Test fun fetcherRemembersChangedPage() {
        val dir = Files.createTempDirectory("i5").toFile()
        Fetcher(dir) { fixture() }.load(now)
        Fetcher(dir) { "<html>new site</html>" }.load(now.plusSeconds(60))

        // After a restart, and after a failed download, the problem is still there
        assertTrue(Fetcher(dir) { fixture() }.cachedOrBuiltIn(now.plusSeconds(120)).pageChanged)
        val offline = Fetcher(dir) { throw IOException("offline") }.load(now.plusSeconds(180))
        assertTrue(offline.pageChanged)
        assertEquals(uncertain, Trust.assess(offline, now.plusSeconds(180)).verdict)

        // A good page clears it
        assertTrue(!Fetcher(dir) { fixture() }.load(now.plusSeconds(240)).pageChanged)
        assertTrue(!Fetcher(dir) { fixture() }.cachedOrBuiltIn(now.plusSeconds(300)).pageChanged)
        dir.deleteRecursively()
    }

    @Test fun futureFetchTimeIsNotTrusted() {
        // The clock was set back after the page was read
        val r = FetchResult(Parser.parse(fixture(), now), DataSource.Cached(now.plus(Duration.ofHours(1))))
        assertEquals(uncertain, Trust.assess(r, now).verdict)
        assertEquals(checking, Trust.assess(r, now, refreshing = true).verdict)
    }

    @Test fun fetcherWithNothingSavedUsesBuiltIn() {
        val dir = Files.createTempDirectory("i5").toFile()
        val r = Fetcher(dir) { throw IOException("offline") }.load(now)
        assertEquals(DataSource.BuiltIn, r.source)
        dir.deleteRecursively()
    }
}
