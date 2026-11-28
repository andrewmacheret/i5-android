package com.andrewmacheret.i5.core

import java.time.Instant
import kotlin.math.roundToInt

/**
 * Reads the server's GET /status answer. Unknown fields are ignored and missing ones get defaults,
 * so a newer server doesn't break older copies of the app. Items missing what they need are skipped.
 */
object StatusJson {
    /** Throws when the text isn't a JSON object */
    fun decode(text: String): ServerStatus =
        status(Json.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("The server's answer isn't a JSON object"))

    /** A saved test case from the server's repo: `{ about, at, status }` */
    data class Fixture(val about: String, val at: Instant, val status: ServerStatus, val raw: Map<*, *>)

    fun fixture(text: String): Fixture {
        val root = Json.parse(text) as? Map<*, *> ?: throw IllegalArgumentException("Not a fixture")
        return Fixture(
            root.str("about") ?: "",
            root.instant("at") ?: throw IllegalArgumentException("Fixture has no time"),
            status(root.obj("status") ?: throw IllegalArgumentException("Fixture has no status")),
            root,
        )
    }

    private fun status(root: Map<*, *>): ServerStatus {
        return ServerStatus(
            published = root.obj("published")?.let(::published),
            fetchedAt = root.instant("fetchedAt"),
            checkedAt = root.instant("checkedAt"),
            error = root.str("error"),
            live = root.obj("live")?.let(::live),
            cameras = root.objects("cameras").mapNotNull(::camera),
        )
    }

    private fun published(m: Map<*, *>): Published? {
        val horizon = m.obj("horizon") ?: return null
        val start = horizon.instant("start") ?: return null
        val end = horizon.instant("end") ?: return null
        return Published(
            publishedAt = m.instant("publishedAt") ?: start,
            horizon = Horizon(start, end),
            schedule = m.obj("schedule")?.let { s -> WeeklySchedule(s.objects("weekday").mapNotNull(::window), s.objects("weekend").mapNotNull(::window)) },
            segments = m.objects("segments").mapNotNull(::segment),
            doubts = m.objects("doubts").mapNotNull(::doubt),
            notices = m.objects("notices").mapNotNull(::notice),
            page = m.obj("page")?.let(::document),
            alerts = m.objects("alerts").map(::document),
        )
    }

    private fun window(m: Map<*, *>): LaneWindow? {
        val start = m.int("start") ?: return null
        val end = m.int("end") ?: return null
        if (start !in 0..1440 || end !in 0..1440) return null
        return LaneWindow(LaneStatus.of(m.str("status")), TimeOfDay(start), TimeOfDay(end))
    }

    private fun segment(m: Map<*, *>): Segment? {
        val start = m.instant("start") ?: return null
        val end = m.instant("end") ?: return null
        return Segment(start, end, LaneStatus.of(m.str("status")))
    }

    private fun doubt(m: Map<*, *>): Doubt? {
        val start = m.instant("start") ?: return null
        val end = m.instant("end") ?: return null
        return Doubt(
            start = start,
            end = end,
            title = m.str("title") ?: "Something may be different",
            detail = m.str("detail") ?: "",
            link = m.str("link"),
            source = m.str("source") ?: "page",
            checking = m["checking"] == true,
        )
    }

    private fun notice(m: Map<*, *>): Notice? {
        val title = m.str("title") ?: return null
        return Notice(
            title = title,
            detail = m.str("detail") ?: "",
            link = m.str("link"),
            kind = m.str("kind") ?: "other",
            spans = m.objects("spans").map { NoticeSpan(it.instant("start"), it.instant("end")) },
            startCertainty = m.str("startCertainty") ?: "unknown",
            endCertainty = m.str("endCertainty") ?: "unknown",
            source = m.str("source") ?: "page",
        )
    }

    private fun document(m: Map<*, *>) = DocumentInfo(
        kind = m.str("kind") ?: "page",
        hash = m.str("hash") ?: "",
        firstSeen = m.instant("firstSeen"),
        models = (m["models"] as? List<*>)?.filterIsInstance<String>() ?: emptyList(),
        status = m.str("status") ?: "answered",
        link = m.str("link"),
    )

    private fun live(m: Map<*, *>): LiveReading? {
        val at = m.instant("at") ?: return null
        val trips = m.obj("trips")?.let { t -> Trips(trip(t.obj("northbound")), trip(t.obj("southbound"))) }
        return LiveReading(at, m.int("northbound"), m.int("southbound"), trips)
    }

    private fun trip(m: Map<*, *>?) = TripTimes(m?.int("express"), m?.int("regular"), m?.int("usual"))

    private fun camera(m: Map<*, *>): Camera? {
        val image = m.str("image") ?: return null
        val lat = m.num("lat") ?: return null
        val lon = m.num("lon") ?: return null
        return Camera(
            id = m.num("id")?.toLong() ?: 0,
            name = m.str("name") ?: "Camera",
            milepost = m.num("milepost") ?: 0.0,
            express = m["express"] == true,
            lat = lat,
            lon = lon,
            image = image,
        )
    }

    private fun Map<*, *>.str(key: String) = this[key] as? String
    private fun Map<*, *>.num(key: String) = (this[key] as? Double)?.takeIf { it.isFinite() }
    private fun Map<*, *>.int(key: String) = num(key)?.roundToInt()
    private fun Map<*, *>.instant(key: String) = num(key)?.let { Instant.ofEpochMilli(it.toLong()) }
    private fun Map<*, *>.obj(key: String) = this[key] as? Map<*, *>
    private fun Map<*, *>.objects(key: String) = (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>() ?: emptyList()
}
