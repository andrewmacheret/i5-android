package com.andrewmacheret.i5.core

import java.time.Duration
import java.time.Instant

/** A reason the app can't vouch for what it shows. */
data class Concern(
    val title: String,
    val detail: String,
    val link: String? = null,
    /** True while the server is still reading a new page or alert, which usually clears up in minutes */
    val checking: Boolean = false,
    /** True for "no data" and "data is old" problems that a download in progress may fix */
    val fixedByRefresh: Boolean = false,
)

data class Assessment(
    val verdict: Verdict,
    /** What the published timeline says right now, trusted or not */
    val scheduled: LaneStatus,
    val concerns: List<Concern>,
) {
    enum class Verdict { CONFIDENT, CHECKING, UNCERTAIN }

    /** What the widget should show. Never a direction unless confident. */
    val shown: LaneStatus? get() = if (verdict == Verdict.CONFIDENT) scheduled else null
}

/**
 * Decides when the app is allowed to show a confident N or S.
 * The rule is simple: if anything looks off, say so instead of guessing.
 */
object Trust {
    /** The server reads WSDOT every 15 minutes, so data older than this means it's stuck */
    val MAX_AGE: Duration = Duration.ofHours(2)

    /** Sensor readings older than this are ignored */
    val LIVE_MAX_AGE: Duration = Duration.ofMinutes(30)

    /** WSDOT's travel times lag a switch by up to this long, so readings this soon after one are ignored */
    val LIVE_LAG: Duration = Duration.ofMinutes(30)

    private val CLOCK_SLACK: Duration = Duration.ofMinutes(5)

    /**
     * `refreshing` means the app is downloading a new copy right now, so problems that
     * download may fix show as "checking" for a moment instead of a warning.
     */
    fun assess(status: ServerStatus, now: Instant, refreshing: Boolean = false): Assessment {
        val p = status.published
        val scheduled = if (p != null) Timeline.statusAt(p, now) else LaneStatus.UNKNOWN
        val concerns = dataConcerns(status, now) + (p?.let { momentConcerns(it, now) } ?: emptyList()) + liveConcerns(status, now)
        val verdict = when {
            concerns.isEmpty() && scheduled != LaneStatus.UNKNOWN -> Assessment.Verdict.CONFIDENT
            concerns.isNotEmpty() && concerns.all { it.checking } -> Assessment.Verdict.CHECKING
            refreshing && concerns.isNotEmpty() && concerns.all { it.checking || it.fixedByRefresh } -> Assessment.Verdict.CHECKING
            else -> Assessment.Verdict.UNCERTAIN
        }
        return Assessment(verdict, scheduled, concerns)
    }

    /** Problems with the data as a whole */
    fun dataConcerns(status: ServerStatus, now: Instant): List<Concern> {
        val out = mutableListOf<Concern>()
        val published = status.published
        if (published == null) {
            out += Concern("Can't reach the server", "The server hasn't published anything yet.", Sources.EXPRESS_LANES, fixedByRefresh = true)
            return out
        }
        val fetchedAt = status.fetchedAt
        if (fetchedAt == null || Duration.between(fetchedAt, now) > MAX_AGE) {
            val at = fetchedAt ?: published.publishedAt
            out += Concern(
                "Data is ${Format.countdown(now, at)} old",
                "The server last read WSDOT's page at ${Format.whenText(at, now)}.${status.error?.let { " $it." } ?: ""}",
                Sources.EXPRESS_LANES,
                fixedByRefresh = true,
            )
        } else if (Duration.between(now, fetchedAt) > CLOCK_SLACK) {
            out += Concern(
                "This device's clock is off",
                "WSDOT's page was read at a time that hasn't happened yet on this device. Check the clock in your settings.",
                Sources.EXPRESS_LANES,
            )
        }
        if (now >= published.horizon.end || now < published.horizon.start) {
            out += Concern("No schedule for today", "The server's timeline doesn't cover today.", Sources.EXPRESS_LANES)
        }
        return out
    }

    /** The server's doubts about this moment */
    fun momentConcerns(p: Published, now: Instant): List<Concern> =
        Timeline.doubtsAt(p, now).map { Concern(it.title, it.detail, it.link ?: Sources.EXPRESS_LANES, checking = it.checking) }

    enum class Seen { NORTHBOUND, SOUTHBOUND, NONE, BOTH }

    /** What WSDOT's sensors see, compared with the timeline */
    fun liveReadingStatus(r: LiveReading): Seen {
        val n = r.northbound != null && r.northbound > 0
        val s = r.southbound != null && r.southbound > 0
        return when {
            n && s -> Seen.BOTH
            n -> Seen.NORTHBOUND
            s -> Seen.SOUTHBOUND
            else -> Seen.NONE
        }
    }

    fun liveConcerns(status: ServerStatus, now: Instant): List<Concern> {
        val live = status.live ?: return emptyList()
        val published = status.published ?: return emptyList()
        if (Duration.between(live.at, now) > LIVE_MAX_AGE || Duration.between(now, live.at) > CLOCK_SLACK) return emptyList()
        val expected = Timeline.statusAt(published, live.at)
        if (expected == LaneStatus.UNKNOWN) return emptyList()
        // Right after a switch the sensors still show the old direction
        val changed = Timeline.lastChange(published, live.at)
        if (changed != null && Duration.between(changed, live.at) < LIVE_LAG) return emptyList()
        val should = expected.title.lowercase()
        val seen = when (liveReadingStatus(live)) {
            Seen.BOTH -> return emptyList()
            Seen.NONE -> {
                if (!expected.isOpen) return emptyList()
                return listOf(
                    Concern(
                        "No traffic measured",
                        "The lanes should be $should, but WSDOT's sensors measured no traffic through them at ${Format.clock(live.at)}.",
                        Sources.TRAVEL_MAP,
                    ),
                )
            }
            Seen.NORTHBOUND -> LaneStatus.NORTHBOUND
            Seen.SOUTHBOUND -> LaneStatus.SOUTHBOUND
        }
        if (seen == expected) return emptyList()
        val saw = seen.title.lowercase()
        return listOf(
            Concern(
                "WSDOT's sensors show $saw traffic",
                "The lanes should be $should, but WSDOT's sensors measured $saw traffic at ${Format.clock(live.at)}.",
                Sources.TRAVEL_MAP,
            ),
        )
    }

    /** The moment the data stops being fresh, or null if it already isn't */
    fun staleAt(status: ServerStatus): Instant? = status.fetchedAt?.plus(MAX_AGE)

    /**
     * The next moment the verdict can change with no new data: the data going stale,
     * a doubt starting or ending, the timeline running out, or a sensor reading aging out.
     * Lane changes and midnight are handled by the caller.
     */
    fun nextReview(status: ServerStatus, now: Instant): Instant? {
        val p = status.published ?: return null
        val moments = mutableListOf<Instant>()
        staleAt(status)?.let { moments += it }
        status.fetchedAt?.let { moments += it.minus(CLOCK_SLACK) }
        moments += p.horizon.start
        moments += p.horizon.end
        for (d in p.doubts) {
            moments += d.start
            moments += d.end
        }
        status.live?.let {
            moments += it.at.minus(CLOCK_SLACK)
            moments += it.at.plus(LIVE_MAX_AGE)
        }
        return moments.filter { it > now }.minOrNull()
    }
}
