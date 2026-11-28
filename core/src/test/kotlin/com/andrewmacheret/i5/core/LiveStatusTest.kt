package com.andrewmacheret.i5.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Reads the real server. The daily canary runs it with I5_LIVE=1, normal test runs skip it. */
class LiveStatusTest {
    @Test fun readsTheServersStatus() {
        assumeTrue(System.getenv("I5_LIVE") == "1", "set I5_LIVE=1 to read the I-5 server")
        val now = Instant.now()
        val status = StatusJson.decode(Fetcher.httpGet(Sources.STATUS))
        val p = requireNotNull(status.published) { "the server hasn't published a timeline" }
        assertTrue(p.horizon.start <= now && now < p.horizon.end, "the timeline doesn't cover now: ${p.horizon}")
        assertTrue(p.segments.isNotEmpty(), "the timeline has no segments")
        val today = Seattle.day(now)
        assertTrue(Timeline.daySegments(p, today).none { it.status == LaneStatus.UNKNOWN }, "the timeline doesn't cover all of today")
        val snap = Snapshot.make(status, now)
        println("Verdict: ${snap.check.verdict}, scheduled ${snap.check.scheduled}, ${snap.nextText}, concerns ${snap.check.concerns.map { it.title }}")
        assertTrue(snap.check.scheduled != LaneStatus.UNKNOWN, "no status for now")
    }
}
