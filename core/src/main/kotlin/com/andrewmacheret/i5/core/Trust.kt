package com.andrewmacheret.i5.core

import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/** A reason the app can't vouch for what it shows. */
data class Concern(
    val title: String,
    val detail: String,
    val link: String? = null,
    /** True for "data is old" problems that a refresh in progress may fix */
    val fixedByRefresh: Boolean = false,
)

data class Assessment(
    val verdict: Verdict,
    /** What the posted schedule says right now, trusted or not */
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
    /** Data older than this is treated as stale */
    val MAX_AGE: Duration = Duration.ofHours(2)

    private val CLOCK_SLACK: Duration = Duration.ofMinutes(5)

    /** `refreshing` means a download is in flight, so old data shows as "checking" for a moment. */
    fun assess(result: FetchResult, now: Instant, refreshing: Boolean = false): Assessment {
        val today = Seattle.day(now)
        val concerns = dataConcerns(result, now) + dayConcerns(result.info, today, today)
        val verdict = when {
            concerns.isEmpty() -> Assessment.Verdict.CONFIDENT
            refreshing && concerns.all { it.fixedByRefresh } -> Assessment.Verdict.CHECKING
            else -> Assessment.Verdict.UNCERTAIN
        }
        return Assessment(verdict, Engine(result.info).status(now), concerns)
    }

    /** The moment the data stops being fresh */
    fun staleAt(result: FetchResult): Instant? = result.source.fetchedAt?.plus(MAX_AGE)

    /** Problems with the data as a whole. */
    fun dataConcerns(result: FetchResult, now: Instant): List<Concern> {
        val out = mutableListOf<Concern>()
        val at = result.source.fetchedAt
        if (at == null) {
            out += Concern(
                "Can't reach WSDOT",
                "Only the schedule built into the app is available, and it may be out of date.",
                Sources.EXPRESS_LANES,
                fixedByRefresh = !result.pageChanged,
            )
        } else if (Duration.between(at, now) > MAX_AGE) {
            out += Concern(
                "Data is ${Format.countdown(now, at)} old",
                "Last read WSDOT's page at ${Format.whenText(at, now)}. The app keeps retrying.",
                Sources.EXPRESS_LANES,
                fixedByRefresh = true,
            )
        } else if (at.isAfter(now.plus(CLOCK_SLACK))) {
            // The phone's clock went backwards, so the data's age is unknown
            out += Concern(
                "The phone's clock changed",
                "WSDOT's page was read at a time that hasn't happened yet. The app will read it again.",
                Sources.EXPRESS_LANES,
                fixedByRefresh = true,
            )
        }
        if (result.pageChanged) {
            out += Concern("WSDOT's page changed", "The app couldn't read the new page, so it's using an older saved copy.", Sources.EXPRESS_LANES)
        }
        for (w in result.info.warnings) {
            out += Concern("Part of WSDOT's page couldn't be read", w, Sources.EXPRESS_LANES)
        }
        scheduleProblem(result.info.schedule)?.let { out += Concern("Schedule looks wrong", it, Sources.EXPRESS_LANES) }
        return out
    }

    /** Things that might make one day differ from the regular schedule. */
    fun dayConcerns(info: ExpressLanesInfo, day: LocalDate, today: LocalDate): List<Concern> {
        val out = mutableListOf<Concern>()
        Holidays.name(day)?.let {
            out += Concern("$it may run a holiday schedule", "WSDOT doesn't post holiday schedules on its express lanes page.")
        }
        for (a in info.advisories) {
            if (applies(a.days, day, today)) out += Concern("WSDOT posted a notice", a.text, a.link ?: Sources.EXPRESS_LANES)
        }
        for (e in info.events) {
            if (e.closure == null && applies(e.days, day, today)) {
                out += Concern(
                    e.title,
                    e.detail.ifEmpty { "The app couldn't tell exactly when this happens." },
                    e.link ?: Sources.EXPRESS_LANES,
                )
            }
        }
        val early = info.earlyClose(day)
        if (early != null && early.time == null) {
            out += Concern(
                "Closing early, time unknown",
                "WSDOT lists this as an early closure day but the app couldn't read the time.",
                Sources.EXPRESS_LANES,
            )
        }
        return out
    }

    /** Undated notices count for today only, since they're on the page right now. */
    private fun applies(days: DayRange?, day: LocalDate, today: LocalDate) = days?.contains(day) ?: (day == today)

    fun scheduleProblem(s: WeeklySchedule): String? {
        for ((name, list) in listOf("weekday" to s.weekday, "weekend" to s.weekend)) {
            if (list.isEmpty()) return "No $name hours found."
            val sorted = list.sortedBy { it.start }
            sorted.forEachIndexed { i, w ->
                if (w.start >= w.end) return "A $name time range ends before it starts."
                if (i > 0 && sorted[i - 1].end > w.start) return "Two $name time ranges overlap."
                if (!w.status.isOpen) return "A $name time range has no direction."
            }
        }
        return null
    }
}
