package com.andrewmacheret.i5

import android.content.Context
import com.andrewmacheret.i5.core.DataSource
import com.andrewmacheret.i5.core.ExpressLanesInfo
import com.andrewmacheret.i5.core.FetchResult
import com.andrewmacheret.i5.core.Parser
import com.andrewmacheret.i5.core.Seattle
import com.andrewmacheret.i5.core.TimeOfDay
import java.time.Duration
import java.time.Instant
import java.time.LocalDate

/**
 * Made up moments for screenshots and testing, debug builds only.
 * They use a saved copy of WSDOT's page from Nov 18, 2026, bundled as a debug asset.
 */
object Scenarios {
    data class Scenario(val now: Instant, val result: FetchResult, val refreshing: Boolean = false)

    val names = listOf("north", "south", "closed", "weekend", "closure", "notice", "holiday", "stale", "checking", "offline", "broken")

    private var cached: Pair<String, Scenario?>? = null

    fun current(context: Context): Scenario? {
        val name = Prefs(context).scenario ?: return null
        cached?.let { if (it.first == name) return it.second }
        val s = build(context, name)
        cached = name to s
        return s
    }

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int = 0) = Seattle.instant(LocalDate.of(y, mo, d), TimeOfDay.of(h, mi))

    private fun build(context: Context, name: String): Scenario? {
        val html = runCatching { context.assets.open("scenario-page.html").bufferedReader().use { it.readText() } }.getOrNull() ?: return null
        fun live(now: Instant, refreshing: Boolean = false) =
            Scenario(now, FetchResult(Parser.parse(html, now), DataSource.Live(now.minus(Duration.ofMinutes(4)))), refreshing)

        return when (name) {
            "north" -> live(at(2026, 11, 18, 16, 55))
            "south" -> live(at(2026, 11, 20, 7, 30))
            "closed" -> live(at(2026, 11, 18, 23, 20))
            "weekend" -> live(at(2026, 11, 21, 14, 30))
            "closure" -> live(at(2026, 12, 5, 10))
            "notice" -> live(at(2026, 11, 17, 8))
            "holiday" -> live(at(2026, 11, 26, 10))
            "stale" -> {
                val now = at(2026, 11, 18, 16, 55)
                Scenario(now, FetchResult(Parser.parse(html, now), DataSource.Cached(at(2026, 11, 18, 13, 30)), error = "Unable to resolve host"))
            }
            "checking" -> Scenario(at(2026, 11, 18, 16, 55), FetchResult(ExpressLanesInfo.BUILT_IN, DataSource.BuiltIn), refreshing = true)
            "offline" -> Scenario(
                at(2026, 11, 18, 16, 55),
                FetchResult(ExpressLanesInfo.BUILT_IN, DataSource.BuiltIn, error = "Unable to resolve host \"wsdot.wa.gov\""),
            )
            "broken" -> {
                // WSDOT added a section the app doesn't know
                val now = at(2026, 11, 18, 16, 55)
                val odd = Parser.parse(html, now).copy(warnings = listOf("New schedule section on WSDOT's page: Holiday hours"))
                Scenario(now, FetchResult(odd, DataSource.Live(now.minus(Duration.ofMinutes(4)))))
            }
            else -> null
        }
    }
}
