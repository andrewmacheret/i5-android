package com.andrewmacheret.i5.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** A Seattle wall clock moment, like "2026-11-18T12:00" */
fun t(s: String): Instant = Seattle.instant(LocalDate.parse(s.substring(0, 10)), TimeOfDay.of(s.substring(11, 13).toInt(), s.substring(14, 16).toInt()))

fun seg(start: String, end: String, status: LaneStatus) = Segment(t(start), t(end), status)

/** A small published timeline around Wed, Nov 18, 2026, the same one the server's core tests use */
fun published(
    doubts: List<Doubt> = emptyList(),
    notices: List<Notice> = emptyList(),
    horizon: Horizon = Horizon(t("2026-11-17T00:00"), t("2026-11-20T00:00")),
    segments: List<Segment> = listOf(
        seg("2026-11-17T00:00", "2026-11-17T05:00", LaneStatus.CLOSED),
        seg("2026-11-17T05:00", "2026-11-17T11:00", LaneStatus.SOUTHBOUND),
        seg("2026-11-17T11:00", "2026-11-17T11:15", LaneStatus.CLOSED),
        seg("2026-11-17T11:15", "2026-11-17T23:00", LaneStatus.NORTHBOUND),
        seg("2026-11-17T23:00", "2026-11-18T05:00", LaneStatus.CLOSED),
        seg("2026-11-18T05:00", "2026-11-18T11:00", LaneStatus.SOUTHBOUND),
        seg("2026-11-18T11:00", "2026-11-18T11:15", LaneStatus.CLOSED),
        seg("2026-11-18T11:15", "2026-11-18T21:00", LaneStatus.NORTHBOUND),
        seg("2026-11-18T21:00", "2026-11-19T05:00", LaneStatus.CLOSED),
        seg("2026-11-19T05:00", "2026-11-20T00:00", LaneStatus.SOUTHBOUND),
    ),
) = Published(
    publishedAt = t("2026-11-18T08:00"),
    horizon = horizon,
    schedule = null,
    segments = segments,
    doubts = doubts,
    notices = notices,
    page = DocumentInfo("page", "abc", t("2026-11-18T07:00"), listOf("m"), "answered", null),
    alerts = emptyList(),
)

/** Read 5 minutes before `now`, so it's always fresh */
fun status(now: Instant, p: Published? = published(), live: LiveReading? = null) =
    ServerStatus(p, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)), null, live)

fun doubt(start: String, end: String, title: String = "A doubt", checking: Boolean = false, source: String = "page") =
    Doubt(t(start), t(end), title, "Why", null, source, checking)

/** A real /status answer saved by i5-server-lambdas, and the moment to look at it */
class Fixture(val name: String, val about: String, val at: Instant, val status: ServerStatus, val raw: Map<*, *>) {
    fun snap(at: Instant = this.at, status: ServerStatus = this.status) = Snapshot.make(status, at)

    /** The server's own verdict for this moment, to check the port against */
    val current: Map<*, *> get() = (raw["status"] as Map<*, *>)["current"] as Map<*, *>
}

val FIXTURES = listOf("checking", "confident", "failed", "holiday", "revive-2026", "stale", "winter-2022")

fun fixture(name: String): Fixture {
    val text = requireNotNull(object {}.javaClass.getResource("/fixtures/$name.json")) { "missing fixture $name" }.readText()
    val f = StatusJson.fixture(text)
    return Fixture(name, f.about, f.at, f.status, f.raw)
}
