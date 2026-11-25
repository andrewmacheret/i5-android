package com.andrewmacheret.i5.core

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/** Reads WSDOT's real page. The daily canary runs it with I5_LIVE=1, normal test runs skip it. */
class LivePageTest {
    @Test
    fun readsWsdotsPage() {
        assumeTrue(System.getenv("I5_LIVE") == "1", "set I5_LIVE=1 to read WSDOT's page")
        val info = Parser.parse(Fetcher.httpGet(Sources.EXPRESS_LANES), Instant.now())
        assertTrue(info.scheduleFromPage, "couldn't read the schedule from WSDOT's page: ${info.warnings}")
    }
}
