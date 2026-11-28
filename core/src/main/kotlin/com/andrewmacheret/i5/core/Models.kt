package com.andrewmacheret.i5.core

import java.time.Instant

// What the server publishes at GET /status, and the app draws.
// Ported from src/core in i5-server-lambdas, which is the source of truth. Keep them in step.

enum class LaneStatus(val title: String, val letter: String) {
    NORTHBOUND("Northbound", "N"),
    SOUTHBOUND("Southbound", "S"),
    CLOSED("Closed", "✕"),
    UNKNOWN("Unknown", "?");

    val isOpen: Boolean get() = this == NORTHBOUND || this == SOUTHBOUND

    companion object {
        fun of(name: String?): LaneStatus = when (name) {
            "northbound" -> NORTHBOUND
            "southbound" -> SOUTHBOUND
            "closed" -> CLOSED
            else -> UNKNOWN
        }
    }
}

/** Minutes since midnight, 0..1440. Use `TimeOfDay.of(hour, minute)` for clock times. */
data class TimeOfDay(val minutes: Int) : Comparable<TimeOfDay> {
    override fun compareTo(other: TimeOfDay) = minutes.compareTo(other.minutes)

    /** "5 AM", "11:15 AM", "Midnight" */
    val label: String
        get() {
            val m = minutes % (24 * 60)
            if (m == 0) return "Midnight"
            if (m == 12 * 60) return "Noon"
            val h = m / 60
            val mm = m % 60
            val h12 = if (h % 12 == 0) 12 else h % 12
            val ampm = if (h < 12) "AM" else "PM"
            return if (mm == 0) "$h12 $ampm" else "%d:%02d %s".format(h12, mm, ampm)
        }

    companion object {
        val MIDNIGHT = TimeOfDay(0)
        val END_OF_DAY = TimeOfDay(24 * 60)
        fun of(hour: Int, minute: Int = 0): TimeOfDay {
            require(hour in 0..24 && minute in 0..59)
            return TimeOfDay(hour * 60 + minute)
        }
    }
}

/** An open window. Any time not covered by a window is closed. */
data class LaneWindow(val status: LaneStatus, val start: TimeOfDay, val end: TimeOfDay)

data class WeeklySchedule(val weekday: List<LaneWindow>, val weekend: List<LaneWindow>) {
    companion object {
        /** The schedule WSDOT posted as of Nov 2026. Used when the page lists none. */
        val BUILT_IN = WeeklySchedule(
            weekday = listOf(
                LaneWindow(LaneStatus.SOUTHBOUND, TimeOfDay.of(5), TimeOfDay.of(11)),
                LaneWindow(LaneStatus.NORTHBOUND, TimeOfDay.of(11, 15), TimeOfDay.of(23)),
            ),
            weekend = listOf(
                LaneWindow(LaneStatus.SOUTHBOUND, TimeOfDay.of(8), TimeOfDay.of(13, 30)),
                LaneWindow(LaneStatus.NORTHBOUND, TimeOfDay.of(13, 45), TimeOfDay.of(23)),
            ),
        )
    }
}

/** A stretch of time with one status. Published segments cover the horizon with no gaps. */
data class Segment(val start: Instant, val end: Instant, val status: LaneStatus)

/** A run of one status across part of a Seattle day, in wall clock minutes */
data class DaySegment(val start: TimeOfDay, val end: TimeOfDay, val status: LaneStatus)

/** A stretch of time the server can't vouch for, with the reason */
data class Doubt(
    val start: Instant,
    val end: Instant,
    val title: String,
    val detail: String,
    val link: String?,
    /** "page", "alert", "holiday" or "models" */
    val source: String,
    /** True while a new page or alert is still being read, which usually clears up within minutes */
    val checking: Boolean = false,
)

/** Null start or end means the notice didn't give one */
data class NoticeSpan(val start: Instant?, val end: Instant?)

/** One notice from the page or an alert, for the lists */
data class Notice(
    val title: String,
    /** The page's own words */
    val detail: String,
    val link: String?,
    /** "northbound", "southbound", "closed", "weekday_schedule", "weekend_schedule", "other" or "unclear" */
    val kind: String,
    val spans: List<NoticeSpan>,
    /** "exact", "approximate" or "unknown" */
    val startCertainty: String,
    val endCertainty: String,
    /** "page" or "alert" */
    val source: String,
)

/** A page version or alert the server has read */
data class DocumentInfo(
    val kind: String,
    val hash: String,
    /** When the server first saw this version */
    val firstSeen: Instant?,
    /** Models whose answers were used, or "reviewed" for an answer a person wrote */
    val models: List<String>,
    /** "answered", "pending" or "failed" */
    val status: String,
    val link: String?,
)

data class Horizon(val start: Instant, val end: Instant)

/** What the server worked out from WSDOT's page and alerts */
data class Published(
    val publishedAt: Instant,
    /** Segments cover exactly this range */
    val horizon: Horizon,
    /** The weekly schedule as the page lists it, or null when it lists none */
    val schedule: WeeklySchedule?,
    val segments: List<Segment>,
    val doubts: List<Doubt>,
    val notices: List<Notice>,
    val page: DocumentInfo?,
    val alerts: List<DocumentInfo>,
)

/** Minutes for one trip. Null when WSDOT measured nothing. */
data class TripTimes(
    /** Through the express lanes. Null while they're closed or run the other way. */
    val express: Int?,
    /** On the regular lanes */
    val regular: Int?,
    /** What the regular lanes usually take at this time of day */
    val usual: Int?,
)

data class Trips(val northbound: TripTimes, val southbound: TripTimes)

/** WSDOT's travel times through the express lanes, which only exist in the direction they're open */
data class LiveReading(
    /** When WSDOT measured it */
    val at: Instant,
    /** Minutes from Seattle to Everett through the express lanes, or null when there's no measurement */
    val northbound: Int?,
    val southbound: Int?,
    /** Downtown Seattle to Everett and back, express lanes against the regular lanes. Missing in old readings. */
    val trips: Trips? = null,
)

/** A WSDOT traffic camera along the express lanes */
data class Camera(
    val id: Long,
    /** Where it is, like "Roanoke St Express Lanes" */
    val name: String,
    val milepost: Double,
    /** WSDOT's name for it says it looks at the express lanes */
    val express: Boolean,
    val lat: Double,
    val lon: Double,
    /** A JPEG on WSDOT's server, updated about every 5 minutes */
    val image: String,
)

/** GET /status */
data class ServerStatus(
    val published: Published?,
    /** When the server last read WSDOT's page successfully */
    val fetchedAt: Instant?,
    /** When the server last tried */
    val checkedAt: Instant?,
    /** Why the last try failed */
    val error: String?,
    val live: LiveReading?,
    /** Cameras along the express lanes, south to north. Empty from older servers. */
    val cameras: List<Camera> = emptyList(),
) {
    companion object {
        val EMPTY = ServerStatus(null, null, null, null, null)
    }
}

object Sources {
    const val STATUS = "https://i5.andrewmacheret.com/status"
    const val EXPRESS_LANES = "https://wsdot.wa.gov/travel/roads-bridges/express-lanes"
    const val MAP = "https://wsdot.com/Travel/Real-time/Map/"

    /** The travel map on the express lanes, with cameras, traffic and alerts on */
    const val TRAVEL_MAP =
        "https://wsdot.com/Travel/Real-time/Map/?layers=traffic-flow%2Calert%2Ccamera&base=wsdot&extent=-122.40%2C47.595%2C-122.25%2C47.715&fromLink=true"

    /** WSDOT Traffic on Bluesky, for King and Snohomish counties. Readable without signing in. */
    const val WSDOT_TRAFFIC = "https://bsky.app/profile/traffic.wsdot.wa.gov"
    const val PRIVACY = "https://github.com/andrewmacheret/i5-android/blob/main/PRIVACY.md"
}
