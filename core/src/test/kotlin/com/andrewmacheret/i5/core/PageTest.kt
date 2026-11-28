package com.andrewmacheret.i5.core

import java.net.URI
import java.net.URLDecoder
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

// Ported from i5-client's test/page.test.tsx. The fixtures are real /status answers from
// i5-server-lambdas, made from saved copies of WSDOT's page with fake models giving the approved answers.

/** Everything the detail screen would say, as one string, for checking what a reader sees */
private fun text(s: Snapshot, loading: Boolean = false): String = buildList {
    val h = Page.header(s, loading)
    add(h.title)
    add(h.subtitle)
    if (!loading && s.check.verdict == Assessment.Verdict.UNCERTAIN) {
        for (c in s.check.concerns) add("${c.title} ${c.detail}")
        add("${Page.LIVE_SOURCES} WSDOT Traffic on Bluesky · Traffic cameras · WSDOT page")
    }
    for (n in Page.notices(s)) add("${n.notice.title} ${n.text}")
    add(Page.timelineTitle(s))
    add(Page.timelineSummary("Today", s.todaySegments))
    add(Page.timelineSummary("Tomorrow", s.tomorrowSegments))
    Page.travelTimes(s)?.let { tt ->
        add("Travel times Express lanes Regular lanes")
        for (r in tt.rows) add(listOfNotNull(r.label, r.express, r.saves?.let { "· $it" }, r.regular, r.usual?.let { "· $it" }).joinToString(" "))
        add(tt.hint)
    }
    if (s.status.cameras.isNotEmpty()) add("Cameras ${Cameras.summary(s.status.cameras, false)}")
    add("Coming up")
    val coming = Page.comingUp(s)
    if (coming.isEmpty()) add("No changes coming up.")
    for (c in coming) add("${c.to.title} ${Format.whenText(c.at, s.now)} ${Format.countdown(c.at, s.now)}")
    for (p in Page.planned(s)) add(listOfNotNull(p.title, *p.times.toTypedArray(), p.note, p.detailLabel, p.detail).joinToString(" "))
    val (schedule, hint) = Page.regularSchedule(s)
    add("Regular schedule")
    hint?.let(::add)
    add(schedule.weekday.joinToString { it.status.title })
    add("Sources")
    for (src in Page.sources(s)) add("${src.title} ${src.subtitle}")
    val d = Page.dataStatus(s, loading)
    add(d.text)
    d.reading?.let(::add)
    d.live?.let(::add)
    Page.loadErrorText(s)?.let(::add)
}.joinToString(" ")

class PageTest {
    private fun page(name: String) = fixture(name).let { text(it.snap()) }

    @Test fun everyFixtureRendersEverySection() {
        for (name in FIXTURES) {
            val text = page(name)
            for (section in listOf("Today", "Tomorrow", "Coming up", "Regular schedule", "Sources")) assertContains(text, section, message = name)
            assertTrue(!Regex("undefined|NaN|null").containsMatchIn(text), "$name: $text")
        }
    }

    @Test fun showsAConfirmedDirectionWhatHappensNextAndTheNoticesComingUp() {
        val f = fixture("confident")
        val s = f.snap()
        val text = text(s)
        assertContains(text, "Northbound")
        assertTrue(Regex("Closes at 11:00 PM · \\d+h").containsMatchIn(text), text)
        assertTrue("Can't confirm" !in text)
        // The alert's lane reduction starts tonight
        assertContains(text, "Reduced to two lanes at night From 7:00 PM until 5:00 AM tomorrow.")
        // Planned changes from the page and the alert
        assertContains(text, "Weekend-long full closure")
        assertContains(text, "Start time is approximate.")
        assertContains(text, "Closing early at 9 p.m.")
        assertTrue(Page.planned(s).any { "Wed, Dec 2 9:00 PM → Thu, Dec 3 5:00 AM" in it.times })
        assertContains(text, "WSDOT's words")
        assertContains(text, "WSDOT's alert")
        assertTrue(Page.sources(s).any { it.url == "https://wsdot.com/Travel/Real-time/Map/?featureid=727762&featuretype=alert" })
        assertContains(text, "Notices read by gemini-4-hydrogen and gpt-oss-120b.")
        assertContains(text, "WSDOT's sensors at 3:00 PM: 18 min northbound through the express lanes.")
        assertContains(text, "Today: Southbound 5 AM to 11 AM, Northbound 11:15 AM to 11 PM")
        assertContains(text, "Tomorrow: Southbound 5 AM to 11 AM, Northbound 11:15 AM to 9 PM")
    }

    @Test fun comparesTheExpressLanesWithTheRegularLanes() {
        val text = page("confident")
        assertContains(text, "Travel times Express lanes Regular lanes")
        assertContains(text, "Seattle → Everett 18 min · saves 8 min 26 min · usually 22")
        assertContains(text, "Everett → Seattle — 15 min · usually 16")
        assertContains(text, "Downtown Seattle to Everett, from WSDOT's sensors at 3:00 PM. A dash means the express lanes aren't open that way.")
    }

    @Test fun leavesOutTravelTimesFromOldReadingsWithoutThemOrOnceTheyAre30MinutesOld() {
        assertTrue("Travel times" !in page("holiday"))
        val f = fixture("confident")
        assertNotNull(Page.travelTimes(f.snap(f.at.plus(Duration.ofMinutes(30)))))
        assertTrue("Travel times" !in text(f.snap(f.at.plus(Duration.ofMinutes(31)))))
        // Saving a minute isn't worth mentioning
        val live = f.status.live!!
        val close = live.copy(trips = Trips(TripTimes(25, 26, 22), TripTimes(null, null, null)))
        val rows = Page.travelTimes(f.snap(status = f.status.copy(live = close)))!!.rows
        assertNull(rows[0].saves)
        assertEquals("—", rows[1].express)
        // Nothing measured either way, so the table is left out
        val none = live.copy(trips = Trips(TripTimes(null, null, 20), TripTimes(null, null, null)))
        assertNull(Page.travelTimes(f.snap(status = f.status.copy(live = none))))
    }

    @Test fun cantConfirmTheDayAfterThanksgivingWithNoHolidayNotice() {
        val s = fixture("holiday").snap()
        val text = text(s)
        assertContains(text, "Can't confirm")
        assertContains(text, "Expected northbound, but don't rely on it")
        assertContains(text, "No word yet on Thanksgiving")
        assertContains(text, "Check a live source before you drive: WSDOT Traffic on Bluesky · Traffic cameras · WSDOT page")
        // No cameras from this server, so "Traffic cameras" opens the travel map
        assertTrue(s.status.cameras.isEmpty())
        assertTrue(Sources.TRAVEL_MAP.startsWith("https://wsdot.com/Travel/Real-time/Map/?layers="))
        assertTrue(Page.sources(s).any { it.url == "https://bsky.app/profile/traffic.wsdot.wa.gov" })
        // The holiday shows under planned changes too
        assertTrue(Page.planned(s).any { it.title == "No word yet on Thanksgiving" && it.note!!.startsWith("WSDOT has run") })
    }

    @Test fun saysItIsCheckingWhileTheModelsReadANewPage() {
        val text = page("checking")
        assertContains(text, "Checking…")
        assertContains(text, "Reading WSDOT's latest update")
        assertTrue("Check a live source before you drive" !in text)
    }

    @Test fun warnsWhenNoModelCouldReadTheLatestPage() {
        val text = page("failed")
        assertContains(text, "Can't confirm")
        assertContains(text, "Couldn't read WSDOT's latest update, so earlier notices are shown.")
        // The earlier page lists no regular hours
        assertContains(text, "WSDOT's page doesn't list its regular schedule right now.")
        // Undated notices count as planned, and one that starts in 2027 waits until it's 2 weeks away
        assertContains(text, "More closures possible this season No dates given")
        assertTrue("Southbound only in 2027" !in Page.planned(fixture("failed").snap()).map { it.title })
        assertEquals("Exact times unclear. The app won't confirm a direction then.", Page.certaintyText(fixture("failed").status.published!!.notices[1]))
    }

    @Test fun warnsWhenWsdotsPageHasBeenDownForHours() {
        val text = page("stale")
        assertContains(text, "Data is 3h old")
        assertContains(text, "WSDOT didn't answer at 2:50 PM. Using what it said at 12:00 PM.")
    }

    @Test fun showsANoticeInEffectNowAndThatItCouldLastLonger() {
        val text = page("winter-2022")
        assertContains(text, "Southbound")
        assertContains(text, "Southbound for winter weather Now until 11:00 AM Mon. It could last longer.")
    }

    @Test fun handlesANoticeThatRunsPastTheEndOfTheTimeline() {
        val text = page("revive-2026")
        assertContains(text, "Northbound")
        assertContains(text, "No changes before Fri, Oct 9")
        assertContains(text, "Northbound around the clock for Revive I-5 Now until 12:00 AM Fri, Jan 1. It could last longer.")
        assertContains(text, "Ninth Avenue on-ramp closed Now until 12:00 AM Tue, Sep 8.")
        assertContains(text, "No changes coming up.")
    }

    @Test fun listsEveryTimeSoTheScreenCanFoldPast20() {
        val f = fixture("confident")
        val p = f.status.published!!
        val n = p.notices.first { it.title == "Closing early at 9 p.m." }
        val day = Duration.ofDays(1)
        val spans = (1..25L).map { i -> NoticeSpan(f.at.plus(day.multipliedBy(i)), f.at.plus(day.multipliedBy(i)).plus(Duration.ofHours(1))) }
        val status = f.status.copy(published = p.copy(notices = listOf(n.copy(spans = spans))))
        val item = Page.planned(f.snap(status = status)).single()
        assertEquals(25, item.times.size)
        assertEquals(20, item.times.take(Page.MAX_TIMES).count { "→" in it })
        assertEquals(5, item.times.drop(Page.MAX_TIMES).size)
        // The express lanes page itself isn't a "More info" link
        assertNull(item.link)
    }

    @Test fun saysItIsLoadingBeforeTheFirstAnswer() {
        val now = t("2026-12-01T12:00")
        val text = text(Snapshot.make(ServerStatus.EMPTY, now, refreshing = true), loading = true)
        assertContains(text, "Loading…")
        assertContains(text, "Asking the server")
        assertTrue("Can't confirm" !in text)
    }

    @Test fun showsTheErrorWhenTheServerCantBeReached() {
        val f = fixture("confident")
        val text = text(Snapshot.make(f.status, f.at, loadError = "Unable to resolve host \"i5.andrewmacheret.com\""))
        assertContains(text, "Couldn't reach the server: Unable to resolve host \"i5.andrewmacheret.com\"")
        // With nothing saved at all
        val empty = text(Snapshot.make(ServerStatus.EMPTY, f.at, loadError = "timeout"))
        assertContains(empty, "Can't confirm")
        assertContains(empty, "Can't reach the server")
        assertContains(empty, "Don't rely on the app right now")
    }
}

class CamerasTest {
    private val f = fixture("confident")
    private val cameras = f.status.cameras

    @Test fun startClosedOfferingTheExpressLaneOnes() {
        assertContains(text(f.snap()), "Cameras Show 2 express lane cameras, downtown to Northgate")
        assertEquals("Hide 2 express lane cameras, downtown to Northgate", Cameras.summary(cameras, true))
    }

    @Test fun putExpressLaneCamerasFirstWithAToggleForAll() {
        assertEquals(cameras.filter { it.express }, Cameras.shown(cameras, all = false))
        assertEquals(cameras, Cameras.shown(cameras, all = true))
        assertEquals("Show all ${cameras.size} cameras along I-5", Cameras.toggleLabel(cameras, all = false))
        assertEquals("Only express lane cameras", Cameras.toggleLabel(cameras, all = true))
        // No express lane cameras, so all of them show and there's nothing to toggle
        val plain = cameras.filter { !it.express }
        assertEquals(plain, Cameras.shown(plain, all = false))
        assertNull(Cameras.toggleLabel(plain, all = false))
        assertEquals("Show ${plain.size} cameras, downtown to Northgate", Cameras.summary(plain, false))
    }

    @Test fun areLeftOutWhenTheServerDidntSendAny() {
        assertTrue("Cameras" !in text(fixture("holiday").snap()))
    }

    @Test fun showWsdotsPictureWhichChangesAddressEvery5MinutesWithItsMilepost() {
        val c = cameras[1]
        val url = Cameras.imageUrl(c, Cameras.version(f.at))
        assertTrue(Regex("^https://images\\.wsdot\\.wa\\.gov/nw/\\w+\\.jpg\\?v=\\d+$").matches(url), url)
        assertEquals("Roanoke St Express Lanes", c.name)
        assertEquals("MP 168", Cameras.milepost(c))
        assertEquals("MP 168.8", Cameras.milepost(cameras[2]))
        assertEquals(Cameras.version(f.at), Cameras.version(f.at.plusSeconds(60)))
        assertEquals(Cameras.version(f.at) + 1, Cameras.version(f.at.plus(Duration.ofMinutes(5))))
        assertEquals(5_903_370L, Cameras.version(java.time.Instant.ofEpochMilli(1_771_011_000_000L)))
    }

    @Test fun giveEachRetryOrReloadANewAddress() {
        val c = cameras[0]
        assertEquals("${c.image}?v=100", Cameras.imageUrl(c, 100))
        assertEquals("${c.image}?v=100", Cameras.imageUrl(c, 100, 0))
        assertEquals("${c.image}?v=100-1", Cameras.imageUrl(c, 100, 1))
        assertEquals("${c.image}?v=100-2", Cameras.imageUrl(c, 100, 2))
        assertNotEquals(Cameras.imageUrl(c, 100, 1), Cameras.imageUrl(c, 101, 1))
    }

    @Test fun retryAPictureThreeTimesWaitingLongerEachTime() {
        assertEquals(listOf(5L, 15L, 45L), Cameras.RETRY_DELAYS.map { it.seconds })
        assertEquals(Duration.ofSeconds(5), Cameras.retryDelay(1))
        assertEquals(Duration.ofSeconds(15), Cameras.retryDelay(2))
        assertEquals(Duration.ofSeconds(45), Cameras.retryDelay(3))
        // The fourth failure is the last
        assertNull(Cameras.retryDelay(4))
        assertNull(Cameras.retryDelay(0))
    }

    @Test fun sayWhenAPictureDidntLoadAndHowToReloadIt() {
        assertEquals("Picture didn't load", Cameras.FAILED)
        assertEquals("Reload the picture from Roanoke St Express Lanes", Cameras.reloadLabel(cameras[1]))
        assertEquals("Open in browser", Cameras.OPEN_IN_BROWSER)
    }

    @Test fun linkToEveryCameraOnTheTravelMapUnderTheHint() {
        assertEquals("From WSDOT. Each picture updates about every 5 minutes.", Cameras.HINT)
        assertEquals("All cameras on WSDOT's travel map", Cameras.MAP_LINK)
        assertTrue(Sources.TRAVEL_MAP.startsWith("https://wsdot.com/Travel/Real-time/Map/?layers=traffic-flow%2Calert%2Ccamera"))
    }

    @Test fun linkToTheTravelMapZoomedInOnTheCamera() {
        val c = cameras[0]
        val url = URI(Cameras.mapLink(c))
        assertEquals("https://wsdot.com/Travel/Real-time/Map/", "${url.scheme}://${url.host}${url.path}")
        val query = url.rawQuery.split("&").associate { it.substringBefore("=") to URLDecoder.decode(it.substringAfter("="), "UTF-8") }
        assertEquals("traffic-flow,alert,camera", query["layers"])
        val (west, south, east, north) = query.getValue("extent").split(",").map { it.toDouble() }
        assertTrue(west < c.lon && east > c.lon && south < c.lat && north > c.lat)
        assertEquals(0.012, east - west, 1e-5)
        // The same text the web page builds
        assertEquals(
            "https://wsdot.com/Travel/Real-time/Map/?layers=traffic-flow%2Calert%2Ccamera&base=wsdot&extent=-122.33089%2C47.59770%2C-122.31889%2C47.60570&fromLink=true",
            Cameras.mapLink(c),
        )
    }
}

/** The server saved its own verdict with each fixture. The app's port has to reach the same one. */
class MatchesServerTest {
    @Test fun everyFixtureGetsTheServersVerdict() {
        for (name in FIXTURES) {
            val f = fixture(name)
            val current = f.current
            val at = java.time.Instant.ofEpochMilli((current["at"] as Double).toLong())
            val s = Snapshot.make(f.status, at)
            assertEquals(current["verdict"], s.check.verdict.name.lowercase(), name)
            assertEquals(current["scheduled"], s.check.scheduled.name.lowercase(), name)
            assertEquals(current["nextText"], s.nextText, name)
            val next = current["next"] as Map<*, *>
            assertEquals((next["at"] as Double).toLong(), s.next!!.at.toEpochMilli(), name)
            val concerns = (current["concerns"] as List<*>).map { it as Map<*, *> }
            assertEquals(concerns.map { it["title"] to it["detail"] }, s.check.concerns.map { it.title to it.detail }, name)
        }
    }
}
