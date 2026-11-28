package com.andrewmacheret.i5.core

import java.io.File
import java.nio.file.Files
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DecodeTest {
    @Test fun readsARealAnswer() {
        val s = fixture("confident").status
        val p = assertNotNull(s.published)
        assertEquals(241, p.segments.size)
        assertEquals(Instant.ofEpochMilli(1796166000000), s.fetchedAt)
        assertNull(s.error)
        assertEquals(LaneStatus.SOUTHBOUND, p.schedule!!.weekday[0].status)
        assertEquals(TimeOfDay.of(11, 15), p.schedule.weekday[1].start)
        assertEquals(listOf("gemini-4-hydrogen", "gpt-oss-120b"), p.page!!.models)
        assertEquals("alert", p.alerts.single().kind)
        val closure = p.notices.first()
        assertEquals("Weekend-long full closure", closure.title)
        assertEquals("approximate", closure.startCertainty)
        assertTrue(closure.detail.contains("\n"))
        assertEquals(TripTimes(18, 26, 22), s.live!!.trips!!.northbound)
        assertEquals(TripTimes(null, 15, 16), s.live.trips.southbound)
        assertEquals(4, s.cameras.size)
        assertEquals(Camera(9541, "Roanoke St Express Lanes", 168.0, true, 47.641693, -122.322819, "https://images.wsdot.wa.gov/nw/005vc16803.jpg"), s.cameras[1])
    }

    @Test fun readsEveryFixture() {
        for (name in FIXTURES) {
            val f = fixture(name)
            assertNotNull(f.status.published, name)
            assertTrue(f.about.isNotEmpty())
        }
        assertEquals("WSDOT's page returned HTTP 503", fixture("stale").status.error)
        assertTrue(fixture("checking").status.published!!.doubts.single().checking)
        assertNull(fixture("failed").status.published!!.notices.first().spans.single().start)
    }

    @Test fun toleratesUnknownAndMissingFields() {
        val s = StatusJson.decode(
            """
            {"published": {"horizon": {"start": 1000, "end": 2000}, "segments": [
                {"start": 1000, "end": 1500, "status": "northbound", "lanes": 3},
                {"start": 1500, "end": 2000, "status": "reversible"},
                {"start": 1500, "status": "closed"}
              ], "future": {"a": [1, 2.5e3, -1, true, null]}},
             "fetchedAt": 1500, "somethingNew": "hi", "live": {"at": 1500, "northbound": 12.4}}
            """.trimIndent(),
        )
        val p = assertNotNull(s.published)
        assertEquals(Instant.ofEpochMilli(1000), p.publishedAt)
        // A status the app doesn't know is unknown, and a segment with no end is skipped
        assertEquals(listOf(LaneStatus.NORTHBOUND, LaneStatus.UNKNOWN), p.segments.map { it.status })
        assertEquals(emptyList(), p.doubts)
        assertEquals(emptyList(), p.notices)
        assertNull(p.page)
        assertNull(p.schedule)
        assertNull(s.checkedAt)
        assertEquals(LiveReading(Instant.ofEpochMilli(1500), 12, null, null), s.live)
        assertEquals(emptyList(), s.cameras)
    }

    @Test fun readsEscapes() {
        val s = StatusJson.decode("""{"published": null, "fetchedAt": null, "checkedAt": null, "error": "a \"quote\", \u00e9 and \ud83d\ude97\n", "live": null}""")
        assertEquals("a \"quote\", é and \uD83D\uDE97\n", s.error)
        assertEquals(ServerStatus.EMPTY.copy(error = s.error), s)
    }

    @Test fun refusesWhatIsntAJsonObject() {
        assertFailsWith<IllegalArgumentException> { StatusJson.decode("<html>Bad gateway</html>") }
        assertFailsWith<IllegalArgumentException> { StatusJson.decode("[1, 2]") }
        assertFailsWith<IllegalArgumentException> { StatusJson.decode("""{"published": """) }
        assertFailsWith<IllegalArgumentException> { StatusJson.decode("""{"a": 1} extra""") }
    }
}

class FetcherTest {
    private fun dir(): File = Files.createTempDirectory("i5").toFile()

    private val answer = """{"published": {"horizon": {"start": 1000, "end": 2000}, "segments": []}, "fetchedAt": 1500, "checkedAt": 1500, "error": null, "live": null}"""

    @Test fun savesTheAnswerAndUsesItWhenTheServerCantBeReached() {
        val d = dir()
        val now = Instant.parse("2026-11-18T20:00:00Z")
        val first = Fetcher(d) { answer }.load(now)
        assertEquals(now, first.loadedAt)
        assertNull(first.error)
        assertEquals(Instant.ofEpochMilli(1500), first.status.fetchedAt)

        val offline = Fetcher(d) { throw java.io.IOException("Unable to resolve host") }.load(now.plusSeconds(600))
        assertEquals("Unable to resolve host", offline.error)
        assertEquals(first.status, offline.status)
        assertEquals(now, offline.loadedAt)
    }

    @Test fun keepsTheSavedCopyWhenTheAnswerIsBroken() {
        val d = dir()
        Fetcher(d) { answer }.load()
        val broken = Fetcher(d) { "<html>502</html>" }.load()
        assertEquals("The server sent something the app couldn't read", broken.error)
        assertNotNull(broken.status.published)
        val empty = Fetcher(d) { """{"published": null}""" }.load()
        assertEquals("The server hasn't published a schedule yet", empty.error)
        assertNotNull(empty.status.published)
    }

    @Test fun startsEmpty() {
        val r = Fetcher(dir()) { throw java.io.IOException("offline") }.load()
        assertEquals(ServerStatus.EMPTY, r.status)
        assertNull(r.loadedAt)
        assertEquals("offline", r.error)
        assertEquals(ServerStatus.EMPTY, Fetcher(dir()).saved().status)
    }
}
