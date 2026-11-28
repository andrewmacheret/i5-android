package com.andrewmacheret.i5

import android.content.Context
import com.andrewmacheret.i5.core.FetchResult
import com.andrewmacheret.i5.core.LaneStatus
import com.andrewmacheret.i5.core.LiveReading
import com.andrewmacheret.i5.core.Seattle
import com.andrewmacheret.i5.core.ServerStatus
import com.andrewmacheret.i5.core.StatusJson
import com.andrewmacheret.i5.core.TimeOfDay
import com.andrewmacheret.i5.core.Timeline
import com.andrewmacheret.i5.core.TripTimes
import com.andrewmacheret.i5.core.Trips
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Made up moments for screenshots and testing, debug builds only.
 * They use real /status answers saved by i5-server-lambdas (the core tests' fixtures, bundled as
 * debug assets), with the clock moved and WSDOT's sensors made to match.
 */
object Scenarios {
    data class Scenario(val now: Instant, val result: FetchResult, val refreshing: Boolean = false)

    val names = listOf(
        "north", "evening", "south", "closed", "weekend", "closure", "notice", "holiday",
        "stale", "checking", "failed", "sensors", "saved", "offline", "loading",
    )

    private var cached: Pair<String, Scenario?>? = null

    fun current(context: Context): Scenario? {
        val name = Prefs(context).scenario ?: return null
        cached?.let { if (it.first == name) return it.second }
        val s = runCatching { build(context, name) }.getOrNull()
        cached = name to s
        return s
    }

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = Seattle.instant(LocalDate.of(y, mo, d), TimeOfDay.of(h, mi))

    private fun build(context: Context, name: String): Scenario? {
        fun read(file: String) = context.assets.open(file).bufferedReader().use { it.readText() }
        val cameras = StatusJson.decode(read("cameras.json")).cameras
        fun fixture(file: String) = StatusJson.fixture(read("$file.json"))

        // The server read WSDOT 4 minutes ago, and the sensors agree with the timeline
        fun fresh(now: Instant, file: String = "confident"): ServerStatus {
            val s = fixture(file).status
            val read = now.minus(Duration.ofMinutes(4))
            return s.copy(fetchedAt = read, checkedAt = read, live = s.published?.let { sensors(Timeline.statusAt(it, read), read) }, cameras = cameras)
        }
        fun live(now: Instant, file: String = "confident") = Scenario(now, FetchResult(fresh(now, file), now))
        // As saved, at the moment it was saved for
        fun asIs(file: String): Scenario {
            val f = fixture(file)
            return Scenario(f.at, FetchResult(f.status.copy(cameras = cameras), f.at))
        }

        return when (name) {
            "north" -> live(at(2026, 12, 1, 16, 55))
            // A regular weekday, so the widget shows the widest time, 11:00 PM
            "evening" -> live(at(2026, 12, 10, 16, 55))
            "south" -> live(at(2026, 12, 2, 7, 30))
            "closed" -> live(at(2026, 12, 1, 23, 20))
            "weekend" -> live(at(2026, 12, 12, 14, 30))
            "closure" -> live(at(2026, 12, 5, 10))
            // Closing early tonight
            "notice" -> live(at(2026, 12, 2, 16))
            "holiday" -> live(at(2026, 11, 27, 10), "holiday")
            "stale" -> asIs("stale")
            "checking" -> asIs("checking")
            "failed" -> asIs("failed")
            "sensors" -> {
                val now = at(2026, 12, 1, 16, 55)
                val s = fresh(now)
                Scenario(now, FetchResult(s.copy(live = sensors(LaneStatus.SOUTHBOUND, s.fetchedAt!!)), now))
            }
            "saved" -> {
                // The phone can't reach the server, and its copy is 3 hours old
                val now = at(2026, 12, 1, 16, 55)
                val then = at(2026, 12, 1, 13, 55)
                Scenario(now, FetchResult(fresh(then), then, error = "Unable to resolve host \"i5.andrewmacheret.com\""))
            }
            "offline" -> Scenario(
                at(2026, 12, 1, 16, 55),
                FetchResult(ServerStatus.EMPTY, null, error = "Unable to resolve host \"i5.andrewmacheret.com\""),
            )
            "loading" -> Scenario(at(2026, 12, 1, 16, 55), FetchResult(ServerStatus.EMPTY, null), refreshing = true)
            else -> null
        }
    }

    /** Travel times as WSDOT's sensors would measure them with the lanes going this way */
    private fun sensors(open: LaneStatus, at: Instant): LiveReading {
        val north = open == LaneStatus.NORTHBOUND
        val south = open == LaneStatus.SOUTHBOUND
        return LiveReading(
            at,
            northbound = if (north) 18 else 0,
            southbound = if (south) 16 else 0,
            trips = Trips(
                northbound = TripTimes(if (north) 18 else null, if (north) 26 else 14, if (north) 22 else 15),
                southbound = TripTimes(if (south) 16 else null, if (south) 29 else 15, if (south) 24 else 16),
            ),
        )
    }
}
