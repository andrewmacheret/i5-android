package com.andrewmacheret.i5.core

import java.net.URI
import java.time.Duration

/**
 * The words on the detail screen, worked out from a snapshot. Matches i5-client's Page.tsx,
 * so the app and the web page say the same thing. The screen only lays these out.
 */
object Page {
    /** "Planned changes" lists notices with a time starting within this many days */
    val PLANNED_DAYS: Duration = Duration.ofDays(14)

    /** Times listed per notice before the rest fold behind "…" */
    const val MAX_TIMES = 20

    data class Header(
        val title: String,
        val subtitle: String,
        /** The color to use, or null for the warning color */
        val tone: LaneStatus?,
    )

    /** `loading` is true before the app has any answer from the server */
    fun header(s: Snapshot, loading: Boolean): Header {
        val check = s.check
        val next = s.next
        return when {
            loading -> Header("Loading…", "Asking the server", LaneStatus.UNKNOWN)
            check.verdict == Assessment.Verdict.CONFIDENT -> Header(
                check.scheduled.title,
                when {
                    next == null -> "No changes coming up"
                    next.to == LaneStatus.UNKNOWN -> "No changes before ${Format.shortDate(next.at)}"
                    else -> "${Snapshot.verb(check.scheduled, next.to)} at ${Format.whenText(next.at, s.now)} · ${Format.countdown(next.at, s.now)}"
                },
                check.scheduled,
            )
            check.verdict == Assessment.Verdict.CHECKING -> Header(
                "Checking…",
                if (check.concerns.all { it.checking }) "Reading WSDOT's latest update" else "Asking the server",
                LaneStatus.UNKNOWN,
            )
            else -> Header(
                "Can't confirm",
                if (check.scheduled == LaneStatus.UNKNOWN) "Don't rely on the app right now"
                else "Expected ${check.scheduled.title.lowercase()}, but don't rely on it",
                null,
            )
        }
    }

    const val LIVE_SOURCES = "Check a live source before you drive:"

    enum class Tone { BAD, WARN, MUTED }

    fun tone(kind: String) = when (kind) {
        "closed" -> Tone.BAD
        "other" -> Tone.MUTED
        else -> Tone.WARN
    }

    data class NoticeItem(val notice: Notice, val text: String, val tone: Tone)

    /** Notices in effect now or starting later today */
    fun notices(s: Snapshot): List<NoticeItem> {
        val notices = s.status.published?.notices ?: return emptyList()
        val endOfToday = Seattle.startOfDay(s.today.plusDays(1))
        return notices.mapNotNull { n ->
            val span = n.spans.firstOrNull { it.start != null && it.start < endOfToday && (it.end == null || it.end > s.now) }
                ?: return@mapNotNull null
            val start = span.start!!
            val end = span.end
            val until = if (end == null) "with no end given" else "until ${Format.whenText(end, s.now)}"
            var text = if (start <= s.now) "Now $until." else "From ${Format.clock(start)} $until."
            if (n.endCertainty != "exact" && end != null) text += " It could last longer."
            NoticeItem(n, text, tone(n.kind))
        }
    }

    fun timelineTitle(s: Snapshot) =
        if (s.check.verdict == Assessment.Verdict.CONFIDENT) "Schedule" else "Expected schedule · not confirmed"

    /** The first doubt for the rest of today, shown next to the "Today" bar */
    fun todayWarning(s: Snapshot): String? {
        val p = s.status.published ?: return null
        return Timeline.doubtsBetween(p, s.now, Seattle.startOfDay(s.today.plusDays(1))).firstOrNull()?.title
    }

    fun tomorrowWarning(s: Snapshot): String? = s.tomorrowDoubts.firstOrNull()?.title

    /** "Today: Southbound 5 AM to 11 AM, Northbound 11:15 AM to 11 PM", for screen readers */
    fun timelineSummary(label: String, segments: List<DaySegment>): String {
        val open = segments.filter { it.status != LaneStatus.CLOSED }
        val summary = if (open.isEmpty()) "Closed all day" else open.joinToString { "${it.status.title} ${it.start.label} to ${it.end.label}" }
        return "$label: $summary"
    }

    data class TripRow(val label: String, val express: String, val saves: String?, val regular: String, val usual: String?)

    data class TravelTimes(val rows: List<TripRow>, val hint: String)

    /** Express lanes against the regular lanes, from WSDOT's sensors */
    fun travelTimes(s: Snapshot): TravelTimes? {
        val live = s.status.live ?: return null
        val trips = live.trips ?: return null
        if (Duration.between(live.at, s.now) > Trust.LIVE_MAX_AGE) return null
        val pairs = listOf("Seattle → Everett" to trips.northbound, "Everett → Seattle" to trips.southbound)
        if (pairs.all { (_, t) -> t.express == null && t.regular == null }) return null
        fun min(m: Int?) = if (m == null) "—" else "$m min"
        val rows = pairs.map { (label, t) ->
            val saves = if (t.express != null && t.regular != null) t.regular - t.express else 0
            TripRow(label, min(t.express), if (saves >= 2) "saves $saves min" else null, min(t.regular), t.usual?.let { "usually $it" })
        }
        var hint = "Downtown Seattle to Everett, from WSDOT's sensors at ${Format.clock(live.at)}."
        if (pairs.any { (_, t) -> t.express == null }) hint += " A dash means the express lanes aren't open that way."
        return TravelTimes(rows, hint)
    }

    /** The next changes, leaving out the end of the timeline */
    fun comingUp(s: Snapshot): List<Change> = s.changes.filter { it.to != LaneStatus.UNKNOWN }

    const val DOUBTFUL_HINT = "⚠ marks days that may not go as planned."

    data class PlannedItem(
        val title: String,
        val times: List<String>,
        /** Shown in the warning color */
        val note: String?,
        /** "WSDOT's words" or "WSDOT's alert", with the notice's own text */
        val detailLabel: String?,
        val detail: String?,
        /** "More info", left out when it's just WSDOT's express lanes page */
        val link: String?,
    )

    fun spanText(span: NoticeSpan): String {
        val start = span.start ?: return "No dates given"
        val from = "${Format.shortDate(start)} ${Format.clock(start)}"
        val end = span.end ?: return "From $from, no end given"
        val sameDay = Seattle.day(end) == Seattle.day(start)
        return "$from → ${if (sameDay) "" else "${Format.shortDate(end)} "}${Format.clock(end)}"
    }

    fun certaintyText(n: Notice): String? {
        if (n.kind == "unclear") return "Exact times unclear. The app won't confirm a direction then."
        val parts = mutableListOf<String>()
        if (n.startCertainty == "approximate") parts += "Start time is approximate."
        if (n.endCertainty == "approximate") parts += "End time is approximate."
        if (n.endCertainty == "unknown" && n.spans.any { it.end != null }) parts += "It could last longer."
        return if (parts.isEmpty()) null else parts.joinToString(" ")
    }

    /** Notices with times in the next two weeks, and holidays with no notice */
    fun planned(s: Snapshot): List<PlannedItem> {
        val p = s.status.published ?: return emptyList()
        val until = s.now.plus(PLANNED_DAYS)
        val notices = p.notices
            .map { n -> n to n.spans.filter { it.end == null || it.end > s.now } }
            .filter { (_, spans) -> spans.any { it.start == null || it.start < until } }
            .map { (n, spans) ->
                PlannedItem(
                    title = n.title,
                    times = spans.map(::spanText),
                    note = certaintyText(n),
                    detailLabel = if (n.source == "alert") "WSDOT's alert" else "WSDOT's words",
                    detail = n.detail,
                    link = n.link?.takeIf { it != Sources.EXPRESS_LANES },
                )
            }
        val holidays = p.doubts
            .filter { it.source == "holiday" && it.end > s.now && it.start < until }
            .map { PlannedItem(it.title, emptyList(), it.detail, null, null, null) }
        return notices + holidays
    }

    /** The weekly schedule, and a note when WSDOT's page doesn't list one */
    fun regularSchedule(s: Snapshot): Pair<WeeklySchedule, String?> {
        val p = s.status.published
        val listed = p?.schedule
        val hint = if (p != null && listed == null) {
            "WSDOT's page doesn't list its regular schedule right now. These are the hours it listed in Nov. 2026."
        } else {
            null
        }
        return (listed ?: WeeklySchedule.BUILT_IN) to hint
    }

    data class SourceItem(val title: String, val subtitle: String, val url: String)

    fun sources(s: Snapshot): List<SourceItem> {
        val p = s.status.published
        val seen = mutableSetOf(Sources.EXPRESS_LANES)
        val links = (p?.notices ?: emptyList()).filter { it.source == "page" }.mapNotNull { it.link }.filter { seen.add(it) }
        val alerts = (p?.alerts ?: emptyList()).mapNotNull { it.link }
        return buildList {
            add(SourceItem("WSDOT express lanes schedule", "Where this data comes from", Sources.EXPRESS_LANES))
            for (l in links) add(SourceItem("Linked from WSDOT's page", runCatching { URI(l).host }.getOrNull() ?: l, l))
            for (a in alerts) add(SourceItem("WSDOT highway alert", "About the express lanes", a))
            add(SourceItem("WSDOT real-time travel map", "Live traffic, alerts and cameras", Sources.TRAVEL_MAP))
            add(SourceItem("WSDOT Traffic on Bluesky", "Unplanned closures get posted here first", Sources.WSDOT_TRAFFIC))
        }
    }

    const val ABOUT =
        "WSDOT doesn't publish a live feed for the express lanes. The server reads WSDOT's page and highway alerts every " +
            "15 minutes, two AI models read each new notice, and code checks their answers and works out the times. The app " +
            "only shows a direction when the server read WSDOT recently, both models agree, nothing about now is unclear, and " +
            "WSDOT's travel time sensors don't show traffic going the other way. Otherwise it shows ⚠. It can't know about " +
            "closures WSDOT hasn't posted yet. Not affiliated with WSDOT. Always follow posted signs."

    data class DataStatus(
        val text: String,
        val warn: Boolean,
        /** Which models read the notices */
        val reading: String?,
        /** What WSDOT's sensors measured */
        val live: String?,
    )

    fun readingText(s: Snapshot): String? {
        val page = s.status.published?.page ?: return null
        return when {
            page.status == "pending" -> "Reading WSDOT's latest update…"
            page.status == "failed" -> "Couldn't read WSDOT's latest update, so earlier notices are shown."
            "reviewed" in page.models -> "Notices checked by hand."
            else -> "Notices read by ${page.models.joinToString(" and ")}."
        }
    }

    fun liveText(s: Snapshot): String? {
        val live = s.status.live ?: return null
        if (Duration.between(live.at, s.now) > Trust.LIVE_MAX_AGE) return null
        val parts = listOfNotNull(
            live.northbound?.takeIf { it != 0 }?.let { "$it min northbound" },
            live.southbound?.takeIf { it != 0 }?.let { "$it min southbound" },
        )
        val what = if (parts.isEmpty()) "no traffic" else parts.joinToString(", ")
        return "WSDOT's sensors at ${Format.clock(live.at)}: $what through the express lanes."
    }

    /** `loading` is true before the app has any answer from the server */
    fun dataStatus(s: Snapshot, loading: Boolean): DataStatus {
        val st = s.status
        val fetchedAt = st.fetchedAt
        val (text, warn) = when {
            loading -> "Loading…" to false
            fetchedAt != null && st.error == null -> "Checked WSDOT at ${Format.whenText(fetchedAt, s.now)}" to false
            fetchedAt != null ->
                "WSDOT didn't answer at ${Format.clock(st.checkedAt ?: s.now)}. Using what it said at ${Format.whenText(fetchedAt, s.now)}." to true
            st.published != null -> "The server hasn't read WSDOT's page yet." to true
            else -> "The server hasn't published a schedule yet." to true
        }
        return DataStatus(text, warn, readingText(s), liveText(s))
    }

    /** The app's own download error, worded like the web page's */
    fun loadErrorText(s: Snapshot): String? = s.loadError?.let { "Couldn't reach the server: $it" }
}
