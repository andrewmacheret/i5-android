package com.andrewmacheret.i5.core

import java.time.Instant
import java.time.LocalDate

/** Reads the WSDOT express lanes page into an [ExpressLanesInfo]. */
object Parser {
    fun parse(html: String, now: Instant = Instant.now()): ExpressLanesInfo {
        val weekday = mutableListOf<LaneWindow>()
        val weekend = mutableListOf<LaneWindow>()
        val advisories = mutableListOf<Advisory>()
        val earlyCloses = mutableListOf<EarlyClose>()
        var earlyDefault: TimeOfDay? = null
        val events = mutableListOf<SpecialEvent>()
        val warnings = mutableListOf<String>()

        // Only read the express lanes part of the page, not the site nav and footer
        val all = Html.blocks(html)
        val start = all.indexOfFirst { it.kind == Block.Kind.H2 && it.text.lowercase().contains("express lanes schedule") }
        if (start < 0) return ExpressLanesInfo(warnings = listOf("Couldn't find the schedule section on WSDOT's page"))
        val endOffset = all.drop(start + 1).indexOfFirst { it.kind == Block.Kind.H2 && it.text.lowercase().contains("map") }
        val end = if (endOffset < 0) all.size else start + 1 + endOffset
        val blocks = all.subList(start, end)

        var section = Section.SCHEDULE
        var h3: String? = null
        var pending: SpecialEvent? = null

        fun flushEvent() {
            val e = pending ?: return
            pending = null
            val closures = closures(e, now)
            if (closures.isEmpty()) {
                events += e.copy(days = range(e.title + " " + e.detail, now))
                return
            }
            val spans = closures.map { DayRange(Seattle.day(it.start), Seattle.day(it.end)) }
            closures.zip(spans).forEach { (c, span) -> events += e.copy(closure = c, days = span) }
            // A date the closures don't cover may be another closure the app couldn't read
            val loose = Dates.all(e.title + " " + e.detail, now).filter { d -> spans.none { it.contains(d) } }
            if (loose.isNotEmpty()) events += e.copy(days = DayRange(loose.min(), loose.max()))
        }

        for (b in blocks) {
            val lower = b.text.lowercase()
            when (b.kind) {
                Block.Kind.H2 -> {
                    flushEvent()
                    h3 = null
                    section = when {
                        lower.contains("express lanes schedule") -> Section.SCHEDULE
                        lower.contains("special") -> Section.SPECIAL
                        else -> {
                            warnings += "New section on WSDOT's page: ${b.text}"
                            Section.UNKNOWN
                        }
                    }
                }
                Block.Kind.H3 -> {
                    flushEvent()
                    h3 = lower
                    if (section == Section.SCHEDULE && !isWeekdayHeading(lower) && !isWeekendHeading(lower)) {
                        warnings += "New schedule section on WSDOT's page: ${b.text}"
                    }
                    if (section == Section.SPECIAL && !lower.contains("closing early")) {
                        pending = SpecialEvent(title = b.text, detail = "", link = null)
                    }
                }
                Block.Kind.P, Block.Kind.LI -> when (section) {
                    Section.UNKNOWN -> Unit
                    Section.SCHEDULE -> {
                        if (b.kind == Block.Kind.P) {
                            // Any note in the schedule area could change today's plan
                            advisories += Advisory(b.text, absolute(b.href), range(b.text, now))
                        } else if (!lower.startsWith("closed")) {
                            val heading = h3
                            val w = window(b.text)
                            when {
                                heading == null || w == null -> warnings += "Couldn't read schedule line: ${b.text}"
                                isWeekendHeading(heading) -> weekend += w
                                isWeekdayHeading(heading) -> weekday += w
                            }
                        }
                    }
                    Section.SPECIAL -> {
                        val p = pending
                        if (h3?.contains("closing early") == true) {
                            if (b.kind == Block.Kind.P) {
                                earlyDefault = Times.firstAfter("close at", b.text) ?: earlyDefault
                            } else {
                                val days = Dates.all(b.text, now)
                                if (days.isEmpty()) warnings += "Couldn't read early closure date: ${b.text}"
                                // A date can carry its own time, like "Monday, Nov. 30 at 8 p.m."
                                val own = Times.explicit(b.text)
                                earlyCloses += days.map { EarlyClose(it, own ?: earlyDefault) }
                            }
                        } else if (p != null) {
                            pending = p.copy(
                                detail = if (p.detail.isEmpty()) b.text else p.detail + "\n" + b.text,
                                link = p.link ?: absolute(b.href),
                            )
                        } else {
                            advisories += Advisory(b.text, absolute(b.href), range(b.text, now))
                        }
                    }
                }
            }
        }
        flushEvent()

        val fromPage = weekday.isNotEmpty() && weekend.isNotEmpty()
        if (!fromPage) warnings += "Couldn't read the weekday and weekend schedule on WSDOT's page"
        return ExpressLanesInfo(
            schedule = if (fromPage) WeeklySchedule(weekday, weekend) else WeeklySchedule.BUILT_IN,
            scheduleFromPage = fromPage,
            earlyCloses = earlyCloses.sortedBy { it.day },
            events = events,
            advisories = advisories,
            warnings = warnings,
        )
    }

    private enum class Section { SCHEDULE, SPECIAL, UNKNOWN }

    private fun isWeekdayHeading(s: String) = (s.contains("monday") && s.contains("friday")) || s.contains("weekday")

    private fun isWeekendHeading(s: String) = (s.contains("saturday") && s.contains("sunday")) || s.contains("weekend")

    private val openEndedRegex = Regex("further notice|indefinite|until repairs|until it is safe", RegexOption.IGNORE_CASE)

    // "As of Nov. 16, the lanes are closed" has a start but no end
    private val startsRegex = Regex("\\b(as of|beginning|starting|effective|since)\\b", RegexOption.IGNORE_CASE)

    /** The span of dates a notice talks about. Open ended if it says "until further notice". */
    internal fun range(text: String, now: Instant): DayRange? {
        val days = Dates.all(text, now)
        val first = days.minOrNull() ?: return null
        val last = days.maxOrNull()
        val openEnded = openEndedRegex.containsMatchIn(text) || (first == last && startsRegex.containsMatchIn(text))
        return DayRange(first, if (openEnded) null else last)
    }

    /** "Southbound - 5 a.m. to 11 a.m." or "Northbound - 1:45 to 11 p.m." */
    internal fun window(text: String): LaneWindow? {
        val lower = text.lowercase()
        val status = when {
            lower.startsWith("southbound") -> LaneStatus.SOUTHBOUND
            lower.startsWith("northbound") -> LaneStatus.NORTHBOUND
            else -> return null
        }
        if (lower.contains("24 hours")) return LaneWindow(status, TimeOfDay.MIDNIGHT, TimeOfDay.END_OF_DAY)

        val sep = lower.indexOfFirst { it == '-' || it == '–' || it == '—' || it == ':' }
        if (sep < 0) return null
        val parts = lower.substring(sep + 1).split(" to ")
        if (parts.size < 2) return null
        val end = Times.parse(parts[1]) ?: return null

        // Times like "1:45 to 11 p.m." borrow the am/pm from the end time
        var start = Times.parse(parts[0], defaultPm = end.pm) ?: return null
        if (start.time >= end.time && end.pm && !start.explicit) {
            start = Times.parse(parts[0], defaultPm = false) ?: start
        }
        val endTime = if (end.time == TimeOfDay.MIDNIGHT) TimeOfDay.END_OF_DAY else end.time
        return LaneWindow(status, start.time, endTime)
    }

    private const val TIME = "(\\d{1,2}(?::\\d{2})?\\s*[ap]\\.?\\s?m\\.?|noon|midnight)"
    private const val DAY = "(?:[A-Za-z]+day,?\\s+)?"
    private const val DATE = "([A-Za-z]{3,9}\\.?\\s+\\d{1,2})"
    private const val SEP = "\\s+(?:to|through|until|-|–)\\s+"

    // "11 p.m. Friday, Dec. 4 to 4 a.m. Monday, Dec. 7"
    private val timeFirst = Regex("$TIME\\s+$DAY$DATE$SEP$TIME\\s+$DAY$DATE", RegexOption.IGNORE_CASE)

    // "Friday, Dec. 4 at 11 p.m. to Monday, Dec. 7 at 4 a.m."
    private val dateFirst = Regex("$DAY$DATE,?\\s+at\\s+$TIME$SEP$DAY$DATE,?\\s+at\\s+$TIME", RegexOption.IGNORE_CASE)

    /** Every closure with exact times in an event, like two weekends in one notice */
    internal fun closures(event: SpecialEvent, now: Instant): List<Closure> {
        // Only trust headings that say closure, so lane reductions don't count
        if (!event.title.lowercase().contains("clos")) return emptyList()
        val found = timeFirst.findAll(event.detail).mapNotNull { m ->
            val g = m.groupValues
            make(event.title, g[2], g[1], g[4], g[3], now)
        } + dateFirst.findAll(event.detail).mapNotNull { m ->
            val g = m.groupValues
            make(event.title, g[1], g[2], g[3], g[4], now)
        }
        // No exact times means an empty list. Don't guess, Trust flags those days instead.
        return found.distinct().sortedBy { it.start }.toList()
    }

    internal fun closure(event: SpecialEvent, now: Instant): Closure? = closures(event, now).firstOrNull()

    private fun make(title: String, d1: String, t1: String, d2: String, t2: String, now: Instant): Closure? {
        val day1 = Dates.all(d1, now).firstOrNull() ?: return null
        var day2 = Dates.all(d2, now).firstOrNull() ?: return null
        val time1 = Times.parse(t1)?.time ?: return null
        val time2 = Times.parse(t2)?.time ?: return null
        if (day2.isBefore(day1)) day2 = day2.plusYears(1)
        return Closure(title, Seattle.instant(day1, time1), Seattle.instant(day2, time2))
    }

    internal fun absolute(href: String?): String? = when {
        href == null -> null
        href.startsWith("http") -> href
        href.startsWith("/") -> "https://wsdot.wa.gov$href"
        else -> null
    }
}

internal object Times {
    data class Parsed(val time: TimeOfDay, val pm: Boolean, /** False when am/pm was guessed */ val explicit: Boolean)

    private val clock = Regex("(\\d{1,2})(?::(\\d{2}))?\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?")
    private val explicitClock = Regex("(\\d{1,2}(?::\\d{2})?\\s*[ap]\\.?\\s?m\\.?|\\bnoon\\b|\\bmidnight\\b)", RegexOption.IGNORE_CASE)

    fun parse(text: String, defaultPm: Boolean? = null): Parsed? {
        val lower = text.lowercase()
        if (lower.contains("noon")) return Parsed(TimeOfDay.of(12), pm = true, explicit = true)
        if (lower.contains("midnight")) return Parsed(TimeOfDay.MIDNIGHT, pm = false, explicit = true)

        val g = clock.find(lower)?.groupValues ?: return null
        var hour = g[1].toIntOrNull() ?: return null
        if (hour > 12) return null
        val minute = g[2].toIntOrNull() ?: 0
        if (minute > 59) return null
        val explicit = g[3].isNotEmpty()
        val pm = if (explicit) g[3].startsWith("p") else (defaultPm ?: false)
        if (hour == 12) hour = 0
        if (pm) hour += 12
        return Parsed(TimeOfDay.of(hour, minute), pm, explicit)
    }

    /** Only times that say a.m., p.m., noon or midnight, so "Nov. 30" isn't read as 30 o'clock */
    fun explicit(text: String): TimeOfDay? = explicitClock.find(text)?.let { parse(it.value)?.time }

    fun firstAfter(phrase: String, text: String): TimeOfDay? {
        val i = text.indexOf(phrase, ignoreCase = true)
        if (i < 0) return null
        return parse(text.substring(i + phrase.length))?.time
    }
}

internal object Dates {
    private val months = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")
    // "Dec. 4-7" also counts the 7, but "Nov. 16 - 4 p.m." doesn't count the 4
    private val pattern = Regex(
        "\\b(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\.?\\s+(\\d{1,2})\\b" +
            "(?:\\s*[-–]\\s*(\\d{1,2})\\b(?!\\s*(?::|[ap]\\.?\\s?m\\b|\\d)))?",
        RegexOption.IGNORE_CASE,
    )

    /** Finds dates like "Nov. 16" or "December 4" and picks the year closest to `now`. */
    fun all(text: String, now: Instant): List<LocalDate> {
        val today = Seattle.day(now)
        fun date(month: Int, day: Int) = listOf(today.year - 1, today.year, today.year + 1)
            .mapNotNull { y -> runCatching { LocalDate.of(y, month, day) }.getOrNull() }
            .minByOrNull { Math.abs(Seattle.daysBetween(today, it)) }
        return pattern.findAll(text).flatMap { m ->
            val month = months.indexOf(m.groupValues[1].lowercase().take(3)) + 1
            if (month == 0) return@flatMap emptySequence()
            val first = m.groupValues[2].toIntOrNull()?.let { date(month, it) }
            val last = m.groupValues[3].toIntOrNull()?.let { date(month, it) }?.takeIf { first != null && it.isAfter(first) }
            sequenceOf(first, last).filterNotNull()
        }.toList()
    }
}
