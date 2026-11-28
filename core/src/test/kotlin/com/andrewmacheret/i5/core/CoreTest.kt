package com.andrewmacheret.i5.core

import java.time.Duration
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from i5-server-lambdas' test/core.test.ts, so the app and the server agree

class FormatTest {
    private val now = t("2026-11-18T12:00") // a Wednesday

    @Test fun saysTheDayOnlyWhenItIsNotToday() {
        assertEquals("9:00 PM", Format.whenText(t("2026-11-18T21:00"), now))
        assertEquals("5:00 AM tomorrow", Format.whenText(t("2026-11-19T05:00"), now))
        assertEquals("8:00 AM Tue", Format.whenText(t("2026-11-24T08:00"), now))
    }

    @Test fun givesTheDateOnceItIsAWeekOrMoreAway() {
        assertEquals("8:00 AM Wed, Nov 25", Format.whenText(t("2026-11-25T08:00"), now))
        assertEquals("12:00 AM Fri, Jan 1", Format.whenText(t("2027-01-01T00:00"), now))
        assertEquals("8:00 AM Tue, Nov 10", Format.whenText(t("2026-11-10T08:00"), now))
    }

    @Test fun countsDown() {
        assertEquals("4h 5m", Format.countdown(t("2026-11-18T16:05"), now))
        assertEquals("2d 3h", Format.countdown(t("2026-11-20T15:00"), now))
        assertEquals("1m", Format.countdown(now, now))
    }

    @Test fun printsNumbersLikeJavaScript() {
        assertEquals("168", Format.number(168.0))
        assertEquals("168.8", Format.number(168.8))
    }
}

class TimelineTest {
    private val p = published()

    @Test fun findsTheStatusAtAMomentUnknownOutsideTheTimeline() {
        assertEquals(LaneStatus.SOUTHBOUND, Timeline.statusAt(p, t("2026-11-18T05:00")))
        assertEquals(LaneStatus.CLOSED, Timeline.statusAt(p, t("2026-11-18T04:59")))
        assertEquals(LaneStatus.CLOSED, Timeline.statusAt(p, t("2026-11-18T21:00")))
        assertEquals(LaneStatus.UNKNOWN, Timeline.statusAt(p, t("2026-11-16T12:00")))
        assertEquals(LaneStatus.UNKNOWN, Timeline.statusAt(p, t("2026-11-20T00:00")))
    }

    @Test fun listsTheNextChangesEndingWithUnknownAtTheEndOfTheTimeline() {
        assertEquals(
            listOf(
                Change(t("2026-11-18T21:00"), LaneStatus.CLOSED),
                Change(t("2026-11-19T05:00"), LaneStatus.SOUTHBOUND),
                Change(t("2026-11-20T00:00"), LaneStatus.UNKNOWN),
            ),
            Timeline.upcomingChanges(p, t("2026-11-18T12:00"), 3),
        )
        assertEquals(listOf(Change(t("2026-11-18T21:00"), LaneStatus.CLOSED)), Timeline.upcomingChanges(p, t("2026-11-18T12:00"), 1))
    }

    @Test fun findsTheLastChange() {
        assertEquals(t("2026-11-18T11:15"), Timeline.lastChange(p, t("2026-11-18T12:00")))
        assertNull(Timeline.lastChange(p, t("2026-11-17T01:00")))
    }

    @Test fun drawsADayInWallClockMinutes() {
        assertEquals(
            listOf(
                DaySegment(TimeOfDay(0), TimeOfDay(300), LaneStatus.CLOSED),
                DaySegment(TimeOfDay(300), TimeOfDay(660), LaneStatus.SOUTHBOUND),
                DaySegment(TimeOfDay(660), TimeOfDay(675), LaneStatus.CLOSED),
                DaySegment(TimeOfDay(675), TimeOfDay(1260), LaneStatus.NORTHBOUND),
                DaySegment(TimeOfDay(1260), TimeOfDay(1440), LaneStatus.CLOSED),
            ),
            Timeline.daySegments(p, LocalDate.of(2026, 11, 18)),
        )
        val unknown = listOf(DaySegment(TimeOfDay(0), TimeOfDay(1440), LaneStatus.UNKNOWN))
        assertEquals(unknown, Timeline.daySegments(p, LocalDate.of(2026, 11, 20)))
        assertEquals(unknown, Timeline.daySegments(p, LocalDate.of(2026, 11, 16)))
    }

    @Test fun fitsFallBackAndSpringForwardDaysInto24Hours() {
        for (day in listOf(LocalDate.of(2026, 11, 1), LocalDate.of(2027, 3, 14))) {
            val start = Seattle.startOfDay(day)
            val end = Seattle.startOfDay(day.plusDays(1))
            val eight = Seattle.instant(day, TimeOfDay.of(8))
            val q = published(
                horizon = Horizon(start, end),
                segments = listOf(Segment(start, eight, LaneStatus.CLOSED), Segment(eight, end, LaneStatus.SOUTHBOUND)),
            )
            assertEquals(
                listOf(DaySegment(TimeOfDay(0), TimeOfDay(480), LaneStatus.CLOSED), DaySegment(TimeOfDay(480), TimeOfDay(1440), LaneStatus.SOUTHBOUND)),
                Timeline.daySegments(q, day),
                "$day",
            )
        }
    }

    @Test fun findsDoubtsAtAMoment() {
        val q = published(doubts = listOf(doubt("2026-11-18T20:00", "2026-11-18T22:00")))
        assertEquals(1, Timeline.doubtsAt(q, t("2026-11-18T21:00")).size)
        assertEquals(0, Timeline.doubtsAt(q, t("2026-11-18T22:00")).size)
    }
}

class TrustTest {
    private val now = t("2026-11-18T12:00")

    @Test fun isConfidentWithFreshDataAndNoDoubts() {
        val a = Trust.assess(status(now), now)
        assertEquals(Assessment(Assessment.Verdict.CONFIDENT, LaneStatus.NORTHBOUND, emptyList()), a)
        assertEquals(LaneStatus.NORTHBOUND, a.shown)
    }

    @Test fun isntConfidentWhenNothingHasBeenPublished() {
        val a = Trust.assess(status(now, p = null), now)
        assertEquals(Assessment.Verdict.UNCERTAIN, a.verdict)
        assertEquals(LaneStatus.UNKNOWN, a.scheduled)
        assertNull(a.shown)
        assertEquals("Can't reach the server", a.concerns.single().title)
    }

    @Test fun isntConfidentWhenThePageHasntBeenReadIn2Hours() {
        val st = status(now).copy(fetchedAt = now.minus(Trust.MAX_AGE).minusSeconds(60), error = "WSDOT returned HTTP 503")
        val a = Trust.assess(st, now)
        assertEquals(Assessment.Verdict.UNCERTAIN, a.verdict)
        assertEquals("Data is 2h 1m old", a.concerns[0].title)
        assertTrue(a.concerns[0].detail.contains("WSDOT returned HTTP 503"))
        // Right at 2 hours it's still fresh, which is when the widget's alarm flips it
        val edge = status(now).copy(fetchedAt = now.minus(Trust.MAX_AGE))
        assertEquals(Assessment.Verdict.CONFIDENT, Trust.assess(edge, now).verdict)
        assertEquals(now, Trust.staleAt(edge))
    }

    @Test fun keepsASavedCopyFor2HoursThenWarns() {
        // The phone went offline after its last download. The server's read time keeps aging.
        val saved = status(t("2026-11-18T10:00"))
        assertEquals(Assessment.Verdict.CONFIDENT, Trust.assess(saved, t("2026-11-18T11:50")).verdict)
        val later = Trust.assess(saved, t("2026-11-18T12:00"))
        assertEquals(Assessment.Verdict.UNCERTAIN, later.verdict)
        assertEquals("Data is 2h 5m old", later.concerns.single().title)
        assertTrue(later.concerns.single().fixedByRefresh)
        // While a download runs it says checking instead of warning
        assertEquals(Assessment.Verdict.CHECKING, Trust.assess(saved, t("2026-11-18T12:00"), refreshing = true).verdict)
    }

    @Test fun noticesADeviceClockThatIsBehind() {
        val a = Trust.assess(status(now).copy(fetchedAt = now.plus(Duration.ofMinutes(10))), now)
        assertTrue("This device's clock is off" in a.concerns.map { it.title })
        // A refresh can't fix a wrong clock
        assertEquals(Assessment.Verdict.UNCERTAIN, Trust.assess(status(now).copy(fetchedAt = now.plus(Duration.ofMinutes(10))), now, refreshing = true).verdict)
        assertEquals(Assessment.Verdict.CONFIDENT, Trust.assess(status(now).copy(fetchedAt = now.plus(Duration.ofMinutes(4))), now).verdict)
    }

    @Test fun isntConfidentOutsideTheTimeline() {
        val later = t("2026-11-20T12:00")
        val a = Trust.assess(status(later), later)
        assertEquals(Assessment.Verdict.UNCERTAIN, a.verdict)
        assertTrue("No schedule for today" in a.concerns.map { it.title })
    }

    @Test fun showsTheServerDoubtsThatApplyNow() {
        val p = published(doubts = listOf(doubt("2026-11-18T11:00", "2026-11-18T13:00", title = "Approximate")))
        val a = Trust.assess(status(now, p), now)
        assertEquals(Assessment.Verdict.UNCERTAIN, a.verdict)
        assertEquals(listOf("Approximate"), a.concerns.map { it.title })
        assertEquals(false, a.concerns[0].checking)
    }

    @Test fun saysCheckingWhenTheOnlyDoubtsAreADocumentBeingRead() {
        val p = published(doubts = listOf(doubt("2026-11-18T11:00", "2026-11-20T00:00", checking = true)))
        assertEquals(Assessment.Verdict.CHECKING, Trust.assess(status(now, p), now).verdict)
        val q = published(doubts = listOf(doubt("2026-11-18T11:00", "2026-11-20T00:00", checking = true), doubt("2026-11-18T11:00", "2026-11-18T13:00")))
        assertEquals(Assessment.Verdict.UNCERTAIN, Trust.assess(status(now, q), now).verdict)
    }

    @Test fun findsTheNextMomentTheVerdictCanChange() {
        val p = published(doubts = listOf(doubt("2026-11-18T20:00", "2026-11-18T22:00")))
        // Read at 11:55, so stale at 1:55, but the doubt at 8 PM comes first after that
        assertEquals(t("2026-11-18T13:55"), Trust.nextReview(status(now, p), now))
        assertEquals(t("2026-11-18T20:00"), Trust.nextReview(status(now, p), t("2026-11-18T14:00")))
        assertEquals(t("2026-11-18T22:00"), Trust.nextReview(status(now, p), t("2026-11-18T21:00")))
    }
}

class LiveTest {
    private fun reading(at: String, northbound: Int?, southbound: Int?) = LiveReading(t(at), northbound, southbound)
    private fun concerns(now: String, live: LiveReading) = Trust.liveConcerns(status(t(now), live = live), t(now))

    @Test fun readsWhichWayTrafficIsMoving() {
        assertEquals(Trust.Seen.NORTHBOUND, Trust.liveReadingStatus(reading("2026-11-18T12:00", 23, 0)))
        assertEquals(Trust.Seen.SOUTHBOUND, Trust.liveReadingStatus(reading("2026-11-18T12:00", 0, 19)))
        assertEquals(Trust.Seen.NONE, Trust.liveReadingStatus(reading("2026-11-18T12:00", null, 0)))
        assertEquals(Trust.Seen.BOTH, Trust.liveReadingStatus(reading("2026-11-18T12:00", 23, 19)))
    }

    @Test fun agreesQuietlyWithTheTimeline() {
        assertEquals(emptyList(), concerns("2026-11-18T14:00", reading("2026-11-18T13:55", 23, 0)))
    }

    @Test fun flagsTrafficGoingTheOtherWay() {
        val c = concerns("2026-11-18T14:00", reading("2026-11-18T13:55", 0, 19))
        assertEquals(listOf("WSDOT's sensors show southbound traffic"), c.map { it.title })
        assertTrue(c[0].detail.contains("should be northbound"))
        assertEquals(false, c[0].checking)
        val now = t("2026-11-18T14:00")
        assertEquals(Assessment.Verdict.UNCERTAIN, Trust.assess(status(now, live = reading("2026-11-18T13:55", 0, 19)), now).verdict)
    }

    @Test fun flagsNoTrafficWhileTheLanesShouldBeOpen() {
        assertEquals(listOf("No traffic measured"), concerns("2026-11-18T14:00", reading("2026-11-18T13:55", 0, 0)).map { it.title })
    }

    @Test fun flagsTrafficWhileTheLanesShouldBeClosed() {
        assertEquals(listOf("WSDOT's sensors show northbound traffic"), concerns("2026-11-19T02:00", reading("2026-11-19T01:55", 21, 0)).map { it.title })
        assertEquals(emptyList(), concerns("2026-11-19T02:00", reading("2026-11-19T01:55", 0, 0)))
    }

    @Test fun ignoresReadingsSoonAfterASwitchSinceTheSensorsLag() {
        // The lanes closed early at 9 p.m., and the sensors still see the last northbound trips
        assertEquals(emptyList(), concerns("2026-11-18T21:20", reading("2026-11-18T21:15", 23, 0)))
        assertEquals(1, concerns("2026-11-18T21:40", reading("2026-11-18T21:35", 23, 0)).size)
    }

    @Test fun ignoresOldReadingsReadingsFromTheFutureAndBoth() {
        assertEquals(emptyList(), concerns("2026-11-18T14:00", reading("2026-11-18T13:00", 0, 19)))
        assertEquals(emptyList(), concerns("2026-11-18T14:00", reading("2026-11-18T14:20", 0, 19)))
        assertEquals(emptyList(), concerns("2026-11-18T14:00", reading("2026-11-18T13:55", 23, 19)))
    }
}

class SnapshotTest {
    @Test fun worksOutTheNextChangeAndDoubtfulDays() {
        val now = t("2026-11-18T12:00")
        val p = published(doubts = listOf(doubt("2026-11-19T06:00", "2026-11-19T07:00")))
        val s = Snapshot.make(status(now, p), now)
        assertEquals(Change(t("2026-11-18T21:00"), LaneStatus.CLOSED), s.next)
        assertEquals("Closes at 9:00 PM", s.nextText)
        assertEquals(setOf(LocalDate.of(2026, 11, 19)), s.doubtfulDays)
        assertEquals(1, s.tomorrowDoubts.size)
        assertEquals(DaySegment(TimeOfDay(675), TimeOfDay(1260), LaneStatus.NORTHBOUND), s.todaySegments[s.todaySegments.size - 2])
    }

    @Test fun copesWithNothingPublished() {
        val now = t("2026-11-18T12:00")
        val s = Snapshot.make(status(now, p = null), now)
        assertNull(s.next)
        assertEquals("No changes coming up", s.nextText)
        assertEquals(listOf(DaySegment(TimeOfDay(0), TimeOfDay(1440), LaneStatus.UNKNOWN)), s.todaySegments)
    }

    @Test fun knowsWhenTheLanesChangeSoon() {
        assertTrue(Snapshot.make(status(t("2026-11-18T20:45")), t("2026-11-18T20:45")).changingSoon)
        assertEquals(false, Snapshot.make(status(t("2026-11-18T20:44")), t("2026-11-18T20:44")).changingSoon)
    }
}
